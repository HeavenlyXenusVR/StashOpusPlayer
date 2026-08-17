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
  serves, but leaderboards, compatibility, activity feed, Listen Together
  (SharePlay has no Android equivalent to map to anyway), and profile
  comments/banners are still unbuilt. Collaborative playlists are now
  built -- see the "Cloud Playlists + Collaboration" entry below.
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

  **Scrobbling is now that separate chunk, and it's done too**
  (`Settings -> Scrobbling`). Last.fm and Libre.fm both use the same
  manual two-step web-auth flow Lumisound uses (no polling, no deep
  link/callback -- the bridge has no way to notify the client when the
  user finishes approving in the browser): request a token, open the
  server-provided `auth_url` via a plain `Intent.ACTION_VIEW`, then the
  user comes back and taps "Finish Linking" -- a 400 there just means
  "not approved yet." Added one small convenience beyond the iOS
  original: a pending link auto-retries once, silently, when the screen
  resumes. ListenBrainz is a plain pasted-token field (no OAuth-style
  flow on either platform). The actual scrobble POST to any of the three
  services is entirely server-side, fire-and-forget from `/user/history`
  -- this chunk is 100% "manage which accounts are linked," zero
  scrobble-triggering logic on the client. The bridge has no per-service
  unlink route, only a blanket "unlink everything," which the UI surfaces
  honestly rather than pretending otherwise.

