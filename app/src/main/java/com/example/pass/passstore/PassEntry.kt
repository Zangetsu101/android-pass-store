package com.example.pass.passstore

import java.io.File

data class PassEntry(
    val path: String,
    val domain: String?,
    val username: String,
    val encryptedFile: File,
)
