package com.zentryx.music.di

import android.content.Context
import com.zentryx.music.player.CacheDataSourceFactory
import com.zentryx.music.player.PlayerPreferences
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.security.EncryptedDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePlayerPreferences(
        @ApplicationContext context: Context
    ): PlayerPreferences = PlayerPreferences(context)

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        @ApplicationContext context: Context,
        baseOkHttpClient: OkHttpClient,
        encryptedDataStore: EncryptedDataStore
    ): CacheDataSourceFactory = CacheDataSourceFactory(context, baseOkHttpClient, encryptedDataStore)

    @Provides
    @Singleton
    fun provideSettingsPreferences(
        @ApplicationContext context: Context
    ): SettingsPreferences = SettingsPreferences(context)
}
