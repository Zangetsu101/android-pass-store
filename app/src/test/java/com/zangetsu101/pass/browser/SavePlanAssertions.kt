// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.passstore.PasswordSource
import org.junit.jupiter.api.Assertions.assertEquals

/** The preview and the save must agree, so a test checks the three parts of a plan together. */
internal fun assertPlan(
    plan: SavePlan,
    command: String,
    passwordSource: PasswordSource,
    secret: String,
) {
    assertEquals(command, plan.command.parts.joinToString(" ") { it.text })
    assertEquals(passwordSource, plan.passwordSource)
    assertEquals(secret, plan.secret)
}
