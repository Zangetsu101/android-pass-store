package com.zangetsu101.pass.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class AppPreferencesTest {
    private lateinit var testScope: TestScope
    private lateinit var tempFile: File
    private lateinit var prefs: AppPreferences

    @BeforeEach
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        tempFile = File.createTempFile("app_prefs_test", ".preferences_pb")
        val store =
            PreferenceDataStoreFactory.create(
                scope = testScope,
                produceFile = { tempFile },
            )
        prefs = AppPreferences(store)
    }

    @AfterEach
    fun teardown() {
        testScope.cancel()
        tempFile.delete()
    }

    @Test
    fun `default remoteUrl is empty string`() =
        testScope.runTest {
            assertEquals("", prefs.remoteUrl.first())
        }

    @Test
    fun `default sessionTimeoutMinutes is 5`() =
        testScope.runTest {
            assertEquals(5, prefs.sessionTimeoutMinutes.first())
        }

    @Test
    fun `default sshPublicKey is null`() =
        testScope.runTest {
            assertNull(prefs.sshPublicKey.first())
        }

    @Test
    fun `default gpgImported is false`() =
        testScope.runTest {
            assertFalse(prefs.gpgImported.first())
        }

    @Test
    fun `default clipboardTimeoutSeconds is 45`() =
        testScope.runTest {
            assertEquals(45, prefs.clipboardTimeoutSeconds.first())
        }

    @Test
    fun `default defaultViewTree is true`() =
        testScope.runTest {
            assertTrue(prefs.defaultViewTree.first())
        }

    @Test
    fun `round-trip write and read survives DataStore serialization`() =
        testScope.runTest {
            prefs.setRemoteUrl("https://git.example.com/repo.git")
            assertEquals("https://git.example.com/repo.git", prefs.remoteUrl.first())

            prefs.setSessionTimeout(30)
            assertEquals(30, prefs.sessionTimeoutMinutes.first())

            prefs.setSshPublicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA test@host")
            assertEquals("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA test@host", prefs.sshPublicKey.first())

            prefs.setGpgImported(true)
            assertTrue(prefs.gpgImported.first())

            prefs.setClipboardTimeout(10)
            assertEquals(10, prefs.clipboardTimeoutSeconds.first())

            prefs.setDefaultViewTree(false)
            assertFalse(prefs.defaultViewTree.first())
        }

    @Test
    fun `clearRemoteUrl causes remoteUrl flow to emit empty string`() =
        testScope.runTest {
            prefs.setRemoteUrl("https://git.example.com/repo.git")
            prefs.clearRemoteUrl()
            assertEquals("", prefs.remoteUrl.first())
        }

    @Test
    fun `clearAll resets all flows to their defaults`() =
        testScope.runTest {
            prefs.setRemoteUrl("https://git.example.com/repo.git")
            prefs.setSessionTimeout(30)
            prefs.setSshPublicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA test@host")
            prefs.setGpgImported(true)
            prefs.setClipboardTimeout(10)
            prefs.setDefaultViewTree(false)

            prefs.clearAll()

            assertEquals("", prefs.remoteUrl.first())
            assertEquals(5, prefs.sessionTimeoutMinutes.first())
            assertNull(prefs.sshPublicKey.first())
            assertFalse(prefs.gpgImported.first())
            assertEquals(45, prefs.clipboardTimeoutSeconds.first())
            assertTrue(prefs.defaultViewTree.first())
        }
}
