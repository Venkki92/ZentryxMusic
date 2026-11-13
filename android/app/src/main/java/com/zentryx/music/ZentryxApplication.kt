package com.zentryx.music

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Main Application class for Zentryx Music.
 * Annotated with @HiltAndroidApp to trigger Hilt's code generation.
 * Implements ImageLoaderFactory to provide a custom Coil ImageLoader.
 * Implements Configuration.Provider to configure WorkManager with HiltWorkerFactory.
 */
@HiltAndroidApp
class ZentryxApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { okHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Limit memory cache to 15% of available app RAM (<150MB budget)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_artwork_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB disk cache for artwork
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}
