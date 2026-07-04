// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement.gpg

import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.keymanagement.GpgPrivateKey
import com.zangetsu101.pass.keymanagement.session.SessionError

interface GpgKeyProvider {
    @Throws(SessionError::class)
    suspend fun getGpgKey(activity: FragmentActivity): GpgPrivateKey
}
