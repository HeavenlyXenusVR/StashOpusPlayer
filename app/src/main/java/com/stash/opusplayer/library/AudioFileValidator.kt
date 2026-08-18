package com.stash.opusplayer.library

import android.content.ContentUris
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import com.stash.opusplayer.data.database.SongEntity
import java.nio.ByteBuffer

/**
 * Validates that an indexed song's audio file is actually intact, ported from
 * Lumisound's CorruptFileFinderService (ios/Lumisound/Sources/Services/CorruptFileFinderService.swift):
 * a too-small/zero-byte check, a decoder-open check (catches header/container
 * corruption), a tail-read check (catches a well-formed header but a
 * truncated end -- a dropped download or interrupted copy that a header-only
 * check would miss), and a duration-mismatch check against whatever this
 * file's own indexed [SongEntity.duration] says it should be.
 *
 * Uses [android.media.MediaExtractor] rather than a full decode (Lumisound's
 * `AVAudioPCMBuffer` tail read is closer to a real decode) since Android has
 * no exact equivalent without pulling in a full decoder per file; reading
 * raw samples near the head and the tail without decoding them is enough to
 * catch the same failure modes (unreadable container, truncated end) at a
 * fraction of the cost, which matters since this runs across the whole
 * library on a schedule, not once per file on demand.
 */
object AudioFileValidator {

    data class Flag(val reason: String)

    private const val MIN_VALID_SIZE_BYTES = 1024L
    private const val TAIL_READ_BUFFER_BYTES = 64 * 1024
    private const val TAIL_SEEK_BACK_US = 500_000L
    private const val MIN_DURATION_TOLERANCE_MS = 10_000L
    private const val DURATION_TOLERANCE_FRACTION = 0.15

    fun validate(context: Context, song: SongEntity): Flag? {
        if (song.size in 1 until MIN_VALID_SIZE_BYTES) {
            return Flag("File is only ${song.size} bytes -- too small to be valid audio")
        }

        val extractor = MediaExtractor()
        return try {
            try {
                if (song.path.startsWith("content://")) {
                    extractor.setDataSource(context, Uri.parse(song.path), null)
                } else {
                    extractor.setDataSource(song.path)
                }
            } catch (e: Exception) {
                return Flag("Couldn't open the file -- ${e.message ?: "unreadable"}")
            }

            var audioTrack = -1
            var trackDurationUs = 0L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    trackDurationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                    break
                }
            }
            if (audioTrack < 0) return Flag("No audio track found in this file")
            extractor.selectTrack(audioTrack)

            val buffer = ByteBuffer.allocate(TAIL_READ_BUFFER_BYTES)
            if (extractor.readSampleData(buffer, 0) < 0) {
                return Flag("File has no readable audio data")
            }

            if (trackDurationUs > 0) {
                val seekTargetUs = (trackDurationUs - TAIL_SEEK_BACK_US).coerceAtLeast(0L)
                extractor.seekTo(seekTargetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                buffer.clear()
                if (extractor.readSampleData(buffer, 0) < 0) {
                    return Flag("File appears truncated -- can't read audio near the end")
                }
            }

            if (trackDurationUs > 0 && song.duration > 0) {
                val actualMs = trackDurationUs / 1000
                val toleranceMs = maxOf(MIN_DURATION_TOLERANCE_MS, (song.duration * DURATION_TOLERANCE_FRACTION).toLong())
                if (actualMs < song.duration - toleranceMs) {
                    return Flag("File is shorter than expected (${actualMs}ms vs. ${song.duration}ms indexed) -- looks truncated")
                }
            }

            null
        } catch (e: Exception) {
            Flag("Error while checking this file -- ${e.message ?: "unknown"}")
        } finally {
            extractor.release()
        }
    }

    fun contentUriFor(song: SongEntity): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
}
