package com.stash.opusplayer.lua

import android.content.Context
import org.luaj.vm2.LuaTable
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.IOException

/**
 * Scriptable EQ-curve effect presets, ported from Lumisound's LuaEffectEngine
 * (ios/Lumisound/Sources/Services/LuaEffectEngine.swift) — the Lua-scripted
 * counterpart to a fixed list of hardcoded EQ presets (Lumisound's native
 * `AudioEffectsService`; Stash's analogue is [com.stash.opusplayer.audio.settings.EQPreset]).
 * A script assigns a global `effect` table computed however the author
 * likes (loops, math, a single tunable knob) instead of 10 hand-picked dB
 * values — see the bundled scripts under `assets/lua_effects/` for examples
 * (an exponential bass-boost curve from one `intensity` knob, a cosine-based
 * symmetric "smiley" scoop, a sibilance notch, an interpolation between two
 * reference curves).
 *
 * Only ports the EQ-curve half of the Swift original's `effect` table
 * (`eq_bands`/`eq_enabled`/`speed`/`pitch_semitones`) — not `special_mode`
 * (`rotation`/`tremolo`/`vibrato`, an LFO applied to pan/volume/pitch over
 * time). Stash has no live DSP equivalent of those at all yet (unlike EQ
 * bands, which map directly onto [com.stash.opusplayer.audio.settings.AudioSettings.eqBands]),
 * so inventing a config shape for a feature this app can't actually run
 * would just be dead data. The 3 bundled Lumisound scripts that rely on
 * `special_mode` (wide_8d_spin, analog_wow_flutter, heartbeat_pulse)
 * weren't ported for the same reason — the other 4 (no special_mode at all)
 * were.
 *
 * Like [LuaThemeEngine], this resolves a script into a plain config object
 * rather than wiring straight into a live pipeline: [com.stash.opusplayer.audio.settings.AudioSettings]
 * is itself still just a persistence/transport schema with no store or DSP
 * consumer yet (see that class's own doc comment) — there is genuinely
 * nowhere live to apply a resolved effect to yet, on either the Lua or the
 * hand-picked-preset side.
 */
object LuaAudioEffectEngine {

    private const val EQ_BAND_COUNT = 10

    data class Config(
        val name: String,
        val icon: String,
        val eqBands: List<Float>,
        val eqEnabled: Boolean,
        val speed: Float,
        val pitchSemitones: Float
    )

    /** Runs [effect]'s bundled script and resolves it into a [Config], or `null` if it couldn't be read, run, or its `eq_bands` table isn't exactly 10 entries. */
    fun resolve(context: Context, effect: LuaAudioEffect): Config? {
        val source = try {
            context.assets.open("lua_effects/${effect.filename}.lua").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            return null
        }
        return resolveSource(source, chunkName = effect.filename)
    }

    /** Same contract as [resolve], for a user-provided/imported script's raw source. */
    fun resolveSource(source: String, chunkName: String): Config? {
        val globals = JsePlatform.standardGlobals()
        return try {
            val chunk = globals.load(source, chunkName)
            chunk.call()
            val effectTable = globals.get("effect")
            if (!effectTable.istable()) return null
            configFrom(effectTable.checktable())
        } catch (e: Exception) {
            // Covers LuaError (syntax/runtime errors) and a malformed
            // `effect` table shape -- always caught broadly, same reasoning
            // as LuaThemeEngine.resolve/LuaSmartPlaylistEngine.filterSongIds:
            // a script here can be hand-edited or community-shared.
            null
        }
    }

    private fun configFrom(table: LuaTable): Config? {
        val bandsValue = table.get("eq_bands")
        if (!bandsValue.istable()) return null
        val bandsTable = bandsValue.checktable()
        val bands = (1..EQ_BAND_COUNT).map { i ->
            val v = bandsTable.get(i)
            if (v.isnumber()) v.tofloat() else return null
        }

        val nameValue = table.get("name")
        val iconValue = table.get("icon")
        val eqEnabledValue = table.get("eq_enabled")
        val speedValue = table.get("speed")
        val pitchValue = table.get("pitch_semitones")

        return Config(
            name = if (nameValue.isstring()) nameValue.tojstring() else "Untitled Effect",
            icon = if (iconValue.isstring()) iconValue.tojstring() else "",
            eqBands = bands,
            eqEnabled = if (eqEnabledValue.isboolean()) eqEnabledValue.toboolean() else true,
            speed = if (speedValue.isnumber()) speedValue.tofloat() else 1.0f,
            pitchSemitones = if (pitchValue.isnumber()) pitchValue.tofloat() else 0.0f
        )
    }
}

/**
 * The bundled effect identities (`assets/lua_effects/<filename>.lua`) — the
 * 4 of Lumisound's 7 `LuaEffects` scripts that are pure EQ curves (see
 * [LuaAudioEffectEngine]'s doc comment for why the other 3 weren't ported).
 */
enum class LuaAudioEffect(val filename: String, val displayName: String, val subtitle: String) {
    PARAMETRIC_BASS_BOOST("parametric_bass_boost", "Parametric Bass", "Exponential sub-bass curve from one intensity knob"),
    SMILEY_CURVE("smiley_curve_generator", "Generated Smiley", "Procedural cosine-shaped bass/treble boost, scooped mids"),
    DE_ESSER("de_esser", "De-Ess", "Narrow cut around the sibilance range"),
    GENRE_BLEND("genre_blend", "Genre Blend", "Interpolates between Rock and Jazz reference curves");

    companion object {
        fun fromFilename(filename: String?): LuaAudioEffect? = entries.firstOrNull { it.filename == filename }
    }
}
