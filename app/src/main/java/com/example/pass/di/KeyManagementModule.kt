package com.example.pass.di

import com.example.pass.keymanagement.CryptoOperations
import com.example.pass.keymanagement.CryptoService
import com.example.pass.keymanagement.SessionManager
import com.example.pass.keymanagement.SessionOperations
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
