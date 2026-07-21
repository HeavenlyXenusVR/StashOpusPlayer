package com.stash.opusplayer.audio

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Drops [ParallelReverbAudioProcessor] into the `DefaultAudioSink`'s audio
 * processor chain by overriding [buildAudioSink], the extension point
 * `DefaultRenderersFactory` exposes for exactly this purpose.
 *
 * Deliberately overrides nothing else: [DefaultRenderersFactory]'s default
 * behaviour (codec selection, extension renderer preferences, etc.) is left
 * completely untouched, so this is a drop-in replacement for
 * `DefaultRenderersFactory(context)` wherever an `ExoPlayer.Builder` already
 * calls `.setRenderersFactory(...)` or omits it entirely.
 *
 * [DefaultAudioSink.Builder.setAudioProcessors] wraps the array it's given
 * in a `DefaultAudioProcessorChain`, which appends its own
 * `SilenceSkippingAudioProcessor` and `SonicAudioProcessor` (speed/pitch)
 * after whatever is passed in — so existing skip-silence and speed/pitch
 * behaviour elsewhere in the app is preserved unchanged; [reverbProcessor]
 * is simply inserted as an earlier stage in the same chain.
 */
@UnstableApi
class ReverbRenderersFactory(
    context: Context,
    val reverbProcessor: ParallelReverbAudioProcessor = ParallelReverbAudioProcessor()
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(reverbProcessor))
            .build()
    }
}
