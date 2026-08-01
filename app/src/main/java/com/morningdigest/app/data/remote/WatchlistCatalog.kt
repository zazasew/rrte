package com.morningdigest.app.data.remote

/**
 * The fixed list of extra crypto coins and fiat currencies the user can add
 * to their dashboard watchlist in Settings, on top of the primary Bitcoin
 * and Currency Pair cards. Kept as a shared catalog (same pattern as
 * [NewsFeedCatalog]) so the repository and the settings picker UI can't
 * drift out of sync.
 */
object CryptoCatalog {

    /** [id] is the CoinGecko coin id used in API calls; [symbol] is what's shown on the dashboard. */
    data class Coin(val id: String, val symbol: String, val displayName: String)

    val ALL: List<Coin> = listOf(
        Coin("ethereum", "ETH", "Ethereum"),
        Coin("solana", "SOL", "Solana"),
        Coin("ripple", "XRP", "XRP"),
        Coin("cardano", "ADA", "Cardano"),
        Coin("dogecoin", "DOGE", "Dogecoin"),
        Coin("litecoin", "LTC", "Litecoin"),
        Coin("polkadot", "DOT", "Polkadot"),
        Coin("binancecoin", "BNB", "BNB"),
        Coin("chainlink", "LINK", "Chainlink"),
        Coin("avalanche-2", "AVAX", "Avalanche"),
        Coin("tron", "TRX", "TRON"),
        Coin("polygon-ecosystem-token", "POL", "Polygon")
    )

    fun byIds(ids: Set<String>): List<Coin> = ALL.filter { it.id in ids }
}
