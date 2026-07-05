// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.keymanagement

import com.zangetsu101.pass.keymanagement.crypto.PlainCryptoStore
import com.zangetsu101.pass.keymanagement.gpg.GpgImportReaderImpl
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyInspector
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStoreImpl
import com.zangetsu101.pass.keymanagement.gpg.KeyImportError
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.EncryptionSubkeyValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.PassphraseProtectionValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.PrivateKeyMaterialValidation
import com.zangetsu101.pass.keymanagement.gpg.importvalidation.SubkeyValidityValidation
import com.zangetsu101.pass.keymanagement.session.SessionError
import com.zangetsu101.pass.validation.ValidationResult
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.pgpainless.PGPainless
import org.pgpainless.decryption_verification.ConsumerOptions
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.util.Date

private class InMemoryPlainCryptoStore : PlainCryptoStore {
    private var data: ByteArray? = null

    override fun store(data: ByteArray) {
        this.data = data.copyOf()
    }

    override fun get(): ByteArray = checkNotNull(data) { "Nothing stored" }

    override fun exists(): Boolean = data != null

    override fun delete() {
        data = null
    }
}

class GpgKeyStoreImplTest {
    private lateinit var gpgKeyStore: GpgKeyStoreImpl
    private lateinit var importReader: GpgImportReaderImpl
    private lateinit var inspector: GpgKeyInspector

    @BeforeEach
    fun setup() {
        importReader = GpgImportReaderImpl()
        inspector = GpgKeyInspector()
        gpgKeyStore = GpgKeyStoreImpl(InMemoryPlainCryptoStore(), importReader, inspector)
    }

    private fun importAndStore(armoredKey: String) {
        validateOrThrow(armoredKey)
        gpgKeyStore.storeImportedGpgKey(armoredKey)
    }

    private fun validateOrThrow(armoredKey: String) {
        val candidate = importReader.parseCandidate(armoredKey)
        val validations =
            listOf(
                EncryptionSubkeyValidation(inspector),
                SubkeyValidityValidation(inspector),
                PrivateKeyMaterialValidation(inspector),
                PassphraseProtectionValidation(),
            )
        validations.forEach { validation ->
            when (val result = validation.validate(candidate)) {
                ValidationResult.Passed, ValidationResult.Neutral -> Unit
                is ValidationResult.Failed -> throw result.error
            }
        }
    }

    private fun armoredProtectedKey(
        passphrase: String = "correct",
        uid: String = "Test <test@example.com>",
    ): String = PGPainless.getInstance().toAsciiArmor(PGPainless().generateKey().modernKeyRing(uid, passphrase))

    private fun armoredUnprotectedKey(): String =
        PGPainless.getInstance().toAsciiArmor(PGPainless().generateKey().modernKeyRing("Test <test@example.com>"))

    // importGpgKey

    @Test
    fun `importGpgKey with passphrase-protected key stores key`() {
        importAndStore(armoredProtectedKey())

        assertTrue(gpgKeyStore.exists())
    }

    @Test
    fun `importGpgKey with unprotected key throws NoPassphrase`() {
        assertThrows<KeyImportError.NoPassphrase> {
            importAndStore(armoredUnprotectedKey())
        }
    }

    @Test
    fun `importGpgKey with malformed text throws Malformed`() {
        assertThrows<KeyImportError.Malformed> {
            importAndStore("not a pgp key at all")
        }
    }

    @Test
    fun `importGpgKey with no encryption subkey throws NoEncryptionKey`() {
        val keyWithoutEncryption =
            armoredKeyWithEd25519AuthSubkey("correct", "NoEnc <noenc@example.com>", includeEncryption = false)
        assertThrows<KeyImportError.NoEncryptionKey> {
            importAndStore(keyWithoutEncryption)
        }
    }

    @Test
    fun `importGpgKey with encryption subkey and passphrase stores key`() {
        importAndStore(armoredKeyWithEd25519AuthSubkey("correct", "Enc <enc@example.com>"))

        assertTrue(gpgKeyStore.exists())
    }

    // armorGpgKey

    @Test
    fun `armorGpgKey with binary PGP bytes returns armored string and round-trip key IDs match`() {
        val key = PGPainless().generateKey().modernKeyRing("Test <test@example.com>")
        val originalRing = key.pgpSecretKeyRing
        val binaryBytes = ByteArrayOutputStream().also { originalRing.encode(it) }.toByteArray()

        val armored = importReader.armor(binaryBytes)

        assertTrue(armored.contains("BEGIN PGP PRIVATE KEY BLOCK"))
        val resultRing = PGPainless().readKey().parseKey(armored).pgpSecretKeyRing
        val originalId = originalRing.firstOrNull { it.isMasterKey }?.keyID
        val resultId = resultRing.firstOrNull { it.isMasterKey }?.keyID
        assertEquals(originalId, resultId)
    }

    @Test
    fun `armorGpgKey with malformed bytes throws Malformed`() {
        assertThrows<KeyImportError.Malformed> {
            importReader.armor("garbage bytes".toByteArray())
        }
    }

    // validatePassphrase