- **Cloud/account services**: **Account/Auth is now done, including full
  account management** -- sign in/register/logout, 2FA-login continuation,
  display-name editing, **session list/revoke, change password, delete
  account, and avatar upload** are all wired (`Settings -> Account & Server`,
  `BridgeAccountFragment` hosting `BridgeSettingsScreen`) against the exact
  same `ios-bridge` endpoints Lumisound's `AccountService` uses, with the
  same account working on both apps.

  **Discover Mix, On This Day, and Artist Bio are now ported** too
  (`Settings -> Discover`, `com.stash.opusplayer.ui.compose.discovery.*`,
  plus a new bio card on the existing artist songs screen). Discover Mix
  and On This Day are metadata-only lists (`GET /user/discover-mix`,
  `GET /user/on-this-day`) -- neither endpoint returns a directly-playable
  URL, so tapping a row resolves one on demand via the same
  `BridgeStreamResolver` search results already use, then plays it
  through the shared `MusicPlayerManager` as a single-track queue. This
  is the "resolved bridge track -> actual playback" wiring
  `StreamingBrowseViewModel`'s own KDoc had explicitly flagged as
  deferred -- built here since these two features are pointless without
  it. No "Play All" for Discover Mix (unlike Lumisound): that needs
  resolving every row's stream URL up front before queueing, a lazy-
  queue-resolution capability this app's player doesn't have; per-row
  tap-to-play covers the real use case without it. Artist Bio
  (`GET /api/artist/bio`, JWT-gated despite its `/api/` prefix -- confirmed
  against main.py directly) is a small card embedded in
  `ArtistSongsFragment` above the track list, matching where Lumisound
  surfaces it in `ArtistDetailView`; silently absent if the artist isn't
  found, the user isn't signed in, or the request fails -- no error state,
  matching iOS's own treatment.

  **Two more Discover tabs, Trending and Community, are now ported**
  too (`GET /social/discover`, `GET /social/activity`) -- purely
  informational lists, no tap-to-play, matching Lumisound's own
  `DiscoverView` exactly (its Trending tab has no tap handler either;
  these rows are title/artist aggregates, not single history rows with
  a resolvable URL). Both draw only from users who opted into a new
  **Share Listening Activity** toggle (`PUT /user/privacy`, `Settings
  -> Account & Server`) -- separate from the friends-only
  `share_now_playing` toggle behind the already-shipped Friend
  Activity/Leaderboard feature; this one controls visibility to ALL
  signed-in users, not just friends. Trending ranks title/artist pairs
  by play count over the last 7 days; Community is a flat "what
  everyone's listening to" feed, newest first.

  Avatar upload/display goes through
  `{baseUrl}/user/avatar/{userId}` directly (a raw-bytes GET/POST, not a
  `avatar_url` JSON field -- that column is dead/unused server-side, same
  as on iOS) with a client-side JPEG-recompress step for non-GIF images,
  matching Lumisound's own client-side handling; animated GIF avatars show
  only their first frame (no Coil/GIF-playback dependency in this project
  to render the rest).

  **Discord "Now Playing" webhook is now ported** too (`Settings ->
  Discord Webhook`, `GET/PUT/DELETE /user/discord-webhook`) -- paste an
  incoming-webhook URL, toggle enabled, remove. Entirely server-side and
  fire-and-forget from `POST /user/history` (the same trigger
  `PlayHistoryLogger` already calls for scrobbling), so linking a webhook
  needed zero new client-side triggering logic. Distinct from Discord
  Rich Presence, which needs a local desktop IPC daemon (`discord-rpc/
  install.sh` on iOS) and has **no Android equivalent** -- ruled out of
  this port entirely, not just deferred.

  **Discord account verification is now ported too** (`Settings ->
  Discord Verification`, `GET /api/discord/oauth/start`, `GET`/`DELETE
  /api/discord/verification`). A real OAuth2 "identify"-scope
  authorization-code flow -- proves the signed-in user actually owns a
  specific Discord account, unlike the webhook above which proves
  nothing about identity. iOS uses `ASWebAuthenticationSession`
  specifically so the OS can observe the redirect without polling;
  Chrome Custom Tabs (new `androidx.browser` dependency, previously
  unused in this project) is the direct Android equivalent, paired with
  an intent-filter on `MainActivity` for the bridge's fixed
  `lumisound://discord-verify` redirect URI (not per-client-configurable
  server-side, so Android registers the exact same scheme/host iOS does
  rather than a Stash-specific one). `MainActivity` is now
  `launchMode="singleTask"` so that redirect reliably reaches the
  existing activity instance via `onNewIntent` instead of spawning a
  second one. The actual code exchange (with Discord's client secret)
  happens entirely server-side -- this client never sees an
  authorization code or an exchanged token, only the final linked/
  not-linked state.

  **Folder Backups are now ported** too (`Settings -> Folder Backups`,
  `PUT`/`GET /user/folder-backups`, `com.stash.opusplayer.backup.FolderBackupService`).
  Wholesale replace-on-push, metadata-only -- mirrors
  `AccountService+FolderBackup.swift`'s exact scope, including its
  notable absence of any restore/redownload action: there's no
  dedicated restore UI on iOS either, only push + fetch, since iOS's
  `source_track_id` field needs a durable per-track source id this
  client doesn't persist anywhere (`Song`/`SongEntity` have no such
  column -- only transiently known inside `VideoDownloadManager` at
  download time). Every pushed track is informational only (title/
  artist/duration for reference after a reinstall), never auto-
  redownloadable -- a real limitation, honestly surfaced in the viewer
  screen's own copy, not a silent gap. Pushed (2s debounced) whenever a
  watched folder is added/removed in Library Settings, and after every
  library rescan (`LibraryRescanWorker`). Covers both plain-path
  folders and SAF tree folders (Android has no "relative to Documents"
  path concept to mirror iOS's `folder_path` with, so tree folders are
  keyed by their `DocumentFile` display name instead).

  Still unbuilt: cross-device sync, subscriptions/feed, push
  notifications, weekly mix (blocked on an entirely separate "personal
  cloud music library" API family -- `/user/music/search|upload|stream|
  artwork|metadata|recommendations|smart-playlists` -- that this app
  doesn't model at all yet; discovered while scoping weekly mix, bigger
  than a one-off addition).

  **Profile banner + guestbook comments are now ported** too (tap a
  friend in `Settings -> Friends`, or `Settings -> Account & Server ->
  View My Public Profile` for your own). A deliberately trimmed slice
  of Lumisound's combined `ProfileView.swift`/`PublicProfileView.swift`:
  identity (username/display name/bio/member-since), a banner image
  (upload/remove when viewing your own profile -- raw-bytes POST/DELETE
  `/api/social/profile/banner`, GIF-sniffed and JPEG-recompressed on
  upload exactly like the existing avatar upload path, GET treated the
  same way avatar GET is: any non-200 means "no banner", not an error),
  and a guestbook (`GET/POST /api/social/profile/{id}/comments`,
  `DELETE /api/social/profile/comments/{id}`) with the same permission
  rules the server enforces -- posting requires being friends and never
  on your own profile, deleting requires being the comment's author or
  the profile owner.

  **Activity feed and leaderboard are now ported** too (`Settings ->
  Friend Activity`, `GET /api/social/activity/friends`,
  `GET /api/social/friends/leaderboard`), closing out the social
  profile bundle. A "Most Active This Week" card (top 5 of the
  server's own top-10, ranked by play count over its default 7-day
  window -- no client-side control to change that window, matching
  Lumisound's own `ActivitySegmentView`) plus a merged plays+favorites
  feed from all friends, newest first. Both endpoints are read-only,
  gated server-side by each friend's own `share_now_playing` toggle
  (a friend who disabled it is excluded from both, not just presence),
  and return nothing (not an error) if the caller has no friends yet.
  Tapping a leaderboard entry opens that friend's `PublicProfileFragment`
  from the previous chunk; activity feed rows are inert, matching iOS.

  **Badges and listening streak were added to the profile screen in a
  follow-up pass** -- purely additive, no new endpoint: both were
  already present in the same `GET /api/social/profile/{id}` response
  the profile screen already calls, just not modeled client-side yet.
  Milestone chips (member-since tenure, play count, friend count, etc.
  -- server always computes them, no privacy toggle) plus a current/
  longest consecutive-day streak (hidden entirely, not shown as zero,
  if the owner disabled `show_listening_stats`). Badge icons are SF
  Symbol names server-side and aren't rendered -- tier (gold/silver/
  bronze) alone drives this client's chip color. Still not built: pinned
  tracks, top genres/artists, visitor stats, and accent-color/glow/
  avatar-frame/avatar-decoration/profile-effect customization -- the
  endpoint returns all of that too, but only the fields this screen
  actually reads are modeled (see `PublicSocialProfile`'s doc comment).

  **User blocking is now ported** too (`POST`/`DELETE /api/social/block/{id}`,
  `GET /api/social/block` for `Settings -> Blocked Users`). A "Block
  User" button appears on a friend's profile screen (never your own,
  never a non-friend -- matches `PublicProfileView.swift`'s own gating
  exactly), confirms, then blocks: the server tears down any
  friendship/pending request in both directions and makes the two
  profiles mutually invisible (`GET /api/social/profile/{id}` 404s for
  a blocked-either-direction pair, same as "not found"), which this
  client surfaces as a plain "This profile isn't available" message
  rather than an error. `Settings -> Blocked Users` lists everyone
  you've blocked with a per-row Unblock action, ported from Lumisound's
  `BlockedUsersView`.

  **Music Match compatibility is now ported** too (`GET
  /api/social/compatibility/{id}`) -- a friends-only 0-100% score card
  (70% shared-artist / 30% shared-genre Jaccard similarity, weighted
  toward specific-artist overlap over broad genre labels) shown on a
  friend's profile screen, with a progress bar and shared-artist/
  shared-genre text. Shows "Not enough listening history yet to compute
  a match" instead of a 0% score when either side's history is too
  thin. Deliberately does NOT model the companion
  `/api/social/blend/{id}` "press play" mix endpoint (a full playable
  blended mix) -- the score card alone was the contained chunk; a
  playable blend is real future scope.

  **Friend nicknames and tags are now ported** too (`PUT
  /api/social/friends/{id}/nickname`, `GET /api/social/friends/tags`,
  `POST`/`DELETE /api/social/friends/{id}/tags/{name}`, `Settings ->
  Friends`). A private nickname per friend (visible only to the
  caller, never the friend themselves -- purely a personal
  organizational label, same spirit as a phone contact's custom name)
  and up to 10 free-form tags per friend, both edited via a small
  dialog reached from an "Edit" button on each friend row, ported from
  Lumisound's per-friend "..." sheet in `FriendsListView.swift`. The
  friend row itself shows the nickname as the primary label when set
  (falling back to display name, then username -- matching iOS's
  `effectiveName`), the raw `@username` alongside only when a nickname
  exists, and any tags as small chips. `BridgeFriend` already carried
  `nickname`/`tags` fields from an earlier pass with nothing to
  populate them -- this chunk is what actually wires them up.

  **Friend suggestions are now ported** too (`GET
  /api/social/friends/suggestions`, `Settings -> Friends`, "People You
  May Know" section). Mutual-friend suggestions -- other users who
  share at least one friend with you, ranked by mutual-friend count,
  automatically excluding existing friends, pending requests, and
  blocks (all server-side). Each row shows the mutual-friend count and
  a one-tap "Add" that reuses the same send-request plumbing as the
  existing username-based flow, just by user id instead of typed
  username; a sent suggestion disappears from the list immediately.
  `StreamingBrowseScreen` is also now reachable
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

  **Cloud Playlists + Collaboration are now ported too** (`Settings ->
  Cloud Playlists`, `com.stash.opusplayer.ui.compose.playlists.*`). This
  chunk turned out bigger than it first looked: `SyncApi` already modeled
  basic playlist CRUD (`getPlaylists`/`getPlaylist`/etc.) but nothing in
  the app UI had ever called any of it -- there was no cloud-playlist
  browsing screen at all before this. Built one screen covering both list
  ("Your Playlists" + "Shared with You") and detail (tracks,
  collaborator management) as one Compose island with internal
  list/detail state, no Fragment-level navigation. Owners can invite by
  username with an Editor/Viewer role picker and remove any collaborator;
  non-owner collaborators can leave a playlist (remove themselves) but
  see no edit UI, matching Lumisound's own `SharedPlaylistDetailView`
  exactly -- confirmed by grep that iOS's own UI never calls
  `POST /user/playlists/{id}/tracks` either, so "add a track to a cloud
  playlist" is deliberately NOT built here (no reference design exists on
  either platform, despite the endpoint existing server-side). No bridge
  changes were needed; all 5 endpoints
  (`POST/GET /user/playlists/{id}/collaborators`,
  `DELETE .../collaborators/{userId}`, `GET /user/playlists/shared-with-me`,
  `POST /user/playlists/{id}/tracks`) already existed.
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
