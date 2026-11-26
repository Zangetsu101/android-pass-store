// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import com.zangetsu101.pass.decryption.Credentials

/** The values that a login fill writes. The username comes from the decrypted [credentials], so a Username Line wins. */
internal fun loginFill(
    credentials: Credentials,
    usernameId: AutofillId?,
    passwordId: AutofillId?,
): List<Pair<AutofillId, AutofillValue>> =
    listOfNotNull(
        usernameId?.let { it to AutofillValue.forText(credentials.username) },
        passwordId?.let { it to AutofillValue.forText(String(credentials.secret)) },
    )
