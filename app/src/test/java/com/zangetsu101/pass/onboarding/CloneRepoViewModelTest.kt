package com.zangetsu101.pass.onboarding

import com.zangetsu101.pass.keymanagement.ssh.SshKeyStore
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloneRepoViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var sshKeyOps: SshKeyStore
    private lateinit var appPrefs: AppPreferences
    private lateinit var viewModel: CloneRepoViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sshKeyOps = mockk()
        appPrefs = mockk()
        every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 TESTKEY"
        coEvery { appPrefs.setSshPublicKey(any()) } just Awaits
        viewModel = CloneRepoViewModel(sshKeyOps, appPrefs, testDispatcher)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init generates SSH key, sets state, and persists to prefs`() =
        runTest(testDispatcher) {
            assertEquals("ssh-ed25519 TESTKEY", viewModel.state.value.sshPublicKey)
            coVerify(exactly = 1) { appPrefs.setSshPublicKey("ssh-ed25519 TESTKEY") }
        }

    @Test
    fun `validateRemoteUrl empty string sets required error and returns false`() {
        viewModel.setRemoteUrl("")
        val result = viewModel.validateRemoteUrl()
        assertFalse(result)
        assertEquals("Remote URL is required", viewModel.state.value.remoteUrlError)
    }

    @Test
    fun `validateRemoteUrl ftp scheme sets invalid error and returns false`() {
        viewModel.setRemoteUrl("ftp://foo")
        val result = viewModel.validateRemoteUrl()
        assertFalse(result)
        assertEquals("Enter a valid git remote URL", viewModel.state.value.remoteUrlError)
    }

    @Test
    fun `validateRemoteUrl https scheme sets invalid error and returns false`() {
        viewModel.setRemoteUrl("https://github.com/x/y")
        val result = viewModel.validateRemoteUrl()
        assertFalse(result)
        assertEquals("Enter a valid git remote URL", viewModel.state.value.remoteUrlError)
    }

    @Test
    fun `validateRemoteUrl git-at scheme clears error and returns true`() {
        viewModel.setRemoteUrl("git@github.com:x/y.git")
        val result = viewModel.validateRemoteUrl()
        assertTrue(result)
        assertNull(viewModel.state.value.remoteUrlError)
    }

    @Test
    fun `validateRemoteUrl ssh scheme clears error and returns true`() {
        viewModel.setRemoteUrl("ssh://git@github.com/x/y")
        val result = viewModel.validateRemoteUrl()
        assertTrue(result)
        assertNull(viewModel.state.value.remoteUrlError)
    }

    @Test
    fun `validateRemoteUrl file scheme clears error and returns true`() {
        viewModel.setRemoteUrl("file:///local/repo")
        val result = viewModel.validateRemoteUrl()
        assertTrue(result)
        assertNull(viewModel.state.value.remoteUrlError)
    }

    @Test
    fun `regenerateSshKey generates new key and updates prefs`() =
        runTest(testDispatcher) {
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 NEWKEY"
            viewModel.regenerateSshKey()
            assertEquals("ssh-ed25519 NEWKEY", viewModel.state.value.sshPublicKey)
            coVerify(exactly = 1) { appPrefs.setSshPublicKey("ssh-ed25519 NEWKEY") }
        }
}
