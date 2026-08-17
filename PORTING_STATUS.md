# Lumisound → StashOpusPlayer: Porting & Lua Rewrite Status

Branch: `phase1-lumisound-parity` (not yet merged to `main`). Everything below
reflects that branch's state as of commit `58eadfe`.

Lumisound (`/Projects/Lumisound/ios`) is the sibling iOS app this port is
tracking. It's a mature codebase (151 files under `Sources/Services`, 174
under `Sources/Views`) with four dedicated Lua-scripting engines. This doc
covers those four engines' port status plus an honest read of how much of
the rest of Lumisound has no Stash equivalent yet.

## The 4 Lua engines

| Engine | Status | Bundled scripts | Wired into a live consumer? |
|---|---|---|---|
| `LuaThemeEngine` | **Ported + wired** | 4 (`lua_presets/`) | Yes — `AppearancePreferences` via `saveToPrefs`/`ThemeManager.broadcastChange` |
| `LuaSmartPlaylistEngine` | **Ported + wired** | n/a (user-authored rules) | Yes — full Room-backed feature |
| `LuaAudioEffectEngine` | **Ported + wired** | 4 (`lua_effects/`) | Yes — live `EqualizerManager`/`MusicService` session-command pipeline |
| `LuaVisualizerEngine` | **Ported, engine-only** | 4 (`lua_visualizers/`) | No — `EnhancedSynthWaveView` not touched yet |
| `LuaUserScriptLibrary` | **Ported, partially wired** | n/a (shared infra) | Only into `LuaThemeEngine`'s screen so far |

### LuaThemeEngine — done
- `app/src/main/java/com/stash/opusplayer/ui/appearance/lua/LuaThemeEngine.kt` + `LuaPreset.kt`.
- Uses LuaJ (pure JVM, no NDK) instead of Lumisound's LuaSwift — reads a
  resolved `theme` Lua table directly rather than JSON-round-tripping through
  a string, since LuaSwift's `evaluate()` only returns a single scalar and
  LuaJ doesn't have that limitation.
- Schema is scoped to what `AppearancePreferences` actually exposes — no
  Liquid-Glass/font-style/panel-material fields invented, since Stash has no
  equivalent settings for those at all.
- **"Lua Presets" section** in `VisualCustomizationFragment`: 4 bundled
  preset chips, an "Import a Preset" form (paste a name + script), and a "My
  Presets" list (apply/delete) backed by `LuaUserScriptLibrary`.

### LuaSmartPlaylistEngine — done
- `app/src/main/java/com/stash/opusplayer/lua/LuaSmartPlaylistEngine.kt`.
- Builds the song batch as a native `LuaTable` and sets it as a global
  directly, rather than JSON-encoding/splicing/decoding through a string the
  way the Swift version has to — no string-escaping/injection surface for a
  weird title/artist tag to worry about.
- Deliberately doesn't port `days_since_added`/play-count facts:
  `Song.dateAdded` mixes two unit conventions (MediaStore seconds vs.
  `File.lastModified()` millis) depending on import path already, and
  there's no persisted play-count store — inventing either would be guessing.
- Full feature: `SmartPlaylistEntity`/`SmartPlaylistDao` (Room, migration
  4→5), `MusicRepository` wrapper methods, and a **Settings → Smart
  Playlists** screen (write a rule, preview match count against the live
  library, save, list/preview/delete saved rules).

### LuaAudioEffectEngine — done
- `app/src/main/java/com/stash/opusplayer/lua/LuaAudioEffectEngine.kt`.
- Only ports the EQ-curve half of Lumisound's `effect` table
  (`eq_bands`/`eq_enabled`/`speed`/`pitch_semitones`) — not `special_mode`
  (rotation/tremolo/vibrato LFOs). Stash has no live DSP for pan/volume/pitch
  LFOs at all, so there'd be nothing to resolve that config into. The 3
  Lumisound scripts that need it (`wide_8d_spin`, `analog_wow_flutter`,
  `heartbeat_pulse`) weren't ported for the same reason.
- **Wired into the real, live equalizer**: `EqualizerManager.applyLuaEffect`
  reuses the existing "truncate/pad to whatever `numberOfBands` this device
  reports" convention every native preset already uses (not a
  frequency-aware resample — matches precedent, doesn't invent a new one).
  Routed through a new `"SET_LUA_EFFECT"` `MediaSession` custom command,
  same cross-process path (`EqualizerFragment` → `MusicService` →
  service-side `EqualizerManager` instance) every other EQ control uses.
  "Lua EQ Presets" chip row added to the Equalizer screen.

