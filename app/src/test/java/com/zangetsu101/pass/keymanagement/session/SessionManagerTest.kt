// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import android.security.keystore.KeyPermanentlyInvalidatedException
import app.cash.turbine.test
import com.zangetsu101.pass.keymanagement.crypto.BiometricCryptoStore
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)
    private val testScope = TestScope(testDispatcher)

    private val keyStore: BiometricCryptoStore = mockk(relaxed = true)
    private val biometricPrompter: BiometricPrompter = mockk()
    private val sessionTimer: SessionTimer = mockk(relaxed = true)
    private val appPreferences: AppPreferences = mockk(relaxed = true)

    private fun buildManager(): SessionManager =
        SessionManager(
            keyStore = keyStore,
            biometricPrompter = biometricPrompter,
            sessionTimer = sessionTimer,
            appPreferences = appPreferences,
            scope = testScope,
            ioDispatcher = testDispatcher,
        )

    @BeforeEach
    fun setUp() {
        every { appPreferences.sessionLastTouched } returns flowOf(-1L)
        every { appPreferences.sessionTimeoutMinutes } returns flowOf(5)
        every { keyStore.maxBytes } returns 190
    }

    @AfterEach
    fun tearDown() {
        testScope.cancel()
    }

    // Test 1: init with active session, time still remaining → Active
    // init sets Active synchronously before Turbine subscribes; Inactive(MANUAL) is never observed
    @Test
    fun `init with recent lastTouched and hasSession emits Active`() =
        testScope.runTest {
            val lastTouched = System.currentTimeMillis() - 60_000L
            every { appPreferences.sessionLastTouched } returns flowOf(lastTouched)
            every { keyStore.exists() } returns true

            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Active, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 2: init with elapsed > timeout → immediate Inactive(TIMEOUT)
    // init sets Active synchronously, then a launched coroutine calls startTimeout(negative) → endSession(TIMEOUT)
    @Test
    fun `init with elapsed greater than timeout emits Inactive TIMEOUT`() =
        testScope.runTest {
            val timeoutMs = 5 * 60_000L
            val lastTouched = System.currentTimeMillis() - timeoutMs - 1_000L
            every { appPreferences.sessionLastTouched } returns flowOf(lastTouched)
            every { keyStore.exists() } returns true

            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Active, awaitItem())
                advanceUntilIdle()
                assertEquals(SessionState.Inactive(EndReason.TIMEOUT), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 3: init with no session → stays Inactive(MANUAL)
    @Test
    fun `init with no session stays Inactive MANUAL`() =
        testScope.runTest {
            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 4: createSession passphrase over limit → PassphraseTooLong, state unchanged
    @Test
    fun `createSession with passphrase over maxPassphraseBytes throws PassphraseTooLong`() =
        testScope.runTest {
            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                val ex = runCatching { manager.createSession("a".repeat(200)) }.exceptionOrNull()
                assertTrue(ex is SessionError.PassphraseTooLong)

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 5: createSession success → Active; createKey + storeEncryptedPassphrase called
    @Test
    fun `createSession success emits Active and calls keyStore createKey and store`() =
        testScope.runTest {
            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                manager.createSession("mypassphrase")
                assertEquals(SessionState.Active, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }

            verify { keyStore.store(any(), false) }
        }

    // Test 6: endSession(MANUAL) from Inactive → no state change (StateFlow deduplicates equal values); deleteSession called
    @Test
    fun `endSession MANUAL calls deleteSession and emits no new state`() =
        testScope.runTest {
            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                manager.endSession(EndReason.MANUAL)
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }

            verify { keyStore.delete() }
        }

    // Test 7: createSession → advanceTimeBy(timeoutMs) → Inactive(TIMEOUT)
    @Test
    fun `touchSession fires timeout after timeoutMs`() =
        testScope.runTest {
            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                manager.createSession("mypassphrase")
                assertEquals(SessionState.Active, awaitItem())

                advanceTimeBy(delayTimeMillis = 5 * 60_000L + 1)
                assertEquals(SessionState.Inactive(EndReason.TIMEOUT), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 8: touchSession before deadline, second touchSession → timer resets; no early expiry
    @Test
    fun `touchSession before deadline resets timer and prevents early expiry`() =
        testScope.runTest {
            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                manager.createSession("mypassphrase")
                runCurrent() // run launched coroutine, set up timer; don't advance clock
                assertEquals(SessionState.Active, awaitItem())

                advanceTimeBy(delayTimeMillis = 4 * 60_000L) // T=4min; original timer fires at T=5min

                manager.touchSession() // reset: new timer fires at T=4min + 5min = T=9min
                runCurrent() // run the second touchSession coroutine

                advanceTimeBy(delayTimeMillis = 60_000L + 1) // T=5min+1ms; original timer would have fired, but was cancelled

                // new timer fires at T=9min; we're at T=5min — no expiry yet
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 9a: touchSession with timeoutMs = 0 → no timer, session stays Active indefinitely
    @Test
    fun `touchSession with timeoutMs 0 does not schedule timer`() =
        testScope.runTest {
            every { appPreferences.sessionTimeoutMinutes } returns flowOf(0)

            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                manager.createSession("passphrase")
                assertEquals(SessionState.Active, awaitItem())

                advanceTimeBy(delayTimeMillis = 24 * 60 * 60_000L) // 24 hours
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 9b: timeoutMs = 0 sessions survive process recreation instead of expiring immediately
    @Test
    fun `init with timeoutMs 0 and existing session stays Active indefinitely`() =
        testScope.runTest {
            val lastTouched = System.currentTimeMillis() - 24 * 60 * 60_000L
            every { appPreferences.sessionTimeoutMinutes } returns flowOf(0)
            every { appPreferences.sessionLastTouched } returns flowOf(lastTouched)
            every { keyStore.exists() } returns true

            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Active, awaitItem())
                advanceTimeBy(delayTimeMillis = 24 * 60 * 60_000L)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 9c: manual-clear sessions prefer StrongBox for the session passphrase key
    @Test
    fun `createSession with timeoutMs 0 asks keyStore to prefer StrongBox`() =
        testScope.runTest {
            every { appPreferences.sessionTimeoutMinutes } returns flowOf(0)

            val manager = buildManager()

            manager.createSession("passphrase")

            verify { keyStore.store(any(), true) }
        }

    // Test 10: getPassphrase when Inactive → throws NoActiveSession
    @Test
    fun `getPassphrase when inactive throws NoActiveSession`() =
        testScope.runTest {
            val manager = buildManager()

            val ex = runCatching { manager.getPassphrase(mockk()) }.exceptionOrNull()

            assertTrue(ex is SessionError.NoActiveSession)
        }

    // Test 11a: sessionTimeoutMinutes changes while Active → Inactive(TIMEOUT_CHANGED)
    // Uses a cold flow backed by CompletableDeferred: emits 5 (for first() callers), then emits
    // the deferred value and completes — ensuring the collect coroutine terminates cleanly.
    @Test
    fun `timeout preference change while Active emits Inactive TIMEOUT_CHANGED`() =
        testScope.runTest {
            val changeSignal = CompletableDeferred<Int>()
            every { appPreferences.sessionTimeoutMinutes } returns
                flow {
                    emit(5)
                    emit(changeSignal.await())
                }

            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                manager.createSession("mypassphrase")
                runCurrent()
                assertEquals(SessionState.Active, awaitItem())

                changeSignal.complete(10)
                advanceUntilIdle()
                assertEquals(SessionState.Inactive(EndReason.TIMEOUT_CHANGED), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 11b: sessionTimeoutMinutes changes while Inactive → no state change
    @Test
    fun `timeout preference change while Inactive has no effect`() =
        testScope.runTest {
            val changeSignal = CompletableDeferred<Int>()
            every { appPreferences.sessionTimeoutMinutes } returns
                flow {
                    emit(5)
                    emit(changeSignal.await())
                }

            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                runCurrent()
                changeSignal.complete(10)
                advanceUntilIdle()
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    // Test 11: getPassphrase → getDecryptCipher throws KeyPermanentlyInvalidatedException
    //          → endSession(BIOMETRIC_CHANGED); throws NoActiveSession
    @Test
    fun `getPassphrase with key invalidated ends session with BIOMETRIC_CHANGED and throws NoActiveSession`() =
        testScope.runTest {
            every { keyStore.getDecryptCipher() } throws mockk<KeyPermanentlyInvalidatedException>()

            val manager = buildManager()

            manager.sessionState.test {
                assertEquals(SessionState.Inactive(EndReason.MANUAL), awaitItem())

                manager.createSession("passphrase")
                runCurrent()
                assertEquals(SessionState.Active, awaitItem())

                val ex = runCatching { manager.getPassphrase(mockk()) }.exceptionOrNull()
                assertTrue(ex is SessionError.NoActiveSession)

                assertEquals(SessionState.Inactive(EndReason.BIOMETRIC_CHANGED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
