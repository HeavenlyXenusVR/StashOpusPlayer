package com.stash.opusplayer.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.stash.opusplayer.audio.settings.ReverbRoomPreset
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * A genuine parallel wet/dry algorithmic reverb, implemented as a Media3
 * [AudioProcessor] and dropped into the ExoPlayer audio pipeline (see
 * [ReverbRenderersFactory]).
 *
 * Design rationale (mirrors Lumisound's iOS engine, see
 * `AudioPlayerManager+EngineConfig.swift` / `+ApplySettings.swift`):
 * Lumisound's `AVAudioUnitReverb` is configured 100% wet and summed with a
 * separately gain-controlled dry path via two mixer buses feeding a common
 * summing node — a true parallel wet/dry topology, as opposed to an
 * insert-only effect (like `android.media.audiofx.PresetReverb`, still used
 * unmodified by [EqualizerManager] for the existing EQ screen) that can only
 * process the signal in-line.
 *
 * Android has no droppable "just give me a 100% wet signal" system unit
 * equivalent to `AVAudioUnitReverb`, so this class implements the reverb
 * algorithm itself from scratch — a classic Schroeder/Moorer topology (4
 * parallel damped comb filters summed together, feeding 2 series allpass
 * filters for diffusion) — entirely in floating point over the PCM buffers
 * Media3 hands to `AudioProcessor.queueInput`. Because Media3's `AudioSink`
 * pipeline is a simple linear chain of processors rather than a graph of
 * mixer nodes, the "parallel bus" topology is realised *inside this single
 * processor*: for every sample it computes the wet signal from the comb/
 * allpass network fed by the dry sample, then sums
 * `dry*dryGain + wet*wetGain` and emits that as the sole output — which is
 * functionally identical to routing dry and 100%-wet signals into two gain
 * buses and summing them, just without needing separate node objects.
 *
 * Thread-safety: [reverbEnabled], [wetDryMix] and [preset] are written from
 * the app's main thread (settings changes) and read from ExoPlayer's
 * internal playback thread inside [queueInput]. Scalar fields are
 * `@Volatile` for cross-thread visibility; the comb/allpass delay-line
 * networks (which must be rebuilt, not just read, on a preset or format
 * change) are guarded by [stateLock].
 */
@UnstableApi
class ParallelReverbAudioProcessor : BaseAudioProcessor() {

    private val stateLock = Any()

    /** Guarded by [stateLock]. One network per output channel. */
    private var channels: Array<ChannelReverbNetwork> = emptyArray()

    /** Guarded by [stateLock]. Format last passed to [onConfigure]. */
    private var configuredSampleRate: Int = 0
    private var configuredChannelCount: Int = 0

    /** Master on/off switch. When false, audio passes through completely unmodified. */
    @Volatile
    var reverbEnabled: Boolean = true
        set(value) {
            val wasEnabled = field
            field = value
            if (wasEnabled && !value) {
                // Silence the delay lines while bypassed so a later re-enable doesn't
                // suddenly bleed in a stale, possibly loud, reverb tail from whatever
                // was playing when the effect was switched off.
                synchronized(stateLock) {
                    channels.forEach { it.clear() }
                }
            }
        }

    /** Wet/dry mix, 0-100, matching [com.stash.opusplayer.audio.settings.AudioSettings.reverbWetDryMix]. */
    @Volatile
    var wetDryMix: Float = 18.0f
        set(value) {
            field = value.coerceIn(0f, 100f)
        }

    /** Room/space character; changing this rebuilds the comb/allpass delay-line network in place. */
    @Volatile
    var preset: ReverbRoomPreset = ReverbRoomPreset.MEDIUM_ROOM
        set(value) {
            field = value
            synchronized(stateLock) {
                if (configuredChannelCount > 0) {
                    rebuildChannelsLocked(configuredSampleRate, configuredChannelCount, value)
                }
            }
        }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.channelCount <= 0 || inputAudioFormat.sampleRate <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        // Deliberately does NOT (re)build the comb/allpass networks here: per the
        // AudioProcessor contract, this "pending" format only becomes the live format
        // that queueInput/inputAudioFormat use once flush() is called (flush() is what
        // reassigns the base class's `inputAudioFormat` field to this new format). If we
        // rebuilt the channel networks right now, any queueInput calls made in the
        // window between this configure() and the next flush() -- which are still
        // required to use the OLD format -- would run old-format audio through
        // networks already sized for the NEW format. See onFlush() for the actual
        // (re)build, which runs after the field switchover.

