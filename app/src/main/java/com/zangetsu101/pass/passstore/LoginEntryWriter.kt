// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import android.util.Log
import com.zangetsu101.pass.decryption.Credentials
import com.zangetsu101.pass.decryption.EditableLogin
import com.zangetsu101.pass.decryption.LoginEditSource
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoginEntryWriter @Inject constructor(
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
    ) = scope.async {
        val plaintext = source.login.render(password, notes)
        lateinit var newSource: LoginEditSource
        val commit = gitSync.commitEdit(entry.path) { root, original ->
            if (!source.ciphertextDigest.contentEquals(original.ciphertextDigest())) {
                throw LoginSaveError("entry changed on disk · reopen it to edit")
            }
            val encryption = LoginEncryption(keyStore.publicKeySource())
            val ciphertext = encryption.encrypt(root, entry.path, plaintext)
            newSource = LoginEditSource(EditableLogin(plaintext), ciphertext.ciphertextDigest())
            EncryptedEdit(ciphertext, encryption.author())
        }
        scope.launch {
            try {
                gitSync.push()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures.trySend(Unit)
            }
        }
        scope.launch {
            try {
                passStore.buildIndex()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("LoginEntryWriter", "Couldn't refresh the entry index after saving", error)
            }
        }
        SavedLogin(parseCredentials(plaintext, entry.username, newSource), commit)
    }.await()
}

internal class SavedLogin(
    val credentials: Credentials,
    val commit: FileCommitInfo,
)

internal class LoginSaveError(message: String) : Exception(message)