### LuaVisualizerEngine — ported, not wired
- `app/src/main/java/com/stash/opusplayer/lua/LuaVisualizerEngine.kt`.
- All 4 bundled scripts ported verbatim (pure color-space math — HSL
  hue-walk, linear channel lerp, single-hue ramp, hard alternation).
- **Not wired into `EnhancedSynthWaveView`** (the live spectrum renderer):
  that's a substantial custom `View` already juggling several `Paint`
  objects, several `ValueAnimator`s, live `Visualizer` FFT capture, and
  multiple render modes, reading its look straight out of `SharedPreferences`
  on its own. Threading a resolved `Config` in safely needs understanding
  that whole rendering pipeline first — real follow-up work, not done yet.

### LuaUserScriptLibrary — ported, partially wired
- `app/src/main/java/com/stash/opusplayer/lua/LuaUserScriptLibrary.kt`.
- Bundled scripts scanned from `assets/<subdirectory>/`; user-imported ones
  saved under `filesDir/<subdirectory>/` (Android's analogue of the Swift
  version's Documents-directory scripts).
- Currently only wired into `LuaThemeEngine`'s screen. **Not yet wired into**
  the Effect or Visualizer screens (Smart Playlists doesn't need it at all —
  each rule is already a persisted Room row, not a shared-library entry).

## What's NOT part of this Lua work at all

Everything below is Lumisound feature surface with **no Stash equivalent**,
confirmed by directory/endpoint survey — not touched by this branch:

- **Social**: friend requests/presence UI now reachable (`FriendsScreen`,
  Settings -> Friends) and backed by the same `SocialApi` Lumisound's bridge
  serves, but leaderboards, compatibility, activity feed, collaborative
  playlists, Listen Together (SharePlay has no Android equivalent to map to
  anyway), and profile comments/banners are still unbuilt.
- **Play History + Achievements are now ported.** `PlayHistoryLogger`
  posts to `/user/history` ~5 seconds after a track starts (cancelled/
  rescheduled on every track change, matching Lumisound's own accidental-
  skip filter exactly), wired into all three places `MusicPlayerManager`
  can start a new track (`updateCurrentSong`, `playQueue`, `playFromPlaylist`).
  This single piece of plumbing was a shared prerequisite for two features
  at once: it's also what `Settings -> Achievements` (`GET /user/achievements`,
  badges/streaks/stats computed live server-side from that same history,
  no separate achievements table) needed to show anything other than
  zeros. Scrobbling (Last.fm/Libre.fm/ListenBrainz) was surveyed as the
  next natural feature to build on this same prerequisite but wasn't
  picked up in this pass -- it needs its own external-browser link flow
  (Last.fm's `auth.gettoken`/`auth.getsession` web flow, fully proxied
  through the bridge already, no bridge changes needed) and is scoped as
  a separate chunk.

