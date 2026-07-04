// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.util

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle

/**
 * Marks a clip as sensitive so Android 13+ masks its value in the clipboard
 * preview UI (and hints keyboards/other apps not to surface it). No-op below
 * API 33, where the flag does not exist.
 */
fun ClipData.markSensitive(): ClipData =
    apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            description.extras =
                PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
        }
    }
