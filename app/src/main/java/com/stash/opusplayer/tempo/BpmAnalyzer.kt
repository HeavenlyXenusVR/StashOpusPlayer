package com.stash.opusplayer.tempo

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device tempo (BPM) detection, ported from Lumisound's BPMAnalyzerService
 * (ios/Lumisound/Sources/Services/BPMAnalyzerService.swift) -- itself a Swift
 * port of the ios-bridge's own `_estimate_bpm`. Not a published beat-tracking
 * algorithm (no Ellis dynamic-programming tracker, no onset-detection-function
 * library) -- a deliberately simple 4-step pipeline: energy envelope -> onset
 * envelope (half-wave-rectified first difference) -> brute-force autocorrelation
 * peak-pick over a 60-200 BPM-bounded lag range. Ported verbatim rather than
 * swapped for something more sophisticated, so results stay consistent with
 * whatever a Lumisound user analyzing the exact same file would see.
 *
 * The one real platform difference from the Swift original: `AVAssetReader`
 * can request PCM output resampled to an arbitrary target rate as part of its
 * output settings; Android's `MediaCodec` decodes at the source's native rate
 * only. This decodes at native rate, downmixes to mono, then downsamples via
 * simple block-averaging to the same ~11025 Hz analysis rate the algorithm
 * expects -- a coarser resample than a proper polyphase filter, but the
 * algorithm's own 20ms energy-envelope windowing already discards far more
 * time resolution than block-averaging costs, so this doesn't measurably
 * affect the result.
 */
object BpmAnalyzer {

    private const val TARGET_SAMPLE_RATE = 11025
    private const val MAX_SECONDS = 60
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 200.0
    private const val DECODE_TIMEOUT_US = 10_000L

    /** Runs the full decode + estimate pipeline. `null` if the file can't be decoded or no confident tempo is found. */
    suspend fun analyze(context: Context, path: String): Double? = withContext(Dispatchers.Default) {
        val mono = decodeMonoDownsampled(context, path) ?: return@withContext null
        estimateBpm(mono, TARGET_SAMPLE_RATE.toDouble())
    }

    // MARK: - Algorithm (operates on already-mono, already-downsampled PCM)

    private fun estimateBpm(samples: ShortArray, sampleRate: Double): Double? {
        if (samples.size < sampleRate * 4) return null

        // ~20ms windows, matching the Swift original's `sampleRate/50`.
        val window = max(1, (sampleRate / 50).toInt())
        val frameCount = samples.size / window
        if (frameCount < 20) return null

        val energies = DoubleArray(frameCount)
        for (i in 0 until frameCount) {
            var sum = 0.0
            val base = i * window
            for (j in 0 until window) {
                val s = samples[base + j].toDouble()
                sum += s * s
            }
            energies[i] = sum / window
        }

        // Half-wave-rectified first difference -- a coarse onset-detection function.
        val onsets = DoubleArray(frameCount)
        for (i in 1 until frameCount) {
            onsets[i] = max(0.0, energies[i] - energies[i - 1])
        }

        val frameRate = sampleRate / window
        val minLag = (frameRate * 60 / MAX_BPM).toInt()
        val maxLag = min((frameRate * 60 / MIN_BPM).toInt(), frameCount - 1)
        if (minLag <= 0 || minLag >= maxLag) return null

        var bestLag = -1
        var bestScore = -1.0
        for (lag in minLag..maxLag) {
            var score = 0.0
            for (idx in lag until frameCount) {
                score += onsets[idx] * onsets[idx - lag]
            }
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag <= 0) return null

        val bpm = 60.0 * frameRate / bestLag
        return (bpm * 10).roundToInt() / 10.0
    }

    // MARK: - Decode: MediaExtractor (compressed) -> MediaCodec (PCM) -> mono -> downsampled

    /**
     * Decodes up to [MAX_SECONDS] of audio to mono PCM at the source's native
     * sample rate, then downsamples to [TARGET_SAMPLE_RATE]. Follows the same
     * `content://` vs. raw-path `MediaExtractor` setup and audio-track
     * selection [com.stash.opusplayer.library.AudioFileValidator] already
     * established for this codebase, extended with an actual `MediaCodec`
     * decode loop (no decode-to-PCM helper existed anywhere in this codebase
     * before this).
     */
    private fun decodeMonoDownsampled(context: Context, path: String): ShortArray? {
        val extractor = MediaExtractor()
        val codec: MediaCodec
        val sourceSampleRate: Int
        val channelCount: Int
        try {
            try {
                if (path.startsWith("content://")) {
                    extractor.setDataSource(context, Uri.parse(path), null)
                } else {
                    extractor.setDataSource(path)
                }
            } catch (e: Exception) {
                return null
            }

            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) return null
            extractor.selectTrack(audioTrack)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            sourceSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else return null
            channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1

            codec = try {
                MediaCodec.createDecoderByType(mime).apply {
                    configure(format, null, null, 0)
                    start()
                }
            } catch (e: Exception) {
                return null
            }
        } catch (e: Exception) {
            extractor.release()
            return null
        }

        val maxMonoSamples = sourceSampleRate.toLong() * MAX_SECONDS
        val pcmOut = ByteArrayOutputStream(min(maxMonoSamples * 2, Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1024))

        try {
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            var monoSamplesWritten = 0L

            while (!sawOutputEOS && monoSamplesWritten < maxMonoSamples) {
                if (!sawInputEOS) {
                    val inputIndex = codec.dequeueInputBuffer(DECODE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DECODE_TIMEOUT_US)
                if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            monoSamplesWritten += downmixAndWrite(chunk, channelCount, pcmOut)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }
        } catch (e: Exception) {
            return null
        } finally {
            try { codec.stop() } catch (_: Exception) {}
            codec.release()
            extractor.release()
        }

        val monoBytes = pcmOut.toByteArray()
        if (monoBytes.size < 4) return null
        val monoShorts = ShortArray(monoBytes.size / 2)
        ByteBuffer.wrap(monoBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(monoShorts)

        return downsample(monoShorts, sourceSampleRate, TARGET_SAMPLE_RATE)
    }

    /** Downmixes an interleaved 16-bit PCM chunk to mono by averaging channels, appending the result to [sink]. Returns mono sample count written. */
    private fun downmixAndWrite(chunk: ByteArray, channelCount: Int, sink: ByteArrayOutputStream): Long {
        val buffer = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
        val frameCount = chunk.size / 2 / channelCount
        var written = 0L
        for (i in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channelCount) {
                sum += buffer.short.toInt()
            }
            val mono = (sum / channelCount).toShort()
            sink.write(mono.toInt() and 0xFF)
            sink.write((mono.toInt() shr 8) and 0xFF)
            written++
        }
        return written
    }

    /** Simple block-averaging decimation -- see the type doc comment for why this is an acceptable substitute for a proper resample here. */
    private fun downsample(mono: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
        if (sourceRate <= targetRate || mono.isEmpty()) return mono
        val ratio = sourceRate.toDouble() / targetRate
        val outLength = (mono.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outLength)
        for (i in 0 until outLength) {
            val start = (i * ratio).toInt()
            val end = min(mono.size, ((i + 1) * ratio).toInt()).coerceAtLeast(start + 1)
            var sum = 0
            for (j in start until end) sum += mono[j]
            out[i] = (sum / (end - start)).toShort()
        }
        return out
    }
}
