package com.zangetsu101.pass.syncpanel

import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncResult
import com.zangetsu101.pass.gitsync.SyncStatus
import com.zangetsu101.pass.passstore.PassStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPanelViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val gitSync: GitSync = mockk()
    private val passStore: PassStore = mockk(relaxed = true)

    private lateinit var viewModel: SyncPanelViewModel

    private val statusTime: Instant = Instant.parse("2024-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { gitSync.syncStatus() } returns
            SyncStatus(
                lastSyncTime = statusTime,
                localCommit = "abc123",
                remoteReachable = true,
            )
        viewModel = SyncPanelViewModel(gitSync, passStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init populates remoteReachable and lastSyncTime from syncStatus`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(true, state.remoteReachable)
            assertEquals(statusTime, state.lastSyncTime)
        }

    @Test
    fun `loadStatus when syncStatus throws sets remoteReachable false`() =
        runTest(testDispatcher) {
            advanceUntilIdle() // settle init

            coEvery { gitSync.syncStatus() } throws RuntimeException("Network error")
            viewModel.loadStatus()
            advanceUntilIdle()

            assertEquals(false, viewModel.state.value.remoteReachable)
        }

    @Test
    fun `pull success calls buildIndex and sets pullSuccess with updated lastSyncTime`() =
        runTest(testDispatcher) {
            advanceUntilIdle() // settle init

            val pullTime: Instant = Instant.parse("2024-06-01T00:00:00Z")
            coEvery { gitSync.pull() } returns
                SyncResult(
                    newEntries = emptyList(),
                    removedEntries = emptyList(),
                    lastSyncTime = pullTime,
                )

            viewModel.pull()
            assertTrue(viewModel.state.value.pulling)

            advanceUntilIdle()

            coVerify(exactly = 1) { passStore.buildIndex() }
            val state = viewModel.state.value
            assertTrue(state.pullSuccess)
            assertEquals(pullTime, state.lastSyncTime)
        }

    @Test
    fun `pull failure sets pullError and triggers loadStatus`() =
        runTest(testDispatcher) {
            advanceUntilIdle() // settle init — syncStatus called once

            coEvery { gitSync.pull() } throws RuntimeException("Connection reset")
            viewModel.pull()
            assertTrue(viewModel.state.value.pulling)

            advanceUntilIdle()

            assertNotNull(viewModel.state.value.pullError)
            // init loadStatus + loadStatus from pull catch block
            coVerify(exactly = 2) { gitSync.syncStatus() }
        }

    @Test
    fun `pullSuccess is not reset by subsequent loadStatus`() =
        runTest(testDispatcher) {
            advanceUntilIdle() // settle init

            val pullTime: Instant = Instant.parse("2024-06-01T00:00:00Z")
            coEvery { gitSync.pull() } returns
                SyncResult(
                    newEntries = emptyList(),
                    removedEntries = emptyList(),
                    lastSyncTime = pullTime,
                )

            viewModel.pull()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.pullSuccess)

            viewModel.loadStatus()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.pullSuccess)
        }

    @Test
    fun `pull resets pullSuccess before launching`() =
        runTest(testDispatcher) {
            advanceUntilIdle() // settle init

            val pullTime: Instant = Instant.parse("2024-06-01T00:00:00Z")
            coEvery { gitSync.pull() } returns
                SyncResult(
                    newEntries = emptyList(),
                    removedEntries = emptyList(),
                    lastSyncTime = pullTime,
                )

            viewModel.pull()
            advanceUntilIdle()
            assertTrue(viewModel.state.value.pullSuccess)

            viewModel.pull()
            assertFalse(viewModel.state.value.pullSuccess)
        }
}
