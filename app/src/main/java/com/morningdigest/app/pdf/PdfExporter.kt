package com.morningdigest.app.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.morningdigest.app.data.model.DigestReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders today's digest report to a simple, clean single-page PDF using the
 * platform PdfDocument API (zero extra dependencies), then exposes it via
 * FileProvider so it can be shared/opened from other apps.
 */
object PdfExporter {

    fun export(context: Context, report: DigestReport, cityLabel: String): File {
        val pageWidth = 595 // A4 @ 72dpi
        val pageHeight = 842
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val titlePaint = Paint().apply { color = Color.rgb(28, 27, 46); textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
        val headerPaint = Paint().apply { color = Color.rgb(74, 63, 207); textSize = 16f; typeface = Typeface.DEFAULT_BOLD }
        val bodyPaint = Paint().apply { color = Color.rgb(60, 58, 80); textSize = 12f }
        val mutedPaint = Paint().apply { color = Color.rgb(150, 145, 170); textSize = 11f }

        var y = 50f
        val x = 40f
        val dateStr = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(Date(report.timestampMillis))

        canvas.drawText("🌅 The Brief", x, y, titlePaint); y += 22f
        canvas.drawText("$dateStr — $cityLabel", x, y, mutedPaint); y += 34f

        if (report.weatherAlerts.available && report.weatherAlerts.alerts.isNotEmpty()) {
            val alertPaint = Paint().apply { color = Color.rgb(198, 40, 40); textSize = 13f; typeface = Typeface.DEFAULT_BOLD }
            canvas.drawText("⚠️ Severe Weather Alert: ${report.weatherAlerts.alerts.first().event}", x, y, alertPaint); y += 24f
        }

        canvas.drawText("Today's Weather", x, y, headerPaint); y += 20f
        val w = report.weatherToday
        if (w.available) {
            canvas.drawText("${w.temp?.toInt() ?: "—"}°C, feels like ${w.feelsLike?.toInt() ?: "—"}°C — ${w.description ?: ""}", x, y, bodyPaint); y += 16f
            canvas.drawText("Humidity ${w.humidity ?: "—"}% · Wind ${w.windSpeed ?: "—"} m/s · Pressure ${w.pressure ?: "—"} hPa", x, y, bodyPaint); y += 16f
        } else {
            canvas.drawText("Unavailable", x, y, bodyPaint); y += 16f
        }
        y += 14f

        canvas.drawText("Tomorrow", x, y, headerPaint); y += 20f
        val t = report.weatherTomorrow
        canvas.drawText(
            if (t.available) "${t.avgTemp?.toInt() ?: "—"}°C avg — ${t.description ?: ""}, ${t.rainChancePercent ?: 0}% rain chance" else "Unavailable",
            x, y, bodyPaint
        ); y += 30f

        canvas.drawText("Bitcoin", x, y, headerPaint); y += 20f
        val b = report.bitcoin
        canvas.drawText(
            if (b.available) "€${b.eur} · \$${b.usd} · kr ${b.nok}  (${b.change24hPercent}% 24h)" else "Unavailable",
            x, y, bodyPaint
        ); y += 30f

        val c = report.currency
        canvas.drawText("${c.baseCurrency} → ${c.targetCurrency}", x, y, headerPaint); y += 20f
        canvas.drawText(
            if (c.available) "1 ${c.baseCurrency} = ${c.rate} ${c.targetCurrency}  (${c.change24hPercent}% 24h)" else "Unavailable",
            x, y, bodyPaint
        ); y += 30f

        canvas.drawText("World News", x, y, headerPaint); y += 20f
        if (report.news.available) {
            for (headline in report.news.headlines.take(15)) {
                if (y > pageHeight - 60) break
                val sourceSuffix = if (headline.source.isNotBlank()) "  (${headline.source})" else ""
                canvas.drawText("• ${headline.title.take(80)}$sourceSuffix", x, y, bodyPaint)
                y += 16f
            }
        } else {
            canvas.drawText("Unavailable", x, y, bodyPaint); y += 16f
        }

        document.finishPage(page)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "morning_digest_${report.timestampMillis}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
