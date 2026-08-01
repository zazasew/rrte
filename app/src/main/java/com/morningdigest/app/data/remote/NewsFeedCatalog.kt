package com.morningdigest.app.data.remote

/**
 * The fixed list of RSS sources/topics the user can pick from in Settings.
 * Kept as a single shared catalog so the repository (fetching) and the
 * settings picker UI can never drift out of sync with each other.
 */
object NewsFeedCatalog {

    data class Feed(val id: String, val label: String, val url: String, val topic: String)

    val ALL: List<Feed> = listOf(
        Feed("bbc", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "World"),
        Feed("guardian", "The Guardian World", "https://www.theguardian.com/world/rss", "World"),
        Feed("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "World"),
        Feed("npr", "NPR", "https://feeds.npr.org/1004/rss.xml", "World"),
        Feed("dw", "DW", "https://rss.dw.com/xml/rss-en-all", "World"),
        Feed("bbc_business", "BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "Business"),
        Feed("bbc_tech", "BBC Technology", "https://feeds.bbci.co.uk/news/technology/rss.xml", "Technology"),
        Feed("techcrunch", "TechCrunch", "https://techcrunch.com/feed/", "Technology"),
        Feed("bbc_sport", "BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "Sport"),
        Feed("bbc_science", "BBC Science", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "Science"),
        Feed("politico", "Politico", "https://www.politico.com/rss/politicopicks.xml", "World")
    )

    /** Feeds selected out of the box, before the user customizes anything - matches the app's original fixed set. */
    val DEFAULT_SELECTED_IDS: Set<String> = setOf("bbc", "guardian", "aljazeera", "npr", "dw", "politico")

    fun byIds(ids: Set<String>): List<Feed> = ALL.filter { it.id in ids }
}

/**
 * Curated sources for the optional, dedicated US Politics card - kept
 * separate from the general World News catalog so enabling it doesn't mix in
 * business/sport/tech the way the old combined feed did.
 */
object PoliticsFeedCatalog {
    val ALL: List<NewsFeedCatalog.Feed> = listOf(
        NewsFeedCatalog.Feed("pol_bbc_us", "BBC US & Canada", "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml", "Politics"),
        NewsFeedCatalog.Feed("pol_guardian", "Guardian US Politics", "https://www.theguardian.com/us-news/us-politics/rss", "Politics"),
        NewsFeedCatalog.Feed("pol_npr", "NPR Politics", "https://feeds.npr.org/1014/rss.xml", "Politics"),
        NewsFeedCatalog.Feed("pol_politico", "Politico", "https://www.politico.com/rss/politicopicks.xml", "Politics"),
        NewsFeedCatalog.Feed("pol_thehill", "The Hill", "https://thehill.com/feed/", "Politics"),
        NewsFeedCatalog.Feed("pol_foxnews", "Fox News Politics", "https://moxie.foxnews.com/google-publisher/politics.xml", "Politics"),
        NewsFeedCatalog.Feed("pol_bbc_news", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "Politics"),
        NewsFeedCatalog.Feed("pol_cnn", "CNN Politics", "http://rss.cnn.com/rss/cnn_allpolitics.rss", "Politics"),
        NewsFeedCatalog.Feed("pol_cnbc", "CNBC Politics", "https://www.cnbc.com/id/10000113/device/rss/rss.html", "Politics"),
        NewsFeedCatalog.Feed("pol_ap", "Associated Press", "https://apnews.com/hub/politics.rss", "Politics")
    )

    /** A sensible, reliable default set - the user can add/remove any of these in Settings. */
    val DEFAULT_SELECTED_IDS: Set<String> = setOf("pol_bbc_us", "pol_guardian", "pol_npr", "pol_politico")

    fun byIds(ids: Set<String>): List<NewsFeedCatalog.Feed> = ALL.filter { it.id in ids }
}

/**
 * Curated sources for the optional, dedicated Business News card - kept
 * separate from the general World News catalog for the same reason as
 * [PoliticsFeedCatalog].
 */
object BusinessFeedCatalog {
    val ALL: List<NewsFeedCatalog.Feed> = listOf(
        NewsFeedCatalog.Feed("biz_bbc", "BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml", "Business"),
        NewsFeedCatalog.Feed("biz_guardian", "Guardian Business", "https://www.theguardian.com/uk/business/rss", "Business"),
        NewsFeedCatalog.Feed("biz_cnbc", "CNBC Business", "https://www.cnbc.com/id/10001147/device/rss/rss.html", "Business"),
        NewsFeedCatalog.Feed("biz_marketwatch", "MarketWatch Top Stories", "https://feeds.content.dowjones.io/public/rss/mw_topstories", "Business"),
        NewsFeedCatalog.Feed("biz_forbes", "Forbes Business", "https://www.forbes.com/business/feed/", "Business"),
        NewsFeedCatalog.Feed("biz_foxbusiness", "Fox Business", "https://moxie.foxbusiness.com/google-publisher/latest.xml", "Business"),
        NewsFeedCatalog.Feed("biz_yahoo", "Yahoo Finance", "https://finance.yahoo.com/news/rssindex", "Business")
    )

    /** A sensible, reliable default set - the user can add/remove any of these in Settings. */
    val DEFAULT_SELECTED_IDS: Set<String> = setOf("biz_bbc", "biz_guardian", "biz_cnbc")

    fun byIds(ids: Set<String>): List<NewsFeedCatalog.Feed> = ALL.filter { it.id in ids }
}
