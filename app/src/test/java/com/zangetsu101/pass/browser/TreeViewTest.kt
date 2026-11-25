// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.zangetsu101.pass.passstore.PassEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TreeViewTest {
    private fun entry(path: String) =
        PassEntry(
            path = "$path.gpg",
            domain = path.substringBeforeLast('/').substringAfterLast('/'),
            username = path.substringAfterLast('/'),
            encryptedFile = File("$path.gpg"),
        )

    @Test
    fun `shows the first entry at the top when only the order of the entries changes`() =
        runComposeUiTest {
            val others = (1..30).map { entry("folder$it/site$it.com/user$it") }
            val target = entry("work/aws.amazon.com/alex.morgan")
            var entries by mutableStateOf(others + target)
            setContent {
                TreeView(
                    entries = entries,
                    selectedPath = null,
                    collapsedDirs = emptySet(),
                    onToggleDir = {},
                    onEntryClick = {},
                )
            }
            onNodeWithText("work/aws.amazon.com/").assertDoesNotExist()

            // A search keeps every entry and sorts the best match first.
            entries = listOf(target) + others

            onNodeWithText("work/aws.amazon.com/").assertIsDisplayed()
            onNodeWithText("alex.morgan").assertIsDisplayed()
        }
}
