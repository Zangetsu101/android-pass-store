// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35])
class WelcomeViewModelTest {
    private val context = mockk<Context>()
    private val manager = mockk<BiometricManager>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(BiometricManager::class)
        every { BiometricManager.from(context) } returns manager
        every { manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) } returns
            BiometricManager.BIOMETRIC_SUCCESS
    }

    @After
    fun teardown() {
        unmockkStatic(BiometricManager::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `PIN alone does not pass onboarding`() {
        every { manager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        val vm = WelcomeViewModel(context)

        assertFalse(vm.state.value.isReady)
        assertTrue(vm.state.value.primaryFailure is PreflightFailure.BiometricNotEnrolled)
    }

    @Test
    fun `enrolling a strong biometric passes the recheck`() {
        every { manager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        val vm = WelcomeViewModel(context)

        every { manager.canAuthenticate(BIOMETRIC_STRONG) } returns BiometricManager.BIOMETRIC_SUCCESS
        vm.recheck()

        assertTrue(vm.state.value.isReady)
    }
}
