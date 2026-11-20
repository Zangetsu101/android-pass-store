// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.passstore

import com.zangetsu101.pass.keymanagement.gpg.PublicKeySource
import org.bouncycastle.openpgp.api.OpenPGPCertificate
import org.eclipse.jgit.lib.PersonIdent
import org.pgpainless.PGPainless
import org.pgpainless.algorithm.KeyFlag
import org.pgpainless.encryption_signing.EncryptionOptions
import org.pgpainless.encryption_signing.ProducerOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

internal class LoginEncryption(private val key: PublicKeySource) {
    private val certificate = OpenPGPCertificate(key.certificate)
    private val uidPattern = Regex("^(.*?)\\s*<([^<>\\s]+@[^<>\\s]+)>$")

    fun author(): PersonIdent {
        val uid = key.certificate.publicKey.userIDs.asSequence().firstOrNull().orEmpty()
        val identity = uidPattern.matchEntire(uid)
        val name = identity?.groupValues?.get(1)?.trim().orEmpty()
        if (name.isEmpty() || identity == null) throw LoginSaveError("first GPG user ID needs a name and email")
        return PersonIdent(name, identity.groupValues[2])
    }

    fun encrypt(root: File, path: String, plaintext: String): ByteArray {
        val pgp = PGPainless.getInstance()
        val info = pgp.inspect(certificate)
        val keys = (
            info.getKeysWithKeyFlag(KeyFlag.ENCRYPT_COMMS) + info.getKeysWithKeyFlag(KeyFlag.ENCRYPT_STORAGE)
        ).map { it.pgpPublicKey } + key.certificate.publicKey
        val keyIds = keys.map { "%016X".format(it.keyID) }
        val fingerprints = keys.map { publicKey -> publicKey.fingerprint.joinToString("") { "%02X".format(it) } }
        val emails = info.validUserIds.asSequence()
            .map { uidPattern.matchEntire(it)?.groupValues?.get(2) ?: it }
            .filter { '@' in it && it.none(Char::isWhitespace) }
            .toList()
        val recipientFile = generateSequence(File(root, path).parentFile) {
            if (it == root) null else it.parentFile
        }.map { File(it, ".gpg-id") }.firstOrNull { Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
            ?: throw LoginSaveError("missing .gpg-id · cannot encrypt")
        if (!recipientFile.isFile || recipientFile != recipientFile.canonicalFile) {
            throw LoginSaveError("cannot read .gpg-id")
        }
        val recipients = recipientFile.readLines().map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }.distinct()
        if (recipients.isEmpty()) throw LoginSaveError("empty .gpg-id · cannot encrypt")
        val unknown = recipients.count { recipient ->
            val hex = recipient.replaceFirst(Regex("^0x", RegexOption.IGNORE_CASE), "")
            (keyIds + fingerprints).none { it.equals(hex, ignoreCase = true) } &&
                emails.none { it.equals(recipient.removeSurrounding("<", ">"), ignoreCase = true) }
        }
        if (unknown > 0) {
            val folder = recipientFile.parentFile!!.relativeTo(root).invariantSeparatorsPath
            val label = if (folder.isEmpty()) "store" else "$folder/"
            val count = if (unknown == 1) "1 other key" else "$unknown other keys"
            throw LoginSaveError("$label is shared with $count · can't encrypt for them yet")
        }

        val options = EncryptionOptions.get().addRecipient(certificate) { candidates ->
            candidates.filter { it.pgpPublicKey.keyID in key.availableKeyIds }
        }
        val output = ByteArrayOutputStream()
        pgp.generateMessage().onOutputStream(output)
            .withOptions(ProducerOptions.encrypt(options).setAsciiArmor(false))
            .use { it.write(plaintext.toByteArray(Charsets.UTF_8)) }
        return output.toByteArray()
    }
}
