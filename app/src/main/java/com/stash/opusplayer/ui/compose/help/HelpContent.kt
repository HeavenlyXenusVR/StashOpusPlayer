package com.stash.opusplayer.ui.compose.help

data class HelpTopic(val icon: String, val title: String, val body: String)
data class HelpCategory(val icon: String, val title: String, val topics: List<HelpTopic>)

/**
 * Static in-app help/FAQ content, ported from Lumisound's
 * `SettingsHelpView.swift` -- same two-level (category list -> topic
 * list) navigation shape, but NOT a line-for-line transcription. Most of
 * the iOS source's ~90 topics describe iOS-platform mechanics (Face
 * ID/Touch ID, Home/Lock Screen widgets, SharePlay, the iOS Keychain,
 * Apple Music library scanning) that don't apply here, so this is a
 * curated, independently-written set covering only features that
 * actually exist in this app -- verified against `SettingsFragment.kt`'s
 * real tile list and this session's shipped features rather than assumed
 * from the Swift outline. Topics are added here as features ship; this
 * is not meant to be exhaustive on day one.
 */
object HelpContent {
    val categories: List<HelpCategory> = listOf(
        HelpCategory(
            icon = "▶️",
            title = "Playback",
            topics = listOf(
                HelpTopic("🎵", "Crossfade", "Smoothly blends the end of one track into the start of the next instead of a hard cut. Adjustable in Settings -> Playback & Queue."),
                HelpTopic("🔁", "Repeat & Shuffle", "Cycle repeat between off/all/one from Now Playing, and shuffle the current queue without losing your place."),
                HelpTopic("⏰", "Sleep Timer", "Stop playback automatically after a chosen duration, or at the end of the current track -- reachable from Now Playing's overflow menu."),
                HelpTopic("🔁↔️", "A-B Repeat", "Loop a specific section of a track by setting points A and B from the overflow menu -- useful for practicing an instrument part."),
                HelpTopic("📱", "Send to Device", "Hand off what's playing to another signed-in device (e.g. Lumisound on iPhone) from Now Playing's overflow menu. Outbound only -- this device can't yet receive an incoming transfer."),
                HelpTopic("☁️", "Restore Synced Queue", "Pull back a queue that was pushed from another device, from Now Playing's overflow menu. Your current queue is pushed to the cloud automatically whenever you start a new one.")
            )
        ),
        HelpCategory(
            icon = "🎛️",
            title = "Audio Effects",
            topics = listOf(
                HelpTopic("🎛️", "Equalizer", "A multi-band EQ with presets and manual control, in Settings -> Audio Effects."),
                HelpTopic("🎶", "Tempo & Pitch (BPM)", "Analyze and adjust a track's tempo/BPM independently of pitch -- see Settings -> Tempo (BPM)."),
                HelpTopic("🔊", "Volume & ReplayGain", "Keeps volume consistent across tracks recorded at different loudness levels.")
            )
        ),
        HelpCategory(
            icon = "📚",
            title = "Library",
            topics = listOf(
                HelpTopic("🔍", "Scanning Your Library", "Indexes on-device audio via Android's MediaStore, plus any folders you've added -- see Settings -> Library & Scanning."),
                HelpTopic("🔧", "Library Maintenance", "Tools for keeping your library tidy -- duplicate finder and related upkeep utilities, in Settings -> Library Maintenance."),
                HelpTopic("🧠", "Automatic Metadata & Liner Notes", "AcoustID-based track identification, plus AI-written liner notes shown on album screens once an album's artist/title is recognized."),
                HelpTopic("✨", "Aria's Daily Pick", "A single recommended track picked fresh each day from your Discover Mix, with a short reason why -- shown on Settings -> Discover when available."),
                HelpTopic("🎶", "Mood & Smart Playlists", "Mood Playlists group tracks by feel; Smart Playlists auto-update based on rules you set. See Settings -> Mood Playlists / Smart Playlists.")
            )
        ),
        HelpCategory(
            icon = "🌐",
            title = "Streaming",
            topics = listOf(
                HelpTopic("🔎", "Browse & Stream", "Search and stream from YouTube/SoundCloud sources without downloading, in Settings -> Browse & Stream."),
                HelpTopic("📡", "Subscriptions", "Follow channels and get notified about new uploads, shared with Lumisound. See Settings -> Subscriptions."),
                HelpTopic("🎙️", "Podcasts", "Subscribe to RSS feeds and play episodes, shared with Lumisound. See Settings -> Podcasts.")
            )
        ),
        HelpCategory(
            icon = "👤",
            title = "Account & Sync",
            topics = listOf(
                HelpTopic("🔐", "Signing In", "Sign in with a username/password or Continue with Discord from Settings -> Account & Server -- the same shared bridge account Lumisound uses."),
                HelpTopic("🔑", "Two-Factor Authentication", "Add an authenticator-app code requirement to your account from the Two-Factor Authentication section of Bridge Settings."),
                HelpTopic("🔒", "App Lock", "Require fingerprint or face unlock whenever the app is reopened from the background -- Settings -> Privacy -> App Lock. Off by default."),
                HelpTopic("☁️", "What Gets Synced", "Play queue, listening history/stats, playlists, favorites, and account settings sync through the shared bridge server -- not raw audio files."),
                HelpTopic("📁", "Backups", "Folder Backups snapshot chosen folders; Backup History shows past cloud backup runs. See Settings -> Folder Backups / Backup History."),
                HelpTopic("📨", "Discord Integration", "Link your Discord account for rich presence, or configure a webhook for notifications -- Settings -> Discord Verification / Discord Webhook."),
                HelpTopic("📈", "Scrobbling", "Send plays to Last.fm/ListenBrainz once you've linked an account -- Settings -> Scrobbling.")
            )
        ),
        HelpCategory(
            icon = "👥",
            title = "Friends & Profile",
            topics = listOf(
                HelpTopic("👥", "Friends", "Send/accept friend requests, see mutual-friend suggestions, and set private nicknames or tags per friend -- Settings -> Friends."),
                HelpTopic("📋", "Friend Activity & Leaderboard", "See what friends are listening to and compare listening stats -- Settings -> Friend Activity."),
                HelpTopic("🚫", "Blocked Users", "Manage who can't see your profile or activity -- Settings -> Blocked Users."),
                HelpTopic("📌", "Public Profile & Pinned Tracks", "Edit your bio, pronouns, and status, and pin up to 5 favorite tracks to show on your public profile."),
                HelpTopic("💯", "Music Match & Blend Mix", "See a compatibility score with a friend based on shared artists/genres, then play a Blend Mix combining both your tastes -- shown on a friend's profile."),
                HelpTopic("📖", "Guestbook", "Let visitors leave a message on your public profile, if you've enabled it.")
            )
        ),
        HelpCategory(
            icon = "🧭",
            title = "Discovery & Achievements",
            topics = listOf(
                HelpTopic("🎲", "Discover Mix & On This Day", "A personalized mix seeded from your top artists, plus a look back at what you played on this date in past years -- Settings -> Discover."),
                HelpTopic("📈", "Trending & Similar Listeners", "See what's trending among opted-in users, and get recommendations from listeners with similar taste to yours."),
                HelpTopic("🏆", "Achievements", "Badges and streaks earned from your listening activity -- Settings -> Achievements."),
                HelpTopic("📊", "Rewind & Listening Heatmap", "Rewind is an all-time/monthly/yearly listening recap; the Heatmap is a calendar view of how much you've listened each day. Both in Settings.")
            )
        ),
        HelpCategory(
            icon = "🎨",
            title = "Appearance",
            topics = listOf(
                HelpTopic("🎨", "Appearance & Layout", "Customize colors, typography, card style, and layout density -- Settings -> Appearance & Layout."),
                HelpTopic("🌄", "Animations & Background", "Control animation speed/intensity and set a custom background image -- Settings -> Animations & Background.")
            )
        ),
        HelpCategory(
            icon = "🔒",
            title = "Privacy",
            topics = listOf(
                HelpTopic("🔒", "App Lock", "Requires a fingerprint or face unlock check (Android BiometricPrompt, strong-class only) whenever the app is reopened from the background. Rejects being turned on if no biometric is enrolled on the device, rather than silently doing nothing."),
                HelpTopic("👁️", "Listening Activity Visibility", "Choose whether your recent plays (title/artist only) are visible to other signed-in users on Discover -- in Bridge Settings.")
            )
        )
    )
}
