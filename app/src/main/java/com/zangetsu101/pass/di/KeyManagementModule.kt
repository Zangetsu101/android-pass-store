package com.zangetsu101.pass.di

import android.content.Context
import com.zangetsu101.pass.keymanagement.KeyManagement
import com.zangetsu101.pass.keymanagement.KeyManager
import com.zangetsu101.pass.keymanagement.crypto.AesGcmCryptoStore
import com.zangetsu101.pass.keymanagement.crypto.AndroidBiometricCryptoStore
import com.zangetsu101.pass.keymanagement.crypto.BiometricCryptoStore
import com.zangetsu101.pass.keymanagement.crypto.CryptoStore
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStore
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyStoreImpl
import com.zangetsu101.pass.keymanagement.session.AndroidSessionTimer
import com.zangetsu101.pass.keymanagement.session.BiometricPrompter
import com.zangetsu101.pass.keymanagement.session.CachedPassphrase
import com.zangetsu101.pass.keymanagement.session.CachingPassphraseProvider
import com.zangetsu101.pass.keymanagement.session.DirectPassphrase
import com.zangetsu101.pass.keymanagement.session.PassphraseProvider
import com.zangetsu101.pass.keymanagement.session.SessionManager
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionTimer
import com.zangetsu101.pass.keymanagement.session.SystemBiometricPrompter
import com.zangetsu101.pass.keymanagement.ssh.SshKeyStore
import com.zangetsu101.pass.keymanagement.ssh.SshKeyStoreImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KeyManagementModule {
    @Binds
    @Singleton
    abstract fun bindSessionOperations(impl: SessionManager): SessionOperations

    @Binds
    @Singleton
    abstract fun bindBiometricCryptoStore(impl: AndroidBiometricCryptoStore): BiometricCryptoStore

    @Binds
    @Singleton
    abstract fun bindBiometricPrompter(impl: SystemBiometricPrompter): BiometricPrompter

    @Binds
    @Singleton
    abstract fun bindSessionTimer(impl: AndroidSessionTimer): SessionTimer

    @Binds
    @Singleton
    abstract fun bindGpgKeyStore(impl: GpgKeyStoreImpl): GpgKeyStore

    @Binds
    @Singleton
    abstract fun bindSshKeyStore(impl: SshKeyStoreImpl): SshKeyStore

    @Binds
    @Singleton
    abstract fun bindKeyManagement(impl: KeyManager): KeyManagement

    @Binds
    @Singleton
    @DirectPassphrase
    abstract fun bindDirectPassphraseProvider(impl: SessionManager): PassphraseProvider

    @Binds
    @Singleton
    @CachedPassphrase
    abstract fun bindCachingPassphraseProvider(impl: CachingPassphraseProvider): PassphraseProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindGpgIntoKeySet(impl: GpgKeyStoreImpl): CryptoStore

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSshIntoKeySet(impl: SshKeyStoreImpl): CryptoStore

    companion object {
        @Provides
        @Singleton
        @AppBackgroundScope
        fun provideAppBackgroundScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Provides
        @Singleton
        fun provideGpgKeyStoreImpl(
            @ApplicationContext context: Context,
        ): GpgKeyStoreImpl = GpgKeyStoreImpl(AesGcmCryptoStore(context, "gpg"))

        @Provides
        @Singleton
        fun provideSshKeyStoreImpl(
            @ApplicationContext context: Context,
        ): SshKeyStoreImpl =
            SshKeyStoreImpl(
                AesGcmCryptoStore(context, "ssh_key"),
                AesGcmCryptoStore(context, "ssh_pub_key"),
            )
    }
}