    @Test
    fun `validatePassphrase with correct passphrase does not throw`() {
        importAndStore(armoredProtectedKey("correct"))
        gpgKeyStore.validatePassphrase("correct")
    }

    @Test
    fun `validatePassphrase with wrong passphrase throws WrongPassphrase`() {
        importAndStore(armoredProtectedKey("correct"))
        assertThrows<SessionError.WrongPassphrase> {
            gpgKeyStore.validatePassphrase("wrong")
        }
    }

    @Test
    fun `validatePassphrase with no GPG key throws IllegalStateException`() {
        assertThrows<IllegalStateException> {
            gpgKeyStore.validatePassphrase("any")
        }
    }

    // loadAndUnlock

    @Test
    fun `loadAndUnlock with correct passphrase returns key usable for decryption`() {
        val passphrase = "correct"
        val protectedKey = PGPainless().generateKey().modernKeyRing("Test <test@example.com>", passphrase)
        importAndStore(PGPainless.getInstance().toAsciiArmor(protectedKey))

        val plaintext = "secret-test-content"
        val ciphertext =
            ByteArrayOutputStream()
                .also { buf ->
                    val encStream =
                        PGPainless()
                            .generateMessage()
                            .onOutputStream(buf)
                            .withOptions(
                                ProducerOptions.encrypt(
                                    EncryptionOptions.encryptCommunications().addRecipient(protectedKey),
                                ),
                            )
                    encStream.use { it.write(plaintext.toByteArray()) }
                }.toByteArray()

        val unlockedKey = gpgKeyStore.loadAndUnlock(passphrase)

        val output = ByteArrayOutputStream()
        val decryptStream =
            PGPainless()
                .processMessage()
                .onInputStream(ByteArrayInputStream(ciphertext))
                .withOptions(ConsumerOptions.get().addDecryptionKey(unlockedKey))
        decryptStream.use { it.copyTo(output) }

        assertEquals(plaintext, output.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun `loadAndUnlock with wrong passphrase throws WrongPassphrase`() {
        importAndStore(armoredProtectedKey("correct"))
        assertThrows<SessionError.WrongPassphrase> {
            gpgKeyStore.loadAndUnlock("wrong")
        }
    }

    // getGpgKeyInfo

    @Test
    fun `getGpgKeyInfo returns null when no key stored`() {
        assertNull(gpgKeyStore.getGpgKeyInfo())
    }

    @Test
    fun `getGpgKeyInfo returns keyId and uid with correct format`() {
        importAndStore(armoredProtectedKey("pass", "Alice <alice@example.com>"))

        val info = gpgKeyStore.getGpgKeyInfo()

        assertNotNull(info)
        requireNotNull(info)
        val (keyId, uid) = info
        assertTrue(keyId.matches(Regex("[0-9A-F]{4} [0-9A-F]{4}")), "keyId format mismatch: $keyId")
        assertTrue(uid.contains("alice@example.com"), "uid should contain email: $uid")
    }

    @Test
    fun `getGpgKeyInfo returns null for corrupted stored data`() {
        gpgKeyStore.store("corrupted content - not a valid pgp key".toByteArray())

        assertNull(gpgKeyStore.getGpgKeyInfo())
    }

    // findAuthSubkey

    @Test
    fun `findAuthSubkey returns null when no key stored`() {
        assertNull(gpgKeyStore.findAuthSubkey())
    }

    @Test
    fun `findAuthSubkey returns null when key has no auth subkey`() {
        importAndStore(armoredProtectedKey())

        assertNull(gpgKeyStore.findAuthSubkey())
    }

    @Test
    fun `findAuthSubkey returns AuthSubkeyInfo for key with ed25519 auth subkey`() {
        val passphrase = "testpass"
        val uid = "Test Auth <auth@example.com>"
        importAndStore(armoredKeyWithEd25519AuthSubkey(passphrase, uid))

        val info = gpgKeyStore.findAuthSubkey()

        assertNotNull(info)
        requireNotNull(info)
        assertTrue(info.sshPublicKey.startsWith("ssh-ed25519 "), "Expected openssh pubkey format")
        assertTrue(info.sshFingerprint.startsWith("SHA256:"), "Expected SHA256 fingerprint")
        assertEquals(uid, info.uid)
        assertTrue(info.created > 0)
    }

    @Test
    fun `findAuthSubkey picks newest when multiple auth subkeys present`() {
        val passphrase = "testpass"
        val armored = armoredKeyWithTwoEd25519AuthSubkeys(passphrase)
        importAndStore(armored)

        val info = gpgKeyStore.findAuthSubkey()

        assertNotNull(info)
    }

    // extractAuthSubkeySeed

    @Test
    fun `extractAuthSubkeySeed returns 32-byte seed for correct passphrase`() {
        val passphrase = "correct"
        val uid = "Seed Test <seed@example.com>"
        importAndStore(armoredKeyWithEd25519AuthSubkey(passphrase, uid))
        val authSubkey = checkNotNull(gpgKeyStore.findAuthSubkey())

        val seed = gpgKeyStore.extractAuthSubkeySeed(passphrase, authSubkey.keyId)

        assertEquals(32, seed.size, "Seed must be 32 bytes")
    }

    @Test
    fun `extractAuthSubkeySeed throws WrongPassphrase for incorrect passphrase`() {
        val uid = "Seed Test <seed@example.com>"
        importAndStore(armoredKeyWithEd25519AuthSubkey("correct", uid))
        val authSubkey = checkNotNull(gpgKeyStore.findAuthSubkey())

        assertThrows<SessionError.WrongPassphrase> {
            gpgKeyStore.extractAuthSubkeySeed("wrong", authSubkey.keyId)
        }
    }

    private fun armoredKeyWithEd25519AuthSubkey(
        passphrase: String,
        uid: String,
        includeEncryption: Boolean = true,
    ): String {
        val provider = BouncyCastleProvider()
        val kpg = KeyPairGenerator.getInstance("EdDSA", provider)
        kpg.initialize(256)
        val creationDate = Date()
        val primaryKp = JcaPGPKeyPair(PublicKeyAlgorithmTags.EDDSA_LEGACY, kpg.generateKeyPair(), creationDate)
        val authKp = JcaPGPKeyPair(PublicKeyAlgorithmTags.EDDSA_LEGACY, kpg.generateKeyPair(), creationDate)
        val sha1 =
            JcaPGPDigestCalculatorProviderBuilder()
                .setProvider(provider)
                .build()
                .get(HashAlgorithmTags.SHA1)
        val keyEncryptor =
            JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                .setProvider(provider)
                .build(passphrase.toCharArray())
        val signerBuilder =
            JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.EDDSA_LEGACY, HashAlgorithmTags.SHA256)
                .setProvider(provider)
        val gen =
            PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                primaryKp,
                uid,
                sha1,
                null,
                null,
                signerBuilder,
                keyEncryptor,
            )
        val authHashed =
            PGPSignatureSubpacketGenerator()
                .apply {
                    setKeyFlags(false, KeyFlags.AUTHENTICATION)
                }.generate()
        gen.addSubKey(authKp, authHashed, null, signerBuilder)
        if (includeEncryption) addEncryptionSubkey(gen, provider, creationDate)
        return PGPainless.asciiArmor(gen.generateSecretKeyRing())
    }

