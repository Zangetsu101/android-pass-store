// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.zangetsu101.pass.passstore.PassEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    fun `a remembered login is suggested even when the target has no matches`() =
        testScope.runTest {
            val login = entry("example.net/alice")
            history.record("web:com.example", login)

            assertEquals(listOf(login), history.suggestLogins("web:com.example", "com.example", emptyList(), listOf(login)))
            assertEquals(emptyList<PassEntry>(), history.suggestLogins("app:com.example", "com.example", emptyList(), listOf(login)))
        }

    @Test
    fun `older history can reorder matches for a target but cannot add suggestions`() =
        testScope.runTest {
            val alice = entry("example.com/alice")
            val bob = entry("example.com/bob")
            val entries = listOf(alice, bob)
            history.record("example.com", bob)

            assertEquals(listOf(bob, alice), history.suggestLogins("web:example.com", "example.com", entries, entries))
            assertEquals(emptyList<PassEntry>(), history.suggestLogins("web:example.com", "example.com", emptyList(), entries))
        }

    @Test
    fun `a remembered login that also matches appears once at the top`() =
        testScope.runTest {
            val alice = entry("example.com/alice")
            val bob = entry("example.com/bob")
            val matches = listOf(alice, bob)
            history.record("web:example.com", bob)

            assertEquals(listOf(bob, alice), history.suggestLogins("web:example.com", "example.com", matches, matches))
        }

    @Test
    fun `a removed login leaves the ordinary matches in place`() =
        testScope.runTest {
            val matches = listOf(entry("example.com/alice"))
            history.record("web:example.com", entry("example.net/bob"))

            assertEquals(matches, history.suggestLogins("web:example.com", "example.com", matches, matches))
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

    @Test
    fun `rank keeps the order when the read of the history fails`() =
        testScope.runTest {
            val broken = AutofillHistory(FailingWriteStore(flow { error("broken store") }))

            assertEquals(cards, broken.rank("payments.google.com", cards))
        }

    @Test
    fun `rank keeps the order when the read of the history does not finish`() =
        testScope.runTest {
            val stuck = AutofillHistory(FailingWriteStore(flow { awaitCancellation() }))

            assertEquals(cards, stuck.rank("payments.google.com", cards))
        }

    @Test
    fun `record does not fail when the write of the history fails`() =
        testScope.runTest {
            val broken = AutofillHistory(FailingWriteStore(flow { error("broken store") }))

            broken.record("payments.google.com", master)
        }

    private class FailingWriteStore(
        override val data: Flow<Preferences>,
    ) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = error("broken store")
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
