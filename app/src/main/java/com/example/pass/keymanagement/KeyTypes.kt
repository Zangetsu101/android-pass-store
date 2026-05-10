package com.example.pass.keymanagement

import org.bouncycastle.openpgp.PGPSecretKeyRing
import java.security.KeyPair

typealias GpgPrivateKey = PGPSecretKeyRing
typealias SshPrivateKey = KeyPair
