// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.syncpanel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncError
import com.zangetsu101.pass.gitsync.SyncStatus
import com.zangetsu101.pass.passstore.PassStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscardState(
    val running: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class SyncPanelViewModel
    @Inject
    constructor(
        private val gitSync: GitSync,
        private val passStore: PassStore,
    ) : ViewModel() {
        val syncState = gitSync.syncState
        private val discard = MutableStateFlow(DiscardState())
        val discardState = discard.asStateFlow()

        fun discardUnpushed(expected: SyncStatus) {
            if (discard.value.running) return
            discard.value = DiscardState(running = true)
            viewModelScope.launch {
                try {
                    gitSync.discardUnpushed(expected)
                    try {
                        passStore.buildIndex()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Log.w("SyncPanel", "Couldn't refresh entries after discard", failure)
                    }
                    discard.value = DiscardState(done = true)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    discard.value =
                        DiscardState(
                            error = (error as? SyncError)?.message ?: "Couldn't finish discarding. Return home and check sync.",
                        )
                }
            }
        }
    }
