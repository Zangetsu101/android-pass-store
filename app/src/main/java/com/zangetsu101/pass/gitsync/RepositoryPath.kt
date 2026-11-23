// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.gitsync

import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File

internal fun File.caseConflictMessage(path: String): String? {
    var parent = this
    for (segment in path.split('/')) {
        val names = parent.list() ?: return null
        if (segment !in names) {
            return names.firstOrNull { it.equals(segment, ignoreCase = true) }?.let { existing ->
                caseConflictMessage(segment, existing)
            }
        }
        parent = File(parent, segment)
    }
    return null
}

internal fun Repository.committedCaseConflictMessage(path: String): String? {
    val tree = resolve("HEAD^{tree}") ?: return null
    val segments = path.split('/')
    TreeWalk(this).use { walk ->
        walk.addTree(tree)
        for ((depth, segment) in segments.withIndex()) {
            var conflict: String? = null
            var exactMatch = false
            while (walk.next() && walk.depth == depth) {
                val existing = walk.nameString
                if (existing == segment) {
                    if (depth == segments.lastIndex || !walk.isSubtree) return null
                    walk.enterSubtree()
                    exactMatch = true
                    break
                }
                if (existing.equals(segment, ignoreCase = true)) conflict = existing
            }
            if (!exactMatch) return conflict?.let { caseConflictMessage(segment, it) }
        }
    }
    return null
}

private fun caseConflictMessage(
    requested: String,
    existing: String,
): String = "\"$requested\" conflicts with \"$existing\" on Windows"
