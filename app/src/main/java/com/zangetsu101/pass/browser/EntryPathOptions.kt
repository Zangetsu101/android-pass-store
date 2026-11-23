// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.browser

import com.zangetsu101.pass.passstore.EntryPath

internal class EntryPathOptions(
    private val paths: List<String>,
) {
    private val folderCounts =
        paths
            .flatMap { path ->
                generateSequence(path.substringBeforeLast('/', "")) { it.substringBeforeLast('/', "") }
                    .takeWhile { it.isNotEmpty() }
                    .toList()
            }.groupingBy { it }
            .eachCount()

    fun hint(
        path: EntryPath,
        folderExists: Boolean = false,
    ): String {
        val count = folderCounts[path.folder]
        return when {
            path.value.isEmpty() -> "name it like your store · ${paths.size} entries"
            path.folder.isEmpty() -> "new entry · ${paths.size} in store"
            count != null || folderExists -> "new in ${path.folder}/ · ${count ?: 0} entries there"
            else -> "new · creates folder ${path.folder}/"
        }
    }

    fun suggestions(path: EntryPath): List<PathSuggestion> {
        if (path.value.isEmpty()) return emptyList()
        val folders =
            folderCounts.keys.sorted().filter { it.startsWith(path.value, ignoreCase = true) }.map {
                PathSuggestion(it, folder = true, matchStart = 0, matchLength = path.value.length)
            }
        val entries =
            if (path.name.length < 2) {
                emptyList()
            } else {
                paths.sorted().mapNotNull { candidate ->
                    val name = candidate.substringAfterLast('/')
                    val match = name.indexOf(path.name, ignoreCase = true)
                    if (match < 0) null else PathSuggestion(candidate, false, candidate.length - name.length + match, path.name.length)
                }
            }
        return folders + entries
    }
}

internal data class PathSuggestion(
    val path: String,
    val folder: Boolean,
    val matchStart: Int,
    val matchLength: Int,
)
