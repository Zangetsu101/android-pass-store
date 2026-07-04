// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.Context
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncResult
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.passstore.PassStore
import com.zangetsu101.pass.preferences.AppPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EntryBrowserViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val passStore = mockk<PassStore>()
    private val gitSync = mockk<GitSync>()
    private val appPreferences = mockk<AppPreferences>()
    private val context = mockk<Context>(relaxed = true)

    private val fakeEntry =
        PassEntry(
            path = "test/secret.gpg",
            domain = "example.com",
            username = "user@example.com",
            encryptedFile = File("test/secret.gpg"),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { appPreferences.defaultViewTree } returns flowOf(true)
        every { passStore.index } returns MutableStateFlow(emptyList())
        every { passStore.buildIndex() } returns emptyList()
        every { passStore.search(any()) } returns emptyList()
        coEvery { gitSync.pull() } returns SyncResult(emptyList(), emptyList(), Instant.now())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() =
        EntryBrowserViewModel(
            context = context,
            passStore = passStore,
            gitSync = gitSync,
            appPreferences = appPreferences,
        )

    @Test
    fun `init - defaultViewTree pref reflected in state`() =
        runTest(testDispatcher) {
            every { appPreferences.defaultViewTree } returns flowOf(false)
            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(false, vm.state.value.treeView)
        }

    @Test
    fun `init - passStore index updates state entries`() =
        runTest(testDispatcher) {
            every { passStore.index } returns MutableStateFlow(listOf(fakeEntry))
            val vm = createViewModel()
            advanceUntilIdle()
            assertEquals(listOf(fakeEntry), vm.state.value.entries)
        }

    @Test
    fun `toggleView flips treeView and clears collapsedDirs`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()
            assertTrue(vm.state.value.treeView) // defaultViewTree = true from setUp

            vm.toggleDir("dir/a")
            assertEquals(setOf("dir/a"), vm.state.value.collapsedDirs)

            vm.toggleView()

            assertFalse(vm.state.value.treeView)
            assertEquals(emptySet<String>(), vm.state.value.collapsedDirs)
        }

    @Test
    fun `toggleDir adds on first call and removes on second`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            vm.toggleDir("subdir")
            assertEquals(setOf("subdir"), vm.state.value.collapsedDirs)
            vm.toggleDir("subdir")
            assertEquals(emptySet<String>(), vm.state.value.collapsedDirs)
        }

    @Test
    fun `pull sets syncing true then false and calls gitSync pull`() =
        runTest(testDispatcher) {
            val vm = createViewModel()
            advanceUntilIdle()

            vm.pull()
            assertTrue(vm.state.value.syncing)

            advanceUntilIdle()
            assertFalse(vm.state.value.syncing)
            coVerify { gitSync.pull() }
        }

    @Test
    fun `pull while already syncing is no-op`() =
        runTest(testDispatcher) {
            val pullLatch = CompletableDeferred<SyncResult>()
            coEvery { gitSync.pull() } coAnswers { pullLatch.await() }

            val vm = createViewModel()
            advanceUntilIdle()

            vm.pull()
            assertTrue(vm.state.value.syncing)

            vm.pull() // guard: syncing == true, returns immediately

            pullLatch.complete(SyncResult(emptyList(), emptyList(), Instant.now()))
            advanceUntilIdle()

            coVerify(exactly = 1) { gitSync.pull() }
        }

    @Test
    fun `pull exception is swallowed and syncing resets to false`() =
        runTest(testDispatcher) {
            coEvery { gitSync.pull() } throws RuntimeException("network error")

            val vm = createViewModel()
            advanceUntilIdle()

            vm.pull()
            advanceUntilIdle()

            assertFalse(vm.state.value.syncing)
        }
}