// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import com.zangetsu101.pass.keymanagement.AuthSubkeyInfo
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.ssh.SshKeyStore
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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

    // init — no auth subkey → device-only path

    @Test
    fun `init with no auth subkey resolves device-only and generates key`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 DEVICEKEY"

            val vm = makeVm()

            assertEquals(KeyResolution.DeviceOnly, vm.keyResolution.value)
            assertEquals(DeviceKey.Ready("ssh-ed25519 DEVICEKEY"), vm.deviceKey.value)
            coVerify { appPrefs.setSshPublicKey("ssh-ed25519 DEVICEKEY") }
        }

    // init — auth subkey present → gpg path

    @Test
    fun `init with auth subkey resolves gpg-available and skips device key generation`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey

            val vm = makeVm()

            assertEquals(
                KeyResolution.GpgAvailable(fakeAuthSubkey, SshKeySource.GPG_AUTH),
                vm.keyResolution.value,
            )
            assertEquals(DeviceKey.None, vm.deviceKey.value)
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
            assertEquals("Repository path is required", vm.form.value.remoteUrlError)
        }

    @Test
    fun `validateRemoteUrl github repo path returns true`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 KEY"
            val vm = makeVm()

            vm.setRemoteUrl("x/y.git")
            val result = vm.validateRemoteUrl()

            assertTrue(result)
            assertNull(vm.form.value.remoteUrlError)
        }

    @Test
    fun `setRemoteUrl strips hardcoded github ssh prefix when pasted`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 KEY"
            val vm = makeVm()

            vm.setRemoteUrl("git@github.com:x/y.git")

            assertEquals("x/y.git", vm.form.value.remoteUrl)
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
            assertEquals("Enter a GitHub repo path like user/pass-store.git", vm.form.value.remoteUrlError)
        }

    // setSource toggle

    @Test
    fun `setSource to device generates key when not yet generated`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 DEVICEKEY"
            val vm = makeVm()

            vm.setSource(SshKeySource.DEVICE)

            assertEquals(
                KeyResolution.GpgAvailable(fakeAuthSubkey, SshKeySource.DEVICE),
                vm.keyResolution.value,
            )
            assertEquals(DeviceKey.Ready("ssh-ed25519 DEVICEKEY"), vm.deviceKey.value)
        }

    @Test
    fun `setSource to gpg restores gpg source without regenerating key`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 DEVICEKEY"
            val vm = makeVm()
            vm.setSource(SshKeySource.DEVICE)

            vm.setSource(SshKeySource.GPG_AUTH)

            assertEquals(
                KeyResolution.GpgAvailable(fakeAuthSubkey, SshKeySource.GPG_AUTH),
                vm.keyResolution.value,
            )
        }

    // onClone — device branch

    @Test
    fun `onClone device branch sets sshKeySource pref and navigates`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 DEVICEKEY"
            val vm = makeVm()
            vm.setRemoteUrl("x/y.git")

            vm.onClone()

            coVerify { appPrefs.setSshKeySource("device") }
            assertEquals("git@github.com:x/y.git", vm.navigation.first())
        }

    // onClone — gpg branch

    @Test
    fun `onClone gpg branch extracts seed, imports, sets prefs, and navigates`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey
            every { gpgKeyOps.extractAuthSubkeySeed("pass", fakeAuthSubkey.keyId) } returns ByteArray(32)
            every { sshKeyOps.importEd25519Seed(any()) } returns "ssh-ed25519 GPGKEY"
            val vm = makeVm()
            vm.setRemoteUrl("x/y.git")

            vm.onClone("pass")

            coVerify { appPrefs.setSshPublicKey("ssh-ed25519 GPGKEY") }
            coVerify { appPrefs.setSshKeySource("gpg_auth") }
            assertEquals("git@github.com:x/y.git", vm.navigation.first())
            assertEquals(Extraction.Idle, vm.extraction.value)
        }

    @Test
    fun `onClone gpg branch with wrong passphrase sets failed extraction and stays`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns fakeAuthSubkey
            every {
                gpgKeyOps.extractAuthSubkeySeed("wrong", fakeAuthSubkey.keyId)
            } throws SessionError.WrongPassphrase()
            val vm = makeVm()
            vm.setRemoteUrl("x/y.git")

            vm.onClone("wrong")

            assertEquals(Extraction.Failed("Wrong passphrase"), vm.extraction.value)
        }

    // regenerateSshKey

    @Test
    fun `regenerateSshKey generates new key and updates state`() =
        runTest(testDispatcher) {
            every { gpgKeyOps.findAuthSubkey() } returns null
            every { sshKeyOps.generateSshKey() } returns "ssh-ed25519 NEWKEY"
            val vm = makeVm()

            vm.regenerateSshKey()

            assertInstanceOf(DeviceKey.Ready::class.java, vm.deviceKey.value)
            assertEquals("ssh-ed25519 NEWKEY", (vm.deviceKey.value as DeviceKey.Ready).publicKey)
        }
}
