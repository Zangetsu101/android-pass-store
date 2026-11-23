// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import android.util.Log
import com.zangetsu101.pass.decryption.DecryptedEntry
import com.zangetsu101.pass.decryption.LoginEditSource
import com.zangetsu101.pass.decryption.RawEntry
import com.zangetsu101.pass.decryption.ciphertextDigest
import com.zangetsu101.pass.decryption.parseCredentials
import com.zangetsu101.pass.di.AppBackgroundScope
import com.zangetsu101.pass.gitsync.EncryptedEdit
import com.zangetsu101.pass.gitsync.FileCommitInfo
import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginEntryWriter
    @Inject
    constructor(
        private val gitSync: GitSync,
        private val passStore: PassStore,
        private val keyStore: GpgKeyStore,
        @AppBackgroundScope private val scope: CoroutineScope,
    ) {
        private val failures = Channel<Unit>(Channel.CONFLATED)
        val pushFailures = failures.receiveAsFlow()

        internal suspend fun save(
            entry: PassEntry,
            source: LoginEditSource,
            password: String,
            notes: String,
        ) = scope
            .async {
                val plaintext = source.login.render(password, notes)
                lateinit var rawEntry: RawEntry
                val commit =
                    gitSync.commitEdit(entry.path) { root, original ->
                        if (!source.ciphertextDigest.contentEquals(original.ciphertextDigest())) {
                            throw LoginSaveError("entry changed on disk · reopen it to edit")
                        }
                        val encryption = LoginEncryption(keyStore.publicKeySource())
                        val ciphertext = encryption.encrypt(root, entry.path, plaintext)
                        rawEntry = RawEntry(plaintext, ciphertext.ciphertextDigest())
                        EncryptedEdit(ciphertext, encryption.author())
                    }
                pushInBackground(reportFailure = true)
                scope.launch { refreshIndex() }
                SavedLogin(DecryptedEntry(parseCredentials(plaintext, entry.username), rawEntry), commit)
            }.await()

        internal fun checkInsertPath(
            root: File,
            path: EntryPath,
        ): ByteArray? {
            val file = File(root, path.filePath)
            if (file != file.canonicalFile || (file.exists() && !file.isFile)) {
                throw LoginSaveError("path is already in use")
            }
            LoginEncryption(keyStore.publicKeySource()).checkRecipients(root, path.filePath)
            return if (file.isFile) file.readBytes().ciphertextDigest() else null
        }

        internal suspend fun insert(
            path: EntryPath,
            expectedDigest: ByteArray?,
            password: String,
            notes: String,
        ) = scope
            .async {
                path.error?.let { throw LoginSaveError(it) }
                if (password.isEmpty() || password.any(Char::isWhitespace)) throw LoginSaveError("enter a password")
                val plaintext = if (notes.isEmpty()) "$password\n" else "$password\n$notes"
                gitSync.commitInsert(path.filePath) { root, original ->
                    val actualDigest = original?.ciphertextDigest()
                    if (!expectedDigest.contentEquals(actualDigest)) {
                        throw LoginSaveError("entry changed on disk · check the path and try again")
                    }
                    val encryption = LoginEncryption(keyStore.publicKeySource())
                    EncryptedEdit(encryption.encrypt(root, path.filePath, plaintext), encryption.author())
                }
                pushInBackground(reportFailure = false)
                refreshIndex()
            }.await()

        private fun pushInBackground(reportFailure: Boolean) {
            scope.launch {
                try {
                    gitSync.push()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (reportFailure) failures.trySend(Unit)
                }
            }
        }

        private suspend fun refreshIndex() {
            try {
                passStore.buildIndex()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("LoginEntryWriter", "Could not refresh the entry index after saving", error)
            }
        }
    }

internal class SavedLogin(
    val entry: DecryptedEntry,
    val commit: FileCommitInfo,
)

internal class LoginSaveError(
    message: String,
) : Exception(message)
