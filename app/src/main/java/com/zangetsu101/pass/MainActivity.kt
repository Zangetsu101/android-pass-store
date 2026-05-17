package com.zangetsu101.pass

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.navigation.PassDroidNavHost
import com.zangetsu101.pass.preferences.AppPreferences
import com.zangetsu101.pass.ui.theme.PassTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PassTheme {
                PassDroidNavHost(appPreferences)
            }
        }
    }
}
