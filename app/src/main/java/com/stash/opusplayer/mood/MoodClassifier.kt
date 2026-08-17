package com.stash.opusplayer.mood

import com.stash.opusplayer.data.Song
import kotlin.math.abs

/** The four fixed mood buckets, ported from Lumisound's `MoodBucket` enum. */
enum class MoodBucket(val displayName: String) {
    ENERGETIC("Energetic"),
    CHILL("Chill"),
    FOCUS("Focus"),
    SLEEP("Sleep")
}

/**
 * Classifies a song into a [MoodBucket], ported from Lumisound's
 * MoodPlaylistService.classify(song:library:) (ios/Lumisound/Sources/Services/MoodPlaylistService.swift).
 * BPM is now wired in as the first-priority tier (via [com.stash.opusplayer.tempo.BpmCacheService],
 * added once a real on-device analyzer existed) -- matching the Swift
 * original's true priority order exactly: BPM bucket, then genre keywords,
 * then title/artist keywords, then duration heuristic, then deterministic
 * fallback, so every song still always lands in some bucket.
 */
object MoodClassifier {

    /** [cachedBpm] is a cache-only lookup ([com.stash.opusplayer.tempo.BpmCacheService.cachedBpm]) supplied by the caller so classification itself stays synchronous and never triggers a fresh decode+analyze. */
    fun classify(song: Song, cachedBpm: Double? = null): MoodBucket {
        if (cachedBpm != null) {
            bucketForBpm(cachedBpm)?.let { return it }
        }

        val genre = song.genre.lowercase()
        if (genre.isNotBlank()) {
            matchByKeywords(genre)?.let { return it }
        }

        val text = "${song.displayName} ${song.artistName} ${song.albumName}".lowercase()
        matchByKeywords(text)?.let { return it }

        val durationSeconds = song.duration / 1000
        when {
            durationSeconds in 1 until 120 -> return MoodBucket.ENERGETIC
            durationSeconds > 480 -> return MoodBucket.FOCUS
        }

        val buckets = MoodBucket.entries
        return buckets[abs(song.id.hashCode()) % buckets.size]
    }

    /** Bucketing thresholds ported verbatim from the Swift original: `<60 sleep, 60..<90 chill, 90..<120 focus, >=120 energetic`. */
    private fun bucketForBpm(bpm: Double): MoodBucket? {
        return when {
            bpm < 60.0 -> MoodBucket.SLEEP
            bpm < 90.0 -> MoodBucket.CHILL
            bpm < 120.0 -> MoodBucket.FOCUS
            else -> MoodBucket.ENERGETIC
        }
    }

    private fun matchByKeywords(text: String): MoodBucket? {
        return when {
            ENERGETIC_KEYWORDS.any { text.contains(it) } -> MoodBucket.ENERGETIC
            SLEEP_KEYWORDS.any { text.contains(it) } -> MoodBucket.SLEEP
            FOCUS_KEYWORDS.any { text.contains(it) } -> MoodBucket.FOCUS
            CHILL_KEYWORDS.any { text.contains(it) } -> MoodBucket.CHILL
            else -> null
        }
    }

    // Checked in this order (energetic, sleep, focus, chill) so "chill" -- the most generic,
    // catch-all-sounding keyword set -- only wins when nothing more specific matched first.
    private val ENERGETIC_KEYWORDS = listOf(
        "rock", "metal", "hip hop", "hip-hop", "dance", "punk", "rave", "drum", "edm",
        "electronic", "pop", "funk", "trap", "workout", "pump", "energy", "hype", "run", "gym"
    )
    private val SLEEP_KEYWORDS = listOf(
        "sleep", "rain", "lofi", "lo-fi", "night", "lullaby", "relax", "calm", "ambient", "dream"
    )
    private val FOCUS_KEYWORDS = listOf(
        "study", "focus", "work", "deep", "flow", "concentration", "instrumental", "piano", "classical"
    )
    private val CHILL_KEYWORDS = listOf(
        "chill", "vibe", "sunset", "breeze", "lazy", "acoustic", "jazz", "soul", "reggae"
    )
}
