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

data class Task(val name: String, val completed: Int, val total: Int)

data class CloneProgressUiState(
    val cloning: Boolean = false,
    val cloneError: String? = null,
    val cloneComplete: Boolean = false,
    val tasks: List<Task> = emptyList(),
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
                tasks = emptyList(),
            )
        }
        cloneJob = viewModelScope.launch {
            try {
                val repoDir = Paths.get(context.filesDir.absolutePath, "repo")
                val sshKeyPair = if (remoteUrl.startsWith("git@") || remoteUrl.startsWith("ssh://")) {
                    withContext(Dispatchers.IO) { keyManagement.getSshKey() }
                } else null

                val monitor = object : ProgressMonitor {
                    override fun start(totalTasks: Int) {}
                    override fun beginTask(title: String, totalWork: Int) {
                        _state.update { it.copy(tasks = it.tasks + Task(title, 0, totalWork)) }
                    }
                    override fun update(completed: Int) {
                        _state.update {
                            val tasks = it.tasks.toMutableList()
                            if (tasks.isNotEmpty()) tasks[tasks.lastIndex] = tasks.last().copy(completed = tasks.last().completed + completed)
                            it.copy(tasks = tasks)
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
                val msg = if (cancelCloneFlag.get()) "Cancelled." else (e.message ?: "Clone failed")
                _state.update { it.copy(cloning = false, cloneError = msg) }
            }
        }
    }
}
