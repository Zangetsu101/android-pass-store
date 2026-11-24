// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.generator.DEFAULT_LENGTH
import com.zangetsu101.pass.generator.GeneratorSettings

internal data class CommandPart(
    val text: String,
    val flag: Boolean = false,
)

/** The `pass` command that does the same thing as a save. [charset] is the character set that no flag can express. */
internal class PassCommand private constructor(
    val parts: List<CommandPart>,
    val charset: String? = null,
) {
    val text get() = parts.joinToString(" ") { it.text }

    companion object {
        fun insert(
            path: String,
            multiline: Boolean,
            force: Boolean,
        ) = PassCommand(listOfNotNull(CommandPart("pass insert"), flag("-m", multiline), flag("-f", force), CommandPart(path)))

        fun generate(
            path: String,
            settings: GeneratorSettings,
            copy: Boolean,
            force: Boolean,
        ) = generate(path, settings, flag("-c", copy), flag("-f", force))

        /** `pass generate -i` replaces only line 1 of the entry and keeps the rest of the file. */
        fun generateInPlace(
            path: String,
            settings: GeneratorSettings,
        ) = generate(path, settings, CommandPart("-i", flag = true))

        private fun generate(
            path: String,
            settings: GeneratorSettings,
            first: CommandPart?,
            force: CommandPart? = null,
        ) = PassCommand(
            listOfNotNull(
                CommandPart("pass generate"),
                first,
                flag("-n", settings.symbols.isEmpty()),
                force,
                CommandPart(path),
                flag(settings.length.toString(), settings.length != DEFAULT_LENGTH),
            ),
            settings.charset,
        )

        private fun flag(
            text: String,
            on: Boolean,
        ) = if (on) CommandPart(text, flag = true) else null
    }
}
