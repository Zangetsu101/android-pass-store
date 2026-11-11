// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.autofill

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zangetsu101.pass.passstore.PassEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AutofillHistoryStore

/**
 * Remembers the entry that the user filled last on each fill target.
 * A fill target is the web domain of a browser form, or the package name of a native app.
 *
 * The history holds entry paths only. It changes the order of suggestions and never adds one.
 * A fill must work without the history, so [record] and [rank] ignore an error of the store.
 */
@Singleton
class AutofillHistory
    @Inject
    constructor(
        @AutofillHistoryStore private val store: DataStore<Preferences>,
    ) {
        suspend fun record(
            fillTarget: String,
            entry: PassEntry,
        ) {
            try {
                store.edit {
                    it[targetKey(fillTarget, entry.isCard)] = entry.path
                    if (entry.isCard) it[KEY_LAST_CARD] = entry.path
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        /**
         * Sorts [candidates] for [fillTarget]. The entry that was filled last on the target comes first.
         * The card that was filled last on any target comes second. The other entries keep their order.
         */
        suspend fun rank(
            fillTarget: String,
            candidates: List<PassEntry>,
        ): List<PassEntry> {
            val history =
                try {
                    withTimeoutOrNull(READ_TIMEOUT_MS) { store.data.first() }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                } ?: return candidates
            val lastCard = history[KEY_LAST_CARD]
            return candidates.sortedBy {
                when (it.path) {
                    history[targetKey(fillTarget, it.isCard)] -> 0
                    lastCard -> 1
                    else -> 2
                }
            }
        }

        suspend fun clear() {
            store.edit { it.clear() }
        }

        // A login and a card on one target have separate keys, so that one does not replace the other.
        private fun targetKey(
            fillTarget: String,
            isCard: Boolean,
        ) = stringPreferencesKey("${if (isCard) "card" else "login"}:$fillTarget")

        private companion object {
            val KEY_LAST_CARD = stringPreferencesKey("last_card")

            // The system waits only a short time for the answer to a fill request.
            const val READ_TIMEOUT_MS = 1_000L
        }
    }
