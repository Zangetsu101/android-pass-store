package com.example.pass.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.pass.keymanagement.KeyManagement
import com.example.pass.keymanagement.isBiometricAvailable
import com.example.pass.passstore.PassEntry
import com.example.pass.passstore.PassStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavViewModel
    @Inject
    constructor(
        private val keyManagement: KeyManagement,
        private val passStore: PassStore,
    ) : ViewModel() {
        fun requiresSessionStart(context: Context): Boolean = isBiometricAvailable(context) && !keyManagement.isSessionActive()

        fun findEntry(path: String): PassEntry? = passStore.index.value.firstOrNull { it.path == path }
    }
