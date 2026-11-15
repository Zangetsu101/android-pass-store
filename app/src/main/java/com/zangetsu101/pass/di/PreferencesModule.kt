// SPDX-License-Identifier: GPL-3.0-or-later
package com.zangetsu101.pass.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.zangetsu101.pass.autofill.AutofillHistoryStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.appPrefsDataStore by preferencesDataStore("app_prefs")
private val Context.autofillHistoryDataStore by preferencesDataStore("autofill_history")

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.appPrefsDataStore

    @Provides
    @Singleton
    @AutofillHistoryStore
    fun provideAutofillHistoryDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.autofillHistoryDataStore
}
