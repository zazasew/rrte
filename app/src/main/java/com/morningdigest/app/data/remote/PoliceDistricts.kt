package com.morningdigest.app.data.remote

/**
 * The offline, always-available reference of Norway's 12 police districts
 * and the municipalities/cities each one covers. This is the ONLY source
 * for the Settings "police district" + "municipality" picker - it is not
 * merged with, or dependent on, any live network call, on purpose.
 *
 * Why: every earlier version of this feature that tried to fetch the
 * district/municipality list live (from Politiloggen's own geo endpoint)
 * turned into a single point of failure - if that one call failed for any
 * reason (network hiccup, a wrong/changed endpoint guess, filtering on a
 * specific device/network), the entire picker broke, even though this data
 * barely ever changes in the first place. Norway's 12 police districts and
 * ~356 municipalities are near-static geography (compiled from
 * Kartverket/SSB's public reference data) - there is no good reason for
 * choosing "which district and city do I want reports for" to depend on
 * reaching a government API at that exact moment.
 *
 * Municipality mergers/splits do occasionally happen, so this may need the
 * odd manual update over time, but that's a small, predictable maintenance
 * cost in exchange for a picker that always works.
 */
object PoliceDistricts {

    data class DistrictEntry(val displayName: String, val municipalities: List<String>)

    /**
     * Keyed by a normalized district name (lowercase, "politidistrikt"
     * suffix stripped) so it can be matched against whatever exact string
     * the live `/districts` endpoint returns.
     */
    val DISTRICTS: List<DistrictEntry> = listOf(
        DistrictEntry(
            "Oslo",
            listOf("Oslo", "Bærum", "Asker")
        ),
        DistrictEntry(
            "Øst",
            listOf(
                "Fredrikstad", "Sarpsborg", "Moss", "Halden", "Indre Østfold", "Hvaler", "Råde", "Våler",
                "Skiptvet", "Rakkestad", "Marker", "Aremark",
                "Lillestrøm", "Ullensaker", "Nordre Follo", "Ås", "Frogn", "Vestby", "Nesodden",
                "Enebakk", "Lørenskog", "Rælingen", "Aurskog-Høland", "Nes", "Gjerdrum", "Nittedal",
                "Nannestad", "Eidsvoll", "Hurdal", "Lunner"
            )
        ),
        DistrictEntry(
            "Innlandet",
            listOf(
                "Hamar", "Lillehammer", "Gjøvik", "Kongsvinger", "Ringsaker", "Løten", "Stange",
                "Nord-Odal", "Sør-Odal", "Eidskog", "Grue", "Åsnes", "Våler (Innlandet)", "Elverum",
                "Trysil", "Åmot", "Stor-Elvdal", "Rendalen", "Engerdal", "Tolga", "Tynset", "Alvdal",
                "Folldal", "Os", "Dovre", "Lesja", "Skjåk", "Lom", "Vågå", "Nord-Fron", "Sel",
                "Sør-Fron", "Ringebu", "Øyer", "Gausdal", "Østre Toten", "Vestre Toten", "Gran",
                "Søndre Land", "Nordre Land", "Sør-Aurdal", "Etnedal", "Nord-Aurdal", "Vestre Slidre",
                "Øystre Slidre", "Vang"
            )
        ),
        DistrictEntry(
            "Sør-Øst",
            listOf(
                "Drammen", "Kongsberg", "Ringerike", "Hønefoss", "Hole", "Lier", "Øvre Eiker",
                "Modum", "Krødsherad", "Flå", "Nesbyen", "Gol", "Hemsedal", "Ål", "Hol", "Sigdal",
                "Flesberg", "Rollag", "Nore og Uvdal", "Jevnaker",
                "Horten", "Holmestrand", "Tønsberg", "Sandefjord", "Larvik", "Færder",
                "Porsgrunn", "Skien", "Notodden", "Siljan", "Bamble", "Kragerø", "Drangedal", "Nome",
                "Midt-Telemark", "Seljord", "Hjartdal", "Tinn", "Kviteseid", "Nissedal", "Fyresdal",
                "Tokke", "Vinje"
            )
        ),
        DistrictEntry(
            "Agder",
            listOf(
                "Kristiansand", "Arendal", "Grimstad", "Lindesnes", "Mandal", "Farsund", "Flekkefjord",
                "Risør", "Gjerstad", "Vegårshei", "Tvedestrand", "Froland", "Lillesand", "Birkenes",
                "Åmli", "Iveland", "Evje og Hornnes", "Bygland", "Valle", "Bykle", "Vennesla",
                "Åseral", "Lyngdal", "Hægebostad", "Kvinesdal"
            )
        ),
        DistrictEntry(
            "Sør-Vest",
            listOf(
                "Stavanger", "Sandnes", "Haugesund", "Eigersund", "Sokndal", "Lund", "Bjerkreim",
                "Hå", "Klepp", "Time", "Bryne", "Gjesdal", "Sola", "Randaberg", "Strand", "Hjelmeland",
                "Suldal", "Sauda", "Kvitsøy", "Bokn", "Tysvær", "Karmøy", "Utsira", "Vindafjord",
                "Sirdal", "Bømlo", "Stord", "Fitjar"
            )
        ),
        DistrictEntry(
            "Vest",
            listOf(
                "Bergen", "Askøy", "Øygarden", "Alver", "Osterøy", "Vaksdal", "Modalen", "Austrheim",
                "Fedje", "Masfjorden", "Gulen", "Solund", "Hyllestad", "Høyanger", "Vik", "Sogndal",
                "Aurland", "Lærdal", "Årdal", "Luster", "Askvoll", "Fjaler", "Sunnfjord", "Bremanger",
                "Stad", "Gloppen", "Stryn", "Kinn", "Etne", "Sveio", "Kvinnherad", "Ullensvang",
                "Eidfjord", "Ulvik", "Voss", "Kvam", "Samnanger", "Bjørnafjorden", "Austevoll", "Tysnes"
            )
        ),
        DistrictEntry(
            "Møre og Romsdal",
            listOf(
                "Ålesund", "Molde", "Kristiansund", "Vanylven", "Sande", "Herøy", "Ulstein", "Hareid",
                "Ørsta", "Volda", "Stranda", "Sykkylven", "Sula", "Giske", "Vestnes", "Rauma",
                "Aukra", "Averøy", "Gjemnes", "Tingvoll", "Sunndal", "Surnadal", "Smøla", "Aure",
                "Fjord", "Hustadvika", "Haram"
            )
        ),
        DistrictEntry(
            "Trøndelag",
            listOf(
                "Trondheim", "Steinkjer", "Namsos", "Stjørdal", "Orkland", "Orkanger", "Levanger",
                "Verdal", "Malvik", "Melhus", "Skaun", "Midtre Gauldal", "Selbu", "Tydal", "Klæbu",
                "Frosta", "Meråker", "Snåsa", "Lierne", "Røyrvik", "Namsskogan", "Grong", "Høylandet",
                "Overhalla", "Flatanger", "Leka", "Inderøy", "Indre Fosen", "Heim", "Hitra", "Frøya",
                "Ørland", "Åfjord", "Osen", "Rennebu", "Rindal", "Røros", "Holtålen", "Nærøysund",
                "Bindal"
            )
        ),
        DistrictEntry(
            "Nordland",
            listOf(
                "Bodø", "Narvik", "Sømna", "Brønnøy", "Brønnøysund", "Vega", "Vevelstad", "Herøy (Nordland)",
                "Alstahaug", "Leirfjord", "Vefsn", "Mosjøen", "Grane", "Hattfjelldal", "Dønna", "Nesna",
                "Hemnes", "Rana", "Mo i Rana", "Lurøy", "Træna", "Rødøy", "Meløy", "Gildeskål", "Beiarn",
                "Saltdal", "Fauske", "Sørfold", "Steigen", "Hamarøy", "Evenes", "Røst", "Værøy",
                "Flakstad", "Vestvågøy", "Vågan", "Svolvær", "Hadsel", "Bø (Vesterålen)", "Øksnes",
                "Sortland", "Andøy", "Moskenes", "Gratangen"
            )
        ),
        DistrictEntry(
            "Troms",
            listOf(
                "Tromsø", "Harstad", "Kvæfjord", "Tjeldsund", "Ibestad", "Lavangen", "Bardu",
                "Salangen", "Målselv", "Sørreisa", "Dyrøy", "Senja", "Finnsnes", "Balsfjord", "Karlsøy",
                "Lyngen", "Storfjord", "Kåfjord", "Skjervøy", "Nordreisa", "Storslett", "Kvænangen", "Skånland"
            )
        ),
        DistrictEntry(
            "Finnmark",
            listOf(
                "Alta", "Hammerfest", "Sør-Varanger", "Kirkenes", "Vadsø", "Vardø", "Karasjok",
                "Kautokeino", "Loppa", "Hasvik", "Måsøy", "Nordkapp", "Porsanger", "Lebesby", "Gamvik",
                "Tana", "Berlevåg", "Båtsfjord", "Nesseby"
            )
        )
    )

