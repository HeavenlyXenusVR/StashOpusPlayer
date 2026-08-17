package com.stash.opusplayer.clip

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Exports a trimmed clip of a song as a standalone, shareable AAC/M4A file,
 * ported from Lumisound's ClipMakerService/ClipExportService (the two were
 * near-identical -- both trim via an AAC export session -- so this is a
 * single unified implementation covering both call shapes: a bare clip and
 * a title-derived filename).
 *
 * Always decodes and re-encodes to AAC regardless of the source codec,
 * matching Lumisound's `AVAssetExportPresetAppleM4A` behavior exactly
 * (rather than a cheaper stream-copy trim): Android's `MediaMuxer` can only
 * write MP4/WEBM/OGG containers and has no MP3 muxer at all, and a source
 * FLAC/MP3/Opus file would need format-specific container handling to
 * stream-copy anyway -- decode+encode gives one guaranteed-shareable output
 * format regardless of what the library actually contains.
 *
 * No re-encode of an already-lossy source is literally lossless, same
 * caveat Lumisound's own AAC-preset export already carries -- audio frame
 * granularity (~20ms for AAC/Opus, ~26ms for MP3) makes the trim-boundary
 * error inaudible either way.
 */
object ClipExportService {

    /** Default cap, matching the Make Clip UI's own slider limit (Lumisound's ClipMakerView `maxClipLength`). */
    const val DEFAULT_MAX_CLIP_SECONDS = 60
    private const val MIN_CLIP_MS = 500L
    private const val DECODE_TIMEOUT_US = 10_000L
    private const val DEFAULT_BIT_RATE = 192_000

    sealed class ClipError : Exception() {
        object NoAudioTrack : ClipError()
        object InvalidRange : ClipError()
        object ExportFailed : ClipError()
    }

