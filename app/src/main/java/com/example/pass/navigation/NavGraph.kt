package com.example.pass.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

@Serializable object Onboarding
@Serializable object EntryBrowser
@Serializable object SyncPanel
@Serializable object Settings

@Composable
fun PassDroidNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Onboarding) {
        composable<Onboarding> {}
        composable<EntryBrowser> {}
        composable<SyncPanel> {}
        composable<Settings> {}
    }
}