        // Identity format: this processor only mixes in a wet signal, it never changes
        // sample rate, channel count or encoding.
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameSizeBytes = inputAudioFormat.bytesPerFrame
        val channelCount = inputAudioFormat.channelCount
        if (frameSizeBytes <= 0 || channelCount <= 0) {
            // Defensive: shouldn't happen for the PCM formats accepted in onConfigure.
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val frameCount = inputBuffer.remaining() / frameSizeBytes
        if (frameCount <= 0) {
            return
        }
        val bytesToProcess = frameCount * frameSizeBytes
        val outputBuffer = replaceOutputBuffer(bytesToProcess)

        val networks = synchronized(stateLock) { channels }
        val enabled = reverbEnabled

        if (!enabled || networks.size != channelCount) {
            // Bypass: either the effect is off, or (transiently) the channel network
            // hasn't been (re)built for this format yet. Pass audio through untouched
            // rather than dropping it.
            val originalLimit = inputBuffer.limit()
            inputBuffer.limit(inputBuffer.position() + bytesToProcess)
            outputBuffer.put(inputBuffer)
            inputBuffer.limit(originalLimit)
            outputBuffer.flip()
            return
        }

        val mix = wetDryMix.coerceIn(0f, 100f) / 100f
        val theta = (mix * (Math.PI / 2.0)).toFloat()
        val dryGain = cos(theta)
        val wetGain = sin(theta)

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                for (frame in 0 until frameCount) {
                    for (ch in 0 until channelCount) {
                        val dry = inputBuffer.getShort().toFloat() / 32768f
                        val wet = networks[ch].process(dry)
                        val mixed = clamp(dry * dryGain + wet * wetGain)
                        outputBuffer.putShort((mixed * 32767f).toInt().toShort())
                    }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                for (frame in 0 until frameCount) {
                    for (ch in 0 until channelCount) {
                        val dry = inputBuffer.getFloat()
                        val wet = networks[ch].process(dry)
                        outputBuffer.putFloat(clamp(dry * dryGain + wet * wetGain))
                    }
                }
            }
        }
        outputBuffer.flip()
    }

    override fun onFlush() {
        // By this point the base class has already reassigned `inputAudioFormat` to the
        // format most recently passed to onConfigure (see BaseAudioProcessor.flush()),
        // so it's now safe to (re)build the per-channel networks against it. A flush
        // triggered by a plain seek (no format change since the last flush) is the
        // common case: just silence the existing delay lines rather than reallocating.
        synchronized(stateLock) {
            val sampleRate = inputAudioFormat.sampleRate
            val channelCount = inputAudioFormat.channelCount
            if (channelCount <= 0 || sampleRate <= 0) {
                channels = emptyArray()
                configuredSampleRate = 0
                configuredChannelCount = 0
                return@synchronized
            }
            if (sampleRate != configuredSampleRate ||
                channelCount != configuredChannelCount ||
                channels.size != channelCount
            ) {
                configuredSampleRate = sampleRate
                configuredChannelCount = channelCount
                rebuildChannelsLocked(sampleRate, channelCount, preset)
            } else {
                channels.forEach { it.clear() }
            }
        }
    }

    override fun onReset() {
        synchronized(stateLock) {
            channels = emptyArray()
            configuredSampleRate = 0
            configuredChannelCount = 0
        }
    }

    /** Must be called while holding [stateLock]. */
    private fun rebuildChannelsLocked(sampleRate: Int, channelCount: Int, roomPreset: ReverbRoomPreset) {
        val params = paramsFor(roomPreset)
        channels = Array(channelCount) { index ->
            ChannelReverbNetwork().apply { configure(sampleRate, params, index) }
        }
    }

    private fun clamp(sample: Float): Float = when {
        sample > 1f -> 1f
        sample < -1f -> -1f
        else -> sample
    }
}

/**
 * A single channel's Schroeder/Moorer reverb network: 4 parallel damped comb
 * filters, summed and averaged, feeding 2 series allpass filters for
 * diffusion/smoothing.
 */
