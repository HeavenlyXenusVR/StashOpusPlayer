package com.stash.opusplayer.audio.settings

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Canonical audio settings schema, ported from Lumisound (the sibling iOS app)'s
 * `AudioSettings` struct in `Sources/Models/PlaybackModels.swift`.
 *
 * This class itself does not talk to `android.media.audiofx.*` or any DSP
 * engine directly — [fromPrefs]/[saveToPrefs] read and write the SAME
 * underlying `SharedPreferences` keys/files that [com.stash.opusplayer.audio.EqualizerManager]
 * and [com.stash.opusplayer.service.MusicService] already own and apply live,
 * rather than introducing a second, competing storage location. That makes
 * this class a genuine unified snapshot/facade over the real engine state,
 * not a parallel copy of it: reading [fromPrefs] reflects exactly what's
 * currently audible, and [saveToPrefs] persists changes those two classes'
 * own listeners/init paths already pick up (see each accessor's doc for
 * whether a given field takes effect immediately or on the next track/
 * session-init — most crossfade/replaygain/speed/pitch/skip-silence
 * fields live on `MusicService`'s own `SharedPreferences.
 * OnSharedPreferenceChangeListener` and apply live; EQ/bass-boost fields
 * persist immediately but only re-apply to the live `Equalizer`/`BassBoost`
 * instances the next time [com.stash.opusplayer.audio.EqualizerManager]
 * initializes a session, since that class has no prefs-change listener of
 * its own today).
 *
 * Several fields have no corresponding engine implementation at all
 * ([reverbEnabled]/[reverbWetDryMix]/[reverbPreset] as a settings-driven
 * toggle -- the app's real reverb DSP, `ParallelReverbAudioProcessor`, has
 * no prefs-backed enable/mix controls yet; [spatialAudioEnabled],
 * [monoAudioEnabled], [nightModeEnabled], [autoEqEnabled], [activeEffectId]).
 * [fromPrefs] leaves these at their class defaults rather than inventing a
 * mapping, and [saveToPrefs] does not write them anywhere — building real
 * DSP for any of these is future work, not something this consolidation
 * pass fabricates. [volume] is also deliberately not read/written here:
 * the live `app_volume` pref is a UI-space value on its own nonlinear
 * curve (`MusicService.uiToAmp`), and per-device output level isn't
 * something a cross-device sync should apply anyway.
 *

 * Persistence convention: this project already depends on Gson (see
 * `com.stash.opusplayer.data.GitHubRelease`) and does not use
 * `kotlinx.serialization` anywhere, so this is a plain data class intended to
 * be serialized with Gson rather than `@Serializable`. Field names intentionally
 * match the Swift property names 1:1 (translated to Kotlin camelCase, which is
 * already Swift's convention) since the Swift struct has no custom
 * `CodingKeys` remapping either — so no `@SerializedName` annotations are
 * needed here.
 *
 * Backward-compatible decoding: the Swift struct makes several late-added
 * fields (`spatialAudioEnabled`, `monoAudioEnabled`, `nightModeEnabled`,
 * `silenceTrimmingEnabled`, `crossfadeCurve`) `Optional`, because Swift's
 * synthesized `Decodable` only tolerates a missing JSON key when the property
 * is `Optional` — a non-optional property with a default value still throws
 * on a missing key. Gson has no such restriction: a missing key simply leaves
 * the Kotlin-declared default value in place (via `GsonBuilder` construction
 * through the primary constructor), so these fields are declared here as
 * plain non-null `Boolean`/enum properties with real defaults rather than
 * nullable types.
 */
data class AudioSettings(
    /**
     * Linear volume multiplier. `1.0` is unity (100%). Values above `1.0` are
     * realised as extra gain (see [MAX_BOOST_DB]) rather than clipping, since
     * a plain linear multiplier above unity would clip the signal outright.
     */
    val volume: Float = 1.0f,

    /** Playback speed multiplier. `1.0` is normal speed. */
    val speed: Float = 1.0f,

    /** Pitch shift, in semitones, independent of [speed]. `0.0` is unshifted. */
    val pitchSemitones: Float = 0.0f,

    /** Master on/off switch for the 10-band equalizer. */
    val equalizerEnabled: Boolean = false,

    /**
     * Ten band gains, in dB, for the fixed center frequencies documented on
     * [EQ_BAND_FREQUENCIES_HZ] (32/64/125/250/500/1000/2000/4000/8000/16000 Hz).
     * Defaults to all-flat (0 dB every band).
     */
    val eqBands: List<Float> = List(10) { 0f },

    /** Which named curve [eqBands] currently reflects; [EQPreset.CUSTOM] once the user hand-edits a band. */
    val eqPreset: EQPreset = EQPreset.FLAT,

    /** Manual crossfade on/off. Mutually exclusive with [smartCrossfadeEnabled] in the UI. */
    val crossfadeEnabled: Boolean = false,

    /** Length, in seconds, of the manual crossfade overlap window. */
    val crossfadeDuration: Double = 2.0,

    /** Gapless playback between consecutive tracks. On by default. */
    val gaplessEnabled: Boolean = true,

    /** Apply per-track ReplayGain loudness normalization, when available. */
    val replayGainEnabled: Boolean = false,

    /** Extra low-shelf boost independent of the main EQ curve. */
    val bassBoostEnabled: Boolean = false,

    /** Extra gain, 0-15 dB, applied on the 32 Hz and 64 Hz bands when [bassBoostEnabled]. */
    val bassBoostGain: Float = 0.0f,

    /** Identifier of the currently active one-tap effect preset; `"none"` when no effect is applied. */
    val activeEffectId: String = "none",

    /**
     * When true, [eqPreset] automatically switches to match each track's
     * tempo/genre (see [EQPreset.forBpm] / [EQPreset.forGenre] / [EQPreset.auto])
     * instead of staying on whatever preset the user last picked manually.
     */
    val autoEqEnabled: Boolean = false,

    /**
     * When true, a BPM-aware crossfade engine beatmatches and beat-aligns the
     * overlap using each track's analysed tempo instead of a fixed
     * [crossfadeDuration]. Opt-in; mutually exclusive with [crossfadeEnabled]
     * in the UI, but either one must drive an actual crossfade transition —
     * see [crossfadeActive].
     */
    val smartCrossfadeEnabled: Boolean = false,

    /**
     * Real-room reverb applied to the main signal chain. On by default per
     * product requirement — gives every track a subtle sense of space rather
     * than a perfectly dry signal.
     */
    val reverbEnabled: Boolean = true,

    /** Wet/dry mix, 0-100, for the reverb effect. */
    val reverbWetDryMix: Float = 18.0f,

    /** Which room/space character the reverb effect uses. */
    val reverbPreset: ReverbRoomPreset = ReverbRoomPreset.MEDIUM_ROOM,

    /**
     * Routes the final mix through HRTF binaural spatial rendering,
     * externalizing the stereo image as a single anchored source in front of
     * the listener. Off by default — opt-in, since it meaningfully changes
     * tonal balance and only makes sense on headphones.
     */
    val spatialAudioEnabled: Boolean = false,

    /**
     * Downmixes the final stereo mix to mono — for single-earbud listening or
     * one-sided hearing loss. Mutually exclusive with [spatialAudioEnabled] in
     * the UI; when both are somehow set, spatial audio takes priority.
     */
    val monoAudioEnabled: Boolean = false,

    /**
     * Gentle, always-tuned dynamic range compression (distinct from any
     * brick-wall peak limiter already in the chain) — evens out quiet vs.
     * loud passages for late-night listening at low volume.
     */
    val nightModeEnabled: Boolean = false,

    /**
     * Detects and skips near-silent lead-in audio at the start of a track
     * only (not mid-track or trailing silence).
     */
    val silenceTrimmingEnabled: Boolean = false,

    /** Volume curve used across a crossfade's overlap window. */
    val crossfadeCurve: CrossfadeCurve = CrossfadeCurve.EQUAL_POWER
) {
    /**
     * True when EITHER crossfade mode is on. Manual and Smart crossfade are
     * mutually exclusive in the UI, but either one must drive an actual
     * crossfade transition, so trigger sites should check this rather than
     * [crossfadeEnabled] alone.
     */
    val crossfadeActive: Boolean
        get() = crossfadeEnabled || smartCrossfadeEnabled

    /**
     * Persists the fields this class actually maps to a real engine (see
     * class doc) into the same `SharedPreferences` files/keys
     * [EqualizerManager]/`MusicService` already read from. EQ/bass-boost go
     * to the default `SharedPreferences` file (matching [EqualizerManager]);
     * everything else goes to the `"settings"`-named file (matching
     * `MusicService`). Fields with no engine mapping are not written.
     */
    fun saveToPrefs(context: Context) {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPrefs.edit()
            .putBoolean("equalizer_enabled", equalizerEnabled)
            .putString("equalizer_preset", eqPreset.name)
            .putString("custom_eq_bands", eqBands.joinToString(",") { (it * 100).toInt().toString() })
            .putInt("bass_boost_strength", if (bassBoostEnabled) (bassBoostGain / MAX_BOOST_DB * 1000f).toInt().coerceIn(0, 1000) else 0)
            .apply()

        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putFloat("playback_speed", speed)
            .putInt("pitch_semitones", pitchSemitones.toInt())
            .putBoolean("crossfade_enabled", crossfadeEnabled)
            .putLong("crossfade_duration_ms", (crossfadeDuration * 1000).toLong())
            .putBoolean("smart_crossfade_enabled", smartCrossfadeEnabled)
            .putString("crossfade_curve", crossfadeCurve.name)
            .putBoolean("replaygain_enabled", replayGainEnabled)
            .putBoolean("skip_silence_enabled", silenceTrimmingEnabled)
            .apply()
    }

    companion object {
        /**
         * Snapshots the fields this class maps to a real engine (see class
         * doc) out of the same `SharedPreferences` files/keys
         * [EqualizerManager]/`MusicService` already own -- this is a live
         * read of currently-audible state, not a separate stored copy.
         * [eqBands] is resampled/padded to exactly [EQ_BAND_COUNT] entries
         * from whatever band count this device's native `Equalizer`
         * actually reports (varies by device/OEM), so it's an approximation
         * when that count isn't 10 -- same truncate/pad tolerance
         * [EqualizerManager.applyLevels] itself already uses.
         */
        fun fromPrefs(context: Context): AudioSettings {
            val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

            val rawBands = defaultPrefs.getString("custom_eq_bands", null)
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.map { it / 100f }
                .orEmpty()
            val eqBands = when {
                rawBands.isEmpty() -> List(EQ_BAND_COUNT) { 0f }
                rawBands.size == EQ_BAND_COUNT -> rawBands
                rawBands.size > EQ_BAND_COUNT -> rawBands.take(EQ_BAND_COUNT)
                else -> rawBands + List(EQ_BAND_COUNT - rawBands.size) { 0f }
            }

            val bassBoostStrength = defaultPrefs.getInt("bass_boost_strength", 0).coerceIn(0, 1000)

            return AudioSettings(
                speed = settingsPrefs.getFloat("playback_speed", 1.0f),
                pitchSemitones = settingsPrefs.getInt("pitch_semitones", 0).toFloat(),
                equalizerEnabled = defaultPrefs.getBoolean("equalizer_enabled", false),
                eqBands = eqBands,
                eqPreset = runCatching {
                    EQPreset.valueOf(defaultPrefs.getString("equalizer_preset", EQPreset.FLAT.name) ?: EQPreset.FLAT.name)
                }.getOrDefault(EQPreset.CUSTOM),
                crossfadeEnabled = settingsPrefs.getBoolean("crossfade_enabled", false),
                crossfadeDuration = settingsPrefs.getLong("crossfade_duration_ms", 2000L) / 1000.0,
                gaplessEnabled = true,
                replayGainEnabled = settingsPrefs.getBoolean("replaygain_enabled", false),
                bassBoostEnabled = bassBoostStrength > 0,
                bassBoostGain = (bassBoostStrength / 1000f) * MAX_BOOST_DB,
                smartCrossfadeEnabled = settingsPrefs.getBoolean("smart_crossfade_enabled", false),
                silenceTrimmingEnabled = settingsPrefs.getBoolean("skip_silence_enabled", false),
                crossfadeCurve = runCatching {
                    CrossfadeCurve.valueOf(settingsPrefs.getString("crossfade_curve", CrossfadeCurve.EQUAL_POWER.name) ?: CrossfadeCurve.EQUAL_POWER.name)
                }.getOrDefault(CrossfadeCurve.EQUAL_POWER)
            )
        }

        /**
         * Upper bound for [volume]. Values above `1.0` (100%) drive the signal
         * chain's gain stages hotter than unity; `4.0` corresponds to
         * `20*log10(4) ≈ +12 dB` of boost headroom (see [MAX_BOOST_DB]) — the
         * widest range the slider exposes.
         */
        const val MAX_VOLUME: Float = 4.0f

        /** Maximum boost, in dB, applied when [volume] exceeds `1.0`. Matches `20*log10(MAX_VOLUME)`. */
        const val MAX_BOOST_DB: Float = 12.0f

        /** Number of bands in [eqBands]. */
        const val EQ_BAND_COUNT: Int = 10

        /** Center frequency, in Hz, of each band in [eqBands], in order. */
        val EQ_BAND_FREQUENCIES_HZ: List<Int> = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    }
}

/**
 * Named 10-band equalizer curves, ported from Lumisound's
 * `Sources/Services/EQPresets.swift`. Band gains (dB) are ordered to match
 * [AudioSettings.EQ_BAND_FREQUENCIES_HZ]: 32/64/125/250/500/1000/2000/4000/8000/16000 Hz.
 */
enum class EQPreset(val displayName: String, val bands: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),

    /** Punchy sub/bass lift with a small compensating dip through the low-mids. */
    BASS_BOOST("Bass Boost", listOf(8.0f, 6.5f, 4.0f, 1.0f, -1.0f, -1.5f, -1.0f, 0f, 0f, 0f)),

    /** Slightly cut lows, open up the high end. */
    TREBLE_BOOST("Treble Boost", listOf(-2f, -1f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 6f)),

    /** Presence-focused curve for spoken word / a cappella content. */
    VOCAL("Vocal", listOf(-2.0f, -1.5f, -1.0f, 0.5f, 2.0f, 3.0f, 2.5f, 1.0f, 0f, -1.0f)),

    /** Bright, vocal-forward pop curve. */
    POP("Pop", listOf(0f, 1.0f, 1.5f, 0.5f, -1.0f, -1.5f, -0.5f, 1.0f, 2.5f, 2.0f)),

    /** Classic EDM "smiley" curve. */
    ELECTRONIC("Electronic", listOf(3.0f, 2.0f, 0f, -1.0f, -1.5f, -1.0f, 1.0f, 2.5f, 3.5f, 3.0f)),

    /** The classic rock "V" curve. */
    ROCK("Rock", listOf(4.0f, 2.5f, -1.0f, -2.0f, -1.0f, 1.0f, 2.5f, 3.0f, 2.0f, 1.5f)),

    /** Natural and accurate. */
    CLASSICAL("Classical", listOf(1.0f, 0.5f, 0f, 0f, 0f, 0f, 0.5f, 1.0f, 1.5f, 2.0f)),

    /** Warm and smooth. */
    JAZZ("Jazz", listOf(1.5f, 1.0f, 0.5f, 0.5f, 0f, 0f, 0.5f, 1.0f, 1.5f, 1.0f)),

    /** Heavy 808 sub-bass with crisp highs. */
    HIP_HOP("Hip-Hop", listOf(6.0f, 4.5f, 1.0f, -1.5f, -1.0f, 0f, 1.0f, 2.0f, 2.5f, 1.5f)),

    /** Natural warmth with detail across the spectrum. */
    ACOUSTIC("Acoustic", listOf(1.0f, 1.0f, 1.5f, 1.0f, 0.5f, 0.5f, 1.0f, 1.0f, 0.5f, 1.0f)),

    /** User-defined values; this all-flat default is never applied over user edits. */
    CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f));

    companion object {
        /**
         * Suggests an EQ preset based on a track's tempo, for "Auto EQ" mode.
         * Slower tracks lean toward presets that favor warmth/clarity over
         * punch (Classical/Acoustic), mid-tempo toward Pop, and fast/
         * high-energy tracks toward Electronic's boosted sub-bass and highs.
         * Returns `null` when [bpm] is unknown/non-positive, leaving the
         * current preset untouched.
         */
        fun forBpm(bpm: Double?): EQPreset? {
            if (bpm == null || bpm <= 0) return null
            return when {
                bpm < 70.0 -> CLASSICAL
                bpm < 100.0 -> ACOUSTIC
                bpm < 130.0 -> POP
                else -> ELECTRONIC
            }
        }

        /**
         * Maps a free-text genre tag (case/substring-insensitive) onto the
         * closest built-in preset. Returns `null` for an empty or
         * unrecognized genre. Rules are ordered most-specific-first so e.g.
         * "acoustic rock" matches [ACOUSTIC] before the broader "rock" rule.
         */
        fun forGenre(genre: String?): EQPreset? {
            val raw = genre?.lowercase()
            if (raw.isNullOrEmpty()) return null

            val rules: List<Pair<List<String>, EQPreset>> = listOf(
                listOf("hip hop", "hip-hop", "hiphop", "rap", "trap", "drill") to HIP_HOP,
                listOf("classical", "orchestra", "symphony", "baroque", "opera") to CLASSICAL,
                listOf("acoustic", "folk", "singer-songwriter", "unplugged") to ACOUSTIC,
                listOf("jazz", "blues", "swing", "bebop") to JAZZ,
                listOf(
                    "edm", "electronic", "house", "techno", "trance", "dubstep",
                    "dance", "drum and bass", "dnb"
                ) to ELECTRONIC,
                listOf("metal", "rock", "punk", "grunge", "alternative") to ROCK,
                listOf("pop", "k-pop", "synthpop", "indie pop") to POP,
                listOf("vocal", "a cappella", "acappella", "spoken") to VOCAL
            )

            for ((needles, preset) in rules) {
                if (needles.any { raw.contains(it) }) return preset
            }
            return null
        }

        /**
         * Genre-aware Auto EQ: when a track carries a recognizable genre tag,
         * map it directly to the matching tonal preset via [forGenre] (a far
         * stronger signal than tempo alone). Falls back to [forBpm] when the
         * genre is empty/unrecognized, so Auto EQ still adapts for untagged
         * tracks. Returns `null` only when neither signal is usable.
         */
        fun auto(bpm: Double?, genre: String?): EQPreset? {
            return forGenre(genre) ?: forBpm(bpm)
        }
    }
}

/**
 * Volume curve applied across a crossfade's overlap window. Ported from
 * Lumisound's `CrossfadeCurve` (`PlaybackModels.swift`).
 */
enum class CrossfadeCurve(val displayName: String, val subtitle: String) {
    LINEAR(
        "Linear",
        "Straight fade — perceived volume dips slightly midway."
    ),
    EQUAL_POWER(
        "Equal-Power",
        "Constant perceived loudness through the whole transition."
    )
}

/**
 * Room/space presets for the live reverb effect. Ported from Lumisound's
 * `ReverbRoomPreset` (`PlaybackModels.swift`), which itself mirrors a subset
 * of `AVAudioUnitReverbPreset` as its own String-backed enum so its Models
 * layer doesn't need to import AVFoundation. Kept as a plain named enum here
 * for the same reason: the mapping to a concrete DSP preset (e.g.
 * `android.media.audiofx.PresetReverb`) belongs in a later engine-wiring
 * layer, not in this schema.
 */
enum class ReverbRoomPreset(val displayName: String) {
    SMALL_ROOM("Small Room"),
    MEDIUM_ROOM("Medium Room"),
    LARGE_ROOM("Large Room"),
    MEDIUM_HALL("Medium Hall"),
    LARGE_HALL("Large Hall"),
    PLATE("Plate"),
    CATHEDRAL("Cathedral")
}
