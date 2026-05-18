package com.zangetsu101.pass.keymanagement.ssh

import com.zangetsu101.pass.keymanagement.SshKeyPair

interface SshKeyOperations {
    fun generateSshKey(): String

    fun getSshKey(): SshKeyPair
}