private class ChannelReverbNetwork {
    private var combs: Array<DampedCombFilter> = emptyArray()
    private var allpasses: Array<AllpassFilter> = emptyArray()

    fun configure(sampleRate: Int, params: ReverbPresetParams, channelIndex: Int) {
        // Small per-channel delay offset decorrelates L/R so a stereo signal reverbs
        // into a wider stereo tail instead of two identical mono-summed copies.
        val spreadMs = channelIndex * STEREO_SPREAD_MS

        combs = Array(params.combDelaysMs.size) { i ->
            val delayMs = params.combDelaysMs[i] + spreadMs
            DampedCombFilter(msToSamples(delayMs, sampleRate)).apply {
                feedback = combFeedbackForRt60(params.rt60Seconds, delayMs / 1000f)
                damping = params.dampingCoefficient
            }
        }
        allpasses = Array(params.allpassDelaysMs.size) { i ->
            val delayMs = params.allpassDelaysMs[i] + spreadMs
            AllpassFilter(msToSamples(delayMs, sampleRate), params.allpassFeedback)
        }
    }

    fun process(input: Float): Float {
        if (combs.isEmpty()) return 0f
        var combSum = 0f
        for (comb in combs) {
            combSum += comb.process(input)
        }
        // Parallel comb outputs are averaged (rather than left to sum to ~4x) to keep
        // the wet signal's level in the same ballpark as the dry signal feeding it.
        combSum /= combs.size
        var out = combSum
        for (allpass in allpasses) {
            out = allpass.process(out)
        }
        return out
    }

    fun clear() {
        combs.forEach { it.clear() }
        allpasses.forEach { it.clear() }
    }
}

/**
 * A feedback comb filter with a one-pole low-pass filter in the feedback
 * path (the "damped" part of Moorer's refinement over Schroeder's original
 * design), modelling the high-frequency absorption of a real room. Higher
 * [damping] darkens the decaying tail faster than the overall [feedback]-
 * controlled amplitude decay alone would.
 */
private class DampedCombFilter(delaySamples: Int) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var writeIndex = 0
    private var filterStore = 0f

    var feedback: Float = 0f
    var damping: Float = 0f

    fun process(input: Float): Float {
        val output = buffer[writeIndex]
        filterStore = output * (1f - damping) + filterStore * damping
        buffer[writeIndex] = input + filterStore * feedback
        writeIndex++
        if (writeIndex >= buffer.size) writeIndex = 0
        return output
    }

    fun clear() {
        buffer.fill(0f)
        filterStore = 0f
        writeIndex = 0
    }
}

/** A Schroeder allpass filter used for diffusion after the comb bank. */
private class AllpassFilter(delaySamples: Int, private val feedback: Float) {
    private val buffer = FloatArray(delaySamples.coerceAtLeast(1))
    private var writeIndex = 0

    fun process(input: Float): Float {
        val bufferedValue = buffer[writeIndex]
        val output = -input * feedback + bufferedValue
        buffer[writeIndex] = input + bufferedValue * feedback
        writeIndex++
        if (writeIndex >= buffer.size) writeIndex = 0
        return output
    }

    fun clear() {
        buffer.fill(0f)
        writeIndex = 0
    }
}

/** Per-preset DSP tuning: decay time and comb/allpass delay-line lengths. */
private class ReverbPresetParams(
    val rt60Seconds: Float,
    val combDelaysMs: FloatArray,
    val allpassDelaysMs: FloatArray,
    val dampingCoefficient: Float,
    val allpassFeedback: Float
)

/**
 * DSP parameter mapping for [ReverbRoomPreset]. Kept in this engine-wiring
 * layer rather than in `AudioSettings.kt`, per that file's own doc comment:
 * "the mapping to a concrete DSP preset... belongs in a later engine-wiring
 * layer, not in this schema."
 *
 * Comb delay lengths are deliberately non-harmonic (no common integer
 * ratios) to avoid the metallic/ringing coloration a Schroeder network gets
 * when its comb delays share factors. RT60 (seconds) is the target time for
 * the decay to fall by 60 dB; per-comb feedback gain is derived from it in
 * [combFeedbackForRt60] rather than hard-coded, so every comb in a preset's
 * bank decays to the same RT60 despite having different delay lengths.
 */
