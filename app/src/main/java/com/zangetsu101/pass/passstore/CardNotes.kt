// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

internal fun parseCardNotes(notes: String): Map<String, String> =
    notes
        .lines()
        .mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx < 0) null else line.substring(0, idx).trim().lowercase() to line.substring(idx + 1).trim()
        }.toMap()
