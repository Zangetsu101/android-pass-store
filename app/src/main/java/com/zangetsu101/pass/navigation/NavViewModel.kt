package com.zangetsu101.pass.navigation

import androidx.lifecycle.ViewModel
import com.zangetsu101.pass.keymanagement.SessionOperations
import com.zangetsu101.pass.keymanagement.SessionState
import com.zangetsu101.pass.passstore.PassEntry
import com.zangetsu101.pass.passstore.PassStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavViewModel
    @Inject
    constructor(
        private val sessionOperations: SessionOperations,
        private val passStore: PassStore,
    ) : ViewModel() {
        fun requiresSessionStart(): Boolean = sessionOperations.sessionState.value !is SessionState.Active

        fun findEntry(path: String): PassEntry? = passStore.index.value.firstOrNull { it.path == path }
    }
