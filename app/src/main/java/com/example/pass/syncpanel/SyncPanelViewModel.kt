package com.example.pass.syncpanel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.gitsync.GitSync
import com.example.pass.gitsync.SyncStatus
import com.example.pass.passstore.PassStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class SyncPanelUiState(
    val lastSyncTime: Instant? = null,
    val remoteReachable: Boolean? = null,
    val pulling: Boolean = false,
    val pullError: String? = null,
    val pullSuccess: Boolean = false,
)

@HiltViewModel
class SyncPanelViewModel
    @Inject
    constructor(
        private val gitSync: GitSync,
        private val passStore: PassStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SyncPanelUiState())
        val state: StateFlow<SyncPanelUiState> = _state.asStateFlow()

        init {
            loadStatus()
        }

        fun loadStatus() {
            viewModelScope.launch {
                val status: SyncStatus =
                    runCatching { gitSync.syncStatus() }.getOrElse {
                        SyncStatus(lastSyncTime = null, localCommit = null, remoteReachable = false)
                    }
                _state.update {
                    it.copy(
                        lastSyncTime = status.lastSyncTime,
                        remoteReachable = status.remoteReachable,
                    )
                }
            }
        }

        fun pull() {
            _state.update { it.copy(pulling = true, pullError = null, pullSuccess = false) }
            viewModelScope.launch {
                try {
                    val result = gitSync.pull()
                    passStore.buildIndex()
                    _state.update { it.copy(pulling = false, pullSuccess = true, lastSyncTime = result.lastSyncTime) }
                } catch (e: Exception) {
                    _state.update { it.copy(pulling = false, pullError = e.message ?: "Pull failed") }
                    loadStatus()
                }
            }
        }
    }
