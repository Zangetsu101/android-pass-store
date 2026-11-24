// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.zangetsu101.pass.decryption.parseCredentials
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35])
class LoginFillTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val usernameId = View(context).autofillId
    private val passwordId = View(context).autofillId

    @Test
    fun `login fill writes the username line and not the file name`() {
        val credentials = parseCredentials("s3cr3t\nurl: https://github.com\nlogin: dave", fileUsername = "github.com")

        val fill = loginFill(credentials, usernameId, passwordId)

        assertEquals(
            listOf(usernameId to "dave", passwordId to "s3cr3t"),
            fill.map { (id, value) -> id to value.textValue.toString() },
        )
    }

    @Test
    fun `login fill skips a field that the form does not have`() {
        val credentials = parseCredentials("s3cr3t", fileUsername = "alice")

        assertEquals(listOf(passwordId), loginFill(credentials, null, passwordId).map { it.first })
    }
}
