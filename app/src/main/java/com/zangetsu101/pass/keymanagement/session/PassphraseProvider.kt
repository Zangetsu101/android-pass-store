// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.session

import androidx.fragment.app.FragmentActivity

interface PassphraseProvider {
    @Throws(SessionError::class)
    suspend fun getPassphrase(activity: FragmentActivity): String
}
