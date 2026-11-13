package com.zentryx.music.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.zentryx.music.network.TofuTrustManager
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext

/**
 * Builds Media3 CacheDataSource with disk stream buffering and authenticated OkHttp calls.
 * Never embeds credentials in URLs; attaches HTTP Basic Auth via OkHttp interceptor.
 * Chunks streamed audio into a 250MB SimpleCache on disk, preserving memory (< 150MB RAM).
 */
@OptIn(UnstableApi::class)
@Singleton
class CacheDataSourceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val baseOkHttpClient: OkHttpClient,
    private val encryptedDataStore: EncryptedDataStore
) {
    companion object {
        private const val MAX_CACHE_SIZE_BYTES = 250L * 1024L * 1024L // 250 MB disk stream cache
    }

    val simpleCache: SimpleCache by lazy {
        val cacheDir = File(context.cacheDir, "media3_stream_cache").apply {
            if (!exists()) mkdirs()
        }
        val databaseProvider = StandaloneDatabaseProvider(context)
        val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES)
        SimpleCache(cacheDir, evictor, databaseProvider)
    }

    /**
     * Builds an authenticated OkHttpClient for ExoPlayer audio streaming.
     */
    private fun buildStreamOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            // Read active Nextcloud credentials synchronously for network request
            val config = runBlocking { encryptedDataStore.serverConfigFlow.firstOrNull() }
            val requestBuilder = request.newBuilder()
                .header("User-Agent", "ZentryxMusic/1.0 (Android Media3; AutoChips AC8259P Headunit)")

            if (config != null && config.isLoggedIn) {
                val credential = Credentials.basic(config.username, config.tokenOrPassword)
                requestBuilder.header("Authorization", credential)
            }

            chain.proceed(requestBuilder.build())
        }

        val clientBuilder = baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)

        // Inject TOFU TrustManager if self-signed cert is pinned
        val config = runBlocking { encryptedDataStore.serverConfigFlow.firstOrNull() }
        if (config?.sslCertificateFingerprint != null) {
            val trustManager = TofuTrustManager(config.sslCertificateFingerprint)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(trustManager), SecureRandom())
            }
            clientBuilder.sslSocketFactory(sslContext.socketFactory, trustManager)
        }

        return clientBuilder.build()
    }

    /**
     * Creates the primary DataSource.Factory used by ExoPlayer.
     */
    fun createDataSourceFactory(): DataSource.Factory {
        val streamClient = buildStreamOkHttpClient()
        val upstreamFactory = OkHttpDataSource.Factory(streamClient)

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(simpleCache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
