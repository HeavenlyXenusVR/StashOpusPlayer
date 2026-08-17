package com.stash.opusplayer.mood

import android.content.Context
import android.net.Uri
import com.stash.opusplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M3U/M3U8 playlist import, ported from Lumisound's M3UImportService
 * (ios/Lumisound/Sources/Services/M3UImportService.swift). Matches entries
 * against the current library in the same two-tier order as the Swift
 * original: exact filename match first (works for both relative and
 * absolute paths in the file, since only the last path segment is
 * compared), then a title[+artist] match from the entry's `#EXTINF` line if
 * present. Unmatched entries are silently skipped rather than failing the
 * whole import -- an M3U built on another device/app routinely references
 * files this library doesn't have.
 */
object M3UImportService {

    data class ImportResult(val matchedSongs: List<Song>, val totalEntries: Int)

    suspend fun import(context: Context, uri: Uri, library: List<Song>): ImportResult =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val content = runCatching { String(bytes, Charsets.UTF_8) }
                .getOrElse { String(bytes, Charsets.ISO_8859_1) }

            // Manual "insert only if absent" rather than Map.putIfAbsent -- that's a Java 8
            // default method requiring API 24+, and minSdk here is 21 with no core library
            // desugaring configured.
            val byFilename = HashMap<String, Song>()
            for (song in library) {
                val filename = song.path.substringAfterLast('/').lowercase()
                if (filename.isNotBlank() && !byFilename.containsKey(filename)) {
                    byFilename[filename] = song
                }
            }

            var pendingArtist: String? = null
            var pendingTitle: String? = null
            var totalEntries = 0
            val seenIds = HashSet<Long>()
            val matched = mutableListOf<Song>()

            for (rawLine in content.split("\n")) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("#EXTINF:")) {
                    val afterComma = line.substringAfter(',', missingDelimiterValue = "")
                    val separatorIndex = afterComma.indexOf(" - ")
                    if (separatorIndex >= 0) {
                        pendingArtist = afterComma.substring(0, separatorIndex).trim()
                        pendingTitle = afterComma.substring(separatorIndex + 3).trim()
                    } else {
                        pendingArtist = ""
                        pendingTitle = afterComma.trim()
                    }
                    continue
                }
                if (line.startsWith("#")) continue

                totalEntries++
                val filename = line.substringAfterLast('/').lowercase()
                val match = byFilename[filename] ?: run {
                    val title = pendingTitle
                    if (title.isNullOrBlank()) return@run null
                    library.firstOrNull { candidate ->
                        candidate.displayName.equals(title, ignoreCase = true) &&
                            (pendingArtist.isNullOrBlank() || candidate.artistName.equals(pendingArtist, ignoreCase = true))
                    }
                }
                if (match != null && seenIds.add(match.id)) {
                    matched += match
                }

                pendingArtist = null
                pendingTitle = null
            }

            ImportResult(matchedSongs = matched, totalEntries = totalEntries)
        }
}
