package com.morningdigest.app.data.prefs

/** Currencies offered in the Settings currency-pair dropdowns (Frankfurter supports many more, but this covers the common ones). */
object CurrencyCatalog {
    data class Entry(val code: String, val label: String)

    val ALL: List<Entry> = listOf(
        Entry("EUR", "EUR — Euro"),
        Entry("USD", "USD — US Dollar"),
        Entry("GBP", "GBP — British Pound"),
        Entry("NOK", "NOK — Norwegian Krone"),
        Entry("SEK", "SEK — Swedish Krona"),
        Entry("DKK", "DKK — Danish Krone"),
        Entry("CHF", "CHF — Swiss Franc"),
        Entry("JPY", "JPY — Japanese Yen"),
        Entry("CAD", "CAD — Canadian Dollar"),
        Entry("AUD", "AUD — Australian Dollar"),
        Entry("PLN", "PLN — Polish Zloty"),
        Entry("INR", "INR — Indian Rupee")
    )
}

/** Country codes offered in the Settings location dropdown (OpenWeather-style ISO 3166 codes). */
object CountryCatalog {
    data class Entry(val code: String, val label: String)

    val ALL: List<Entry> = listOf(
        Entry("NO", "Norway"),
        Entry("SE", "Sweden"),
        Entry("DK", "Denmark"),
        Entry("GB", "United Kingdom"),
        Entry("US", "United States"),
        Entry("DE", "Germany"),
        Entry("FR", "France"),
        Entry("ES", "Spain"),
        Entry("IT", "Italy"),
        Entry("NL", "Netherlands"),
        Entry("PL", "Poland"),
        Entry("FI", "Finland")
    )
}
