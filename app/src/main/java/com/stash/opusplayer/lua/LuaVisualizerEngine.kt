package com.stash.opusplayer.lua

import android.content.Context
import org.luaj.vm2.LuaTable
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.IOException

/**
 * Scriptable visualizer LOOK, ported from Lumisound's LuaVisualizerEngine
 * (ios/Lumisound/Sources/Services/LuaVisualizerEngine.swift). Same "resolve
 * once, apply many times" contract as [LuaThemeEngine]/[LuaAudioEffectEngine]:
 * the FFT/spectrum analysis itself always stays native (there's no way for a
 * fresh Lua evaluation to keep up with a 60fps render loop, and no need to —
 * a script here only controls gradient colors, sensitivity, bar corner
 * radius/spacing, and mirroring, computed however the author likes — hue
 * rotation around a color wheel, linear channel interpolation, a hard
 * discrete alternation — see the bundled scripts under
 * `assets/lua_visualizers/` for examples of each technique).
 *
 * Engine-only, like the equalizer-curve engine before its own follow-up:
 * Stash's actual live spectrum renderer,
 * [com.stash.opusplayer.ui.visualizer.EnhancedSynthWaveView], is a
 * substantial custom `View` already juggling several `Paint` objects, several
 * `ValueAnimator`s, live `android.media.audiofx.Visualizer` FFT capture, and
 * multiple selectable render modes, reading its current look straight out of
 * `SharedPreferences` on its own. Wiring a resolved [Config] into that
 * safely means understanding its whole rendering pipeline first, not
 * threading one new input through blind — real follow-up work, not part of
 * getting this engine itself working and buildable.
 */
object LuaVisualizerEngine {

    data class Config(
        /** Hex gradient stops, bottom-to-top. At least 2 expected. */
        val colors: List<String>,
        /** Multiplies each bar's raw 0..1 magnitude before clamping -- >1 makes quiet passages busier, <1 calms loud ones. */
        val sensitivity: Float,
        val barCornerRadius: Float,
        val barSpacing: Float,
        /** Renders the bar sequence mirrored (reversed) -- a purely cosmetic left/right flip. */
        val mirrored: Boolean
    )

    /** Runs [visualizer]'s bundled script and resolves it into a [Config], or `null` if it couldn't be read, run, or its `colors` table has fewer than 2 entries. */
    fun resolve(context: Context, visualizer: LuaVisualizer): Config? {
        val source = try {
            context.assets.open("lua_visualizers/${visualizer.filename}.lua").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            return null
        }
        return resolveSource(source, chunkName = visualizer.filename)
    }

    /** Same contract as [resolve], for a user-provided/imported script's raw source. */
    fun resolveSource(source: String, chunkName: String): Config? {
        val globals = JsePlatform.standardGlobals()
        return try {
            val chunk = globals.load(source, chunkName)
            chunk.call()
            val table = globals.get("visualizer")
            if (!table.istable()) return null
            configFrom(table.checktable())
        } catch (e: Exception) {
            // Covers LuaError (syntax/runtime errors) and a malformed
            // `visualizer` table shape, same reasoning as every other engine
            // in this package -- a script here can be hand-edited/shared.
            null
        }
    }

    private fun configFrom(table: LuaTable): Config? {
        val colorsValue = table.get("colors")
        if (!colorsValue.istable()) return null
        val colorsTable = colorsValue.checktable()
        val colors = mutableListOf<String>()
        var i = 1
        while (true) {
            val v = colorsTable.get(i)
            if (v.isnil()) break
            if (!v.isstring()) return null
            colors.add(v.tojstring())
            i++
        }
        if (colors.size < 2) return null

        val sensitivityValue = table.get("sensitivity")
        val cornerRadiusValue = table.get("bar_corner_radius")
        val spacingValue = table.get("bar_spacing")
        val mirroredValue = table.get("mirrored")

        return Config(
            colors = colors,
            sensitivity = if (sensitivityValue.isnumber()) sensitivityValue.tofloat() else 1.0f,
            barCornerRadius = if (cornerRadiusValue.isnumber()) cornerRadiusValue.tofloat() else 0.0f,
            barSpacing = if (spacingValue.isnumber()) spacingValue.tofloat() else 2.0f,
            mirrored = if (mirroredValue.isboolean()) mirroredValue.toboolean() else false
        )
    }
}

/**
 * The bundled visualizer identities (`assets/lua_visualizers/<filename>.lua`)
 * -- all 4 of Lumisound's bundled `LuaVisualizers` scripts, ported verbatim
 * (pure color-space math, nothing Lumisound-specific in any of them).
 */
enum class LuaVisualizer(val filename: String, val displayName: String, val subtitle: String) {
    AURORA_WAVE("aurora_wave", "Aurora Wave", "Shifting teal-to-violet hue rotation"),
    SUNSET_BARS("sunset_bars", "Sunset Bars", "Warm orange-to-pink gradient, mirrored"),
    MONOCHROME_PULSE("monochrome_pulse", "Monochrome Pulse", "Single-hue dark-to-light ramp"),
    PHONK_STROBE("phonk_strobe", "Phonk Strobe", "Hard red/black alternation, mirrored");

    companion object {
        fun fromFilename(filename: String?): LuaVisualizer? = entries.firstOrNull { it.filename == filename }
    }
}
