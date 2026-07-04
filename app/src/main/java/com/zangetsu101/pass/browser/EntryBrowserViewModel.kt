// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.passstore.PassStore
import com.zangetsu101.pass.preferences.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
    val syncing: Boolean = false,
)

@HiltViewModel
class EntryBrowserViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val passStore: PassStore,
        private val gitSync: GitSync,
        private val appPreferences: AppPreferences,
    ) : ViewModel() {
        private val _state = MutableStateFlow(EntryBrowserUiState())
        val state: StateFlow<EntryBrowserUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                appPreferences.defaultViewTree.collect { tree ->
                    _state.update { it.copy(treeView = tree) }
                }
            }
            viewModelScope.launch {
                passStore.index.collect { entries ->
                    updateDisplayedEntries(entries, _state.value.searchQuery)
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
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
                val collapsed =
                    if (dir in state.collapsedDirs) {
                        state.collapsedDirs - dir
                    } else {
                        state.collapsedDirs + dir
                    }
                state.copy(collapsedDirs = collapsed)
            }
        }

        fun pull() {
            if (_state.value.syncing) return
            _state.update { it.copy(syncing = true) }
            viewModelScope.launch {
                try {
                    gitSync.pull()
                    passStore.buildIndex()
                } catch (_: Exception) {
                } finally {
                    _state.update { it.copy(syncing = false) }
                }
            }
        }

        private fun updateDisplayedEntries(
            entries: List<PassEntry>,
            query: String,
        ) {
            val displayed = if (query.isEmpty()) entries else passStore.search(query)
            _state.update { it.copy(entries = displayed) }
        }
    }