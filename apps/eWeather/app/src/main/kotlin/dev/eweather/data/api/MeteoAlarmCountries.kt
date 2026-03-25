package dev.eweather.data.api

object MeteoAlarmCountries {

    fun getSlug(country: String): String? = COUNTRY_SLUGS[country.lowercase()]

    fun isInEurope(lat: Double, lon: Double): Boolean =
        lat in 35.0..71.0 && lon in -25.0..50.0

    private val COUNTRY_SLUGS = mapOf(
        "austria" to "meteoalarm-legacy-atom-austria",
        "belgium" to "meteoalarm-legacy-atom-belgium",
        "bosnia and herzegovina" to "meteoalarm-legacy-atom-bosnia-herzegovina",
        "bulgaria" to "meteoalarm-legacy-atom-bulgaria",
        "croatia" to "meteoalarm-legacy-atom-croatia",
        "cyprus" to "meteoalarm-legacy-atom-cyprus",
        "czechia" to "meteoalarm-legacy-atom-czechia",
        "czech republic" to "meteoalarm-legacy-atom-czechia",
        "denmark" to "meteoalarm-legacy-atom-denmark",
        "estonia" to "meteoalarm-legacy-atom-estonia",
        "finland" to "meteoalarm-legacy-atom-finland",
        "france" to "meteoalarm-legacy-atom-france",
        "germany" to "meteoalarm-legacy-atom-germany",
        "greece" to "meteoalarm-legacy-atom-greece",
        "hungary" to "meteoalarm-legacy-atom-hungary",
        "iceland" to "meteoalarm-legacy-atom-iceland",
        "ireland" to "meteoalarm-legacy-atom-ireland",
        "israel" to "meteoalarm-legacy-atom-israel",
        "italy" to "meteoalarm-legacy-atom-italy",
        "latvia" to "meteoalarm-legacy-atom-latvia",
        "lithuania" to "meteoalarm-legacy-atom-lithuania",
        "luxembourg" to "meteoalarm-legacy-atom-luxembourg",
        "malta" to "meteoalarm-legacy-atom-malta",
        "moldova" to "meteoalarm-legacy-atom-moldova",
        "montenegro" to "meteoalarm-legacy-atom-montenegro",
        "netherlands" to "meteoalarm-legacy-atom-netherlands",
        "north macedonia" to "meteoalarm-legacy-atom-north-macedonia",
        "norway" to "meteoalarm-legacy-atom-norway",
        "poland" to "meteoalarm-legacy-atom-poland",
        "portugal" to "meteoalarm-legacy-atom-portugal",
        "romania" to "meteoalarm-legacy-atom-romania",
        "serbia" to "meteoalarm-legacy-atom-serbia",
        "slovakia" to "meteoalarm-legacy-atom-slovakia",
        "slovenia" to "meteoalarm-legacy-atom-slovenia",
        "spain" to "meteoalarm-legacy-atom-spain",
        "sweden" to "meteoalarm-legacy-atom-sweden",
        "switzerland" to "meteoalarm-legacy-atom-switzerland",
        "turkey" to "meteoalarm-legacy-atom-turkey",
        "united kingdom" to "meteoalarm-legacy-atom-united-kingdom",
        "uk" to "meteoalarm-legacy-atom-united-kingdom",
    )
}
