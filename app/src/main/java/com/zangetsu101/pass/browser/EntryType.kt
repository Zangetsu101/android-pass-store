// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.passstore.PassEntry

/** The type of an entry. The file never stores it: a Card Entry is an entry in a Card Folder. */
internal enum class EntryType(
    /** The name of the secret on line 1. */
    val secretLabel: String,
) {
    Login("password"),
    Card("card number"),
}

internal val PassEntry.type get() = if (isCard) EntryType.Card else EntryType.Login
