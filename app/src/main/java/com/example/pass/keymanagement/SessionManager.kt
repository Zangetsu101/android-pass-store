package com.example.pass.keymanagement

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.fragment.app.FragmentActivity
import com.example.pass.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private const val SESSION_PASSPHRASE_BLOB = "session.enc"
private const val SESSION_KEY_ALIAS = "passdroid_session_key"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12

@Singleton
class SessionManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appPreferences: AppPreferences,
    ) : SessionOperations {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var timeoutJob: Job? = null

        private val _sessionState = MutableStateFlow<SessionState>(SessionState.Inactive(EndReason.REBOOT))
        override val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

        private fun keysDir(): File = File(context.filesDir, "keys").also { it.mkdirs() }

        override suspend fun createSession(passphrase: String) {
            withContext(Dispatchers.IO) {
                val sessionKey = createSessionKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, sessionKey)
                val iv = cipher.iv
                val ciphertext = cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8))
                File(keysDir(), SESSION_PASSPHRASE_BLOB).writeBytes(iv + ciphertext)
            }
            _sessionState.value = SessionState.Active
            touchSession()
        }

        override fun endSession(reason: EndReason) {
            timeoutJob?.cancel()
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(SESSION_KEY_ALIAS)) {
                keyStore.deleteEntry(SESSION_KEY_ALIAS)
            }
            File(keysDir(), SESSION_PASSPHRASE_BLOB).delete()
            _sessionState.value = SessionState.Inactive(reason)
        }

        override fun touchSession() {
            timeoutJob?.cancel()
            timeoutJob =
                scope.launch {
                    val timeoutMinutes = appPreferences.sessionTimeoutMinutes.first()
                    val timeoutMs = timeoutMinutes * 60_000L
                    if (timeoutMs <= 0L) return@launch
                    delay(timeoutMs)
                    endSession(EndReason.TIMEOUT)
                }
        }

        override fun isSessionActive(): Boolean {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            return keyStore.containsAlias(SESSION_KEY_ALIAS)
        }

        override suspend fun getPassphrase(activity: FragmentActivity): String {
            val (iv, ciphertext) =
                withContext(Dispatchers.IO) {
                    val blob = File(keysDir(), SESSION_PASSPHRASE_BLOB).readBytes()
                    blob.copyOfRange(0, GCM_IV_BYTES) to blob.copyOfRange(GCM_IV_BYTES, blob.size)
                }
            withContext(Dispatchers.Main) { showBiometricPrompt(activity) }
            return withContext(Dispatchers.IO) { decryptBlob(iv, ciphertext) }
        }

        private fun decryptBlob(
            iv: ByteArray,
            ciphertext: ByteArray,
        ): String {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val sessionKey = keyStore.getKey(SESSION_KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }

        private fun createSessionKey(): SecretKey {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(SESSION_KEY_ALIAS)) {
                keyStore.deleteEntry(SESSION_KEY_ALIAS)
            }
            val spec =
                KeyGenParameterSpec
                    .Builder(
                        SESSION_KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build()
            return KeyGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                .apply { init(spec) }
                .generateKey()
        }
    }
