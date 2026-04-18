package com.example.pass.di

import com.example.pass.gitsync.GitSync
import com.example.pass.gitsync.GitSyncImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GitSyncModule {

    @Binds
    @Singleton
    abstract fun bindGitSync(impl: GitSyncImpl): GitSync
}
