package com.stash.opusplayer.lua

import android.content.Context
import androidx.preference.PreferenceManager
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
 * Wired into the real, live spectrum renderer,
 * [com.stash.opusplayer.ui.visualizer.EnhancedSynthWaveView] — same
 * "resolve once, bake the result into the same `SharedPreferences` keys the
 * hand-editable settings already use, broadcast a change" shape
 * [com.stash.opusplayer.ui.appearance.lua.LuaThemeEngine] established for
 * the theme engine. [EnhancedSynthWaveView] reads the persisted [Config]
 * back out via [configFromPrefs] (in `init` and its existing
 * `OnSharedPreferenceChangeListener`), so applying a visualizer here takes
 * effect on any already-open Now Playing screen immediately, not just on
 * next launch.
 */
object LuaVisualizerEngine {

    private const val PREF_SELECTED = "lua_visualizer_selected"
    private const val PREF_COLORS = "lua_visualizer_colors"
    private const val PREF_SENSITIVITY = "lua_visualizer_sensitivity"
    private const val PREF_BAR_CORNER_RADIUS = "lua_visualizer_bar_corner_radius"
    private const val PREF_BAR_SPACING = "lua_visualizer_bar_spacing"
    private const val PREF_MIRRORED = "lua_visualizer_mirrored"
    /** Colors are stored as one pref value joined by this separator — none of
     * the bundled/expected hex strings can contain a comma, so a plain split
     * is safe without needing a JSON/CSV-escaping dependency for one field. */
    private const val COLOR_SEPARATOR = ","

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

    /**
     * Resolves [visualizer]'s bundled script and persists the result into
     * `SharedPreferences` (same instance/keys [configFromPrefs] and
     * [com.stash.opusplayer.ui.visualizer.EnhancedSynthWaveView] read) —
     * once applied, it's indistinguishable from a hardcoded look until a
     * different one is applied or [clear] is called. Returns the resolved
     * [Config] (already persisted) so a caller can update its own UI state
     * without a second read, or `null` (leaving whatever was previously
     * applied untouched) if the script failed to load/run.
     */
    fun apply(context: Context, visualizer: LuaVisualizer): Config? {
        val config = resolve(context, visualizer) ?: return null
        persist(context, selectedId = visualizer.filename, config = config)
        return config
    }

    /** Same contract as [apply], for a user-provided/imported script's raw source. */
    fun applySource(context: Context, source: String, chunkName: String): Config? {
        val config = resolveSource(source, chunkName) ?: return null
        persist(context, selectedId = "custom:$chunkName", config = config)
        return config
    }

    /** Clears any applied Lua visualizer — [configFromPrefs] returns `null` again afterward, and the renderer falls back to its original built-in gradient. */
    fun clear(context: Context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(PREF_SELECTED)
            .remove(PREF_COLORS)
            .remove(PREF_SENSITIVITY)
            .remove(PREF_BAR_CORNER_RADIUS)
            .remove(PREF_BAR_SPACING)
            .remove(PREF_MIRRORED)
            .apply()
    }

    /** Which bundled/custom visualizer is currently applied, if any — a bundled one resolves via [LuaVisualizer.fromFilename]; a custom one (imported source) only ever shows as "Custom" in the UI, since the raw script itself isn't kept around once resolved (same "bake the result in, don't re-run the script every read" choice [com.stash.opusplayer.ui.appearance.lua.LuaThemeEngine] makes). */
    fun selectedVisualizer(context: Context): LuaVisualizer? {
        val id = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SELECTED, null) ?: return null
        return LuaVisualizer.fromFilename(id)
    }

    /** `true` if a custom (imported-source) visualizer is currently applied, as opposed to a bundled one or none at all. */
    fun isCustomSelected(context: Context): Boolean {
        val id = PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_SELECTED, null) ?: return false
        return id.startsWith("custom:")
    }

    /**
     * Reads back whatever [Config] was last persisted by [apply]/[applySource],
     * or `null` if none has ever been applied (or it was [clear]ed) — the
     * renderer treats `null` as "use the original built-in gradient/behavior",
     * so every field here has to have been genuinely resolved from a real
     * script at apply-time, never guessed at read-time.
     */
    fun configFromPrefs(context: Context): Config? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val colorsRaw = prefs.getString(PREF_COLORS, null) ?: return null
        val colors = colorsRaw.split(COLOR_SEPARATOR).filter { it.isNotBlank() }
        if (colors.size < 2) return null
        return Config(
            colors = colors,
            sensitivity = prefs.getFloat(PREF_SENSITIVITY, 1.0f),
            barCornerRadius = prefs.getFloat(PREF_BAR_CORNER_RADIUS, 0.0f),
            barSpacing = prefs.getFloat(PREF_BAR_SPACING, 2.0f),
            mirrored = prefs.getBoolean(PREF_MIRRORED, false)
        )
    }

    private fun persist(context: Context, selectedId: String, config: Config) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(PREF_SELECTED, selectedId)
            .putString(PREF_COLORS, config.colors.joinToString(COLOR_SEPARATOR))
            .putFloat(PREF_SENSITIVITY, config.sensitivity)
            .putFloat(PREF_BAR_CORNER_RADIUS, config.barCornerRadius)
            .putFloat(PREF_BAR_SPACING, config.barSpacing)
            .putBoolean(PREF_MIRRORED, config.mirrored)
            .apply()
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
