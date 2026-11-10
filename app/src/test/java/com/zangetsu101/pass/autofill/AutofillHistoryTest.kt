// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.zangetsu101.pass.passstore.PassEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AutofillHistoryTest {
    private lateinit var testScope: TestScope
    private lateinit var tempFile: File
    private lateinit var history: AutofillHistory

    private val visa = card("cards/visa/daily")
    private val amex = card("cards/amex/travel")
    private val master = card("cards/master/backup")
    private val cards = listOf(visa, amex, master)

    @BeforeEach
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        tempFile = File.createTempFile("autofill_history_test", ".preferences_pb")
        history = AutofillHistory(PreferenceDataStoreFactory.create(scope = testScope, produceFile = { tempFile }))
    }

    @AfterEach
    fun teardown() {
        testScope.cancel()
        tempFile.delete()
    }

    @Test
    fun `the entry that was filled last on a target comes first on that target`() =
        testScope.runTest {
            history.record("payments.google.com", master)

            assertEquals(listOf(master, visa, amex), history.rank("payments.google.com", cards))
        }

    @Test
    fun `the card that was filled last on another target comes first on a new target`() =
        testScope.runTest {
            history.record("payments.google.com", amex)

            assertEquals(listOf(amex, visa, master), history.rank("shop.example.com", cards))
        }

    @Test
    fun `the last card of the target comes before the last card of another target`() =
        testScope.runTest {
            history.record("payments.google.com", master)
            history.record("shop.example.com", amex)

            assertEquals(listOf(master, amex, visa), history.rank("payments.google.com", cards))
        }

    @Test
    fun `a login that was filled on another target does not change the order`() =
        testScope.runTest {
            val alice = entry("github.com/alice")
            val bob = entry("github.com/bob")
            history.record("gist.github.com", bob)

            assertEquals(listOf(alice, bob), history.rank("github.com", listOf(alice, bob)))
        }

    @Test
    fun `a login and a card on the same target are remembered separately`() =
        testScope.runTest {
            val alice = entry("amazon.com/alice")
            val bob = entry("amazon.com/bob")
            history.record("amazon.com", bob)
            history.record("amazon.com", master)
            history.record("shop.example.com", amex)

            assertEquals(listOf(bob, alice), history.rank("amazon.com", listOf(alice, bob)))
            assertEquals(listOf(master, amex, visa), history.rank("amazon.com", cards))
        }

    @Test
    fun `a remembered entry that is not a candidate does not change the order`() =
        testScope.runTest {
            history.record("payments.google.com", card("cards/old/removed"))

            assertEquals(cards, history.rank("payments.google.com", cards))
        }

    @Test
    fun `clear removes all remembered entries`() =
        testScope.runTest {
            history.record("payments.google.com", master)

            history.clear()

            assertEquals(cards, history.rank("payments.google.com", cards))
            assertEquals(cards, history.rank("shop.example.com", cards))
        }

    private fun card(path: String) = entry(path, isCard = true)

    private fun entry(
        path: String,
        isCard: Boolean = false,
    ): PassEntry {
        val segments = path.split("/")
        return PassEntry(
            path = path,
            domain = segments[segments.size - 2],
            username = segments.last(),
            encryptedFile = File("$path.gpg"),
            isCard = isCard,
        )
    }
}
