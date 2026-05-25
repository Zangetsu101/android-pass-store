package com.zangetsu101.pass.di

import com.zangetsu101.pass.keymanagement.CryptoService
import com.zangetsu101.pass.keymanagement.KeyManagement
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyOperations
import com.zangetsu101.pass.keymanagement.session.AndroidSessionKeyStore
import com.zangetsu101.pass.keymanagement.session.AndroidSessionTimer
import com.zangetsu101.pass.keymanagement.session.BiometricPrompter
import com.zangetsu101.pass.keymanagement.session.CachedPassphrase
import com.zangetsu101.pass.keymanagement.session.CachingPassphraseProvider
import com.zangetsu101.pass.keymanagement.session.DirectPassphrase
import com.zangetsu101.pass.keymanagement.session.PassphraseProvider
import com.zangetsu101.pass.keymanagement.session.SessionKeyStore
import com.zangetsu101.pass.keymanagement.session.SessionManager
import com.zangetsu101.pass.keymanagement.session.SessionOperations
import com.zangetsu101.pass.keymanagement.session.SessionTimer
import com.zangetsu101.pass.keymanagement.session.SystemBiometricPrompter
import com.zangetsu101.pass.keymanagement.ssh.SshKeyOperations
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    abstract fun bindSessionKeyStore(impl: AndroidSessionKeyStore): SessionKeyStore

    @Binds
    @Singleton
    abstract fun bindBiometricPrompter(impl: SystemBiometricPrompter): BiometricPrompter

    @Binds
    @Singleton
    abstract fun bindSessionTimer(impl: AndroidSessionTimer): SessionTimer

    @Binds
    @Singleton
    abstract fun bindGpgKeyOperations(impl: CryptoService): GpgKeyOperations

    @Binds
    @Singleton
    abstract fun bindSshKeyOperations(impl: CryptoService): SshKeyOperations

    @Binds
    @Singleton
    abstract fun bindKeyManagement(impl: CryptoService): KeyManagement

    @Binds
    @Singleton
    @DirectPassphrase
    abstract fun bindDirectPassphraseProvider(impl: SessionManager): PassphraseProvider

    @Binds
    @Singleton
    @CachedPassphrase
    abstract fun bindCachingPassphraseProvider(impl: CachingPassphraseProvider): PassphraseProvider

    companion object {
        @Provides
        @Singleton
        @AppBackgroundScope
        fun provideAppBackgroundScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