    // Real-world GPG keys used with pass always carry an encryption [E] subkey; import now
    // requires one, so test fixtures must include it to stay realistic.
    private fun addEncryptionSubkey(
        gen: PGPKeyRingGenerator,
        provider: BouncyCastleProvider,
        creationDate: Date,
    ) {
        val ecdhKpg = KeyPairGenerator.getInstance("X25519", provider)
        val encKp = JcaPGPKeyPair(PublicKeyAlgorithmTags.ECDH, ecdhKpg.generateKeyPair(), creationDate)
        val encHashed =
            PGPSignatureSubpacketGenerator()
                .apply {
                    setKeyFlags(false, KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE)
                }.generate()
        // 3-arg overload signs the binding with the master key (the 4-arg form signs with the
        // subkey itself, which an encryption-only ECDH key can't do).
        gen.addSubKey(encKp, encHashed, null)
    }

    private fun armoredKeyWithTwoEd25519AuthSubkeys(passphrase: String): String {
        val provider = BouncyCastleProvider()
        val kpg = KeyPairGenerator.getInstance("EdDSA", provider)
        kpg.initialize(256)
        val uid = "Multi Auth <multi@example.com>"
        val date1 = Date(1_000_000L)
        val date2 = Date(2_000_000L)
        val primaryKp = JcaPGPKeyPair(PublicKeyAlgorithmTags.EDDSA_LEGACY, kpg.generateKeyPair(), date1)
        val auth1Kp = JcaPGPKeyPair(PublicKeyAlgorithmTags.EDDSA_LEGACY, kpg.generateKeyPair(), date1)
        val auth2Kp = JcaPGPKeyPair(PublicKeyAlgorithmTags.EDDSA_LEGACY, kpg.generateKeyPair(), date2)
        val sha1 =
            JcaPGPDigestCalculatorProviderBuilder()
                .setProvider(provider)
                .build()
                .get(HashAlgorithmTags.SHA1)
        val keyEncryptor =
            JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                .setProvider(provider)
                .build(passphrase.toCharArray())
        val signerBuilder =
            JcaPGPContentSignerBuilder(PublicKeyAlgorithmTags.EDDSA_LEGACY, HashAlgorithmTags.SHA256)
                .setProvider(provider)
        val gen =
            PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                primaryKp,
                uid,
                sha1,
                null,
                null,
                signerBuilder,
                keyEncryptor,
            )
        val authHashed =
            PGPSignatureSubpacketGenerator()
                .apply {
                    setKeyFlags(false, KeyFlags.AUTHENTICATION)
                }.generate()
        gen.addSubKey(auth1Kp, authHashed, null, signerBuilder)
        gen.addSubKey(auth2Kp, authHashed, null, signerBuilder)
        addEncryptionSubkey(gen, provider, date1)
        return PGPainless.asciiArmor(gen.generateSecretKeyRing())
    }
}
