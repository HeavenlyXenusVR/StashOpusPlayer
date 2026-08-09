package com.stash.opusplayer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Lua-scripted smart playlist: unlike [PlaylistEntity] (a static list of
 * tracks in [PlaylistTrackEntity]), a smart playlist has no stored track
 * list at all — its membership is computed on demand by running [luaScript]
 * through `com.stash.opusplayer.lua.LuaSmartPlaylistEngine` against the
 * current song library. Ported from Lumisound's `SmartPlaylist` model
 * (`.luaScript` field), minus the fields that model has no equivalent for
 * here yet (folder/tags/pin state).
 */
@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val luaScript: String,
    val createdAt: Long = System.currentTimeMillis()
)
