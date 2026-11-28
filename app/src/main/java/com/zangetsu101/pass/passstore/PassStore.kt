// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import kotlinx.coroutines.flow.StateFlow

interface PassStore {
    val index: StateFlow<List<PassEntry>>
    val folders: StateFlow<Set<String>>

    suspend fun readEntry(filePath: String): PassEntry?

    suspend fun buildIndex(): List<PassEntry>

    fun search(query: String): List<PassEntry>

    fun resolve(domain: String): List<PassEntry>

    fun resolveByPackage(packageName: String): List<PassEntry>
}
