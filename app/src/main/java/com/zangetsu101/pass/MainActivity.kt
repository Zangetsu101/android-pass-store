// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.passstore.LoginEntryWriter
import com.zangetsu101.pass.navigation.PassAndroidNavHost
import com.zangetsu101.pass.preferences.AppPreferences
import com.zangetsu101.pass.ui.theme.PassTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var loginWriter: LoginEntryWriter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PassTheme {
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(loginWriter) {
                    loginWriter.pushFailures.collect {
                        snackbar.showSnackbar("saved locally · push failed")
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    PassAndroidNavHost(appPreferences)
                    SnackbarHost(
                        hostState = snackbar,
                        modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            }
        }
    }
}
