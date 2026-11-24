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

internal enum class CharacterSet(
    val label: String,
    /** All the characters of the set. The user can choose a subset of the symbols. */
    val chars: String,
) {
    Upper("A–Z", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    Lower("a–z", "abcdefghijklmnopqrstuvwxyz"),
    Digits("0–9", "0123456789"),
    Symbols("!@#…", ALL_SYMBOLS),
}

/** The settings for one generated password. [symbols] applies only when [sets] has [CharacterSet.Symbols]. */
internal data class GeneratorSettings(
    val length: Int = DEFAULT_LENGTH,
    val sets: Set<CharacterSet> = CharacterSet.entries.toSet(),
    val symbols: String = ALL_SYMBOLS,
) {
    init {
        require(length in MIN_LENGTH..MAX_LENGTH) { "length out of range" }
        require(sets.isNotEmpty()) { "no character set is on" }
        require(symbols.isNotEmpty()) { "no allowed symbols" }
    }

    val symbolsOn get() = CharacterSet.Symbols in sets
    val customSymbols get() = symbolsOn && symbols.length != ALL_SYMBOLS.length
    private val customCharset get() = !sets.containsAll(alphanumeric) || customSymbols
    private val setsInOrder get() = CharacterSet.entries.filter { it in sets }

    val pool get() = setsInOrder.joinToString("") { charsOf(it) }

    val isDefault get() = length == DEFAULT_LENGTH && symbolsOn && !customCharset

    /** The closed customize row, for example `25 · A–z 0–9 !@#`. */
    val summary: String
        get() {
            val symbolText = if (customSymbols) "${symbols.length} symbols" else "!@#"
            val parts = listOfNotNull(letters, digits, symbolText.takeIf { symbolsOn })
            return "$length · ${parts.joinToString(" ")}"
        }

    val note: String
        get() =
            when {
                customCharset -> "custom set · app-side charset"
                !symbolsOn -> "symbols off = -n"
                else -> "* pass default"
            }

    /** The character set that the `pass` flags cannot express, or null when the flags express it. */
    val charset: String?
        get() {
            if (!customCharset) return null
            val alphanumeric = listOfNotNull(letters, digits).joinToString(" ")
            return when {
                !symbolsOn -> alphanumeric
                customSymbols -> "$alphanumeric  $symbols"
                else -> "$alphanumeric  all symbols"
            }
        }

    private val letters
        get() =
            when {
                CharacterSet.Upper in sets && CharacterSet.Lower in sets -> "A–z"
                CharacterSet.Upper in sets -> CharacterSet.Upper.label
                CharacterSet.Lower in sets -> CharacterSet.Lower.label
                else -> null
            }

    private val digits get() = CharacterSet.Digits.label.takeIf { CharacterSet.Digits in sets }

    private fun charsOf(set: CharacterSet) = if (set == CharacterSet.Symbols) symbols else set.chars

    fun withLength(length: Int) = copy(length = length.coerceIn(MIN_LENGTH, MAX_LENGTH))

    /** Turns [set] on or off. A change that turns off the last set that is on does nothing. */
    fun toggle(set: CharacterSet): GeneratorSettings {
        val next = if (set in sets) sets - set else sets + set
        return if (next.isEmpty()) this else copy(sets = next)
    }

    /**
     * Keeps the real symbols in [text], once each. No symbols turns symbols off and keeps the
     * last list for the next time that symbols turn on.
     */
    fun withSymbols(text: String): GeneratorSettings {
        val kept = text.filter { it in ALL_SYMBOLS }.toSet().joinToString("")
        return if (kept.isEmpty()) {
            if (symbolsOn) toggle(CharacterSet.Symbols) else this
        } else {
            copy(sets = sets + CharacterSet.Symbols, symbols = kept)
        }
    }

    /**
     * Makes a password from [random]. Each set that is on appears at least once: the generator
     * makes passwords again until this is true, so that no position is fixed to one set.
     */
    fun generate(random: Random): String {
        val pool = pool
        val required = setsInOrder.map { charsOf(it) }
        while (true) {
            val password = String(CharArray(length) { pool[random.unbiasedIndex(pool.length)] })
            if (required.all { set -> password.any { it in set } }) return password
        }
    }

    fun strength() = Strength.of(length, pool.length)

    private companion object {
        val alphanumeric = listOf(CharacterSet.Upper, CharacterSet.Lower, CharacterSet.Digits)
    }
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
    val poolSize = CharacterSet.entries.filter { set -> password.any { it in set.chars } }.sumOf { it.chars.length }
    return Strength.of(password.length, poolSize)
}
