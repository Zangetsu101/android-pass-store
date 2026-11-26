// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

private const val MAX_DIGITS = 19

/** The shortest card number. The Luhn checksum applies from this length. */
private const val MIN_DIGITS = 13

/** The card number field accepts ASCII digits only, so a pasted number with spaces or dashes keeps its digits. */
internal fun cardNumberDigits(input: String): String = input.filter { it in '0'..'9' }.take(MAX_DIGITS)

/** The groups of digits that the field shows: 4-6-5 for Amex (`34` and `37`), and groups of 4 for other cards. */
internal fun groupCardNumber(digits: String): String {
    val amex = digits.startsWith("34") || digits.startsWith("37")
    val sizes = if (amex) listOf(4, 6, 5) else List(5) { 4 }
    return buildList {
        var start = 0
        for (size in sizes) {
            if (start >= digits.length) break
            add(digits.substring(start, minOf(start + size, digits.length)))
            start += size
        }
        if (start < digits.length) add(digits.substring(start))
    }.joinToString(" ")
}

/** The status line below the card number field. A failed checksum is only a warning, because some real numbers fail it. */
internal fun cardNumberStatus(digits: String): String =
    when {
        digits.isEmpty() -> "type or paste the number"
        digits.length < MIN_DIGITS -> "${digits.length} digits"
        cardChecksumFails(digits) -> "checksum fails · check for a typo"
        else -> "${digits.length} digits · checksum ok"
    }

internal fun cardChecksumFails(digits: String) = digits.length >= MIN_DIGITS && !luhnValid(digits)

/** A line that has only ASCII digits, such as a card number that the app saved. */
internal fun isCardDigits(line: String) = line.isNotEmpty() && line.all { it in '0'..'9' }

private fun luhnValid(digits: String): Boolean =
    digits
        .reversed()
        .mapIndexed { index, char ->
            val digit = char - '0'
            if (index % 2 == 1) (digit * 2).let { if (it > 9) it - 9 else it } else digit
        }.sum() % 10 == 0
