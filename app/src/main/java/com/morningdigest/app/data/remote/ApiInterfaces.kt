package com.morningdigest.app.data.remote

import com.morningdigest.app.data.model.CoinGeckoResponse
import com.morningdigest.app.data.model.CurrentWeatherResponse
import com.morningdigest.app.data.model.ForecastResponse
import com.morningdigest.app.data.model.FrankfurterResponse
import com.morningdigest.app.data.model.FrankfurterTimeSeriesResponse
import com.morningdigest.app.data.model.GenericCoinPrice
import com.morningdigest.app.data.model.GeoLocationResponse
import com.morningdigest.app.data.model.MarketChartResponse
import com.morningdigest.app.data.model.OneCallResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenWeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") cityAndCountry: String,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): CurrentWeatherResponse

    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("q") cityAndCountry: String,
        @Query("units") units: String = "metric",
        @Query("appid") apiKey: String
    ): ForecastResponse

    // Turns "city,country" into lat/lon, which the One Call alerts endpoint below requires.
    @GET("geo/1.0/direct")
    suspend fun geocode(
        @Query("q") cityAndCountry: String,
        @Query("limit") limit: Int = 1,
        @Query("appid") apiKey: String
    ): List<GeoLocationResponse>

    // Severe weather alerts (storm/flood/heat warnings etc.) for a lat/lon,
    // sourced from the relevant national weather service - plus the hourly
    // forecast block (up to 48h, with UV index), which powers the custom
    // weather alert rules configured in Settings. Requires a One Call
    // 3.0-enabled API key; if the key doesn't have access this simply fails
    // and both the alerts and custom-alerts sections are marked unavailable,
    // same "continue on fail" behaviour as every other section of the digest.
    @GET("data/3.0/onecall")
    suspend fun getAlerts(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("exclude") exclude: String = "current,minutely,daily",
        @Query("appid") apiKey: String
    ): OneCallResponse
}

interface CoinGeckoApi {
    @GET("api/v3/simple/price")
    suspend fun getBitcoinPrice(
        @Query("ids") ids: String = "bitcoin",
        @Query("vs_currencies") vsCurrencies: String = "eur,usd,nok",
        @Query("include_24hr_change") include24hChange: Boolean = true
    ): CoinGeckoResponse

    // Powers the tap-to-see-chart on the Bitcoin card. days=7 gives roughly
    // hourly resolution, which is plenty for a small sparkline-style chart.
    @GET("api/v3/coins/bitcoin/market_chart")
    suspend fun getMarketChart(
        @Query("vs_currency") vsCurrency: String = "eur",
        @Query("days") days: Int = 7
    ): MarketChartResponse

    // Generic multi-coin price lookup for the user's own watchlist entries
    // (Settings > Watchlist) - always priced in USD to keep this simple
    // regardless of which coins are selected. Same underlying endpoint as
    // getBitcoinPrice above, just with a dynamic map response instead of the
    // fixed "bitcoin" field, since ids/vsCurrencies vary per user here.
    @GET("api/v3/simple/price")
    suspend fun getPrices(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "usd",
        @Query("include_24hr_change") include24hChange: Boolean = true
    ): Map<String, GenericCoinPrice>
}

interface FrankfurterApi {
    @GET("latest")
    suspend fun getRate(
        @Query("from") from: String = "EUR",
        @Query("to") to: String = "NOK"
    ): FrankfurterResponse

    // Frankfurter returns the rate for a specific past date here (or the
    // nearest prior business day if the date falls on a weekend/holiday),
    // which is what lets us compute a 24h change like the Bitcoin section.
    @GET("{date}")
    suspend fun getRateOn(
        @Path("date") date: String,
        @Query("from") from: String = "EUR",
        @Query("to") to: String = "NOK"
    ): FrankfurterResponse

    // Powers the tap-to-see-chart on the EUR->NOK card - a range of daily
    // rates (Frankfurter only publishes one rate per business day, so this
    // is naturally a daily series rather than intraday).
    @GET("{startDate}..{endDate}")
    suspend fun getTimeSeries(
        @Path("startDate") startDate: String,
        @Path("endDate") endDate: String,
        @Query("from") from: String = "EUR",
        @Query("to") to: String = "NOK"
    ): FrankfurterTimeSeriesResponse
}
