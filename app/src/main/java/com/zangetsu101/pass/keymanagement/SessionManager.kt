package com.zangetsu101.pass.keymanagement

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.zangetsu101.pass.preferences.AppPreferences
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
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.inject.Inject
import javax.inject.Singleton

private const val SESSION_PASSPHRASE_BLOB = "session.enc"
private const val SESSION_KEY_ALIAS = "passdroid_session_key"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val RSA_KEY_SIZE = 2048
private const val RSA_MAX_PLAINTEXT_BYTES = RSA_KEY_SIZE / 8 - 2 * 32 - 2 // OAEP-SHA256: 190 bytes
private const val RSA_CIPHER = "RSA/ECB/OAEPPadding"

// OAEP hash = SHA-256; MGF1 hash = SHA-1 (Android Keystore hardware only supports SHA-1 for MGF1)
private val RSA_OAEP_SPEC = OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)

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
                if (passphrase.toByteArray(Charsets.UTF_8).size > RSA_MAX_PLAINTEXT_BYTES) {
                    throw SessionError.PassphraseTooLong()
                }
                createSessionKey()
                val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                val publicKey = keyStore.getCertificate(SESSION_KEY_ALIAS).publicKey
                val cipher = Cipher.getInstance(RSA_CIPHER)
                cipher.init(Cipher.ENCRYPT_MODE, publicKey, RSA_OAEP_SPEC)
                File(keysDir(), SESSION_PASSPHRASE_BLOB).writeBytes(
                    cipher.doFinal(passphrase.toByteArray(Charsets.UTF_8)),
                )
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
            val ciphertext =
                withContext(Dispatchers.IO) {
                    File(keysDir(), SESSION_PASSPHRASE_BLOB).readBytes()
                }
            val privateKey =
                withContext(Dispatchers.IO) {
                    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                    keyStore.getKey(SESSION_KEY_ALIAS, null) as PrivateKey
                }
            val cipher = Cipher.getInstance(RSA_CIPHER)
            try {
                cipher.init(Cipher.DECRYPT_MODE, privateKey, RSA_OAEP_SPEC)
            } catch (e: KeyPermanentlyInvalidatedException) {
                endSession(EndReason.BIOMETRIC_CHANGED)
                throw SessionError.NoActiveSession()
            }
            val result =
                withContext(Dispatchers.Main) {
                    showBiometricPromptWithCrypto(activity, BiometricPrompt.CryptoObject(cipher))
                }
            val authenticatedCipher =
                result.cryptoObject?.cipher
                    ?: throw BiometricAuthException("No authenticated cipher returned")
            return withContext(Dispatchers.IO) {
                String(authenticatedCipher.doFinal(ciphertext), Charsets.UTF_8)
            }
        }

        private fun createSessionKey() {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(SESSION_KEY_ALIAS)) {
                keyStore.deleteEntry(SESSION_KEY_ALIAS)
            }
            val spec =
                KeyGenParameterSpec
                    .Builder(SESSION_KEY_ALIAS, KeyProperties.PURPOSE_DECRYPT)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setKeySize(RSA_KEY_SIZE)
                    .setUserAuthenticationRequired(true)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                        }
                    }.build()
            KeyPairGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER)
                .apply { initialize(spec) }
                .generateKeyPair()
        }
    }
