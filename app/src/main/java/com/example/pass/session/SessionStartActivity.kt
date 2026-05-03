package com.example.pass.session

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.pass.ui.theme.PassTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SessionStartActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PassTheme {
                val vm: SessionStartViewModel = hiltViewModel()
                SessionStartScreen(
                    viewModel = vm,
                    onSuccess = { finish() },
                )
            }
        }
    }
}