    /** Normalizes a district name for matching: lowercase, trimmed, "politidistrikt" suffix stripped. */
    fun normalize(name: String): String =
        name.trim().lowercase().removeSuffix("politidistrikt").trim()

    /** All municipality names across every district, for a flat fallback list. */
    val ALL_MUNICIPALITIES: List<String> by lazy {
        DISTRICTS.flatMap { it.municipalities }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun municipalitiesFor(districtDisplayName: String): List<String> {
        val target = normalize(districtDisplayName)
        return DISTRICTS.firstOrNull { normalize(it.displayName) == target }?.municipalities
            ?: emptyList()
    }

    /** Which of our 12 static districts a municipality belongs to, if known. */
    fun districtFor(municipality: String): String? {
        val target = municipality.trim().lowercase()
        return DISTRICTS.firstOrNull { entry -> entry.municipalities.any { it.lowercase() == target } }?.displayName
    }

    /** A district as shown in the Settings picker. [id] is just the display name - stable and unique across our 12 static entries, no server round-trip needed to get it. */
    data class DistrictItem(val id: String, val name: String)

    /** The 12 districts for the first-level Settings dropdown, sorted alphabetically. */
    val districts: List<DistrictItem> by lazy {
        DISTRICTS.map { DistrictItem(it.displayName, it.displayName) }.sortedBy { it.name.lowercase() }
    }

    /** Municipalities/cities for the second-level Settings dropdown, sorted alphabetically. */
    fun municipalitiesFor(district: DistrictItem): List<String> =
        municipalitiesFor(district.name).sortedWith(String.CASE_INSENSITIVE_ORDER)
}
