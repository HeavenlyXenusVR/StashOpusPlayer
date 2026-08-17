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

- **Social**: friend requests/presence, leaderboards, activity feed
  (both friends-only and global), compatibility, blocking, nicknames/
  tags, suggestions, profile banner/guestbook/badges/streak, and basic
  self-profile editing are all now built -- see the dedicated entries
  below for each. Listen Together (SharePlay has no Android equivalent
  to map to anyway) is ruled out entirely. Still unbuilt: pinned
  tracks, top genres/artists, visitor stats, accent/frame/decoration/
  effect profile customization, friend blocking's own "compatibility"
  variant is done but its blend-mix companion isn't, and Discord Rich
  Presence config registration (`/user/discord-rpc-config`) -- still
  tied to the local desktop RPC daemon this port already ruled out.
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

  **A fifth Discover tab, Similar Listeners, is now ported** too
  (`GET /social/similar-listeners`) -- real user-to-user collaborative
  filtering: finds other opted-in users whose top artists overlap with
  the caller's own, then surfaces tracks THOSE similar listeners play
  a lot (distinct from both Discover Mix, a YouTube "similar artist"
  search seeded from the caller's own data, and Trending, which isn't
  personalized). Ported from Lumisound's `HubSimilarListenersCarousel`
  on the Library Hub. One deliberate improvement over the iOS
  original: tapping a row here runs a live search (reusing
  `StreamingApi.search`, the same one Cloud Services search uses) and
  plays the first result directly, rather than iOS's approach of
  opening Cloud Services search pre-filled with the query -- a one-tap
  shortcut made possible by this app already having the resolve+play
  pipeline built for Discover Mix. Shows a specific empty-state message
  ("play a few songs first" vs. "no similar listeners yet") driven by
  the server's own `reason` field rather than a generic "nothing
  here." Deliberately skips `/social/trending-by-energy` -- confirmed
  it has no wired UI anywhere in the iOS app, an orphaned endpoint
  with nothing to port from.

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

  **Channel subscriptions + a new-releases feed are now ported** too
  (`Settings -> Subscriptions`, `com.stash.opusplayer.bridge.api.SubscriptionsApi`).
  A subscription is a followed YouTube channel -- NOT the separate,
  unrelated "tracked playlist" feature iOS bundles on the same screen,
  out of scope here. Two tabs: Channels (subscribe by URL/@handle/
  name, per-row Check + Check All, mute toggle, unsubscribe, inline
  new-tracks list after a check) and Feed (a flat new-releases list,
  unread dot indicator, Mark All Read, swipe-equivalent Dismiss).
  Tapping any track (inline new-track or feed row) resolves + plays
  directly via the same pipeline built for Discover Mix -- matches
  iOS's own tap-to-play behavior exactly (never "open source"). No
  client-side polling: the bridge already auto-checks every
  subscription roughly every 4 hours regardless of whether the app is
  open (confirmed background polling loop in main.py), so this screen
  only ever triggers checks in the foreground on an explicit tap,
  matching iOS. Deliberately skips per-subscription auto-download/
  destination-folder/category settings (would need an Android SAF-
  folder-picker equivalent) -- mute is the only per-subscription
  setting this pass edits.

  **Podcast subscriptions + episode playback are now ported** too
  (`Settings -> Podcasts`, `com.stash.opusplayer.bridge.api.PodcastsApi`).
  A completely separate feature from artist channel subscriptions
  above -- RSS-feed-based, not YouTube-channel-based. Subscribe by
  feed URL (validated server-side by actually fetching it), mute/
  unsubscribe, tap a subscription to browse its episode list, tap an
  episode to play it. Notably **needs no bridge resolve/stream step at
  all**: each episode's `audio_url` is a direct RSS enclosure URL,
  playable as-is through the shared player -- unlike every YouTube-
  sourced track list elsewhere in this app, which all need the
  search-or-discover -> resolve -> play two-step. Deliberately trimmed
  from the bridge's full podcast subsystem: OPML import/export is a
  real, separately-portable feature not attempted in this pass.

  **Playback-progress sync (resume-where-you-left-off) was added in a
  follow-up pass** (`PUT`/`GET /user/podcasts/episode-progress`).
  Pushes position every 5 seconds during playback, matching
  Lumisound's own cadence exactly; shows "Resume at M:SS" or "Played"
  on episode rows once progress exists; seeks to the saved position
  automatically when replaying an in-progress episode. A podcast
  episode is identified purely by marker fields on the plain `Song`
  object handed to the player (`genre = "Podcast"`, `album = <feed
  URL>`, `relativePath = <episode guid>`) -- mirroring Lumisound's own
  identical reuse-the-Song-model trick, since neither platform's
  `Song`/track model has dedicated podcast fields. **Scoped to "while
  the Podcasts screen is open," not app-wide background tracking**:
  `MusicPlayerManager` has no steady internal tick this ViewModel
  could piggyback on the way Lumisound's own position timer (ticking
  every 0.5s regardless of visible screen) does -- this app's position
  updates are event-driven, not timer-driven. A future pass could move
  this into `MusicPlayerManager` itself for true background tracking;
  this pass keeps the change fully additive and confined to one
  screen's ViewModel.

  **Podcast search + trending discovery were added in a follow-up
  pass** (`GET /podcasts/search`, `GET /podcasts/trending`, a new
  "Discover" tab alongside Subscriptions). Both are iTunes-Search-API-
  backed (public, no operator API key needed) and return the exact
  same result shape; trending is server-filtered to exclude shows
  already subscribed, search is not (re-subscribing to an
  already-followed show is a harmless no-op upsert either way). Before
  this, a podcast could only be added by already knowing its raw RSS
  feed URL -- this is the first real podcast discovery surface in the
  app.

  **Podcast chapters were added in a follow-up pass** too (`GET
  /user/podcasts/chapters`) -- a "Chapters" button on any episode that
  has a Podcasting 2.0 chapters file, opening a dialog listing them;
  tapping a chapter plays the episode from that timestamp, deliberately
  ignoring any saved resume position (jumping to a chapter mark is an
  explicit choice, not a continuation), matching Lumisound's own
  `PodcastChaptersSheet.playFrom` exactly.

  **OPML import/export were added in a follow-up pass** too (`GET
  /user/podcasts/export-opml`, `POST /user/podcasts/import-opml`),
  closing out the full podcast feature set. Export fetches the raw
  OPML document and shares it via the standard Android share sheet
  (a `content://` URI through the app's existing `FileProvider`, same
  mechanism already used for clip sharing). Import uses a document
  picker (`ACTION_OPEN_DOCUMENT`, any file type -- OPML has no
  standard registered MIME type) to read a file's text and hand it to
  the bridge, which bulk-subscribes to every feed URL found and
  reports how many were added/failed. Notably, `GET /user/podcasts/
  export-opml` returns raw XML text rather than JSON -- modeled with
  Retrofit's `ResponseBody` return type (which bypasses the shared
  Gson converter) rather than a manual `HttpURLConnection` the way the
  avatar/banner raw-bytes GETs elsewhere in this app do, since
  `ResponseBody` still goes through the normal OkHttp client and its
  auth interceptor automatically.

  **Pinned tracks were added in a follow-up pass** too (`PUT
  /api/social/profile/pinned-tracks`) -- up to 5 tracks pinned to your
  profile, shown as a "Pinned Tracks" section on any profile that has
  any (or on your own, always, with add/remove controls). Picked from
  the on-device library (`MusicRepository.getAllSongsFromAllSourcesFast`),
  not a bridge search -- mirrors Lumisound's own `PinnedTrackPickerSheet`
  exactly ("a pinned track is just a display card on the profile, not
  something that needs to be streamable from someone else's device"),
  so every pinned track is metadata-only (title/artist/album), never
  playable from someone else's device, same honest scope choice
  already made for folder-backup tracks. This closes the last small
  gap in the profile-customization surface that was easy to add;
  top genres/artists, visitor stats, and accent/frame/decoration/
  effect customization remain real future scope for the reasons noted
  above.

  Still unbuilt: cross-device sync, push notifications, weekly mix
  (blocked on an entirely separate "personal cloud music library" API
  family -- `/user/music/search|upload|stream|artwork|metadata|
  recommendations|smart-playlists` -- that this app doesn't model at
  all yet; discovered while scoping weekly mix, bigger than a one-off
  addition), and tracked playlists (the sibling feature to artist
  subscriptions -- deliberately skipped after investigation: unlike
  every other feature ported this session, the real `GET
  /user/playlists` endpoint this app's playlist CRUD already uses
  never echoes back `source_url`/`source_new_count`, so a "this
  playlist is tracked" badge or a pending-new-tracks indicator can't
  be shown from a normal list load -- only `/user/sync`'s separate,
  deliberately-unmodeled bespoke payload includes those fields. A
  real implementation would need either adopting `/user/sync` (already
  ruled out, see that endpoint's own doc comment) or a bridge change,
  neither of which fits this pass.

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
  bronze) alone drives this client's chip color. Still not built: top
  genres/artists, visitor stats, and accent-color/glow/avatar-frame/
  avatar-decoration/profile-effect customization -- the endpoint
  returns all of that too, but only the fields this screen actually
  reads are modeled (see `PublicSocialProfile`'s doc comment).

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
  thin.

  **Blend Mix -- the "press play" companion to Music Match -- was
  added in a follow-up pass** too (`GET /api/social/blend/{id}`). A
  "Play Blend Mix" button next to the score (only shown once a real
  score is computed, not on "not enough history") loads a playable
  mix interleaving both people's top artists, using the same seeded-
  yt-dlp-search + resolve-and-play pipeline built for Discover Mix.
  Same friends-only gating as the score itself; empty state matches
  Lumisound's own `BlendMixView` ("Nothing to blend yet..."). Shown
  inline below the Music Match card rather than as a separate screen,
  matching this app's established flat-Compose-island pattern.

  **Basic self-profile editing is now ported** too (`PUT
  /api/social/profile`, an "Edit Profile" button on your own profile
  screen). Bio, pronouns, a status emoji + text, and the guestbook-
  enabled toggle -- deliberately only the fields the profile GET
  response actually returns back to the caller (see
  `SocialProfileUpdateRequest`'s doc comment): the rest of that same
  bridge endpoint also accepts accent colors, avatar frame/decoration,
  profile effect, `share_now_playing`, `show_top_genres`, and the
  visitor/listening-stats toggles, none of which `GET
  /api/social/profile/{id}` echoes back, so an edit UI for them would
  either show a possibly-wrong default or need a second `/me`-shaped
  endpoint this pass doesn't add. Real future scope, not silently
  dropped -- same reasoning as the already-noted top-genres/accent-
  customization gaps.

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

  **Listening stats/insights are now ported** too, as two separate
  screens matching iOS's own split rather than one combined screen
  (`Settings -> Rewind` and `Settings -> Listening Heatmap`, both new
  rows, `com.stash.opusplayer.bridge.api.StatsApi`). Rewind
  (`GET /user/stats`, `/user/stats/month-in-review`,
  `/user/stats/year-in-review`) is a 3-tab recap -- All Time/This
  Month/This Year -- each tab showing total plays, listen time, distinct
  artist/track counts, average BPM where the server provides it, and
  top-artists/top-tracks lists, ported from `RewindView.swift`. Listening
  Heatmap (`GET /user/stats/heatmap`) is a GitHub-contributions-style
  weekly grid over the last 365 days, shaded by daily play count, tap a
  day for its exact count, ported from `ListeningHeatmapView.swift`;
  built client-side from the server's non-zero-filled day list rather
  than assuming one entry per day. Deliberately does NOT port "Share My
  Rewind" -- iOS rasterizes the recap card via `ImageRenderer` and hands
  it to the share sheet, a pure on-device rendering feature with no
  bridge contract to port; only the underlying stats data is modeled
  here.

  **App Lock is now ported** too (`Settings -> Privacy -> App Lock`,
  `security/AppLockManager.kt`), ported from
  `SettingsView+AppLockSection.swift`. A single toggle that requires
  fingerprint/face re-authentication (`androidx.biometric.BiometricPrompt`,
  `BIOMETRIC_STRONG` only) whenever the app is reopened from the
  background, backed by a full-screen lock overlay added to
  `MainActivity`'s root `DrawerLayout` in `onResume` and torn down on
  successful auth. Unlike iOS (which can gate on Face ID/Touch ID alone
  with no PIN fallback shown), this deliberately does not offer a device
  PIN/pattern fallback either -- that would just be the device's own
  lock screen wrapped a second time. If the toggle is turned on but no
  biometric is enrolled on the device, it's rejected immediately with an
  explanatory toast rather than silently no-opping. Purely local/on-device;
  no bridge dependency.

  **Two-Factor Authentication setup is now ported** too (`Settings ->
  Bridge Settings -> Two-Factor Authentication`, in
  `BridgeSettingsScreen.kt`/`BridgeSettingsViewModel.kt`), ported from
  `AccountService+TwoFactorAuth.swift`/`TwoFactorAuthView.swift`. This is
  distinct from -- and was previously the only 2FA piece ported -- the
  login-time 2FA *completion* step (entering a code during sign-in,
  `POST /auth/2fa/login`); this chunk adds actually turning it on/off:
  `GET /auth/2fa/status`, `POST /auth/2fa/setup` (returns a fresh secret +
  `otpauth://` URL), `POST /auth/2fa/verify` (confirms a 6-digit code,
  enables), `POST /auth/2fa/disable` (password-gated). The `otpauth://`
  URL is rendered as a scannable QR code client-side via ZXing's
  `QRCodeWriter` (new dependency: `com.google.zxing:core`, encoder only --
  no camera/scanning code, so not `zxing-android-embedded`); the raw
  secret is also shown as selectable text underneath, matching how both
  the server and Lumisound's own UI already treat manual entry as a
  first-class alternative to scanning, not an afterthought.

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