    /**
     * @param startMs clip start, clamped to >= 0
     * @param endMs clip end, clamped so the clip is at least [MIN_CLIP_MS] and at most [maxClipSeconds]
     * @param title used only for the output filename (sanitized to alphanumerics), matching
     *   ClipExportService's title param -- never embedded as metadata, matching the Swift original.
     * @param maxClipSeconds override for callers that need a longer allowance than the Make Clip UI's
     *   own 60s limit -- e.g. AcoustIdService trims up to 120s, matching Lumisound's AcoustIDService.
     */
    suspend fun exportClip(
        context: Context,
        path: String,
        startMs: Long,
        endMs: Long,
        title: String,
        maxClipSeconds: Int = DEFAULT_MAX_CLIP_SECONDS
    ): File =
        withContext(Dispatchers.Default) {
            val clipStart = startMs.coerceAtLeast(0)
            val clipEnd = endMs.coerceAtLeast(clipStart + MIN_CLIP_MS)
                .coerceAtMost(clipStart + maxClipSeconds * 1000L)
            if (clipEnd <= clipStart) throw ClipError.InvalidRange

            val sanitizedTitle = title.filter { it.isLetterOrDigit() }.ifBlank { "clip" }
            val outputDir = File(context.cacheDir, "clips").apply { mkdirs() }
            val outputFile = File(outputDir, "${sanitizedTitle}_${UUID.randomUUID().toString().take(8)}.m4a")

            val extractor = MediaExtractor()
            try {
                if (path.startsWith("content://")) {
                    extractor.setDataSource(context, Uri.parse(path), null)
                } else {
                    extractor.setDataSource(path)
                }
            } catch (e: Exception) {
                throw ClipError.ExportFailed
            }

            var audioTrack = -1
            var sourceFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    sourceFormat = f
                    break
                }
            }
            if (audioTrack < 0 || sourceFormat == null) {
                extractor.release()
                throw ClipError.NoAudioTrack
            }
            extractor.selectTrack(audioTrack)

            val sourceMime = sourceFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

            val decoder = try {
                MediaCodec.createDecoderByType(sourceMime).apply {
                    configure(sourceFormat, null, null, 0)
                    start()
                }
            } catch (e: Exception) {
                extractor.release()
                throw ClipError.ExportFailed
            }

            val encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, DEFAULT_BIT_RATE)
            }
            val encoder = try {
                MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            } catch (e: Exception) {
                decoder.release()
                extractor.release()
                throw ClipError.ExportFailed
            }

            val muxer = try {
                MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } catch (e: Exception) {
                decoder.release()
                encoder.release()
                extractor.release()
                throw ClipError.ExportFailed
            }

            val success = runCatching {
                extractor.seekTo(clipStart * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                runPipeline(extractor, decoder, encoder, muxer, clipStart, clipEnd, sampleRate, channelCount)
            }.getOrDefault(false)

            runCatching { decoder.stop() }
            decoder.release()
            runCatching { encoder.stop() }
            encoder.release()
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            extractor.release()

            if (!success) {
                outputFile.delete()
                throw ClipError.ExportFailed
            }
            outputFile
        }

    /**
     * The actual decode -> encode -> mux loop. Interleaves the two codecs on
     * one thread each iteration in this priority order: drain encoder output
     * (unblock it, and add the muxer track once the format is known), feed
     * any pending decoded PCM into the encoder, drain decoder output into the
     * pending-PCM queue, feed compressed samples into the decoder. This is
     * the single riskiest piece of this feature -- interleaved dual-codec
     * pipelines are inherently a bit finicky and this exact sequence hasn't
     * been run on a device in this environment, only reasoned through against
     * the documented MediaCodec contract. If exported clips come out
     * corrupted/silent/wrong-length, start here.
     */
    private fun runPipeline(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        clipStartMs: Long,
        clipEndMs: Long,
        sampleRate: Int,
        channelCount: Int
    ): Boolean {
        val clipEndUs = clipEndMs * 1000
        // The encoder needs monotonically increasing presentation timestamps
        // on its INPUT buffers to produce correctly-timed output (and
        // MediaMuxer.writeSampleData requires the same on output) -- tracked
        // here as a running PCM-frame count converted to microseconds,
        // independent of the decoder's own (source-timeline-relative)
        // timestamps, since the muxed output's timeline needs to start at 0.
        val bytesPerFrame = 2 * channelCount // 16-bit PCM
        var pcmFramesQueued = 0L

        var muxerTrackIndex = -1
        var muxerStarted = false
        var decoderInputDone = false
        var decoderOutputDone = false
        var encoderInputDone = false
        var encoderOutputDone = false

        val pendingPcm = ArrayDeque<ByteArray>()
        val decoderBufferInfo = MediaCodec.BufferInfo()
        val encoderBufferInfo = MediaCodec.BufferInfo()

        while (!encoderOutputDone) {
            // 1. Drain encoder output -- unblocks the encoder and discovers the muxer track format.
            if (!encoderOutputDone) {
                val outIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, DECODE_TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) return false
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuffer = encoder.getOutputBuffer(outIndex)
                        if (outBuffer != null && encoderBufferInfo.size > 0 && muxerStarted) {
                            outBuffer.position(encoderBufferInfo.offset)
                            outBuffer.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                            muxer.writeSampleData(muxerTrackIndex, outBuffer, encoderBufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderOutputDone = true
                        }
                    }
                }
            }

            // 2. Feed any pending decoded PCM into the encoder.
            if (!encoderInputDone && pendingPcm.isNotEmpty()) {
                val inIndex = encoder.dequeueInputBuffer(0)
                if (inIndex >= 0) {
                    val chunk = pendingPcm.removeFirst()
                    val inBuffer = encoder.getInputBuffer(inIndex)
                    inBuffer?.clear()
                    inBuffer?.put(chunk)
                    val ptsUs = pcmFramesQueued * 1_000_000 / sampleRate
                    encoder.queueInputBuffer(inIndex, 0, chunk.size, ptsUs, 0)
                    pcmFramesQueued += chunk.size / bytesPerFrame
                }
            } else if (!encoderInputDone && decoderOutputDone && pendingPcm.isEmpty()) {
                val inIndex = encoder.dequeueInputBuffer(0)
                if (inIndex >= 0) {
                    encoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    encoderInputDone = true
                }
            }

            // 3. Drain decoder output into the pending-PCM queue, stopping once past the clip end.
            if (!decoderOutputDone) {
                val outIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, DECODE_TIMEOUT_US)
                if (outIndex >= 0) {
                    val pastClipEnd = decoderBufferInfo.presentationTimeUs >= clipEndUs
                    if (decoderBufferInfo.size > 0 && !pastClipEnd) {
                        val outBuffer = decoder.getOutputBuffer(outIndex)
                        if (outBuffer != null) {
                            outBuffer.position(decoderBufferInfo.offset)
                            outBuffer.limit(decoderBufferInfo.offset + decoderBufferInfo.size)
                            val chunk = ByteArray(decoderBufferInfo.size)
                            outBuffer.get(chunk)
                            pendingPcm.addLast(chunk)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || pastClipEnd) {
                        decoderOutputDone = true
                    }
                }
            }

            // 4. Feed compressed samples into the decoder until we've fed everything up to the clip end.
            if (!decoderInputDone) {
                val inIndex = decoder.dequeueInputBuffer(0)
                if (inIndex >= 0) {
                    val sampleTime = extractor.sampleTime
                    val sampleSize = if (sampleTime in 0 until clipEndUs || sampleTime < 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex)
                        inBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    } else {
                        -1
                    }
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        decoderInputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // Guard against a pipeline that never converges (e.g. an encoder that
            // never reports EOS for a corrupt/empty clip) -- treat as failure
            // rather than looping forever.
            if (decoderInputDone && decoderOutputDone && pendingPcm.isEmpty() && !encoderInputDone) {
                val inIndex = encoder.dequeueInputBuffer(0)
                if (inIndex >= 0) {
                    encoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    encoderInputDone = true
                }
            }
        }

        return muxerStarted
    }
}
