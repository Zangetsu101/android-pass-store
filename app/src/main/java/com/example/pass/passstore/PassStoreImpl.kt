package com.example.pass.passstore

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

            return search(q).take(5)
        }

        override fun resolveByPackage(packageName: String): List<PassEntry> {
            // com.github.android → github.com
            val parts = packageName.split(".")
            val domain = if (parts.size >= 2) "${parts[1]}.${parts[0]}" else packageName
            return resolve(domain)
        }

        private fun parseEntry(
            relativePath: String,
            file: File,
        ): PassEntry {
            val parts = relativePath.removeSuffix(".gpg").split("/")
            return when {
                parts.size >= 2 -> {
                    PassEntry(
                        path = relativePath,
                        domain = parts[parts.size - 2],
                        username = parts.last(),
                        encryptedFile = file,
                    )
                }

                else -> {
                    PassEntry(
                        path = relativePath,
                        domain = null,
                        username = parts.last(),
                        encryptedFile = file,
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
