// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.zangetsu101.pass.gitsync.gitSyncDataStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PassStoreImplTest {
    private lateinit var context: Context
    private lateinit var store: PassStoreImpl
    private lateinit var repoDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.gitSyncDataStore.edit { it.clear() } }
        store = PassStoreImpl(context)
        repoDir = File(context.filesDir, "repo")
        repoDir.deleteRecursively()
        repoDir.mkdirs()
    }

    private fun createGpgFile(relativePath: String) {
        val file = File(repoDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText("dummy")
    }

    @Test
    fun `readEntry finds a saved card before the index is built`() =
        runBlocking {
            createGpgFile("cards/visa/personal.gpg")
            val entry = requireNotNull(store.readEntry("cards/visa/personal.gpg"))
            assertTrue(entry.isCard)
            assertEquals("visa", entry.domain)
            assertEquals("personal", entry.username)
            assertTrue(store.index.value.isEmpty())
            assertNull(store.readEntry("cards/visa/missing.gpg"))
            assertNull(store.readEntry("../outside.gpg"))
        }

    @Test
    fun `buildIndex parses two-level path correctly`() =
        runBlocking {
            createGpgFile("web/github.com/alice.gpg")
            val entries = store.buildIndex()

            val entry = entries.single { it.username == "alice" }
            assertEquals("github.com", entry.domain)
            assertEquals("alice", entry.username)
            assertEquals("web/github.com/alice.gpg", entry.path)
        }

    @Test
    fun `the index includes folders without entries and skips hidden folders`() =
        runBlocking {
            createGpgFile("web/github.com/alice.gpg")
            createGpgFile("shared/.gpg-id")
            createGpgFile(".git/refs/heads/main")
            store.buildIndex()

            assertEquals(setOf("web", "web/github.com", "shared"), store.folders.value)
        }

    @Test
    fun `buildIndex recognizes a Card Entry beneath organizational directories`() =
        runBlocking {
            createGpgFile("finance/cards/visa/my-card.gpg")

            val entry = store.buildIndex().single()

            assertTrue(entry.isCard)
            assertEquals("visa", entry.domain)
            assertEquals("my-card", entry.username)
        }

    @Test
    fun `buildIndex recognizes credit-cards marker beneath organizational directories`() =
        runBlocking {
            createGpgFile("finance/credit-cards/mastercard/travel.gpg")

            val entry = store.buildIndex().single()

            assertTrue(entry.isCard)
            assertEquals("mastercard", entry.domain)
            assertEquals("travel", entry.username)
        }

    @Test
    fun `buildIndex treats card markers as case-sensitive`() =
        runBlocking {
            createGpgFile("finance/Cards/visa/my-card.gpg")

            val entry = store.buildIndex().single()

            assertTrue(!entry.isCard)
            assertEquals("visa", entry.domain)
            assertEquals("my-card", entry.username)
        }

    @Test
    fun `buildIndex discards organization after a nested card marker`() =
        runBlocking {
            createGpgFile("finance/cards/personal/visa/my-card.gpg")

            val entry = store.buildIndex().single()

            assertTrue(entry.isCard)
            assertEquals("visa", entry.domain)
            assertEquals("my-card", entry.username)
        }

    @Test
    fun `buildIndex continues to recognize root card marker`() =
        runBlocking {
            createGpgFile("cards/visa/my-card.gpg")

            val entry = store.buildIndex().single()

            assertTrue(entry.isCard)
            assertEquals("visa", entry.domain)
            assertEquals("my-card", entry.username)
        }

    @Test
    fun `buildIndex uses the first card marker`() =
        runBlocking {
            createGpgFile("cards/personal/credit-cards/my-card.gpg")

            val entry = store.buildIndex().single()

            assertTrue(entry.isCard)
            assertEquals("credit-cards", entry.domain)
        }

    @Test
    fun `buildIndex reserves card marker names at every depth`() =
        runBlocking {
            createGpgFile("clients/cards/alice.gpg")

            val entry = store.buildIndex().single()

            assertTrue(entry.isCard)
            assertNull(entry.domain)
        }

    @Test
    fun `buildIndex requires an exact card marker segment`() =
        runBlocking {
            createGpgFile("finance/giftcards/visa/my-card.gpg")

            val entry = store.buildIndex().single()

            assertTrue(!entry.isCard)
        }

    @Test
    fun `buildIndex preserves null domain for card directly beneath root marker`() =
        runBlocking {
            createGpgFile("cards/my-card.gpg")

            val entry = store.buildIndex().single()

            assertTrue(entry.isCard)
            assertNull(entry.domain)
            assertEquals("my-card", entry.username)
        }

    @Test
    fun `buildIndex handles one-level path with null domain`() =
        runBlocking {
            createGpgFile("github.com.gpg")
            val entries = store.buildIndex()

            val entry = entries.single { it.username == "github.com" }
            assertNull(entry.domain)
        }

    @Test
    fun `buildIndex ignores non-gpg files`() =
        runBlocking {
            createGpgFile("web/github.com/alice.gpg")
            File(repoDir, "README.md").writeText("readme")
            val entries = store.buildIndex()

            assertTrue(entries.none { it.path.endsWith(".md") })
            assertEquals(1, entries.size)
        }

    @Test
    fun `resolve exact domain match returns that entry first`() =
        runBlocking {
            createGpgFile("web/github.com/alice.gpg")
            createGpgFile("web/gitlab.com/alice.gpg")
            store.buildIndex()

            val results = store.resolve("github.com")

            assertEquals("github.com", results.first().domain)
        }

    @Test
    fun `resolve subdomain match - github dot com resolves gist dot github dot com`() =
        runBlocking {
            createGpgFile("web/github.com/alice.gpg")
            store.buildIndex()

            val results = store.resolve("gist.github.com")

            assertTrue(results.isNotEmpty())
            assertEquals("github.com", results.first().domain)
        }

    @Test
    fun `resolve returns empty list when no candidates`() =
        runBlocking {
            createGpgFile("web/github.com/alice.gpg")
            store.buildIndex()

            val results = store.resolve("totally-unrelated-xyz-12345.io")

            assertTrue(results.isEmpty())
        }

    @Test
    fun `resolveByPackage maps com dot github dot android to github dot com`() =
        runBlocking {
            createGpgFile("web/github.com/alice.gpg")
            store.buildIndex()

            val results = store.resolveByPackage("com.github.android")

            assertTrue(results.isNotEmpty())
            assertEquals("github.com", results.first().domain)
        }

    @Test
    fun `search is case-insensitive`() =
        runBlocking {
            createGpgFile("web/GitHub.com/Alice.gpg")
            store.buildIndex()

            val results = store.search("github")

            assertTrue(results.isNotEmpty())
        }

    @Test
    fun `search ranks startsWith match above contains match`() =
        runBlocking {
            createGpgFile("web/github.com/alice.gpg")
            createGpgFile("web/mygithub.com/alice.gpg")
            store.buildIndex()

            val results = store.search("github")

            assertEquals("github.com", results.first().domain)
        }
}
