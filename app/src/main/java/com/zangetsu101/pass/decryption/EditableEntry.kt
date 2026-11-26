// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.decryption

import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import java.security.MessageDigest

internal class EntryEditSource(
    internal val entry: EditableEntry,
    internal val ciphertextDigest: ByteArray,
)

internal fun ByteArray.ciphertextDigest(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

internal class EditableEntry(
    plaintext: String,
) {
    private class Line(
        val text: String,
        val ending: String,
    )

    private val lines =
        linePattern
            .findAll(plaintext)
            .filter { it.value.isNotEmpty() }
            .map {
                Line(it.groupValues[1], it.groupValues[2])
            }.toList()
    private val defaultEnding = lines.firstOrNull { it.ending.isNotEmpty() }?.ending ?: "\n"
    private val firstIsOtp = lines.firstOrNull()?.let { isOtpLine(it.text) } == true
    private val finalEnding = lines.lastOrNull()?.ending.orEmpty()
    val secret = if (firstIsOtp) "" else lines.firstOrNull()?.text.orEmpty()
    private val noteLines = lines.drop(1).filterNot { isOtpLine(it.text) }
    val notes = noteLines.joinToString("\n") { it.text }
    val hasOtp = lines.any { isOtpLine(it.text) }

    fun render(
        secret: String,
        notes: String,
    ): String {
        val noteEdits = if (notes == this.notes) null else editNotes(notes)
        val result =
            buildList {
                if (!firstIsOtp || secret.isNotEmpty()) {
                    add(Line(secret, if (firstIsOtp) defaultEnding else lines.firstOrNull()?.ending.orEmpty()))
                }
                var noteIndex = 0
                for (line in lines.drop(if (firstIsOtp) 0 else 1)) {
                    if (isOtpLine(line.text) || noteEdits == null) {
                        add(line)
                    } else {
                        addAll(noteEdits[noteIndex++])
                    }
                }
                if (noteEdits != null) addAll(noteEdits.last())
            }
        return buildString {
            result.forEachIndexed { index, line ->
                append(line.text)
                append(if (index == result.lastIndex) finalEnding else line.ending.ifEmpty { defaultEnding })
            }
        }
    }

    private fun editNotes(notes: String): List<List<Line>> {
        val updated = if (notes.isEmpty()) emptyList() else notes.lines()

        fun asRawText(lines: List<String>): RawText {
            val text = if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")
            return RawText(text.toByteArray(Charsets.UTF_8))
        }
        val edits =
            HistogramDiff().diff(
                RawTextComparator.DEFAULT,
                asRawText(noteLines.map { it.text }),
                asRawText(updated),
            )

        // Assign replacements to their original note slots. Insertions next to surviving
        // notes follow those anchors, so an OTP line keeps its position between them.
        val replacements = List(noteLines.size + 1) { mutableListOf<Line>() }
        var unchangedFrom = 0
        for (edit in edits) {
            for (index in unchangedFrom until edit.beginA) replacements[index].add(noteLines[index])
            for (index in edit.beginB until edit.endB) {
                val slot = if (edit.lengthA == 0) edit.beginA else minOf(edit.beginA + index - edit.beginB, edit.endA - 1)
                val ending = if (edit.lengthA > index - edit.beginB) noteLines[slot].ending else defaultEnding
                replacements[slot].add(Line(updated[index], ending))
            }
            unchangedFrom = edit.endA
        }
        for (index in unchangedFrom until noteLines.size) replacements[index].add(noteLines[index])
        return replacements
    }

    private companion object {
        val linePattern = Regex("([^\\r\\n]*)(\\r\\n|\\r|\\n|$)")
    }
}
