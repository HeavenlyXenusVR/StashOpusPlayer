package com.stash.opusplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached tempo-detection result, ported from Lumisound's BPMAnalyzerService
 * on-disk cache (`bpm_cache_v1.json`, keyed by path+mtime+size). Keyed here
 * by `songId` (Room-native) but still carries `sizeBytes` so a rescanned/
 * replaced file at the same MediaStore id self-invalidates rather than
 * silently serving a stale BPM for different audio -- same self-invalidating
 * intent as the Swift original, without needing an explicit cache-bust call
 * anywhere in the scan pipeline.
 */
@Entity(tableName = "bpm_cache")
data class BpmCacheEntity(
    @PrimaryKey val songId: Long,
    val bpm: Double,
    val sizeBytes: Long,
    val analyzedAt: Long
)
