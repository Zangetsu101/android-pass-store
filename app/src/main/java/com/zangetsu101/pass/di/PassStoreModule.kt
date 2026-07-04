// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.di

import com.zangetsu101.pass.passstore.PassStore
import com.zangetsu101.pass.passstore.PassStoreImpl
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
