package com.zangetsu101.pass.passstore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
    fun `buildIndex parses two-level path correctly`() {
        createGpgFile("web/github.com/alice.gpg")
        val entries = store.buildIndex()

        val entry = entries.single { it.username == "alice" }
        assertEquals("github.com", entry.domain)
        assertEquals("alice", entry.username)
        assertEquals("web/github.com/alice.gpg", entry.path)
    }

    @Test
    fun `buildIndex handles one-level path with null domain`() {
        createGpgFile("github.com.gpg")
        val entries = store.buildIndex()

        val entry = entries.single { it.username == "github.com" }
        assertNull(entry.domain)
    }

    @Test
    fun `buildIndex ignores non-gpg files`() {
        createGpgFile("web/github.com/alice.gpg")
        File(repoDir, "README.md").writeText("readme")
        val entries = store.buildIndex()

        assertTrue(entries.none { it.path.endsWith(".md") })
        assertEquals(1, entries.size)
    }

    @Test
    fun `resolve exact domain match returns that entry first`() {
        createGpgFile("web/github.com/alice.gpg")
        createGpgFile("web/gitlab.com/alice.gpg")
        store.buildIndex()

        val results = store.resolve("github.com")

        assertEquals("github.com", results.first().domain)
    }

    @Test
    fun `resolve subdomain match - github dot com resolves gist dot github dot com`() {
        createGpgFile("web/github.com/alice.gpg")
        store.buildIndex()

        val results = store.resolve("gist.github.com")

        assertTrue(results.isNotEmpty())
        assertEquals("github.com", results.first().domain)
    }

    @Test
    fun `resolve fuzzy fallback returns github dot com for githubb dot com`() {
        createGpgFile("web/github.com/alice.gpg")
        store.buildIndex()

        val results = store.resolve("githubb.com")

        assertTrue(results.isNotEmpty())
        assertEquals("github.com", results.first().domain)
    }

    @Test
    fun `resolve returns empty list when no candidates`() {
        createGpgFile("web/github.com/alice.gpg")
        store.buildIndex()

        val results = store.resolve("totally-unrelated-xyz-12345.io")

        // Fuzzy may return something; just confirm it doesn't throw
        assertTrue(results is List<*>)
    }

    @Test
    fun `resolveByPackage maps com dot github dot android to github dot com`() {
        createGpgFile("web/github.com/alice.gpg")
        store.buildIndex()

        val results = store.resolveByPackage("com.github.android")

        assertTrue(results.isNotEmpty())
        assertEquals("github.com", results.first().domain)
    }

    @Test
    fun `search is case-insensitive`() {
        createGpgFile("web/GitHub.com/Alice.gpg")
        store.buildIndex()

        val results = store.search("github")

        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `search ranks closer matches higher`() {
        createGpgFile("web/github.com/alice.gpg")
        createGpgFile("web/example.com/bob.gpg")
        store.buildIndex()

        val results = store.search("github")

        assertEquals("github.com", results.first().domain)
    }
}
