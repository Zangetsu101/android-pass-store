// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.generator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class PasswordGeneratorTest {
    private val letters = ('A'..'Z').joinToString("")

    @Test
    fun `each length from 8 to 128 gives a password of that length`() {
        val random = Random(1)
        for (length in MIN_LENGTH..MAX_LENGTH) {
            assertEquals(length, GeneratorSettings(length = length).generate(random).length)
        }
    }

    @Test
    fun `the length stays between 8 and 128`() {
        assertEquals(8, GeneratorSettings().withLength(3).length)
        assertEquals(128, GeneratorSettings().withLength(500).length)
    }

    @Test
    fun `each set that is on appears and a set that is off never appears`() {
        val random = Random(2)
        val choices =
            listOf(
                GeneratorSettings(),
                GeneratorSettings(length = 8),
                GeneratorSettings(length = 8, symbols = "-"),
                GeneratorSettings(upper = false, symbols = ""),
                GeneratorSettings(lower = false, digits = false),
                GeneratorSettings(upper = false, lower = false, symbols = ""),
            )
        for (settings in choices) {
            repeat(200) {
                val password = settings.generate(random)
                assertEquals(settings.upper, password.any { it in 'A'..'Z' }, password)
                assertEquals(settings.lower, password.any { it in 'a'..'z' }, password)
                assertEquals(settings.digits, password.any { it in '0'..'9' }, password)
                assertEquals(settings.symbols.isNotEmpty(), password.any { !it.isLetterOrDigit() }, password)
            }
        }
    }

    @Test
    fun `only the allowed symbols appear`() {
        val random = Random(3)
        val settings = GeneratorSettings(symbols = "-_.")
        repeat(500) {
            val symbols = settings.generate(random).filterNot { it.isLetterOrDigit() }
            assertTrue(symbols.all { it in "-_." }, symbols)
        }
    }

    @Test
    fun `a fixed random source gives a fixed password`() {
        assertEquals("zj5-EpD9[kZuZgQK,2lhRD,5A", GeneratorSettings().generate(Random(42)))
        assertEquals("NVHuE5HDuGHAZ22m", GeneratorSettings(length = 16, symbols = "").generate(Random(42)))
    }

    @Test
    fun `a random value in the biased top range is rejected`() {
        val pool = GeneratorSettings(lower = false, digits = false, symbols = "").pool
        assertEquals(letters, pool)
        val range = 1L shl 32
        val biased = (range - range % pool.length).toInt()
        val random = ScriptedRandom(listOf(biased, -1, 0, 1, 2, 3, 4, 5, 6, 7))

        assertEquals("ABCDEFGH", GeneratorSettings(length = 8, lower = false, digits = false, symbols = "").generate(random))
    }

    @Test
    fun `the characters are close to uniform over many passwords`() {
        val random = Random(4)
        val settings = GeneratorSettings(length = 64)
        val counts = IntArray(128)
        repeat(2_000) { settings.generate(random).forEach { counts[it.code]++ } }
        val expected = 2_000.0 * 64 / settings.pool.length
        val chiSquared = settings.pool.sumOf { (counts[it.code] - expected).let { diff -> diff * diff / expected } }

        // The 99.9th percentile of chi-squared with 93 degrees of freedom is about 135.
        assertTrue(chiSquared < 135, "chi-squared $chiSquared")
    }

    @Test
    fun `the symbol field keeps real symbols once each`() {
        assertEquals("!-_", GeneratorSettings().withSymbols("!a-1 _-!é").symbols)
        assertEquals(32, ALL_SYMBOLS.length)
        assertEquals(ALL_SYMBOLS.toSet().size, ALL_SYMBOLS.length)
        assertTrue(ALL_SYMBOLS.all { it.code in 33..126 && !it.isLetterOrDigit() })
    }

    @Test
    fun `an empty symbol field turns symbols off unless no other set stays on`() {
        assertEquals("", GeneratorSettings().withSymbols("abc").symbols)
        val symbolsOnly = GeneratorSettings(upper = false, lower = false, digits = false, symbols = "-")
        assertEquals(symbolsOnly, symbolsOnly.withSymbols(""))
    }

    @Test
    fun `at least one set stays on`() {
        val digitsOnly = GeneratorSettings(upper = false, lower = false, symbols = "")
        assertEquals(digitsOnly, digitsOnly.toggle(CharacterSet.Digits))
        val noUpper = GeneratorSettings().toggle(CharacterSet.Upper)
        assertFalse(noUpper.upper)
        assertTrue(noUpper.toggle(CharacterSet.Upper).upper)
        val noSymbols = GeneratorSettings(symbols = "-").toggle(CharacterSet.Symbols)
        assertEquals("", noSymbols.symbols)
        assertEquals(ALL_SYMBOLS, noSymbols.toggle(CharacterSet.Symbols).symbols)
    }

    @Test
    fun `the summary and the note describe the settings`() {
        val default = GeneratorSettings()
        assertTrue(default.isDefault)
        assertEquals("25 · A–z 0–9 !@#", default.summary)
        assertEquals("* pass default", default.note)
        assertEquals(null, default.charset)

        val reordered = default.withSymbols(ALL_SYMBOLS.reversed())
        assertTrue(reordered.isDefault)

        val noSymbols = default.toggle(CharacterSet.Symbols).withLength(32)
        assertFalse(noSymbols.isDefault)
        assertEquals("32 · A–z 0–9", noSymbols.summary)
        assertEquals("symbols off = -n", noSymbols.note)
        assertEquals(null, noSymbols.charset)

        val safe = default.withSymbols(SAFE_SYMBOLS).toggle(CharacterSet.Lower)
        assertEquals("25 · A–Z 0–9 10 symbols", safe.summary)
        assertEquals("custom set · app-side charset", safe.note)
        assertEquals("A–Z 0–9  !@#\$%^&*-_", safe.charset)

        assertEquals(
            "25 · a–z 3 symbols",
            default
                .toggle(CharacterSet.Upper)
                .toggle(CharacterSet.Digits)
                .withSymbols("-_.")
                .summary,
        )
        assertEquals("25 · 0–9", GeneratorSettings(upper = false, lower = false, symbols = "").summary)
        assertEquals(
            "25 · A–z",
            default
                .withLength(25)
                .toggle(CharacterSet.Digits)
                .toggle(CharacterSet.Symbols)
                .summary,
        )
        assertEquals("custom set · app-side charset", GeneratorSettings(digits = false).note)
        assertEquals("A–z  all symbols", GeneratorSettings(digits = false).charset)
        assertEquals("a–z 0–9", GeneratorSettings(upper = false, symbols = "").charset)
    }

    @Test
    fun `the strength of a generated password uses the size of the pool`() {
        // 25 × log2(94) = 163.9
        assertEquals(Strength(164, StrengthLevel.Excellent), GeneratorSettings().strength())
        // 16 × log2(62) = 95.3
        assertEquals(Strength(95, StrengthLevel.Strong), GeneratorSettings(length = 16, symbols = "").strength())
        // 8 × log2(26) = 37.6
        assertEquals(
            Strength(38, StrengthLevel.Weak),
            GeneratorSettings(length = 8, lower = false, digits = false, symbols = "").strength(),
        )
        assertEquals("excellent · 164 bits", GeneratorSettings().strength().label)
        assertEquals(4, GeneratorSettings().strength().level.segments)
    }

    @Test
    fun `the strength of a typed password uses the sets that it contains`() {
        assertEquals(Strength(0, StrengthLevel.Weak), typedPasswordStrength(""))
        // 13 × log2(26) = 61.1
        assertEquals(Strength(61, StrengthLevel.Fair), typedPasswordStrength("abcdefghijklm"))
        // 10 × log2(10) = 33.2
        assertEquals(Strength(33, StrengthLevel.Weak), typedPasswordStrength("0123456789"))
        // 16 × log2(26 + 10 + 32) = 97.4
        assertEquals(Strength(97, StrengthLevel.Strong), typedPasswordStrength("abcdefgh1234!@#$"))
        // A character outside the four sets adds nothing to the pool, and the pool has at least 2 characters.
        assertEquals(Strength(4, StrengthLevel.Weak), typedPasswordStrength("éééé"))
        // 20 × log2(94) = 131.1
        assertEquals(Strength(131, StrengthLevel.Excellent), typedPasswordStrength("Aa1!Aa1!Aa1!Aa1!Aa1!"))
    }

    @Test
    fun `the strength levels start at 60, 90 and 128 bits`() {
        assertEquals(StrengthLevel.Weak, StrengthLevel.of(59))
        assertEquals(StrengthLevel.Fair, StrengthLevel.of(60))
        assertEquals(StrengthLevel.Fair, StrengthLevel.of(89))
        assertEquals(StrengthLevel.Strong, StrengthLevel.of(90))
        assertEquals(StrengthLevel.Strong, StrengthLevel.of(127))
        assertEquals(StrengthLevel.Excellent, StrengthLevel.of(128))
    }

    private class ScriptedRandom(
        values: List<Int>,
    ) : Random() {
        private val values = values.iterator()

        override fun next(bits: Int): Int = values.next() ushr (32 - bits)
    }
}
