package com.zangetsu101.pass.session

import app.cash.turbine.test
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyOperations
import com.zangetsu101.pass.keymanagement.session.EndReason
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionState
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class SessionStartViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val gpgKeyOperations: GpgKeyOperations = mockk()
    private val sessionOperations: SessionOperations = mockk()
    private val appPreferences: AppPreferences = mockk()

    private val sessionTimeoutFlow = MutableStateFlow(5)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { appPreferences.sessionTimeoutMinutes } returns sessionTimeoutFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(reason: EndReason = EndReason.MANUAL): SessionStartViewModel {
        every { sessionOperations.sessionState } returns MutableStateFlow(SessionState.Inactive(reason))
        return SessionStartViewModel(gpgKeyOperations, sessionOperations, appPreferences)
    }

    @Test
    fun `init with MANUAL reason sets title to start session`() =
        runTest(testDispatcher) {
            val viewModel = createVm(EndReason.MANUAL)
            advanceUntilIdle()
            assertEquals("start session", viewModel.state.value.title)
        }

    @Test
    fun `init with BIOMETRIC_CHANGED reason sets title to security change detected`() =
        runTest(testDispatcher) {
            val viewModel = createVm(EndReason.BIOMETRIC_CHANGED)
            advanceUntilIdle()
            assertEquals("security change detected", viewModel.state.value.title)
        }

    @Test
    fun `init with TIMEOUT reason sets title to session expired`() =
        runTest(testDispatcher) {
            val viewModel = createVm(EndReason.TIMEOUT)
            advanceUntilIdle()
            assertEquals("session expired", viewModel.state.value.title)
        }

    @Test
    fun `init with TIMEOUT_CHANGED reason sets title to session timeout changed`() =
        runTest(testDispatcher) {
            val viewModel = createVm(EndReason.TIMEOUT_CHANGED)
            advanceUntilIdle()
            assertEquals("session timeout changed", viewModel.state.value.title)
        }

    @Test
    fun `new sessionTimeoutMinutes emission updates state`() =
        runTest(testDispatcher) {
            val viewModel = createVm()
            advanceUntilIdle()

            sessionTimeoutFlow.value = 15
            advanceUntilIdle()

            assertEquals(15, viewModel.state.value.sessionTimeoutMinutes)
        }

    @Test
    fun `submit with empty passphrase does not set loading or call createSession`() =
        runTest(testDispatcher) {
            val viewModel = createVm()
            advanceUntilIdle()

            viewModel.submit()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.loading)
            coVerify(exactly = 0) { sessionOperations.createSession(any()) }
        }

    @Test
    fun `submit success emits loading=true then success=true and loading=false`() =
        runTest(testDispatcher) {
            every { gpgKeyOperations.validatePassphrase(any()) } just Runs
            coEvery { sessionOperations.createSession(any()) } just Runs
            val viewModel = createVm()
            advanceUntilIdle()
            viewModel.setPassphrase("correct-passphrase")

            viewModel.state.test {
                awaitItem() // current state with passphrase set

                viewModel.submit()

                val loading = awaitItem()
                assertTrue(loading.loading)
                assertFalse(loading.success)

                advanceUntilIdle()

                val success = awaitItem()
                assertTrue(success.success)
                assertFalse(success.loading)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `submit with WrongPassphrase emits loading=true then error set and loading=false`() =
        runTest(testDispatcher) {
            every { gpgKeyOperations.validatePassphrase(any()) } throws SessionError.WrongPassphrase()
            val viewModel = createVm()
            advanceUntilIdle()
            viewModel.setPassphrase("wrong-passphrase")

            viewModel.state.test {
                awaitItem() // current state with passphrase set

                viewModel.submit()

                val loading = awaitItem()
                assertTrue(loading.loading)
                assertNull(loading.error)

                advanceUntilIdle()

                val error = awaitItem()
                assertFalse(error.loading)
                assertNotNull(error.error)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
