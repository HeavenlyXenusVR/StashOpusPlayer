package com.stash.opusplayer.ui.appearance.lua

import android.content.Context
import android.graphics.Color
import com.stash.opusplayer.ui.appearance.AppearancePreferences
import com.stash.opusplayer.ui.appearance.NowPlayingLayoutTheme
import com.stash.opusplayer.ui.appearance.ThemeManager
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.JsePlatform
import java.io.IOException

/**
 * The Lua-scripted theming layer, ported from Lumisound's LuaThemeEngine
 * (ios/Lumisound/Sources/Theme/LuaThemeEngine.swift). A "preset" is a single
 * `.lua` script (bundled under `assets/lua_presets/`, see [LuaPreset] below)
 * that builds and assigns a global `theme` table covering every field
 * [AppearancePreferences] already exposes — same idea as the Kotlin-hardcoded
 * [com.stash.opusplayer.ui.appearance.AppearancePresets] this supersedes,
 * except the preset body is a script instead of a fixed list, so new looks
 * (and eventually user-shared ones, mirroring Lumisound's community preset
 * import) don't require an app update.
 *
 * Uses LuaJ (pure JVM, no native/NDK component) rather than a JSON
 * round-trip: a `.lua` script's `theme` table is read directly via LuaJ's
 * [LuaTable] API after execution, which is simpler here than Lumisound's
 * `json.encode(theme)` + `JSONDecoder` dance — that indirection existed
 * there because LuaSwift's `evaluate()` only returns a single [String]/
 * number/boolean value, not a table.
 *
 * `apply(_:_:)` fans a resolved preset straight into the same
 * [AppearancePreferences]/`SharedPreferences` keys the hand-editable
 * Appearance screen already reads (via `saveToPrefs`) — once applied, a Lua
 * preset is indistinguishable from the user having picked each value
 * manually, and [ThemeManager.broadcastChange] makes it take effect
 * immediately, same as any other appearance change.
 */
object LuaThemeEngine {

    /**
     * Runs [preset]'s bundled script and applies the resulting theme.
     * Returns `null` (the caller decides how to surface that) if the script
     * fails to load, run, or its `theme` table is missing/malformed — the
     * previously-applied look is left untouched in that case.
     */
    fun apply(context: Context, preset: LuaPreset): AppearancePreferences? {
        val source = try {
            context.assets.open("lua_presets/${preset.filename}.lua").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            return null
        }
        return applySource(context, source, chunkName = preset.filename)
    }

    /** Same contract as [apply], for a user-provided/imported script's raw source. */
    fun applySource(context: Context, source: String, chunkName: String): AppearancePreferences? {
        val config = resolve(source, chunkName) ?: return null
        val updated = config.applyTo(AppearancePreferences.fromPrefs(context))
        updated.saveToPrefs(context)
        ThemeManager.broadcastChange(context, requiresRecreate = true)
        return updated
    }

    /**
     * Executes `source` in a fresh sandbox and reads its `theme` global back
     * out as a [LuaThemeConfig]. A fresh [JsePlatform.standardGlobals] is
     * created per call (cheap — no persistent state to isolate between
     * preset runs, and it guarantees one script's globals can never leak
     * into the next).
     */
    private fun resolve(source: String, chunkName: String): LuaThemeConfig? {
        val globals = JsePlatform.standardGlobals()
        return try {
            val chunk = globals.load(source, chunkName)
            chunk.call()
            val theme = globals.get("theme")
            if (theme.isnil() || !theme.istable()) null else LuaThemeConfig.from(theme.checktable())
        } catch (e: Exception) {
            // Covers LuaError (syntax/runtime errors in the script) and any
            // unexpected shape (e.g. `theme` assigned as a non-table) —
            // deliberately caught broadly since this always runs against a
            // script that could be hand-edited or community-shared.
            null
        }
    }
}

/**
 * Mirrors the `theme` table every preset script assigns. Field names below
 * match the Lua table's snake_case keys (see any bundled script under
 * `assets/lua_presets/` for the exact shape); [applyTo] maps them onto
 * [AppearancePreferences]'s existing camelCase properties.
 */
