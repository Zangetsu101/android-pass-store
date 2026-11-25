// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.passstore.EntryPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEntryPathTest {
    @Test
    fun `entry paths allow spaces and dots inside names`() {
        val path = EntryPath("  github.com/my bank  ")
        assertEquals("github.com/my bank", path.value)
        assertNull(path.error)
    }

    @Test
    fun `entry paths reject invalid segments and Windows characters`() {
        val invalid =
            listOf(
                "",
                "/email",
                "email/",
                "email//gmail",
                ".",
                "..",
                "email/../gmail",
                ".hidden",
                "email/.hidden",
                "email.gpg",
                "NUL",
                "work/com1.txt",
                "work/LPT¹",
                "dev./alice",
                "dev /alice",
            )
        for (path in invalid + "\\:*?\"<>|".map { "email${it}gmail" }) {
            assertNotNull(path, EntryPath(path).error)
        }
    }

    @Test
    fun `a path is in a card folder when a folder in it is named cards or credit-cards`() {
        val inCardFolder = listOf("cards/visa", "credit-cards/visa", "finance/cards/visa/daily", "cards/cards/visa")
        val outside = listOf("cards", "credit-cards", "Cards/visa", "my-cards/visa", "dev/github.com/alice", "card/visa")
        for (path in inCardFolder) assertTrue(path, EntryPath(path).inCardFolder)
        for (path in outside) assertFalse(path, EntryPath(path).inCardFolder)
        assertNull(EntryPath("cards/visa").error)
    }

    @Test
    fun `grouped store reports folder hints and matches folder prefixes`() {
        val index =
            EntryPathOptions(
                listOf("dev/github.com/alex", "dev/github.com/ann", "work/morgan"),
                setOf("dev", "dev/github.com", "work", "empty"),
            )
        assertEquals("name it like your store · 3 entries", index.hint(EntryPath("")))
        assertEquals("new in dev/github.com/ · 2 entries there", index.hint(EntryPath("dev/github.com/sam")))
        assertEquals("new · creates folder dev/gitlab.com/", index.hint(EntryPath("dev/gitlab.com/sam")))
        assertEquals(listOf("dev/github.com"), index.suggestions(EntryPath("dev/git")).map { it.path })
        assertEquals("new in empty/ · 0 entries there", index.hint(EntryPath("empty/sam")))
        assertEquals(listOf("empty"), index.suggestions(EntryPath("em")).map { it.path })
    }

    @Test
    fun `a folder suggestion highlights only the typed name`() {
        val options = EntryPathOptions(emptyList(), setOf("dev/github.com"))
        val suggestion = options.suggestions(EntryPath("dev/git")).single()

        assertEquals("git", suggestion.path.substring(suggestion.match))
    }

    @Test
    fun `flat store matches entry names by substring`() {
        val index = EntryPathOptions(listOf("github.com", "gitlab.com", "bank"), emptySet())
        assertEquals("new entry · 3 in store", index.hint(EntryPath("mail")))
        assertEquals(listOf("github.com", "gitlab.com"), index.suggestions(EntryPath("IT")).map { it.path })
        assertEquals(emptyList<PathSuggestion>(), index.suggestions(EntryPath("g")))
    }
}
