// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

/** Where the password on line 1 came from. It selects the commit message. */
enum class PasswordSource { Given, Generated }

/** The messages that `pass insert` and `pass generate` use. [name] is the entry path without `.gpg`. */
internal fun insertCommitMessage(
    name: String,
    source: PasswordSource,
) = when (source) {
    PasswordSource.Given -> "Add given password for $name to store."
    PasswordSource.Generated -> "Add generated password for $name."
}

/** The messages that `pass edit` and `pass generate -i` use. [name] is the entry path without `.gpg`. */
internal fun editCommitMessage(
    name: String,
    source: PasswordSource,
) = when (source) {
    PasswordSource.Given -> "Edit password for $name using editor."
    PasswordSource.Generated -> "Replace generated password for $name."
}
