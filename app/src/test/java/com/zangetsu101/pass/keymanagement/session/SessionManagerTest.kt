package com.zangetsu101.pass.keymanagement.session

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
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
    private val testScope = TestScope(testScheduler)

    private val keyStore: SessionKeyStore = mockk(relaxed = true)
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
        )

    @BeforeEach
    fun setUp() {
        every { appPreferences.sessionLastTouched } returns flowOf(-1L)
        every { appPreferences.sessionTimeoutMinutes } returns flowOf(5)
        every { keyStore.maxPassphraseBytes } returns 190
    }

    @AfterEach
    fun tearDown() {
        testScope.cancel()
    }

    // Test 1: init with active session, time still remaining → Active
    @Test
    fun `init with recent lastTouched and hasSession emits Active`() =
        testScope.runTest {
            val lastTouched = System.currentTimeMillis() - 60_000L
            every { appPreferences.sessionLastTouched } returns flowOf(lastTouched)
            every { keyStore.hasSession() } returns true

            val manager = buildManager()

            assertEquals(SessionState.Active, manager.sessionState.value)
        }

    // Test 2: init with elapsed > timeout → immediate Inactive(TIMEOUT)
    @Test
    fun `init with elapsed greater than timeout emits Inactive TIMEOUT`() =
        testScope.runTest {
            val timeoutMs = 5 * 60_000L
            val lastTouched = System.currentTimeMillis() - timeoutMs - 1_000L
            every { appPreferences.sessionLastTouched } returns flowOf(lastTouched)
            every { keyStore.hasSession() } returns true

            val manager = buildManager()
            advanceUntilIdle()

            assertEquals(SessionState.Inactive(EndReason.TIMEOUT), manager.sessionState.value)
        }

    // Test 3: init with no session → stays Inactive(MANUAL)
    @Test
    fun `init with no session stays Inactive MANUAL`() {
        val manager = buildManager()
        assertEquals(SessionState.Inactive(EndReason.MANUAL), manager.sessionState.value)
    }

    // Test 4: createSession passphrase over limit → PassphraseTooLong, state unchanged
    @Test
    fun `createSession with passphrase over maxPassphraseBytes throws PassphraseTooLong`() =
        testScope.runTest {
            val manager = buildManager()

            val ex = runCatching { manager.createSession("a".repeat(200)) }.exceptionOrNull()

            assertTrue(ex is SessionError.PassphraseTooLong)
            assertEquals(SessionState.Inactive(EndReason.MANUAL), manager.sessionState.value)
        }

    // Test 5: createSession success → Active; createKey + storeEncryptedPassphrase called
    @Test
    fun `createSession success emits Active and calls keyStore createKey and store`() =
        testScope.runTest {
            val manager = buildManager()
            manager.createSession("mypassphrase")

            assertEquals(SessionState.Active, manager.sessionState.value)
            verify { keyStore.createKey() }
            verify { keyStore.storeEncryptedPassphrase(any()) }
        }

    // Test 6: endSession(MANUAL) → Inactive(MANUAL); deleteSession called
    @Test
    fun `endSession MANUAL emits Inactive MANUAL and calls deleteSession`() =
        testScope.runTest {
            val manager = buildManager()
            manager.endSession(EndReason.MANUAL)

            assertEquals(SessionState.Inactive(EndReason.MANUAL), manager.sessionState.value)
            verify { keyStore.deleteSession() }
        }

    // Test 7: createSession → advanceTimeBy(timeoutMs) → Inactive(TIMEOUT)
    @Test
    fun `touchSession fires timeout after timeoutMs`() =
        testScope.runTest {
            val manager = buildManager()
            manager.createSession("mypassphrase")
            advanceUntilIdle()

            advanceTimeBy(delayTimeMillis = 5 * 60_000L + 1)

            assertEquals(SessionState.Inactive(EndReason.TIMEOUT), manager.sessionState.value)
        }

    // Test 8: touchSession before deadline, second touchSession → timer resets; no early expiry
    @Test
    fun `touchSession before deadline resets timer and prevents early expiry`() =
        testScope.runTest {
            val manager = buildManager()
            manager.createSession("mypassphrase")
            runCurrent() // run launched coroutine, set up timer; don't advance clock

            advanceTimeBy(delayTimeMillis = 4 * 60_000L) // T=4min; original timer fires at T=5min

            manager.touchSession() // reset: new timer fires at T=4min + 5min = T=9min
            runCurrent() // run the second touchSession coroutine

            advanceTimeBy(delayTimeMillis = 60_000L + 1) // T=5min+1ms; original timer would have fired, but was cancelled

            // new timer fires at T=9min; we're at T=5min — no expiry yet
            assertEquals(SessionState.Active, manager.sessionState.value)
        }

    // Test 9: touchSession with timeoutMs = 0 → no timer, session stays Active indefinitely
    @Test
    fun `touchSession with timeoutMs 0 does not schedule timer`() =
        testScope.runTest {
            every { appPreferences.sessionTimeoutMinutes } returns flowOf(0)

            val manager = buildManager()
            manager.createSession("passphrase")
            advanceUntilIdle()

            advanceTimeBy(delayTimeMillis = 24 * 60 * 60_000L) // 24 hours

            assertEquals(SessionState.Active, manager.sessionState.value)
        }

    // Test 10: getPassphrase when Inactive → throws NoActiveSession
    @Test
    fun `getPassphrase when inactive throws NoActiveSession`() =
        testScope.runTest {
            val manager = buildManager()

            val ex = runCatching { manager.getPassphrase(mockk()) }.exceptionOrNull()

            assertTrue(ex is SessionError.NoActiveSession)
        }

    // Test 11: getPassphrase → getDecryptCipher throws KeyPermanentlyInvalidatedException
    //          → endSession(BIOMETRIC_CHANGED); throws NoActiveSession
    @Test
    fun `getPassphrase with key invalidated ends session with BIOMETRIC_CHANGED and throws NoActiveSession`() =
        testScope.runTest {
            every { keyStore.readEncryptedPassphrase() } returns ByteArray(16)
            every { keyStore.getDecryptCipher() } throws mockk<KeyPermanentlyInvalidatedException>()

            val manager = buildManager()
            manager.createSession("passphrase")
            // state is set to Active synchronously in createSession; don't drain — draining
            // would advance past the 5-min timer set by touchSession(), triggering TIMEOUT first

            assertEquals(SessionState.Active, manager.sessionState.value)

            val ex = runCatching { manager.getPassphrase(mockk()) }.exceptionOrNull()

            assertTrue(ex is SessionError.NoActiveSession)
            assertEquals(SessionState.Inactive(EndReason.BIOMETRIC_CHANGED), manager.sessionState.value)
        }
}
