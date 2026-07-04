// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.text.similarity.LevenshteinDistance
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PassStoreImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PassStore {
        private val _index = MutableStateFlow<List<PassEntry>>(emptyList())
        override val index: StateFlow<List<PassEntry>> = _index.asStateFlow()

        private val repoDir get() = File(context.filesDir, "repo")
        private val levenshtein = LevenshteinDistance.getDefaultInstance()

        override fun buildIndex(): List<PassEntry> {
            val entries =
                repoDir
                    .walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".gpg") }
                    .map { file ->
                        val relativePath = file.relativeTo(repoDir).path.replace(File.separatorChar, '/')
                        parseEntry(relativePath, file)
                    }.toList()
            _index.value = entries
            return entries
        }

        override fun search(query: String): List<PassEntry> {
            val q = query.lowercase()
            return _index.value
                .map { entry -> entry to entryScore(entry, q) }
                .sortedBy { (_, score) -> score }
                .map { (entry, _) -> entry }
        }

        override fun resolve(domain: String): List<PassEntry> {
            val q = domain.lowercase()
            val entries = _index.value

            val exact = entries.filter { it.domain?.lowercase() == q }
            if (exact.isNotEmpty()) return exact

            val subdomain =
                entries.filter { entry ->
                    entry.domain?.lowercase()?.let { d -> q.endsWith(".$d") || q == d } == true
                }
            if (subdomain.isNotEmpty()) return subdomain

            return emptyList()
        }

        override fun resolveByPackage(packageName: String): List<PassEntry> {
            // Reverse package → domain: com.github.android → android.github.com
            // Match any entry domain contained within reversed package (dot-bounded).
            // bd.com.ucb.unet → unet.ucb.com.bd — contains ".ucb.com" and ".unet.ucb.com" ✓
            val reversed =
                packageName
                    .split(".")
                    .reversed()
                    .joinToString(".")
                    .lowercase()
            return _index.value
                .filter { entry ->
                    val d = entry.domain?.lowercase() ?: return@filter false
                    reversed == d || reversed.contains(".$d") || reversed.startsWith("$d.")
                }.sortedByDescending { it.domain?.length ?: 0 }
        }

        private fun parseEntry(
            relativePath: String,
            file: File,
        ): PassEntry {
            val pathNoExt = relativePath.removeSuffix(".gpg")
            val isCard = pathNoExt.startsWith("cards/") || pathNoExt.startsWith("credit-cards/")
            val parts = pathNoExt.split("/").let { if (isCard) it.drop(1) else it }
            return when {
                parts.size >= 2 -> {
                    PassEntry(
                        path = relativePath,
                        domain = parts[parts.size - 2],
                        username = parts.last(),
                        encryptedFile = file,
                        isCard = isCard,
                    )
                }

                else -> {
                    PassEntry(
                        path = relativePath,
                        domain = null,
                        username = parts.last(),
                        encryptedFile = file,
                        isCard = isCard,
                    )
                }
            }
        }

        private fun fieldScore(
            query: String,
            field: String,
        ): Int =
            when {
                field == query -> 0
                field.startsWith(query) -> 1
                field.contains(query) -> 2
                else -> 1000 + levenshtein.apply(query, field)
            }

        private fun entryScore(
            entry: PassEntry,
            query: String,
        ): Int {
            val candidates =
                listOfNotNull(
                    entry.username.lowercase(),
                    entry.domain?.lowercase(),
                    entry.path.lowercase().removeSuffix(".gpg"),
                )
            return candidates.minOfOrNull { fieldScore(query, it) } ?: Int.MAX_VALUE
        }
    }
