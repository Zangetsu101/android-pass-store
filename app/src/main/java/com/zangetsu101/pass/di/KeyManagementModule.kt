package com.zangetsu101.pass.di

import com.zangetsu101.pass.keymanagement.CryptoOperations
import com.zangetsu101.pass.keymanagement.CryptoService
import com.zangetsu101.pass.keymanagement.SessionManager
import com.zangetsu101.pass.keymanagement.SessionOperations
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class KeyManagementModule {
    @Binds
    @Singleton
    abstract fun bindSessionOperations(impl: SessionManager): SessionOperations

    @Binds
    @Singleton
    abstract fun bindCryptoOperations(impl: CryptoService): CryptoOperations
}
