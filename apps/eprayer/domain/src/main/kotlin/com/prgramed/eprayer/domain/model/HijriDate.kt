package com.prgramed.eprayer.domain.model

data class HijriDate(
    val day: Int,
    val month: Int,
    val year: Int,
) {
    val monthName: String
        get() = MONTH_NAMES.getOrElse(month - 1) { "" }

    val formatted: String
        get() = "$day $monthName $year AH"

    companion object {
        private val MONTH_NAMES = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Ula", "Jumada al-Thani", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhul Qi'dah", "Dhul Hijjah",
        )

        /**
         * Convert Gregorian date to approximate Hijri date.
         * Uses the Kuwaiti algorithm — accurate to ±1 day for most dates.
         */
        fun fromGregorian(year: Int, month: Int, day: Int): HijriDate {
            val jd = gregorianToJulianDay(year, month, day)
            return julianDayToHijri(jd)
        }

        private fun gregorianToJulianDay(y: Int, m: Int, d: Int): Int {
            val a = (14 - m) / 12
            val yy = y + 4800 - a
            val mm = m + 12 * a - 3
            return d + (153 * mm + 2) / 5 + 365 * yy + yy / 4 - yy / 100 + yy / 400 - 32045
        }

        private fun julianDayToHijri(jd: Int): HijriDate {
            val l = jd - 1948440 + 10632
            val n = (l - 1) / 10631
            val ll = l - 10631 * n + 354
            val j = ((10985 - ll) / 5316) * ((50 * ll) / 17719) +
                (ll / 5670) * ((43 * ll) / 15238)
            val lll = ll - ((30 - j) / 15) * ((17719 * j) / 50) -
                (j / 16) * ((15238 * j) / 43) + 29
            val month = (24 * lll) / 709
            val day = lll - (709 * month) / 24
            val year = 30 * n + j - 30
            return HijriDate(day, month, year)
        }
    }
}
