// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommitMessageTest {
    @Test
    fun `commit messages match the messages from pass`() {
        assertEquals("Add given password for dev/github to store.", insertCommitMessage("dev/github", PasswordSource.Given))
        assertEquals("Add generated password for dev/github.", insertCommitMessage("dev/github", PasswordSource.Generated))
        assertEquals("Edit password for dev/github using editor.", editCommitMessage("dev/github", PasswordSource.Given))
        assertEquals("Replace generated password for dev/github.", editCommitMessage("dev/github", PasswordSource.Generated))
    }
}
