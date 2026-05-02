package com.example.pass.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.gitsync.GitSync
import com.example.pass.gitsync.SyncError
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.preferences.AppPreferences
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

data class CloneProgressUiState(
    val cloning: Boolean = false,
    val cloneError: String? = null,
    val cloneComplete: Boolean = false,
    val cloneLog: List<String> = emptyList(),
    val cloneProgress: Float = 0f,
    val cloneTaskName: String? = null,
    val cloneTaskDone: Int = 0,
    val cloneTaskTotal: Int = 0,
    val cloneTotalTasks: Int = 0,
    val cloneCompletedTasks: Int = 0,
)

@HiltViewModel(assistedFactory = CloneProgressViewModel.Factory::class)
class CloneProgressViewModel @AssistedInject constructor(
    @Assisted val remoteUrl: String,
    @ApplicationContext private val context: Context,
    private val keyManagement: KeyManagement,
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
        _state.update {
            it.copy(
                cloning = true,
                cloneError = null,
                cloneComplete = false,
                cloneLog = emptyList(),
                cloneProgress = 0f,
                cloneTaskName = null,
                cloneTaskDone = 0,
                cloneTaskTotal = 0,
            )
        }
        cloneJob = viewModelScope.launch {
            try {
                val repoDir = Paths.get(context.filesDir.absolutePath, "repo")
                val sshKeyPair = if (remoteUrl.startsWith("git@") || remoteUrl.startsWith("ssh://")) {
                    withContext(Dispatchers.IO) { keyManagement.getSshKey() }
                } else null

                var taskTotal = 0
                var taskDone = 0
                val monitor = object : ProgressMonitor {
                    override fun start(totalTasks: Int) {
                        _state.update { it.copy(cloneTotalTasks = totalTasks, cloneCompletedTasks = 0) }
                    }
                    override fun beginTask(title: String, totalWork: Int) {
                        taskTotal = totalWork
                        taskDone = 0
                        _state.update {
                            it.copy(
                                cloneLog = it.cloneLog + title,
                                cloneTaskName = title,
                                cloneTaskDone = 0,
                                cloneTaskTotal = totalWork,
                            )
                        }
                    }
                    override fun update(completed: Int) {
                        taskDone += completed
                        val progress = if (taskTotal > 0) taskDone.toFloat() / taskTotal else 0f
                        _state.update {
                            it.copy(
                                cloneProgress = progress.coerceIn(0f, 1f),
                                cloneTaskDone = taskDone,
                            )
                        }
                    }
                    override fun endTask() {
                        _state.update {
                            it.copy(
                                cloneProgress = 0f,
                                cloneTaskDone = taskTotal,
                                cloneCompletedTasks = it.cloneCompletedTasks + 1,
                            )
                        }
                    }
                    override fun isCancelled(): Boolean = cancelCloneFlag.get()
                    override fun showDuration(enabled: Boolean) {}
                }

                gitSync.clone(remoteUrl, repoDir, sshKeyPair, monitor)
                appPreferences.setRemoteUrl(remoteUrl)
                _state.update { it.copy(cloning = false, cloneComplete = true) }
            } catch (e: CancellationException) {
                _state.update { it.copy(cloning = false, cloneLog = _state.value.cloneLog + "cancelled.") }
                throw e
            } catch (e: SyncError) {
                _state.update { it.copy(cloning = false, cloneError = e.message) }
            } catch (e: Exception) {
                val msg = if (cancelCloneFlag.get()) "Cancelled." else (e.message ?: "Clone failed")
                _state.update { it.copy(cloning = false, cloneError = msg) }
            }
        }
    }
}
