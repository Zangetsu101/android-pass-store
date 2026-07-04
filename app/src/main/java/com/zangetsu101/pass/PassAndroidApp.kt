// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PassAndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Global FLAG_SECURE: every window is treated as secure — blocked from
        // screenshots, screen recording, non-secure external displays, and the
        // recents thumbnail. Applied app-wide (not per-screen) because even the
        // entry list is sensitive metadata (which services the user has accounts
        // with). Registered here so future activities are covered automatically.
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    activity.window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                }

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityResumed(activity: Activity) {}

                override fun onActivityPaused(activity: Activity) {}

                override fun onActivityStopped(activity: Activity) {}

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) {}

                override fun onActivityDestroyed(activity: Activity) {}
            },
        )
    }
}
