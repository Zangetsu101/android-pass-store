// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.passstore.PasswordSource

internal data class SavePlan(
    val command: PassCommand,
    val passwordSource: PasswordSource,
    val secret: String,
) {
    override fun toString() = "SavePlan"
}
