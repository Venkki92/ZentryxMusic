package com.zentryx.music.network.webdav

import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.models.ErrorType
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.network.TofuTrustManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException

/**
 * Core WebDAV communication engine using OkHttp.
 * Implements PROPFIND directory scanning with strict Depth: 1 (avoiding server-blocked Depth: infinity),
 * fast folder ETag diffing with Depth: 0, and HTTP Range byte chunking for metadata reads.
 */
@Singleton
class WebDavClient @Inject constructor(
    private val baseOkHttpClient: OkHttpClient,
    private val xmlParser: WebDavXmlParser,
    private val urlHelper: WebDavUrlHelper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val XML_CONTENT_TYPE = "application/xml; charset=utf-8"

        private val PROPFIND_XML_BODY = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength/>
                    <d:getcontenttype/>
                    <d:getetag/>
                    <d:getlastmodified/>
                </d:prop>
            </d:propfind>
        """.trimIndent().toRequestBody(XML_CONTENT_TYPE.toMediaType())
    }

    private fun getClientForConfig(config: ServerConfig): OkHttpClient {
        if (config.sslCertificateFingerprint.isNullOrBlank()) {
            return baseOkHttpClient
        }

        val trustManager = TofuTrustManager(config.sslCertificateFingerprint)
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }

        return baseOkHttpClient.newBuilder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Lists children of a Nextcloud WebDAV directory using PROPFIND with Depth: 1.
     */
    suspend fun listFolder(
        folderUrl: String,
        config: ServerConfig
    ): Resource<List<WebDavItem>> = withContext(ioDispatcher) {
        val client = getClientForConfig(config)
        val normalizedUrl = folderUrl.trimEnd('/') + "/"
        val credential = Credentials.basic(config.username, config.tokenOrPassword)

        val request = Request.Builder()
            .url(normalizedUrl)
            .method("PROPFIND", PROPFIND_XML_BODY)
            .header("Depth", "1")
            .header("Authorization", credential)
            .header("User-Agent", "ZentryxMusic/1.0 (AutoChips AC8259P Headunit)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 207 || response.isSuccessful -> {
                        val body = response.body ?: return@withContext Resource.Error(
                            ErrorType.UNKNOWN,
                            "Empty WebDAV response body"
                        )
                        val requestedPath = response.request.url.encodedPath
                        val items = xmlParser.parseMultistatus(body.byteStream(), requestedPath)
                        Resource.Success(items)
                    }
                    response.code == 401 -> {
                        Resource.Error(ErrorType.UNAUTHORIZED, "Invalid Nextcloud credentials (HTTP 401)")
                    }
                    response.code == 404 -> {
                        Resource.Error(ErrorType.WEBDAV_UNREACHABLE, "Folder not found on server (HTTP 404)")
                    }
                    else -> {
                        Resource.Error(
                            ErrorType.UNKNOWN,
                            "WebDAV PROPFIND failed: HTTP ${response.code} ${response.message}"
                        )
                    }
                }
            }
        } catch (e: UnknownHostException) {
            Resource.Error(ErrorType.NO_NETWORK, "Nextcloud server host unreachable", e)
        } catch (e: SocketTimeoutException) {
            Resource.Error(ErrorType.TIMEOUT, "Directory listing timed out", e)
        } catch (e: SSLException) {
            Resource.Error(ErrorType.SSL_ERROR, "SSL handshake error: ${e.localizedMessage}", e)
        } catch (e: IOException) {
            Resource.Error(ErrorType.UNKNOWN, "I/O connection error: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "Failed to list folder: ${e.localizedMessage}", e)
        }
    }

    /**
     * Retrieves metadata (ETag, lastmodified) for a folder using Depth: 0.
     * Used by the incremental sync engine to skip unmodified folders.
     */
    suspend fun getFolderMetadata(
        folderUrl: String,
        config: ServerConfig
    ): Resource<WebDavItem> = withContext(ioDispatcher) {
        val client = getClientForConfig(config)
        val normalizedUrl = folderUrl.trimEnd('/') + "/"
        val credential = Credentials.basic(config.username, config.tokenOrPassword)

        val request = Request.Builder()
            .url(normalizedUrl)
            .method("PROPFIND", PROPFIND_XML_BODY)
            .header("Depth", "0")
            .header("Authorization", credential)
            .header("User-Agent", "ZentryxMusic/1.0 (AutoChips AC8259P Headunit)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 207 || response.isSuccessful) {
                    val body = response.body ?: return@withContext Resource.Error(
                        ErrorType.UNKNOWN,
                        "Empty response"
                    )
                    val items = xmlParser.parseMultistatus(body.byteStream())
                    val folderItem = items.firstOrNull() ?: return@withContext Resource.Error(
                        ErrorType.UNKNOWN,
                        "No folder metadata returned"
                    )
                    Resource.Success(folderItem)
                } else {
                    Resource.Error(ErrorType.UNKNOWN, "Failed with HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "Failed to retrieve folder ETag: ${e.localizedMessage}", e)
        }
    }

    /**
     * Fetches a slice of bytes from a remote audio file via HTTP Range header.
     * Allows metadata extraction without downloading the whole audio file.
     */
    suspend fun fetchRangeBytes(
        fileUrl: String,
        startByte: Long,
        endByte: Long,
        config: ServerConfig
    ): Resource<ByteArray> = withContext(ioDispatcher) {
        val client = getClientForConfig(config)
        val credential = Credentials.basic(config.username, config.tokenOrPassword)

        val request = Request.Builder()
            .url(fileUrl)
            .header("Range", "bytes=$startByte-$endByte")
            .header("Authorization", credential)
            .header("User-Agent", "ZentryxMusic/1.0 (AutoChips AC8259P Headunit)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 206 || response.code == 200) {
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    Resource.Success(bytes)
                } else {
                    Resource.Error(
                        ErrorType.UNKNOWN,
                        "Range request failed with HTTP ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "HTTP Range fetch error: ${e.localizedMessage}", e)
        }
    }
}
