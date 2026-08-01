package com.morningdigest.app.data.prefs

/**
 * The set of dashboard cards the user can reorder in Settings > Dashboard
 * Layout. A few items are always pinned in place regardless of the saved
 * order (status bar, greeting, alerts banner, action row, goodbye) since
 * reordering those wouldn't make sense - this list is just the "content"
 * cards in between.
 */
object DashboardCards {
    const val WEATHER = "weather"
    const val MARKETS = "markets"
    const val TOMORROW = "tomorrow"
    const val FACT = "fact"

    // News, US Politics, and Business used to have their own dashboard cards
    // here too, but they now live exclusively in the Assistants tab (Scoop /
    // Anja / Panda respectively) so the same headlines aren't shown twice.
    val DEFAULT_ORDER: List<String> = listOf(WEATHER, MARKETS, TOMORROW, FACT)

    val LABELS: Map<String, String> = mapOf(
        WEATHER to "🌤 Weather",
        MARKETS to "₿💱 Bitcoin & Currency",
        TOMORROW to "🌦 Tomorrow's Forecast",
        FACT to "💡 Fact of the Day"
    )

    /** Guards against a saved order that's missing new cards or has stale/removed keys (e.g. after an app update). */
    fun sanitize(order: List<String>): List<String> {
        val known = order.filter { it in LABELS }
        val missing = DEFAULT_ORDER.filter { it !in known }
        return known + missing
    }
}
