// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

/** A card expiry with a two-digit month and a four-digit year. */
data class CardExpiry(
    val month: String,
    val year: String,
) {
    /** The year with 2 or 4 digits. When [digits] is null, the year has 4 digits. */
    fun yearText(digits: Int?): String = if (digits == 2) year.takeLast(2) else year

    fun dateText(digits: Int?): String = "$month/${yearText(digits)}"
}

private val EXPIRY = Regex("""(\d{1,2})\s*[/-]\s*(\d{2}|\d{4})""")

/** Reads the `expiry` note of a Card Entry, for example `03/27` or `3/2027`. */
fun parseCardExpiry(raw: String): CardExpiry? {
    val match = EXPIRY.matchEntire(raw.trim()) ?: return null
    val (month, year) = match.destructured
    if (month.toInt() !in 1..12) return null
    return CardExpiry(month.padStart(2, '0'), if (year.length == 2) "20$year" else year)
}
