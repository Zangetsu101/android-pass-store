package com.zangetsu101.pass.onboarding

import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.session.SessionError
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloneRepoViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var sshKeyOps: SshKeyStore
    private lateinit var gpgKeyOps: GpgKeyStore
    private lateinit var appPrefs: AppPreferences

    private val fakeAuthSubkey =
        AuthSubkeyInfo(
            keyId = 0xDEADBEEFL,
            sshPublicKey = "ssh-ed25519 AAAAAUTHKEY",
            sshFingerprint = "SHA256:abc123",
            uid = "Test User <test@example.com>",
            created = 1_700_000_000L,
        )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        sshKeyOps = mockk()
        gpgKeyOps = mockk()
        appPrefs = mockk(relaxed = true)
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): CloneRepoViewModel = CloneRepoViewModel(sshKeyOps, gpgKeyOps, appPrefs, testDispatcher)

    // init — no auth subkey → device path

    @Test
    fun `init with no auth subkey defaults to device source and generates key`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 DEVICEKEY"

            val vm = makeVm()

            assertEquals(SshKeySource.DEVICE, vm.state.value.source)
            assertEquals("ssh-ed25519 DEVICEKEY", vm.state.value.devicePublicKey)
            coVerify { appPrefs.setSshPublicKey("ssh-ed25519 DEVICEKEY") }
        }

    // init — auth subkey present → gpg path

    @Test
    fun `init with auth subkey defaults to gpg source and skips device key generation`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey

            val vm = makeVm()

            assertEquals(SshKeySource.GPG_AUTH, vm.state.value.source)
            assertEquals(fakeAuthSubkey, vm.state.value.authSubkey)
            assertNull(vm.state.value.devicePublicKey)
        }

    // validateRemoteUrl

    @Test
    fun `validateRemoteUrl empty string sets required error and returns false`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 KEY"
            val vm = makeVm()

            vm.setRemoteUrl("")
            val result = vm.validateRemoteUrl()

            assertFalse(result)
            assertEquals("Remote URL is required", vm.state.value.remoteUrlError)
        }

    @Test
    fun `validateRemoteUrl git-at scheme returns true`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 KEY"
            val vm = makeVm()

            vm.setRemoteUrl("git@github.com:x/y.git")
            val result = vm.validateRemoteUrl()

            assertTrue(result)
            assertNull(vm.state.value.remoteUrlError)
        }

    @Test
    fun `validateRemoteUrl https scheme sets invalid error and returns false`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 KEY"
            val vm = makeVm()

            vm.setRemoteUrl("https://github.com/x/y")
            val result = vm.validateRemoteUrl()

            assertFalse(result)
            assertEquals("Enter a valid git remote URL", vm.state.value.remoteUrlError)
        }

    // setSource toggle

    @Test
    fun `setSource to device generates key when not yet generated`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 DEVICEKEY"
            val vm = makeVm()

            vm.setSource(SshKeySource.DEVICE)

            assertEquals(SshKeySource.DEVICE, vm.state.value.source)
            assertEquals("ssh-ed25519 DEVICEKEY", vm.state.value.devicePublicKey)
        }

    @Test
    fun `setSource to gpg restores gpg source without regenerating key`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 DEVICEKEY"
            val vm = makeVm()
            vm.setSource(SshKeySource.DEVICE)

            vm.setSource(SshKeySource.GPG_AUTH)

            assertEquals(SshKeySource.GPG_AUTH, vm.state.value.source)
        }

    // onClone — device branch

    @Test
    fun `onClone device branch sets sshKeySource pref and navigates`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 DEVICEKEY"
            val vm = makeVm()
            vm.setRemoteUrl("git@github.com:x/y.git")

            vm.onClone()

            coVerify { appPrefs.setSshKeySource("device") }
            assertNotNull(vm.state.value.navigateTo)
            assertEquals("git@github.com:x/y.git", vm.state.value.navigateTo)
        }

    // onClone — gpg branch

    @Test
    fun `onClone gpg branch extracts seed, imports, sets prefs, and navigates`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey
            every { gpgKeyOps.extractAuthSubkeySeed("pass", fakeAuthSubkey.keyId) } returns ByteArray(32)
            every { sshKeyOps.importEd25519Seed(any()) } returns "ssh-ed25519 GPGKEY"
            val vm = makeVm()
            vm.setRemoteUrl("git@github.com:x/y.git")

            vm.onClone("pass")

            coVerify { appPrefs.setSshPublicKey("ssh-ed25519 GPGKEY") }
            coVerify { appPrefs.setSshKeySource("gpg_auth") }
            assertNotNull(vm.state.value.navigateTo)
            assertEquals("git@github.com:x/y.git", vm.state.value.navigateTo)
        }

    @Test
    fun `onClone gpg branch with wrong passphrase sets passphraseError and stays`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey
            every {
                gpgKeyOps.extractAuthSubkeySeed("wrong", fakeAuthSubkey.keyId)
            } throws SessionError.WrongPassphrase()
            val vm = makeVm()
            vm.setRemoteUrl("git@github.com:x/y.git")

            vm.onClone("wrong")

            assertEquals("Wrong passphrase", vm.state.value.passphraseError)
            assertNull(vm.state.value.navigateTo)
            assertFalse(vm.state.value.isExtracting)
        }

    // regenerateSshKey

    @Test
    fun `regenerateSshKey generates new key and updates state`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 NEWKEY"
            val vm = makeVm()

            vm.regenerateSshKey()

            assertEquals("ssh-ed25519 NEWKEY", vm.state.value.devicePublicKey)
        }
}
