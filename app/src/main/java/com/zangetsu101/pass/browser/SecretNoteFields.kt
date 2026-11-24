// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.decryption.NoteField

/** Entry detail blurs the value of a secret Note Field until the user taps it. */
internal val NoteField.secret: Boolean
    get() = key.lowercase().let { it in secretKeys || "password" in it }

private val secretKeys = setOf("pin", "cvv", "secret", "answer", "recovery")
