package com.morningdigest.app.data.prefs

/**
 * The analyst characters, each publishing their own independent daily
 * briefing card on the dashboard - not a chatbot, no conversation, just an
 * automatically-generated summary of their own domain (see
 * AssistantReportBuilder for how each report is actually built). Max is the
 * odd one out: his "briefing" is the live public police incident feed
 * (Politiloggen) rather than a generated summary sentence, see
 * AssistantDetailScreen for how his screen is special-cased.
 *
 * [accentColorArgb] is a plain Long (0xAARRGGBB) rather than a Compose
 * Color, so this data-layer enum doesn't need to depend on Compose - the UI
 * layer wraps it with Color(character.accentColorArgb) where needed.
 */
enum class MascotCharacter(
    val id: String,
    val displayName: String,
    val role: String,
    /** One sentence shown in Settings next to their avatar, explaining what they cover. */
    val description: String,
    val emoji: String,
    val drawableRes: Int,
    val accentColorArgb: Long
) {
    PANDA(
        "panda", "Panda", "Business Analyst",
        "What's new in business right now, pulled live from the business news feeds.",
        "🐼", com.morningdigest.app.R.drawable.mascot_panda, 0xFFB8860BL
    ),
    OWL(
        "owl", "Scoop", "World News",
        "The most important world news happening right now, pulled live from the news feeds.",
        "🦉", com.morningdigest.app.R.drawable.mascot_owl, 0xFF3B5A9AL
    ),
    BULL(
        "bull", "Bully", "Bull Market Strategist",
        "The bullish take - today's biggest stock market winners, plus today's best-performing tracked assets.",
        "🐂", com.morningdigest.app.R.drawable.mascot_bull, 0xFF2ECC71L
    ),
    BEAR(
        "bear", "Beary", "Risk Manager",
        "The cautious take - today's biggest stock market losers, weakest tracked movers, and risk reminders.",
        "🐻", com.morningdigest.app.R.drawable.mascot_bear, 0xFFE53935L
    ),
    FOX(
        "fox", "Satoshi", "Crypto Specialist",
        "Bitcoin, your tracked crypto coins, and the latest crypto news.",
        "🦊", com.morningdigest.app.R.drawable.mascot_fox, 0xFFCC6B1EL
    ),
    CAT(
        "cat", "Anja", "US Politics Analyst",
        "The latest US politics news, mixed from multiple sources - Politico, The Hill, BBC, CNBC, and more.",
        "🐱", com.morningdigest.app.R.drawable.mascot_cat, 0xFF7B4FC9L
    ),
    MAX(
        "max", "Max", "Police Correspondent",
        "Nearby public police incidents for your municipality, pulled live from Politiloggen.",
        "🛡️", com.morningdigest.app.R.drawable.mascot_max, 0xFF1E4C8CL
    );

    companion object {
        val ALL_IDS: Set<String> = entries.map { it.id }.toSet()
        fun fromId(id: String?): MascotCharacter? = entries.firstOrNull { it.id == id }
    }
}
