// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.SyncError
import com.zangetsu101.pass.keymanagement.ssh.SshKeyStore
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.lib.ProgressMonitor
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

sealed class LogEntry {
    data class Simple(
        val text: String,
    ) : LogEntry()

    data class Progress(
        val name: String,
        val completed: Int,
        val total: Int,
    ) : LogEntry()
}

data class CloneProgressUiState(
    val cloning: Boolean = false,
    val cloneError: String? = null,
    val cloneComplete: Boolean = false,
    val logs: List<LogEntry> = emptyList(),
)

@HiltViewModel(assistedFactory = CloneProgressViewModel.Factory::class)
class CloneProgressViewModel
    @AssistedInject
    constructor(
        @Assisted val remoteUrl: String,
        @ApplicationContext private val context: Context,
        private val cryptoOperations: SshKeyStore,
        private val gitSync: GitSync,
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(remoteUrl: String): CloneProgressViewModel
        }

        private val _state = MutableStateFlow(CloneProgressUiState())
        val state: StateFlow<CloneProgressUiState> = _state.asStateFlow()

        private var cloneJob: Job? = null
        private val cancelCloneFlag = AtomicBoolean(false)

        init {
            startClone()
        }

        fun cancelClone() {
            cancelCloneFlag.set(true)
            cloneJob?.cancel()
        }

        fun retryClone() {
            startClone()
        }

        private fun startClone() {
            cancelCloneFlag.set(false)
            val repoName = remoteUrl.substringAfterLast('/').substringAfterLast(':').removeSuffix(".git")
            _state.update {
                it.copy(
                    cloning = true,
                    cloneError = null,
                    cloneComplete = false,
                    logs = listOf(LogEntry.Simple("Cloning into '$repoName'...")),
                )
            }
            cloneJob =
                viewModelScope.launch {
                    try {
                        val repoDir = Paths.get(context.filesDir.absolutePath, "repo")
                        val sshKeyPair =
                            if (remoteUrl.startsWith("git@") || remoteUrl.startsWith("ssh://")) {
                                withContext(Dispatchers.IO) { cryptoOperations.getSshKey() }
                            } else {
                                null
                            }

                        val monitor =
                            object : ProgressMonitor {
                                override fun start(totalTasks: Int) {}

                                override fun beginTask(
                                    title: String,
                                    totalWork: Int,
                                ) {
                                    _state.update { it.copy(logs = it.logs + LogEntry.Progress(title, 0, totalWork)) }
                                }

                                override fun update(completed: Int) {
                                    _state.update {
                                        val logs = it.logs.toMutableList()
                                        val last = logs.lastOrNull()
                                        if (last is LogEntry.Progress) {
                                            logs[logs.lastIndex] = last.copy(completed = last.completed + completed)
                                        }
                                        it.copy(logs = logs)
                                    }
                                }

                                override fun endTask() {}

                                override fun isCancelled(): Boolean = cancelCloneFlag.get()

                                override fun showDuration(enabled: Boolean) {}
                            }

                        gitSync.clone(remoteUrl, repoDir, sshKeyPair, monitor)
                        appPreferences.setRemoteUrl(remoteUrl)
                        _state.update { it.copy(cloning = false, cloneComplete = true) }
                    } catch (e: CancellationException) {
                        _state.update { it.copy(cloning = false) }
                        throw e
                    } catch (e: SyncError) {
                        _state.update { it.copy(cloning = false, cloneError = e.message) }
                    } catch (e: Exception) {
                        if (cancelCloneFlag.get()) {
                            _state.update { it.copy(cloning = false, logs = it.logs + LogEntry.Simple("Cancelled.")) }
                        } else {
                            _state.update { it.copy(cloning = false, cloneError = e.message ?: "Clone failed") }
                        }
                    }
                }
        }
    }