// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.di

import com.zangetsu101.pass.gitsync.GitSync
import com.zangetsu101.pass.gitsync.GitSyncImpl
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