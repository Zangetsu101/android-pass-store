package com.example.pass.di

import com.example.pass.passstore.PassStore
import com.example.pass.passstore.PassStoreImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PassStoreModule {
    @Binds
    @Singleton
    abstract fun bindPassStore(impl: PassStoreImpl): PassStore
}
