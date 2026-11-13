package com.zentryx.music.repository

import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.models.ErrorType
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.network.TofuCertificateException
import com.zentryx.music.network.TofuTrustManager
import com.zentryx.music.security.EncryptedDataStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val baseOkHttpClient: OkHttpClient,
    private val encryptedDataStore: EncryptedDataStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    override val serverConfig: Flow<ServerConfig?> = encryptedDataStore.serverConfigFlow

    /**
     * Builds an isolated OkHttpClient configured with optional TOFU SSL Pinning
     * and automotive-friendly 10-second timeouts.
     */
    private fun createClientForConfig(config: ServerConfig, onUntrustedCert: ((String) -> Unit)? = null): OkHttpClient {
        val builder = baseOkHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        if (!config.sslCertificateFingerprint.isNullOrBlank() || onUntrustedCert != null) {
            val trustManager = TofuTrustManager(config.sslCertificateFingerprint, onUntrustedCert)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        }

        return builder.build()
    }

    override suspend fun testConnection(config: ServerConfig): Resource<Unit> = withContext(ioDispatcher) {
        val cleanServerUrl = config.serverUrl.trim().trimEnd('/')
        if (!cleanServerUrl.startsWith("http://") && !cleanServerUrl.startsWith("https://")) {
            return@withContext Resource.Error(
                ErrorType.BAD_URL,
                "Invalid server URL. Please include https://"
            )
        }

        if (cleanServerUrl.startsWith("http://")) {
            return@withContext Resource.Error(
                ErrorType.BAD_URL,
                "Insecure HTTP is blocked. Nextcloud streaming requires HTTPS."
            )
        }

        var detectedFingerprint: String? = null
        val client = createClientForConfig(config) { fingerprint ->
            detectedFingerprint = fingerprint
        }

        // WebDAV PROPFIND with Depth: 0 to test root access
        val propfindBody = """<?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop><d:resourcetype/></d:prop>
            </d:propfind>""".trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

        val webDavUrl = config.webDavBaseUrl
        val credential = Credentials.basic(config.username, config.tokenOrPassword)

        val request = Request.Builder()
            .url(webDavUrl)
            .method("PROPFIND", propfindBody)
            .header("Depth", "0")
            .header("Authorization", credential)
            .header("User-Agent", "ZentryxMusic/1.0 (Android Headunit; AutoChips AC8259P)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 207 || response.isSuccessful -> {
                        Resource.Success(Unit)
                    }
                    response.code == 401 -> {
                        Resource.Error(
                            ErrorType.UNAUTHORIZED,
                            "Authentication failed. Please verify your username and password/app-token."
                        )
                    }
                    response.code == 404 || response.code in 500..599 -> {
                        Resource.Error(
                            ErrorType.WEBDAV_UNREACHABLE,
                            "WebDAV endpoint unreachable (${response.code} ${response.message}). Verify Nextcloud server URL and username."
                        )
                    }
                    else -> {
                        Resource.Error(
                            ErrorType.UNKNOWN,
                            "Unexpected server response: HTTP ${response.code} ${response.message}"
                        )
                    }
                }
            }
        } catch (e: MalformedURLException) {
            Resource.Error(ErrorType.BAD_URL, "Malformed server URL: ${e.localizedMessage}", e)
        } catch (e: UnknownHostException) {
            Resource.Error(ErrorType.NO_NETWORK, "Server not found. Check server address and Wi-Fi connection.", e)
        } catch (e: SocketTimeoutException) {
            Resource.Error(ErrorType.TIMEOUT, "Connection timed out. Check server reachability.", e)
        } catch (e: SocketException) {
            Resource.Error(ErrorType.NO_NETWORK, "Network connection error: ${e.localizedMessage}", e)
        } catch (e: TofuCertificateException) {
            Resource.Error(
                ErrorType.SSL_ERROR,
                "Untrusted SSL certificate detected.\nSHA-256 Fingerprint:\n${e.sha256Fingerprint}",
                e
            )
        } catch (e: SSLHandshakeException) {
            val fpMsg = if (detectedFingerprint != null) {
                "\nFingerprint: $detectedFingerprint"
            } else ""
            Resource.Error(
                ErrorType.SSL_ERROR,
                "SSL Handshake failed. The server certificate is untrusted or self-signed.$fpMsg",
                e
            )
        } catch (e: SSLException) {
            Resource.Error(ErrorType.SSL_ERROR, "SSL security error: ${e.localizedMessage}", e)
        } catch (e: IOException) {
            Resource.Error(ErrorType.UNKNOWN, "I/O connection error: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "Failed to connect: ${e.localizedMessage}", e)
        }
    }

    override suspend fun saveCredentials(config: ServerConfig) {
        encryptedDataStore.saveConfig(config.copy(isLoggedIn = true))
    }

    override suspend fun startLoginFlowV2(serverUrl: String): Resource<LoginFlowSession> = withContext(ioDispatcher) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val endpoint = "$cleanUrl/index.php/login/v2"

        val request = Request.Builder()
            .url(endpoint)
            .post("".toRequestBody(null))
            .header("User-Agent", "ZentryxMusic/1.0 (Android Headunit; AutoChips AC8259P)")
            .build()

        try {
            baseOkHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Resource.Error(
                        ErrorType.WEBDAV_UNREACHABLE,
                        "Server returned HTTP ${response.code} for Login Flow v2"
                    )
                }
                val bodyStr = response.body?.string() ?: return@withContext Resource.Error(
                    ErrorType.UNKNOWN,
                    "Empty response from server"
                )

                val json = JSONObject(bodyStr)
                val loginUrl = json.getString("login")
                val pollObj = json.getJSONObject("poll")
                val token = pollObj.getString("token")
                val pollEndpoint = pollObj.getString("endpoint")

                Resource.Success(LoginFlowSession(loginUrl, token, pollEndpoint))
            }
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "Failed to start Login Flow v2: ${e.localizedMessage}", e)
        }
    }

    override suspend fun pollLoginFlowV2(token: String, endpoint: String): Resource<ServerConfig> = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url("$endpoint?token=$token")
            .post("".toRequestBody(null))
            .header("User-Agent", "ZentryxMusic/1.0 (Android Headunit; AutoChips AC8259P)")
            .build()

        try {
            baseOkHttpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val bodyStr = response.body?.string() ?: return@withContext Resource.Error(
                            ErrorType.UNKNOWN,
                            "Empty authorization payload"
                        )
                        val json = JSONObject(bodyStr)
                        val server = json.getString("server")
                        val loginName = json.getString("loginName")
                        val appPassword = json.getString("appPassword")

                        val config = ServerConfig(
                            serverUrl = server,
                            username = loginName,
                            tokenOrPassword = appPassword,
                            isLoggedIn = true
                        )
                        Resource.Success(config)
                    }
                    404 -> {
                        // Still waiting for user authorization in browser
                        Resource.Loading
                    }
                    else -> {
                        Resource.Error(
                            ErrorType.UNKNOWN,
                            "Authorization polling failed with HTTP ${response.code}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "Error polling Login Flow v2: ${e.localizedMessage}", e)
        }
    }

    override suspend fun logout() {
        encryptedDataStore.clear()
    }
}