- **Cloud/account services**: **Account/Auth is now done, including full
  account management** -- sign in/register/logout, 2FA-login continuation,
  display-name editing, **session list/revoke, change password, delete
  account, and avatar upload** are all wired (`Settings -> Account & Server`,
  `BridgeAccountFragment` hosting `BridgeSettingsScreen`) against the exact
  same `ios-bridge` endpoints Lumisound's `AccountService` uses, with the
  same account working on both apps. Avatar upload/display goes through
  `{baseUrl}/user/avatar/{userId}` directly (a raw-bytes GET/POST, not a
  `avatar_url` JSON field -- that column is dead/unused server-side, same
  as on iOS) with a client-side JPEG-recompress step for non-GIF images,
  matching Lumisound's own client-side handling; animated GIF avatars show
  only their first frame (no Coil/GIF-playback dependency in this project
  to render the rest). Still unbuilt: backups, folder backup, cross-device
  sync, subscriptions/feed, scrobbling (Last.fm/Libre.fm), Discord
  RPC/webhook, push notifications, achievements, discover mix, weekly mix,
  on-this-day, artist bio. `StreamingBrowseScreen` is also now reachable
  (`Settings -> Browse & Stream`) but its underlying `StreamingApi` coverage
  wasn't audited as part of this pass.

  **Cloud Backups (`/user/backups*`) are now ported** too
  (`Settings -> Backup History`, `com.stash.opusplayer.backup.CloudBackupService`).
  Metadata-only, matching the bridge's own design -- server-side snapshots
  are favorites+playlists (+ iOS-only settings this client doesn't touch),
  never audio files. Important scoping note carried over from `SyncApi`'s
  existing doc comment: snapshots are only ever created server-side before
  a `/user/sync` push or a restore, and Stash deliberately never pushes
  `/user/sync` (see that doc comment for why) -- so a Stash-only account
  sees an empty backup list until it's also been used with Lumisound at
  least once, or until its own first restore self-seeds one entry. This is
  surfaced directly in the empty-state UI, not a bug. Restore matches a
  snapshot's favorites/playlist tracks against the local library by
  title[+artist] (same fallback-matching approach `M3UImportService`
  already established) and merges rather than replaces locally, since a
  snapshot may reference songs that only exist on the other device.
- **Watch/widgets/system integration**: `PhoneWatchSync`, `WidgetDataView`,
  Live Activities, Siri App Intents (`LumisoundAppIntents`), Focus Filter.
  No Android Wear or widget work has started.
- **Audio-adjacent services**: **BPM analyzer, mood playlists, and M3U
  import are now ported.** BPM: `com.stash.opusplayer.tempo.BpmAnalyzer`
  ports the Swift original's autocorrelation-on-onset-envelope algorithm
  verbatim; Android has no `AVAssetReader`-equivalent resample-on-decode,
  so `MediaExtractor`+`MediaCodec` decode at native rate then a
  block-averaging downsample substitutes for it (see that file's doc
  comment). Cached in a new `bpm_cache` table (self-invalidates on file
  size change), filled gradually by a new periodic `BpmAnalysisWorker` or
  on-demand via `Settings -> Tempo (BPM)`. Wired into `MoodClassifier` as
  the first-priority tier, matching Lumisound's real priority order (BPM
  before genre keywords). Sort-by-BPM, BPM-proximity shuffle, and
  beat-matched crossfade (all real consumers in the Swift original) are
  NOT ported yet -- the analyzer/cache exists, nothing downstream uses it
  besides mood classification.

  **Clip Maker is also now ported** (`com.stash.opusplayer.clip.ClipExportService`,
  reachable from Now Playing's overflow menu -> "Make Clip"). Unlike
  Lumisound's `AVAssetExportSession`-based trim, Android's `MediaMuxer`
  can't write MP3 and has no format-agnostic stream-copy trim API, so
  this always decodes and re-encodes to AAC/M4A via a hand-built
  `MediaExtractor` -> `MediaCodec` decode -> `MediaCodec` encode ->
  `MediaMuxer` pipeline (no prior art for encode/mux in this codebase --
  BpmAnalyzer only ever decoded for read-only analysis). UI is a plain
  two-thumb `RangeSlider` dialog (60s max clip, matching Lumisound's own
  "plain sliders, not a waveform scrubber" choice), export shares via the
  existing FileProvider pattern. `ClipMakerService`'s and
  `ClipExportService`'s near-duplicate Swift implementations were merged
  into one Kotlin service.

  **AcoustID fingerprint identification is also now ported**
  (`com.stash.opusplayer.identify.AcoustIdService`, reachable from Now
  Playing's overflow menu -> "Identify Track (AcoustID)"). No fingerprinting
  happens client-side on either platform -- the bridge runs real Chromaprint
  (`fpcalc`) and queries api.acoustid.org; the client's only job is
  trimming/uploading a representative clip. Reuses `ClipExportService`'s
  decode/encode pipeline (built for Clip Maker) rather than a second
  implementation, with a 120s cap matching the Swift original. New
  `FingerprintApi` Retrofit client -- NOT `@Multipart` despite uploading a
  file, since the bridge's actual contract is a raw `application/octet-stream`
  body with the extension as a query param, confirmed against both the iOS
  client and main.py directly. Requires being signed in (same shared-bridge
  account as everything else) and a user-configured AcoustID API key
  server-side.

  Still unported: harmonic mixing, spatial audio, GIF search. Harmonic
  mixing and GIF search were surveyed and found to be thin bridge-HTTP
  wrappers around server-side analysis/proxying (small effort if picked up
  later) but contingent on features StashOpusPlayer doesn't have yet
  (cloud-uploaded pre-analyzed tracks; an avatar/banner picker UI).
  Spatial audio is genuine `AVAudioEnvironmentNode` HRTF rendering + head
  tracking with no Android equivalent -- would need a from-scratch HRTF
  convolution engine, a substantially larger undertaking than everything
  else in this category.
- **Library maintenance**: corrupt-file finder, recently-deleted recovery,
  periodic metadata refresh. Stash has its own separate duplicate-finder
  path (`DuplicateFinderService.kt`, pre-existing) but nothing matching
  Lumisound's corrupt-file scanner.

None of this is a gap in the Lua work specifically — it's just the honest
size of what Lumisound has that Stash doesn't, Lua or otherwise.
