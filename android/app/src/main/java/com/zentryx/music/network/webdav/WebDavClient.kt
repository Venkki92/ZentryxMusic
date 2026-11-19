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
 * Implements PROPFIND directory scanning with strict Depth: 1, ETag diffing with Depth: 0,
 * HTTP Range reads, plus MKCOL / PUT / GET helpers for .m3u8 playlist sync & DSP backups.
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
        private const val USER_AGENT = "ZentryxMusic/1.0 (AutoChips AC8259P Headunit)"

        private val PROPFIND_XML_BODY = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
                <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength/>
                    <d:getcontenttype/>
                    <d:getetag/>
                    <d:getlastmodified/>
                    <oc:permissions/>
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
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Diagnostic PROPFIND probe returning raw HTTP status code, folder <oc:permissions>,
     * parsed child items, and latency for step-by-step scan diagnostics.
     */
    suspend fun probeFolder(
        folderUrl: String,
        config: ServerConfig,
        depth: Int = 1
    ): WebDavProbeResult = withContext(ioDispatcher) {
        val client = getClientForConfig(config)
        val normalizedUrl = folderUrl.trimEnd('/') + "/"
        val credential = Credentials.basic(config.username, config.tokenOrPassword)
        val startNs = System.nanoTime()

        val request = try {
            Request.Builder()
                .url(normalizedUrl)
                .method("PROPFIND", PROPFIND_XML_BODY)
                .header("Depth", depth.toString())
                .header("Authorization", credential)
                .header("User-Agent", USER_AGENT)
                .build()
        } catch (e: Exception) {
            val ms = (System.nanoTime() - startNs) / 1_000_000L
            return@withContext WebDavProbeResult(
                url = normalizedUrl,
                httpStatusCode = null,
                selfPermissions = null,
                davHeader = null,
                items = emptyList(),
                latencyMs = ms,
                errorMessage = "Invalid URL: ${e.localizedMessage ?: normalizedUrl}"
            )
        }

        try {
            client.newCall(request).execute().use { response ->
                val ms = (System.nanoTime() - startNs) / 1_000_000L
                val davHdr = response.header("DAV")
                val code = response.code
                if (code == 207 || response.isSuccessful) {
                    val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                    val requestedPath = response.request.url.encodedPath
                    // Parse all items including self-folder to inspect <oc:permissions> on target directory
                    val allEntries = xmlParser.parseMultistatus(bodyBytes.inputStream(), requestedPath = null)
                    val targetNorm = urlHelper.decodePath(requestedPath).trimEnd('/')
                    val selfEntry = allEntries.firstOrNull { entry ->
                        val entryNorm = urlHelper.decodePath(
                            if (entry.href.startsWith("http")) {
                                runCatching { java.net.URI(entry.href).rawPath }.getOrNull() ?: entry.href
                            } else entry.href
                        ).trimEnd('/')
                        entryNorm.equals(targetNorm, ignoreCase = true)
                    }
                    val children = xmlParser.parseMultistatus(bodyBytes.inputStream(), requestedPath = requestedPath)
                    WebDavProbeResult(
                        url = normalizedUrl,
                        httpStatusCode = code,
                        selfPermissions = selfEntry?.permissions,
                        davHeader = davHdr,
                        items = children,
                        latencyMs = ms,
                        errorMessage = null
                    )
                } else {
                    WebDavProbeResult(
                        url = normalizedUrl,
                        httpStatusCode = code,
                        selfPermissions = null,
                        davHeader = davHdr,
                        items = emptyList(),
                        latencyMs = ms,
                        errorMessage = "HTTP $code ${response.message}".trim()
                    )
                }
            }
        } catch (e: Exception) {
            val ms = (System.nanoTime() - startNs) / 1_000_000L
            WebDavProbeResult(
                url = normalizedUrl,
                httpStatusCode = null,
                selfPermissions = null,
                davHeader = null,
                items = emptyList(),
                latencyMs = ms,
                errorMessage = "${e::class.simpleName ?: "NetworkError"}: ${e.localizedMessage ?: "Request failed"}"
            )
        }
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
            .header("User-Agent", USER_AGENT)
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
            .header("User-Agent", USER_AGENT)
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
            .header("User-Agent", USER_AGENT)
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

    /**
     * Ensures a remote WebDAV directory exists via MKCOL (201 Created or 405 Already Exists).
     */
    suspend fun ensureCollection(
        folderUrl: String,
        config: ServerConfig
    ): Resource<Unit> = withContext(ioDispatcher) {
        val client = getClientForConfig(config)
        val credential = Credentials.basic(config.username, config.tokenOrPassword)
        val request = Request.Builder()
            .url(folderUrl.trimEnd('/') + "/")
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .header("Authorization", credential)
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 405 || resp.code == 301 || resp.code == 302) {
                    Resource.Success(Unit)
                } else if (resp.code == 401) {
                    Resource.Error(ErrorType.UNAUTHORIZED, "Unauthorized (401)")
                } else {
                    Resource.Error(ErrorType.UNKNOWN, "MKCOL failed with HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "MKCOL failed: ${e.localizedMessage}", e)
        }
    }

    /**
     * Uploads text/binary file content via WebDAV PUT.
     */
    suspend fun uploadBytes(
        fileUrl: String,
        bytes: ByteArray,
        contentType: String,
        config: ServerConfig
    ): Resource<Unit> = withContext(ioDispatcher) {
        val client = getClientForConfig(config)
        val credential = Credentials.basic(config.username, config.tokenOrPassword)
        val request = Request.Builder()
            .url(fileUrl)
            .put(bytes.toRequestBody(contentType.toMediaType()))
            .header("Authorization", credential)
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 201 || resp.code == 204) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(ErrorType.UNKNOWN, "Upload failed with HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "WebDAV upload error: ${e.localizedMessage}", e)
        }
    }

    /**
     * Downloads UTF-8 text (e.g. .m3u8 playlist or JSON backup) from Nextcloud WebDAV.
     */
    suspend fun downloadText(
        fileUrl: String,
        config: ServerConfig
    ): Resource<String> = withContext(ioDispatcher) {
        val client = getClientForConfig(config)
        val credential = Credentials.basic(config.username, config.tokenOrPassword)
        val request = Request.Builder()
            .url(fileUrl)
            .get()
            .header("Authorization", credential)
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    Resource.Success(resp.body?.string().orEmpty())
                } else {
                    Resource.Error(ErrorType.WEBDAV_UNREACHABLE, "Download failed with HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "WebDAV download error: ${e.localizedMessage}", e)
        }
    }
}
