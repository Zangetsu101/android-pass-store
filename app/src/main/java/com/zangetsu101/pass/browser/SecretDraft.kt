// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.generator.PasswordDraft

/** Line 1 in the entry editor: a password with its generator settings, or the digits of a card number. */
internal sealed interface SecretDraft {
    val secret: String

    data class Password(
        val draft: PasswordDraft,
    ) : SecretDraft {
        override val secret get() = draft.password

        override fun toString() = "SecretDraft.Password"
    }

    data class CardNumber(
        val digits: String,
    ) : SecretDraft {
        override val secret get() = digits

        override fun toString() = "SecretDraft.CardNumber"
    }
}
