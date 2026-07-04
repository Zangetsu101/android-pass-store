// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

data class Credentials(
    val password: CharArray,
    val notes: String,
    val username: String,
) {
    fun zero() {
        password.fill(' ')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Credentials) return false
        return password.contentEquals(other.password) && notes == other.notes && username == other.username
    }

    override fun hashCode(): Int {
        var result = password.contentHashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + username.hashCode()
        return result
    }
}