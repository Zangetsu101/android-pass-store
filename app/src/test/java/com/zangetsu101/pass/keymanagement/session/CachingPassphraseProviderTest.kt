package com.zangetsu101.pass.keymanagement.session

import androidx.fragment.app.FragmentActivity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachingPassphraseProviderTest {
    private val testScheduler = TestCoroutineScheduler()
    private val testScope = TestScope(testScheduler)

    private val delegate: PassphraseProvider = mockk()
    private val sessionOperations: SessionOperations = mockk(relaxed = true)
    private val sessionStateFlow = MutableStateFlow<SessionState>(SessionState.Active)
    private val activity: FragmentActivity = mockk()
    private lateinit var provider: CachingPassphraseProvider

    @BeforeEach
    fun setUp() {
        every { sessionOperations.sessionState } returns sessionStateFlow
        provider =
            CachingPassphraseProvider(
                delegate = delegate,
                sessionOperations = sessionOperations,
                scope = testScope.backgroundScope,
            )
        testScope.runCurrent() // start init collect coroutine
    }

    @AfterEach
    fun tearDown() {
        testScope.cancel()
    }

    // Test 1: cache miss — delegates, result cached, touchSession called
    @Test
    fun `getPassphrase on cache miss delegates to provider and calls touchSession`() =
        testScope.runTest {
            coEvery { delegate.getPassphrase(any()) } returns "secret"

            val result = provider.getPassphrase(activity)

            assertEquals("secret", result)
            coVerify(exactly = 1) { delegate.getPassphrase(any()) }
            verify(exactly = 1) { sessionOperations.touchSession() }
        }

    // Test 2: cache hit — cached value returned, delegate NOT called again, touchSession called
    @Test
    fun `getPassphrase on cache hit returns cached value without calling delegate again`() =
        testScope.runTest {
            coEvery { delegate.getPassphrase(any()) } returns "secret"

            provider.getPassphrase(activity) // prime cache
            val result = provider.getPassphrase(activity) // hit

            assertEquals("secret", result)
            coVerify(exactly = 1) { delegate.getPassphrase(any()) }
            verify(exactly = 2) { sessionOperations.touchSession() }
        }

    // Test 3: cache expires after BIOMETRIC_CACHE_TIMEOUT_MS — delegate called again
    @Test
    fun `getPassphrase after cache timeout delegates again`() =
        testScope.runTest {
            coEvery { delegate.getPassphrase(any()) } returns "secret"

            provider.getPassphrase(activity) // prime cache
            advanceTimeBy(delayTimeMillis = BIOMETRIC_CACHE_TIMEOUT_MS + 1) // fire cache expiry

            provider.getPassphrase(activity)

            coVerify(exactly = 2) { delegate.getPassphrase(any()) }
        }

    // Test 4: Inactive session state clears cache — next call delegates
    @Test
    fun `Inactive session state clears cache so next call delegates again`() =
        testScope.runTest {
            coEvery { delegate.getPassphrase(any()) } returns "secret"

            provider.getPassphrase(activity) // prime cache

            sessionStateFlow.value = SessionState.Inactive(EndReason.MANUAL)
            runCurrent() // deliver state update through collect

            provider.getPassphrase(activity)

            coVerify(exactly = 2) { delegate.getPassphrase(any()) }
        }
}