private class LuaThemeConfig(
    private val colors: LuaTable?,
    private val typography: LuaTable?,
    private val ui: LuaTable?,
    private val background: LuaTable?,
    private val animations: LuaTable?,
    private val miniPlayer: LuaTable?,
    private val synthwave: LuaTable?
) {
    fun applyTo(base: AppearancePreferences): AppearancePreferences {
        var result = base
        colors?.let {
            result = result.copy(
                primaryColor = it.hexColor("primary", result.primaryColor),
                accentColor = it.hexColor("accent", result.accentColor),
                backgroundColor = it.hexColor("background", result.backgroundColor),
                textPrimaryColor = it.hexColor("text_primary", result.textPrimaryColor),
                textSecondaryColor = it.hexColor("text_secondary", result.textSecondaryColor)
            )
        }
        typography?.let {
            result = result.copy(
                fontScale = it.float("font_scale", result.fontScale),
                titleBold = it.bool("title_bold", result.titleBold)
            )
        }
        ui?.let {
            result = result.copy(
                buttonSizeScale = it.float("button_size_scale", result.buttonSizeScale),
                cardCornerRadiusDp = it.int("card_corner_radius_dp", result.cardCornerRadiusDp),
                shadowsEnabled = it.bool("shadows_enabled", result.shadowsEnabled),
                shadowIntensity = it.float("shadow_intensity", result.shadowIntensity)
            )
        }
        background?.let {
            result = result.copy(
                backgroundBlurRadius = it.int("blur_radius", result.backgroundBlurRadius),
                backgroundDimPercent = it.int("dim_percent", result.backgroundDimPercent),
                useSolidBackgroundColor = it.bool("use_solid_color", result.useSolidBackgroundColor)
            )
        }
        animations?.let {
            val speedName = it.string("speed", result.animationSpeed.name).uppercase()
            val speed = AppearancePreferences.AnimationSpeed.entries
                .firstOrNull { entry -> entry.name == speedName } ?: result.animationSpeed
            result = result.copy(
                animationsEnabled = it.bool("enabled", result.animationsEnabled),
                animationSpeed = speed
            )
        }
        miniPlayer?.let {
            result = result.copy(
                miniPlayerHeightDp = it.int("height_dp", result.miniPlayerHeightDp),
                miniPlayerShowArt = it.bool("show_art", result.miniPlayerShowArt),
                miniPlayerShowArtist = it.bool("show_artist", result.miniPlayerShowArtist),
                miniPlayerCompactMode = it.bool("compact_mode", result.miniPlayerCompactMode),
                miniPlayerSpinningArt = it.bool("spinning_art", result.miniPlayerSpinningArt),
                nowPlayingLayoutTheme = NowPlayingLayoutTheme.fromStorage(
                    it.string("layout_theme", result.nowPlayingLayoutTheme.storageValue)
                )
            )
        }
        synthwave?.let {
            result = result.copy(
                synthWaveEnabled = it.bool("enabled", result.synthWaveEnabled),
                synthWaveUseCustomColors = it.bool("use_custom_colors", result.synthWaveUseCustomColors),
                synthWavePrimaryColor = it.hexColor("primary_color", result.synthWavePrimaryColor),
                synthWaveSecondaryColor = it.hexColor("secondary_color", result.synthWaveSecondaryColor),
                synthWaveGlowColor = it.hexColor("glow_color", result.synthWaveGlowColor)
            )
        }
        return result
    }

    companion object {
        fun from(theme: LuaTable): LuaThemeConfig = LuaThemeConfig(
            colors = theme.subtable("colors"),
            typography = theme.subtable("typography"),
            ui = theme.subtable("ui"),
            background = theme.subtable("background"),
            animations = theme.subtable("animations"),
            miniPlayer = theme.subtable("mini_player"),
            synthwave = theme.subtable("synthwave")
        )
    }
}

// MARK: - LuaTable read helpers

private fun LuaTable.subtable(key: String): LuaTable? {
    val v: LuaValue = get(key)
    return if (v.istable()) v.checktable() else null
}

private fun LuaTable.string(key: String, default: String): String {
    val v = get(key)
    return if (v.isstring()) v.tojstring() else default
}

private fun LuaTable.float(key: String, default: Float): Float {
    val v = get(key)
    return if (v.isnumber()) v.tofloat() else default
}

private fun LuaTable.int(key: String, default: Int): Int {
    val v = get(key)
    return if (v.isnumber()) v.toint() else default
}

private fun LuaTable.bool(key: String, default: Boolean): Boolean {
    val v = get(key)
    return if (v.isboolean()) v.toboolean() else default
}

/** Parses a `"#RRGGBB"`/`"#AARRGGBB"` hex string field, falling back to [default] (an ARGB Int) if absent or unparseable. */
private fun LuaTable.hexColor(key: String, default: Int): Int {
    val v = get(key)
    if (!v.isstring()) return default
    return try {
        Color.parseColor(v.tojstring())
    } catch (e: IllegalArgumentException) {
        default
    }
}
