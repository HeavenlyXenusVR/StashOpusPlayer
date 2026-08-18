package com.stash.opusplayer.ui.compose.achievements

/**
 * Friendly name/description for each badge id the bridge is known to
 * return (main.py `get_achievements`, confirmed against the live route).
 * Deliberately a lookup with a generic fallback, not a closed enum -- the
 * server can add new badge ids without this client needing an update to
 * at least render *something* reasonable for them.
 */
object BadgeInfo {

    data class Display(val title: String, val description: String)

    private val known: Map<String, Display> = mapOf(
        "plays_10" to Display("Getting Started", "Played 10 tracks"),
        "plays_50" to Display("Regular Listener", "Played 50 tracks"),
        "plays_100" to Display("Dedicated Listener", "Played 100 tracks"),
        "plays_500" to Display("Superfan", "Played 500 tracks"),
        "plays_1000" to Display("Legend", "Played 1,000 tracks"),
        "hours_1" to Display("First Hour", "1 hour listened"),
        "hours_10" to Display("Ten Hours In", "10 hours listened"),
        "hours_24" to Display("Full Day", "24 hours listened"),
        "hours_100" to Display("Century Club", "100 hours listened"),
        "streak_3" to Display("On a Roll", "3-day listening streak"),
        "streak_7" to Display("Weekly Habit", "7-day listening streak"),
        "streak_30" to Display("Monthly Devotion", "30-day listening streak"),
        "streak_100" to Display("Unstoppable", "100-day listening streak"),
        "night_owl" to Display("Night Owl", "Late-night listening"),
        "early_bird" to Display("Early Bird", "Early-morning listening"),
        "marathon" to Display("Marathon", "A long, uninterrupted listening session"),
        "crate_digger" to Display("Crate Digger", "A large, varied local library"),
        "globe_trotter" to Display("Globe Trotter", "A wide range of genres/artists"),
        "completionist" to Display("Completionist", "Listened through full albums"),
        "shuffle_master" to Display("Shuffle Master", "Heavy shuffle-mode listening")
    )

    fun displayFor(badgeId: String): Display =
        known[badgeId] ?: Display(
            title = badgeId.replace('_', ' ').replaceFirstChar { it.uppercase() },
            description = "Achievement unlocked"
        )
}
