package com.example.pass.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.pass.keymanagement.SessionOperations
import com.example.pass.keymanagement.isBiometricAvailable
import com.example.pass.passstore.PassEntry
import com.example.pass.passstore.PassStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavViewModel
    @Inject
    constructor(
        private val sessionOperations: SessionOperations,
        private val passStore: PassStore,
    ) : ViewModel() {
        fun requiresSessionStart(): Boolean = !sessionOperations.isSessionActive()

        fun findEntry(path: String): PassEntry? = passStore.index.value.firstOrNull { it.path == path }
    }
