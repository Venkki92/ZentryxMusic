package com.zentryx.music.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.network.TofuTrustManager
import com.zentryx.music.security.EncryptedDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
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
 * Builds Media3 DataSource with scheme-aware routing:
 * - http:// and https:// streams route through CacheDataSource (SimpleCache + authenticated TOFU OkHttp).
 * - file://, content://, asset://, and local paths route directly through DefaultDataSource
 *   (FileDataSource / ContentDataSource) so offline downloads & USB tracks play reliably without OkHttp.
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

        fun isRemoteHttpScheme(scheme: String?): Boolean {
            return scheme.equals("http", ignoreCase = true) ||
                scheme.equals("https", ignoreCase = true)
        }

        fun isRemoteHttpUri(uri: Uri?): Boolean = isRemoteHttpScheme(uri?.scheme)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var latestConfig: ServerConfig? = null

    init {
        scope.launch {
            encryptedDataStore.serverConfigFlow.collectLatest { cfg ->
                latestConfig = cfg
            }
        }
    }

    private fun resolveConfig(): ServerConfig? {
        return latestConfig ?: runBlocking { encryptedDataStore.serverConfigFlow.firstOrNull() }.also {
            latestConfig = it
        }
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
     * Builds an authenticated OkHttpClient for ExoPlayer audio streaming with dynamic TOFU pinning.
     */
    private fun buildStreamOkHttpClient(): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val config = resolveConfig()
            val requestBuilder = request.newBuilder()
                .header("User-Agent", "ZentryxMusic/1.0 (Android Media3; AutoChips AC8259P Headunit)")

            if (config != null && config.isLoggedIn && config.username.isNotBlank()) {
                val credential = Credentials.basic(config.username, config.tokenOrPassword)
                requestBuilder.header("Authorization", credential)
            }

            chain.proceed(requestBuilder.build())
        }

        val retryChunkInterceptor = Interceptor { chain ->
            val request = chain.request()
            val method = request.method.uppercase()
            val isIdempotentChunk = method == "GET" || method == "HEAD"
            if (!isIdempotentChunk) {
                return@Interceptor chain.proceed(request)
            }

            val maxRetries = 3
            val retryableStatuses = setOf(408, 429, 500, 502, 503, 504)
            var attempt = 0
            var lastException: java.io.IOException? = null

            while (attempt <= maxRetries) {
                if (chain.call().isCanceled()) {
                    throw java.io.IOException("Stream request canceled")
                }
                try {
                    val response = chain.proceed(request)
                    if (response.code !in retryableStatuses || attempt == maxRetries) {
                        return@Interceptor response
                    }
                    response.close()
                } catch (ioe: java.io.IOException) {
                    if (chain.call().isCanceled() || attempt == maxRetries) {
                        throw ioe
                    }
                    lastException = ioe
                }

                attempt++
                val baseDelayMs = 250L * (1L shl (attempt - 1))
                val jitterMs = kotlin.random.Random.nextLong(0L, 120L)
                val sleepMs = minOf(2500L, baseDelayMs + jitterMs)
                try {
                    Thread.sleep(sleepMs)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw lastException ?: java.io.IOException("Interrupted during chunk retry", ie)
                }
            }

            throw lastException ?: java.io.IOException("Chunk stream failed after retries")
        }

        val trustManager = TofuTrustManager(
            pinnedFingerprintProvider = { resolveConfig()?.sslCertificateFingerprint }
        )
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }

        return baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(authInterceptor)
            .addInterceptor(retryChunkInterceptor)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .build()
    }

    private fun buildCacheDataSourceFactory(): CacheDataSource.Factory {
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

    /**
     * Direct CacheDataSource instance for Tunnel Guard predictive HTTP pre-caching.
     */
    fun createCacheDataSource(): CacheDataSource {
        return buildCacheDataSourceFactory().createDataSource()
    }

    /**
     * Creates the primary scheme-routing DataSource.Factory used by ExoPlayer:
     * - http:// and https:// -> CacheDataSource (SimpleCache + authenticated TOFU OkHttp)
     * - file://, content://, asset://, and local paths -> DefaultDataSource (native local audio playback)
     */
    fun createDataSourceFactory(): DataSource.Factory {
        val remoteCacheFactory = buildCacheDataSourceFactory()
        val localFactory = DefaultDataSource.Factory(context)

        return DataSource.Factory {
            SchemeRoutingDataSource(
                remoteDataSource = remoteCacheFactory.createDataSource(),
                localDataSource = localFactory.createDataSource()
            )
        }
    }

    private class SchemeRoutingDataSource(
        private val remoteDataSource: DataSource,
        private val localDataSource: DataSource
    ) : DataSource {
        private var activeDataSource: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            remoteDataSource.addTransferListener(transferListener)
            localDataSource.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val target = if (isRemoteHttpUri(dataSpec.uri)) remoteDataSource else localDataSource
            activeDataSource = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return activeDataSource?.read(buffer, offset, length) ?: -1
        }

        override fun getUri(): Uri? = activeDataSource?.uri

        override fun getResponseHeaders(): Map<String, List<String>> {
            return activeDataSource?.responseHeaders ?: emptyMap()
        }

        override fun close() {
            val ds = activeDataSource
            activeDataSource = null
            ds?.close()
        }
    }
}
