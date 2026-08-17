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
 * The Swift original tries a BPM tag/analyzer first; Stash has no BPM field
 * on [Song] and no audio-analysis pipeline to add one from, so that tier is
 * dropped entirely rather than faked -- every remaining tier (genre
 * keywords, title/artist keywords, duration heuristic, deterministic
 * fallback) carries over exactly, in the same priority order, so every song
 * still always lands in some bucket.
 */
object MoodClassifier {

    fun classify(song: Song): MoodBucket {
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
