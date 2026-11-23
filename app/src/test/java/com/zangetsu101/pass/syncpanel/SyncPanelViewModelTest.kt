// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.syncpanel

import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncError
import com.zangetsu101.pass.gitsync.SyncState
import com.zangetsu101.pass.gitsync.SyncStatus
import com.zangetsu101.pass.passstore.PassStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncPanelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val gitSync = mockk<GitSync>()
    private val passStore = mockk<PassStore>()
    private val status = SyncStatus(localCommit = "local", upstreamCommit = "remote")

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { gitSync.syncState } returns MutableStateFlow(SyncState(status, error = SyncError.Conflict(emptyList())))
        coEvery { passStore.buildIndex() } returns emptyList()
    }

    @AfterEach
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful discard refreshes entries before closing the panel`() =
        runTest(dispatcher) {
            coEvery { gitSync.discardUnpushed(status) } returns Unit
            val viewModel = SyncPanelViewModel(gitSync, passStore)

            viewModel.discardUnpushed(status)
            assertTrue(viewModel.discardState.value.running)
            advanceUntilIdle()

            coVerify { passStore.buildIndex() }
            assertTrue(viewModel.discardState.value.done)
        }

    @Test
    fun `rejected discard stays in the panel with an actionable explanation`() =
        runTest(dispatcher) {
            coEvery { gitSync.discardUnpushed(status) } throws SyncError.RecoveryFailure("Sync again")
            val viewModel = SyncPanelViewModel(gitSync, passStore)

            viewModel.discardUnpushed(status)
            advanceUntilIdle()

            assertEquals(DiscardState(error = "Sync again"), viewModel.discardState.value)
            coVerify(exactly = 0) { passStore.buildIndex() }
        }
}
