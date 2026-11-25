// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

/** The number of notes lines that the new-entry form and the edit form show. Blank lines do not count. */
internal fun noteLineCount(notes: String): Int = notes.lines().count { it.isNotBlank() }
