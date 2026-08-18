package com.stash.opusplayer.lua

import com.stash.opusplayer.data.Song
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Smart-playlist predicate engine, ported from Lumisound's
 * LuaSmartPlaylistEngine (ios/Lumisound/Sources/Services/LuaSmartPlaylistEngine.swift).
 * A rule script is expected to define `function matches(song) ... end`; this
 * runs it against a whole batch of songs in ONE Lua invocation (never
 * per-song — no per-item JVM<->Lua call overhead) and returns the ids that
 * matched.
 *
 * Unlike the Swift original — which has to JSON-encode the song batch into a
 * string, splice it into the Lua source, and have the script `json.decode`
 * it back out (LuaSwift's `evaluate()` only ever returns a single scalar
 * value, and only ever takes a source *string* in) — this builds the songs
 * as a native [LuaTable] directly and sets it as a global before running the
 * harness. That sidesteps the whole "escape a title/artist that might
 * contain a Lua string-literal terminator" problem the Swift version needs
 * `LuaJSONBridge.quotedLuaString` for: there is no string splicing here for
 * a malicious/weird tag to break out of.
 *
 * `dateAdded` deliberately isn't exposed as a `days_since_added` fact (the
 * way the Swift version does): [Song.dateAdded] is populated from two
 * different unit conventions depending on import path (MediaStore's
 * `DATE_ADDED`, which is seconds-since-epoch, vs. `File.lastModified()`,
 * which is milliseconds — see `MusicRepository.kt`/`MetadataExtractor.kt`),
 * a pre-existing ambiguity in this codebase that isn't this engine's place
 * to silently guess at. Same reasoning for play count / last-played, which
 * this app has no persisted store for at all yet.
 */
object LuaSmartPlaylistEngine {

    private const val HARNESS = """

-- Auto-appended harness -- see LuaSmartPlaylistEngine.
local matched = {}
for i = 1, #songs do
    local ok, result = pcall(matches, songs[i])
    if ok and result then
        table.insert(matched, songs[i].id)
    end
end
return matched
"""

    /**
     * Returns the subset of [songs]' ids that pass [script]'s `matches`
     * predicate, or `null` if the script couldn't be run at all (syntax
     * error, missing `matches` function, a runtime error escaping every
     * `pcall`, …) — callers should treat `null` as "leave the existing
     * result alone", not "nothing matched".
     */
    fun filterSongIds(script: String, songs: List<Song>): Set<Long>? {
        if (songs.isEmpty()) return emptySet()

        val globals = JsePlatform.standardGlobals()
        globals.set("songs", songsTable(songs))

        return try {
            val chunk = globals.load(script + HARNESS, "smart_playlist_rule")
            val result = chunk.call()
            if (!result.istable()) return null
            matchedIds(result.checktable())
        } catch (e: Exception) {
            // Covers LuaError (syntax/runtime errors) and anything else a
            // hand-edited rule script could throw -- always caught broadly,
            // same as LuaThemeEngine.resolve, since the script is never
            // trusted to be well-formed.
            null
        }
    }

    private fun songsTable(songs: List<Song>): LuaTable {
        val table = LuaTable()
        songs.forEachIndexed { index, song ->
            val row = LuaTable()
            // Lua numbers are doubles -- round-tripping a Long id through
            // one risks silent precision loss for very large MediaStore row
            // ids, so it's carried as a string on both sides of the bridge.
            row.set("id", LuaValue.valueOf(song.id.toString()))
            row.set("title", LuaValue.valueOf(song.title))
            row.set("artist", LuaValue.valueOf(song.artist))
            row.set("album", LuaValue.valueOf(song.album))
            row.set("genre", LuaValue.valueOf(song.genre))
            row.set("year", LuaValue.valueOf(song.year))
            row.set("duration_ms", LuaValue.valueOf(song.duration.toDouble()))
            row.set("favorite", LuaValue.valueOf(song.isFavorite))
            table.set(index + 1, row)
        }
        return table
    }

    private fun matchedIds(table: LuaTable): Set<Long> {
        val ids = mutableSetOf<Long>()
        var i = 1
        while (true) {
            val v = table.get(i)
            if (v.isnil()) break
            v.tojstring().toLongOrNull()?.let { ids.add(it) }
            i++
        }
        return ids
    }
}