private fun paramsFor(preset: ReverbRoomPreset): ReverbPresetParams = when (preset) {
    ReverbRoomPreset.SMALL_ROOM -> ReverbPresetParams(
        rt60Seconds = 0.42f,
        combDelaysMs = floatArrayOf(25.3f, 28.9f, 32.1f, 35.7f),
        allpassDelaysMs = floatArrayOf(6.5f, 9.1f),
        dampingCoefficient = 0.20f,
        allpassFeedback = 0.50f
    )
    ReverbRoomPreset.MEDIUM_ROOM -> ReverbPresetParams(
        rt60Seconds = 0.75f,
        combDelaysMs = floatArrayOf(27.9f, 31.9f, 35.8f, 40.3f),
        allpassDelaysMs = floatArrayOf(7.1f, 10.1f),
        dampingCoefficient = 0.25f,
        allpassFeedback = 0.50f
    )
    ReverbRoomPreset.LARGE_ROOM -> ReverbPresetParams(
        rt60Seconds = 1.20f,
        combDelaysMs = floatArrayOf(30.6f, 34.7f, 39.3f, 44.0f),
        allpassDelaysMs = floatArrayOf(7.7f, 11.1f),
        dampingCoefficient = 0.28f,
        allpassFeedback = 0.50f
    )
    ReverbRoomPreset.MEDIUM_HALL -> ReverbPresetParams(
        rt60Seconds = 1.80f,
        combDelaysMs = floatArrayOf(33.4f, 37.9f, 42.7f, 47.9f),
        allpassDelaysMs = floatArrayOf(8.1f, 11.6f),
        dampingCoefficient = 0.32f,
        allpassFeedback = 0.50f
    )
    ReverbRoomPreset.LARGE_HALL -> ReverbPresetParams(
        rt60Seconds = 2.50f,
        combDelaysMs = floatArrayOf(36.2f, 41.0f, 46.1f, 51.7f),
        allpassDelaysMs = floatArrayOf(8.5f, 12.0f),
        dampingCoefficient = 0.35f,
        allpassFeedback = 0.50f
    )
    ReverbRoomPreset.PLATE -> ReverbPresetParams(
        rt60Seconds = 1.50f,
        // Denser/brighter: tighter comb spacing and shorter allpass delays than a
        // room/hall of comparable decay time, plus lower damping (brighter tail) and
        // higher allpass feedback (denser diffusion) per the plate-reverb brief.
        combDelaysMs = floatArrayOf(19.7f, 23.1f, 26.7f, 30.1f),
        allpassDelaysMs = floatArrayOf(3.7f, 5.9f),
        dampingCoefficient = 0.12f,
        allpassFeedback = 0.62f
    )
    ReverbRoomPreset.CATHEDRAL -> ReverbPresetParams(
        rt60Seconds = 4.20f,
        combDelaysMs = floatArrayOf(41.3f, 46.7f, 52.9f, 59.3f),
        allpassDelaysMs = floatArrayOf(9.7f, 13.9f),
        dampingCoefficient = 0.42f,
        allpassFeedback = 0.50f
    )
}

/** Per-channel comb/allpass delay-offset, for stereo decorrelation. */
private const val STEREO_SPREAD_MS = 0.83f

/** Safety ceiling on comb feedback gain to guarantee filter stability. */
private const val MAX_COMB_FEEDBACK = 0.98f

/**
 * Classic Schroeder feedback-gain formula: after `delaySeconds` of decay,
 * gain^1 has elapsed; after `rt60Seconds / delaySeconds` repetitions the
 * total should be -60 dB, i.e. `gain^(rt60/delaySeconds) = 10^(-3)`, which
 * solves to `gain = 10^(-3 * delaySeconds / rt60Seconds)`.
 */
private fun combFeedbackForRt60(rt60Seconds: Float, delaySeconds: Float): Float {
    if (rt60Seconds <= 0f || delaySeconds <= 0f) return 0f
    val exponent = -3.0 * delaySeconds / rt60Seconds
    val gain = Math.pow(10.0, exponent).toFloat()
    return gain.coerceIn(0f, MAX_COMB_FEEDBACK)
}

private fun msToSamples(ms: Float, sampleRate: Int): Int {
    val samples = (ms / 1000f) * sampleRate
    return samples.toInt().coerceAtLeast(1)
}
