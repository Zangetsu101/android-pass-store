// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.generator

import java.util.Random

/** The password in an entry form, and the generator settings for it. */
internal data class PasswordDraft(
    val password: String,
    val settings: GeneratorSettings = GeneratorSettings(),
    /** False after the user types in the password. */
    val generated: Boolean = false,
) {
    val strength get() = if (generated) settings.strength() else typedPasswordStrength(password)

    fun type(text: String) = copy(password = text.filterNot(Char::isWhitespace), generated = false)

    fun regenerate(random: Random) = copy(password = settings.generate(random), generated = true)

    fun withSettings(
        settings: GeneratorSettings,
        random: Random,
    ) = copy(settings = settings).regenerate(random)

    override fun toString() = "PasswordDraft"

    companion object {
        fun generate(random: Random) = PasswordDraft("").regenerate(random)
    }
}
