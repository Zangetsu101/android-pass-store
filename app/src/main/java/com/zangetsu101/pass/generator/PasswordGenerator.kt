// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.generator

import java.util.Random
import kotlin.math.log2
import kotlin.math.roundToInt

internal const val MIN_LENGTH = 8
internal const val MAX_LENGTH = 128

/** The length that `pass generate` uses when PASSWORD_STORE_GENERATED_LENGTH is not set. */
internal const val DEFAULT_LENGTH = 25

/** The ASCII punctuation in `[:punct:]`, which `pass generate` uses for symbols. */
internal const val ALL_SYMBOLS = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
internal const val SAFE_SYMBOLS = "!@#\$%^&*-_"

private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
private const val DIGITS = "0123456789"

internal enum class CharacterSet { Upper, Lower, Digits, Symbols }

/** The settings for one generated password. An empty [symbols] means that symbols are off. */
internal data class GeneratorSettings(
    val length: Int = DEFAULT_LENGTH,
    val upper: Boolean = true,
    val lower: Boolean = true,
    val digits: Boolean = true,
    val symbols: String = ALL_SYMBOLS,
) {
    init {
        require(length in MIN_LENGTH..MAX_LENGTH) { "length out of range" }
        require(upper || lower || digits || symbols.isNotEmpty()) { "no character set is on" }
    }

    val customSymbols get() = symbols.isNotEmpty() && symbols.length != ALL_SYMBOLS.length
    private val customCharset get() = !(upper && lower && digits) || customSymbols

    val pool: String
        get() =
            buildString {
                if (upper) append(UPPER)
                if (lower) append(LOWER)
                if (digits) append(DIGITS)
                append(symbols)
            }

    val isDefault get() = length == DEFAULT_LENGTH && symbols.isNotEmpty() && !customCharset

    /** The closed customize row, for example `25 · A–z 0–9 !@#`. */
    val summary: String
        get() {
            val symbolText = if (customSymbols) "${symbols.length} symbols" else "!@#"
            val sets = listOfNotNull(letters, "0–9".takeIf { digits }, symbolText.takeIf { symbols.isNotEmpty() })
            return "$length · ${sets.joinToString(" ")}"
        }

    val note: String
        get() =
            when {
                customCharset -> "custom set · app-side charset"
                symbols.isEmpty() -> "symbols off = -n"
                else -> "* pass default"
            }

    /** The character set that the `pass` flags cannot express, or null when the flags express it. */
    val charset: String?
        get() {
            if (!customCharset) return null
            val sets = listOfNotNull(letters, "0–9".takeIf { digits }).joinToString(" ")
            return when {
                symbols.isEmpty() -> sets
                customSymbols -> "$sets  $symbols"
                else -> "$sets  all symbols"
            }
        }

    private val letters
        get() =
            when {
                upper && lower -> "A–z"
                upper -> "A–Z"
                lower -> "a–z"
                else -> null
            }

    fun withLength(length: Int) = copy(length = length.coerceIn(MIN_LENGTH, MAX_LENGTH))

    /** Turns [set] on or off. A change that turns off the last set that is on does nothing. */
    fun toggle(set: CharacterSet): GeneratorSettings {
        val upper = upper != (set == CharacterSet.Upper)
        val lower = lower != (set == CharacterSet.Lower)
        val digits = digits != (set == CharacterSet.Digits)
        val symbols =
            if (set != CharacterSet.Symbols) {
                symbols
            } else if (symbols.isEmpty()) {
                ALL_SYMBOLS
            } else {
                ""
            }
        return if (upper || lower || digits || symbols.isNotEmpty()) GeneratorSettings(length, upper, lower, digits, symbols) else this
    }

    /** Keeps the real symbols in [text], once each. No symbols turns symbols off. */
    fun withSymbols(text: String): GeneratorSettings {
        val kept = text.filter { it in ALL_SYMBOLS }.toSet().joinToString("")
        return if (kept.isEmpty() && !(upper || lower || digits)) this else copy(symbols = kept)
    }

    fun isOn(set: CharacterSet) =
        when (set) {
            CharacterSet.Upper -> upper
            CharacterSet.Lower -> lower
            CharacterSet.Digits -> digits
            CharacterSet.Symbols -> symbols.isNotEmpty()
        }

    /**
     * Makes a password from [random]. Each set that is on appears at least once: the generator
     * makes passwords again until this is true, so that no position is fixed to one set.
     */
    fun generate(random: Random): String {
        val pool = pool
        val required = listOfNotNull(UPPER.takeIf { upper }, LOWER.takeIf { lower }, DIGITS.takeIf { digits }, symbols.ifEmpty { null })
        while (true) {
            val password = String(CharArray(length) { pool[random.unbiasedIndex(pool.length)] })
            if (required.all { set -> password.any { it in set } }) return password
        }
    }

    fun strength() = Strength.of(length, pool.length)
}

/** Picks an index below [bound] with rejection sampling, so that no index is more likely than another. */
private fun Random.unbiasedIndex(bound: Int): Int {
    val range = 1L shl 32
    val limit = range - range % bound
    while (true) {
        val value = nextInt().toLong() and 0xFFFFFFFFL
        if (value < limit) return (value % bound).toInt()
    }
}

internal enum class StrengthLevel(
    val label: String,
) {
    Weak("weak"),
    Fair("fair"),
    Strong("strong"),
    Excellent("excellent"),
    ;

    val segments get() = ordinal + 1

    companion object {
        fun of(bits: Int) =
            when {
                bits >= 128 -> Excellent
                bits >= 90 -> Strong
                bits >= 60 -> Fair
                else -> Weak
            }
    }
}

internal data class Strength(
    val bits: Int,
    val level: StrengthLevel,
) {
    val label get() = "${level.label} · $bits bits"

    companion object {
        fun of(
            length: Int,
            poolSize: Int,
        ): Strength {
            val bits = (length * log2(maxOf(2, poolSize).toDouble())).roundToInt()
            return Strength(bits, StrengthLevel.of(bits))
        }
    }
}

/** The strength of a password that the user typed. The pool is the sets whose characters the password contains. */
internal fun typedPasswordStrength(password: String): Strength {
    if (password.isEmpty()) return Strength(0, StrengthLevel.Weak)
    val poolSize = listOf(UPPER, LOWER, DIGITS, ALL_SYMBOLS).filter { set -> password.any { it in set } }.sumOf { it.length }
    return Strength.of(password.length, poolSize)
}
