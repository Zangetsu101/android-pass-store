package com.example.pass.decryption

data class Credentials(
    val password: CharArray,
    val notes: String,
) {
    fun zero() {
        password.fill('\u0000')
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Credentials) return false
        return password.contentEquals(other.password) && notes == other.notes
    }

    override fun hashCode(): Int = 31 * password.contentHashCode() + notes.hashCode()
}

data class AutofillCredentials(
    val password: CharArray,
    val username: String,
) {
    fun zero() {
        password.fill('\u0000')
    }
}

fun CharArray.zero() = fill('\u0000')
