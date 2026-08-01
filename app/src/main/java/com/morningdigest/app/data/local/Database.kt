package com.morningdigest.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.content.Context
import com.google.gson.Gson
import com.morningdigest.app.data.model.BitcoinInfo
import com.morningdigest.app.data.model.CurrencyInfo
import com.morningdigest.app.data.model.DailyFact
import com.morningdigest.app.data.model.DigestReport
import com.morningdigest.app.data.model.NewsInfo
import com.morningdigest.app.data.model.StockMover
import com.morningdigest.app.data.model.YoutubeVideoUpdate
import com.morningdigest.app.data.model.WeatherAlertsInfo
import com.morningdigest.app.data.model.WeatherDayForecast
import com.morningdigest.app.data.model.WeatherToday
import com.morningdigest.app.data.model.WeatherTomorrow
import com.morningdigest.app.data.model.WatchlistEntry
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val weatherTodayJson: String,
    val weatherTomorrowJson: String,
    val bitcoinJson: String,
    val currencyJson: String,
    val newsJson: String,
    val dailyFactJson: String = "",
    val weatherAlertsJson: String = "",
    val politicsNewsJson: String = "",
    val businessNewsJson: String = "",
    val upcomingDaysJson: String = "",
    val watchlistJson: String = "",
    val marketsNewsJson: String = "",
    val cryptoNewsJson: String = "",
    val stockGainersJson: String = "",
    val stockLosersJson: String = "",
    val youtubeUpdatesJson: String = "",
    val notificationSent: Boolean,
    val notificationError: String?
)

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun anyToJson(map: Map<String, Any>?): String = gson.toJson(map)
}

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReportEntity): Long

    @Query("SELECT * FROM reports ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports ORDER BY timestampMillis DESC LIMIT 1")
    suspend fun getLatest(): ReportEntity?

    @Query("SELECT * FROM reports ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ReportEntity>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getById(id: Long): ReportEntity?

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reports")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM reports")
    suspend fun count(): Int

    // Keep only the newest [keep] rows - called after every insert to enforce the
    // "last 30 reports" retention rule requested by the user.
    @Query(
        """
        DELETE FROM reports WHERE id NOT IN (
            SELECT id FROM reports ORDER BY timestampMillis DESC LIMIT :keep
        )
        """
    )
    suspend fun trimTo(keep: Int)
}

@Database(entities = [ReportEntity::class], version = 10, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "morning_digest.db"
                ).fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}

/** Maps between the Room entity and the clean domain model used by the UI/notification layer. */
object ReportMapper {
    private val gson = Gson()

    fun toEntity(report: DigestReport): ReportEntity = ReportEntity(
        id = report.id,
        timestampMillis = report.timestampMillis,
        weatherTodayJson = gson.toJson(report.weatherToday),
        weatherTomorrowJson = gson.toJson(report.weatherTomorrow),
        bitcoinJson = gson.toJson(report.bitcoin),
        currencyJson = gson.toJson(report.currency),
        newsJson = gson.toJson(report.news),
        dailyFactJson = gson.toJson(report.dailyFact),
        weatherAlertsJson = gson.toJson(report.weatherAlerts),
        politicsNewsJson = gson.toJson(report.politicsNews),
        businessNewsJson = gson.toJson(report.businessNews),
        upcomingDaysJson = gson.toJson(report.upcomingDays),
        watchlistJson = gson.toJson(report.watchlist),
        marketsNewsJson = gson.toJson(report.marketsNews),
        cryptoNewsJson = gson.toJson(report.cryptoNews),
        stockGainersJson = gson.toJson(report.stockGainers),
        stockLosersJson = gson.toJson(report.stockLosers),
        youtubeUpdatesJson = gson.toJson(report.youtubeUpdates),
        notificationSent = report.notificationSent,
        notificationError = report.notificationError
    )

    fun toDomain(entity: ReportEntity): DigestReport = DigestReport(
        id = entity.id,
        timestampMillis = entity.timestampMillis,
        weatherToday = gson.fromJson(entity.weatherTodayJson, WeatherToday::class.java),
        weatherTomorrow = gson.fromJson(entity.weatherTomorrowJson, WeatherTomorrow::class.java)
            .let { it.copy(parts = it.parts ?: emptyList()) },
        bitcoin = gson.fromJson(entity.bitcoinJson, BitcoinInfo::class.java),
        currency = gson.fromJson(entity.currencyJson, CurrencyInfo::class.java),
        news = gson.fromJson(entity.newsJson, NewsInfo::class.java),
        dailyFact = entity.dailyFactJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { gson.fromJson(it, DailyFact::class.java) }.getOrNull() }
            ?: DailyFact(),
        weatherAlerts = entity.weatherAlertsJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { gson.fromJson(it, WeatherAlertsInfo::class.java) }.getOrNull() }
            ?: WeatherAlertsInfo(),
        politicsNews = entity.politicsNewsJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { gson.fromJson(it, NewsInfo::class.java) }.getOrNull() }
            ?: NewsInfo(),
        businessNews = entity.businessNewsJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { gson.fromJson(it, NewsInfo::class.java) }.getOrNull() }
            ?: NewsInfo(),
        upcomingDays = entity.upcomingDaysJson.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    gson.fromJson<List<WeatherDayForecast>>(it, object : TypeToken<List<WeatherDayForecast>>() {}.type)
                }.getOrNull()
            }
            ?: emptyList(),
        watchlist = entity.watchlistJson.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    gson.fromJson<List<WatchlistEntry>>(it, object : TypeToken<List<WatchlistEntry>>() {}.type)
                }.getOrNull()
            }
            ?: emptyList(),
        marketsNews = entity.marketsNewsJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { gson.fromJson(it, NewsInfo::class.java) }.getOrNull() }
            ?: NewsInfo(),
        cryptoNews = entity.cryptoNewsJson.takeIf { it.isNotBlank() }
            ?.let { runCatching { gson.fromJson(it, NewsInfo::class.java) }.getOrNull() }
            ?: NewsInfo(),
        stockGainers = entity.stockGainersJson.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    gson.fromJson<List<StockMover>>(it, object : TypeToken<List<StockMover>>() {}.type)
                }.getOrNull()
            }
            ?: emptyList(),
        stockLosers = entity.stockLosersJson.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    gson.fromJson<List<StockMover>>(it, object : TypeToken<List<StockMover>>() {}.type)
                }.getOrNull()
            }
            ?: emptyList(),
        youtubeUpdates = entity.youtubeUpdatesJson.takeIf { it.isNotBlank() }
            ?.let {
                runCatching {
                    gson.fromJson<List<YoutubeVideoUpdate>>(it, object : TypeToken<List<YoutubeVideoUpdate>>() {}.type)
                }.getOrNull()
            }
            ?: emptyList(),
        notificationSent = entity.notificationSent,
        notificationError = entity.notificationError
    )
}
