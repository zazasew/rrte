package com.morningdigest.app.data.facts

import com.morningdigest.app.data.model.WeatherToday
import kotlin.math.roundToInt

/**
 * Turns today's already-fetched weather into a short "what to wear/bring"
 * line (e.g. "☔ Bring an umbrella", "🧥 Chilly, grab a jacket"). Pure
 * function over [WeatherToday] - no extra API call, no new field to fetch,
 * just a smarter read of data the app already has.
 */
object WeatherOutfitAdvisor {

    fun suggestionFor(weather: WeatherToday?): String? {
        if (weather == null || !weather.available) return null

        val desc = weather.description?.lowercase().orEmpty()
        val feelsLike = weather.feelsLike ?: weather.temp
        val wind = weather.windSpeed ?: 0.0

        val primary = when {
            "thunderstorm" in desc -> "⛈️ Thunderstorms expected — stay indoors if you can"
            "snow" in desc -> "🧤 Snow expected — bundle up and watch your footing"
            "rain" in desc || "drizzle" in desc -> "☔ Bring an umbrella"
            feelsLike == null -> null
            feelsLike < 0 -> "🧥 Freezing — wear a heavy coat"
            feelsLike < 10 -> "🧥 Chilly, grab a jacket"
            feelsLike < 18 -> "👕 Mild — a light layer will do"
            feelsLike < 27 -> "😎 Warm and pleasant"
            else -> "🥵 Hot — stay hydrated"
        } ?: return null

        // Wind gets appended as a secondary note rather than replacing the
        // main advice, since "bring an umbrella AND it's windy" both matter.
        return if (wind >= 10.0) "$primary · 💨 windy, secure loose items" else primary
    }
}
