package com.example.pass.di

import com.example.pass.decryption.Decryption
import com.example.pass.decryption.DecryptionImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DecryptionModule {

    @Binds
    @Singleton
    abstract fun bindDecryption(impl: DecryptionImpl): Decryption
}
