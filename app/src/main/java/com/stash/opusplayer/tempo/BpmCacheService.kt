package com.stash.opusplayer.tempo

import android.content.Context
import com.stash.opusplayer.data.database.BpmCacheDao
import com.stash.opusplayer.data.database.BpmCacheEntity
import com.stash.opusplayer.data.database.MusicDatabase
import com.stash.opusplayer.data.database.SongEntity

/**
 * Cache-or-analyze wrapper around [BpmAnalyzer], mirroring the two-entry-point
 * shape Lumisound's BPMAnalyzerService/LibraryManager+TempoBPM.swift use:
 * a cache-only lookup for callers that must stay fast/synchronous-feeling
 * (mood classification, a sort routine) and a cache-or-compute entry point
 * for callers that are fine paying for a fresh decode+analyze on a miss
 * (a manual "Analyze Tempo" action, the periodic background worker).
 */
object BpmCacheService {

    /** Cache-only -- never triggers analysis. `null` means "not yet analyzed" (not "analysis failed"; that isn't distinguished, matching the Swift original). */
    suspend fun cachedBpm(context: Context, songId: Long): Double? {
        val dao = MusicDatabase.getDatabase(context).bpmCacheDao()
        return dao.get(songId)?.bpm
    }

    /** Bulk cache-only lookup, for classifying/sorting a whole library without one DB round-trip per song. */
    suspend fun cachedBpmBySongId(context: Context): Map<Long, Double> {
        val dao = MusicDatabase.getDatabase(context).bpmCacheDao()
        return dao.getAll().associate { it.songId to it.bpm }
    }

    /** Cache hit, or analyzes now and persists the result (including a null result being left un-cached, so a failed analysis is retried later rather than permanently stuck). */
    suspend fun getOrAnalyze(context: Context, song: SongEntity): Double? {
        val dao = MusicDatabase.getDatabase(context).bpmCacheDao()
        val cached = dao.get(song.id)
        if (cached != null && cached.sizeBytes == song.size) {
            return cached.bpm
        }
        val bpm = BpmAnalyzer.analyze(context, song.path) ?: return null
        dao.insert(
            BpmCacheEntity(
                songId = song.id,
                bpm = bpm,
                sizeBytes = song.size,
                analyzedAt = System.currentTimeMillis()
            )
        )
        return bpm
    }
}
