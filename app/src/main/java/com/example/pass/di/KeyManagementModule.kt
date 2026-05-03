package com.example.pass.di

import com.example.pass.keymanagement.KeyManagement
import com.example.pass.keymanagement.KeyManagementImpl
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
    abstract fun bindKeyManagement(impl: KeyManagementImpl): KeyManagement
}
