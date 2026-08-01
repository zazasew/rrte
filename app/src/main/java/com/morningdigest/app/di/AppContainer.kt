package com.morningdigest.app.di

import android.content.Context
import com.morningdigest.app.data.local.AppDatabase
import com.morningdigest.app.data.prefs.SettingsRepository
import com.morningdigest.app.data.remote.CoinGeckoApi
import com.morningdigest.app.data.remote.FrankfurterApi
import com.morningdigest.app.data.remote.LiveCurrencyFetcher
import com.morningdigest.app.data.remote.OpenWeatherApi
import com.morningdigest.app.data.remote.PoliceReportFetcher
import com.morningdigest.app.data.remote.RssFeedFetcher
import com.morningdigest.app.data.remote.StockMoversFetcher
import com.morningdigest.app.data.remote.YoutubeChannelFetcher
import com.morningdigest.app.data.repository.DigestRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Simple, explicit dependency container. Chosen deliberately over a
 * reflection/annotation-processor based DI framework (Hilt/Koin) to keep the
 * build fast, predictable, and free of KSP/annotation-processor version
 * mismatches - a good tradeoff for an app of this size while still giving
 * clean separation between layers (repository pattern + constructor injection).
 */
class AppContainer(context: Context) {

    val settingsRepository = SettingsRepository(context)

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofitFor(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val openWeatherApi: OpenWeatherApi by lazy {
        retrofitFor("https://api.openweathermap.org/").create(OpenWeatherApi::class.java)
    }

    private val coinGeckoApi: CoinGeckoApi by lazy {
        retrofitFor("https://api.coingecko.com/").create(CoinGeckoApi::class.java)
    }

    private val frankfurterApi: FrankfurterApi by lazy {
        retrofitFor("https://api.frankfurter.app/").create(FrankfurterApi::class.java)
    }

    val rssFeedFetcher: RssFeedFetcher by lazy { RssFeedFetcher(okHttpClient) }

    val stockMoversFetcher: StockMoversFetcher by lazy { StockMoversFetcher(okHttpClient) }

    val youtubeChannelFetcher: YoutubeChannelFetcher by lazy { YoutubeChannelFetcher(okHttpClient) }

    val liveCurrencyFetcher: LiveCurrencyFetcher by lazy { LiveCurrencyFetcher(okHttpClient) }

    val policeReportFetcher: PoliceReportFetcher by lazy { PoliceReportFetcher(context, okHttpClient) }

    private val database: AppDatabase by lazy { AppDatabase.getInstance(context) }

    val digestRepository: DigestRepository by lazy {
        DigestRepository.create(database, openWeatherApi, coinGeckoApi, frankfurterApi, rssFeedFetcher, stockMoversFetcher, youtubeChannelFetcher, liveCurrencyFetcher)
    }

    companion object {
        @Volatile private var INSTANCE: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppContainer(context.applicationContext).also { INSTANCE = it }
            }
    }
}
