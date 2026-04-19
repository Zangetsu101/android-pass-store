package com.example.pass.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pass.decryption.Credentials
import com.example.pass.decryption.Decryption
import com.example.pass.decryption.DecryptionError
import com.example.pass.passstore.PassEntry
import com.example.pass.passstore.PassStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryBrowserUiState(
    val entries: List<PassEntry> = emptyList(),
    val searchQuery: String = "",
    val treeView: Boolean = false,
    val collapsedDirs: Set<String> = emptySet(),
    val decryptingEntry: PassEntry? = null,
    val credentials: Credentials? = null,
    val decryptError: String? = null,
    val clipboardCopied: Boolean = false,
)

@HiltViewModel
class EntryBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val passStore: PassStore,
    private val decryption: Decryption,
) : ViewModel() {

    private val _state = MutableStateFlow(EntryBrowserUiState())
    val state: StateFlow<EntryBrowserUiState> = _state.asStateFlow()

    private var clipClearJob: Job? = null

    init {
        viewModelScope.launch {
            passStore.index.collect { entries ->
                updateDisplayedEntries(entries, _state.value.searchQuery)
            }
        }
        viewModelScope.launch {
            passStore.buildIndex()
        }
    }

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        val entries = if (query.isEmpty()) passStore.index.value else passStore.search(query)
        updateDisplayedEntries(entries, query)
    }

    fun toggleView() {
        _state.update { it.copy(treeView = !it.treeView, collapsedDirs = emptySet()) }
    }

    fun toggleDir(dir: String) {
        _state.update { state ->
            val collapsed = if (dir in state.collapsedDirs) {
                state.collapsedDirs - dir
            } else {
                state.collapsedDirs + dir
            }
            state.copy(collapsedDirs = collapsed)
        }
    }

    fun requestDecrypt(entry: PassEntry, activity: FragmentActivity) {
        _state.update { it.copy(decryptingEntry = entry, credentials = null, decryptError = null) }
        viewModelScope.launch {
            try {
                val creds = decryption.decrypt(entry, activity)
                _state.update { it.copy(credentials = creds) }
            } catch (e: DecryptionError) {
                _state.update { it.copy(decryptError = e.message ?: "Decryption failed", decryptingEntry = null) }
            } catch (e: Exception) {
                _state.update { it.copy(decryptError = e.message ?: "Decryption failed", decryptingEntry = null) }
            }
        }
    }

    fun copyPassword() {
        val creds = _state.value.credentials ?: return
        val password = String(creds.password)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("password", password))
        _state.update { it.copy(clipboardCopied = true) }

        clipClearJob?.cancel()
        clipClearJob = viewModelScope.launch {
            delay(45_000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            _state.update { it.copy(clipboardCopied = false) }
        }
    }

    fun dismissDetail() {
        _state.value.credentials?.zero()
        _state.update { it.copy(decryptingEntry = null, credentials = null, decryptError = null, clipboardCopied = false) }
        clipClearJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        _state.value.credentials?.zero()
    }

    private fun updateDisplayedEntries(entries: List<PassEntry>, query: String) {
        val displayed = if (query.isEmpty()) entries else passStore.search(query)
        _state.update { it.copy(entries = displayed) }
    }
}
