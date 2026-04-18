package com.example.pass.keymanagement

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyManagementImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : KeyManagement {

    internal val blobStore = KeyBlobStore(context)

    override fun clearAllKeys() {
        blobStore.deleteAll()
    }
}
