package com.zangetsu101.pass.di

import com.zangetsu101.pass.autofill.AutofillDecryption
import com.zangetsu101.pass.decryption.Decryption
import com.zangetsu101.pass.decryption.DecryptionImpl
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyOperations
import com.zangetsu101.pass.keymanagement.gpg.GpgKeyProviderImpl
import com.zangetsu101.pass.keymanagement.session.CachedPassphrase
import com.zangetsu101.pass.keymanagement.session.DirectPassphrase
import com.zangetsu101.pass.keymanagement.session.PassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DecryptionModule {
    @Provides
    @Singleton
    fun provideDecryption(
        @CachedPassphrase pp: PassphraseProvider,
        ops: GpgKeyOperations,
    ): Decryption = DecryptionImpl(GpgKeyProviderImpl(pp, ops))

    @Provides
    @Singleton
    @AutofillDecryption
    fun provideAutofillDecryption(
        @DirectPassphrase pp: PassphraseProvider,
        ops: GpgKeyOperations,
    ): Decryption = DecryptionImpl(GpgKeyProviderImpl(pp, ops))
}
