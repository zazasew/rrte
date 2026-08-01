package com.morningdigest.app.data.remote

import com.morningdigest.app.data.model.NewsHeadline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fetches and parses an RSS 2.0 feed. Kept dependency-free (no extra XML/RSS
 * library) by using the platform's built-in XmlPullParser, which is fast and
 * reliable for the simple <item><title>/<link>/<pubDate> structure standard
 * RSS 2.0 world-news feeds use (BBC, The Guardian, Al Jazeera, NPR, DW, etc).
 */
class RssFeedFetcher(private val client: OkHttpClient) {

    /** Result of testing whether a feed URL is actually reachable and parseable. */
    sealed class FeedCheckResult {
        data class Success(val headlineCount: Int, val sampleTitle: String?) : FeedCheckResult()
        data class Failure(val reason: String) : FeedCheckResult()
    }

    /**
     * Actively fetches and parses [url] and reports back a human-readable
     * result, so the Settings screen can show "✓ Confirmed - found 8
     * articles" or a specific, actionable reason it failed, instead of the
     * source silently contributing zero headlines to the digest.
     */
    suspend fun checkFeed(url: String): FeedCheckResult = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return@withContext FeedCheckResult.Failure("Enter a URL first")
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return@withContext FeedCheckResult.Failure("URL must start with http:// or https://")
        }
        try {
            val headlines = fetchTopHeadlines(trimmed, limit = 5)
            if (headlines.isEmpty()) {
                FeedCheckResult.Failure(
                    "Connected, but no articles were found. This may be a regular webpage, not an RSS feed - " +
                        "look for a link ending in /rss, /feed, or .xml on the site."
                )
            } else {
                FeedCheckResult.Success(headlineCount = headlines.size, sampleTitle = headlines.first().title)
            }
        } catch (e: java.net.UnknownHostException) {
            FeedCheckResult.Failure("Couldn't reach that address - check the URL is spelled correctly")
        } catch (e: java.net.SocketTimeoutException) {
            FeedCheckResult.Failure("The site took too long to respond - it may be down, try again shortly")
        } catch (e: javax.net.ssl.SSLException) {
            FeedCheckResult.Failure("Secure connection failed - the site's certificate may be invalid")
        } catch (e: Exception) {
            val msg = e.message ?: "unknown error"
            FeedCheckResult.Failure(
                when {
                    "HTTP 404" in msg -> "Page not found (404) - double-check the feed URL is correct"
                    "HTTP 403" in msg -> "Access denied (403) - this site blocks automated requests"
                    "HTTP 401" in msg -> "Access denied (401) - this feed may require a login"
                    msg.startsWith("HTTP") -> "Server returned an error ($msg)"
                    "Empty RSS body" in msg -> "The page loaded but returned no content"
                    else -> "Couldn't read this as an RSS feed ($msg) - make sure the link points at the feed itself, not the website's homepage"
                }
            )
        }
    }

    suspend fun fetchTopHeadlines(url: String, source: String = "", limit: Int = 10): List<NewsHeadline> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("RSS HTTP ${response.code}")
                val body = response.body?.string() ?: throw Exception("Empty RSS body")
                parse(body, source).take(limit)
            }
        }

    private fun parse(xml: String, source: String): List<NewsHeadline> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val results = mutableListOf<NewsHeadline>()
        var eventType = parser.eventType
        var inItem = false
        var currentTag: String? = null
        var title: String? = null
        var link: String? = null
        var pubDate: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag.equals("item", ignoreCase = true)) {
                        inItem = true
                        title = null
                        link = null
                        pubDate = null
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem) {
                        val text = parser.text?.trim()
                        if (!text.isNullOrEmpty()) {
                            when (currentTag?.lowercase()) {
                                "title" -> title = text
                                "link" -> link = text
                                "pubdate" -> pubDate = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", ignoreCase = true)) {
                        if (!title.isNullOrBlank() && !link.isNullOrBlank()) {
                            results.add(
                                NewsHeadline(
                                    title = title,
                                    link = link,
                                    source = source,
                                    pubDateMillis = pubDate?.let { parsePubDate(it) }
                                )
                            )
                        }
                        inItem = false
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }
        return results
    }

    /**
     * RSS <pubDate> is normally RFC-822 (e.g. "Fri, 11 Jul 2026 08:15:00 GMT"),
     * but real-world feeds are inconsistent, so a couple of fallback patterns
     * are tried too. Returns null (rather than throwing) if none match, so a
     * single malformed date never breaks the whole feed.
     */
    private fun parsePubDate(raw: String): Long? {
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in patterns) {
            runCatching {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                return fmt.parse(raw.trim())?.time
            }
        }
        return null
    }
}
