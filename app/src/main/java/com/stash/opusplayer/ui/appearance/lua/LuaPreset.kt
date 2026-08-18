package com.stash.opusplayer.ui.appearance.lua

/**
 * The bundled preset identities (`assets/lua_presets/<filename>.lua`) —
 * ported from a subset of Lumisound's 10 `LuaPreset` cases, picked for
 * looks that map cleanly onto what [com.stash.opusplayer.ui.appearance.AppearancePreferences]
 * actually exposes today (Stash has no Liquid-Glass/font-style/panel-material
 * equivalents yet, so presets relying purely on those weren't ported).
 * `synthwaveSunset` doubles as a showcase for the synthwave-specific fields
 * this engine can set, since Stash — unlike Lumisound — has a dedicated
 * SynthWave visualizer.
 */
enum class LuaPreset(val filename: String, val displayName: String, val subtitle: String) {
    AMOLED_DARK("amoled_dark", "AMOLED Midnight", "True black, battery-friendly"),
    CYBERPUNK_NEON("cyberpunk_neon", "Neon Cyberdeck", "Dark, neon, high contrast"),
    FOREST_CALM("forest_calm", "Forest Calm", "Calm greens, natural tones"),
    SYNTHWAVE_SUNSET("synthwave_sunset", "Synthwave Sunset", "Retrowave pink & orange, matching visualizer");

    companion object {
        fun fromFilename(filename: String?): LuaPreset? = entries.firstOrNull { it.filename == filename }
    }
}
