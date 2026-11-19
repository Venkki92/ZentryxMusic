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

    companion object {
        private const val USER_AGENT = "ZentryxMusic/1.0 (Android Headunit; AutoChips AC8259P)"
    }

    override val serverConfig: Flow<ServerConfig?> = encryptedDataStore.serverConfigFlow

    /**
     * Builds an isolated OkHttpClient configured with TOFU SSL Pinning and generous headunit timeouts.
     */
    private fun createClientForConfig(
        pinnedFingerprint: String? = null,
        onUntrustedCert: ((String) -> Unit)? = null
    ): OkHttpClient {
        val builder = baseOkHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        if (!pinnedFingerprint.isNullOrBlank() || onUntrustedCert != null) {
            val trustManager = TofuTrustManager(pinnedFingerprint, onUntrustedCert)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), SecureRandom())
            builder.sslSocketFactory(sslContext.socketFactory, trustManager)
        }

        return builder.build()
    }

    override suspend fun testConnection(config: ServerConfig): Resource<Unit> {
        return when (val resolved = resolveAndTestConnection(config)) {
            is Resource.Success -> Resource.Success(Unit)
            is Resource.Error -> Resource.Error(resolved.errorType, resolved.message, resolved.cause)
            is Resource.Loading -> Resource.Loading
        }
    }

    override suspend fun resolveAndTestConnection(config: ServerConfig): Resource<ServerConfig> =
        withContext(ioDispatcher) {
            val cleanServerUrl = config.serverUrl.trim().trimEnd('/')
            if (!cleanServerUrl.startsWith("http://") && !cleanServerUrl.startsWith("https://")) {
                return@withContext Resource.Error(
                    ErrorType.BAD_URL,
                    "Invalid server URL. Please include https:// or http://"
                )
            }

            var detectedFingerprint: String? = null
            val client = createClientForConfig(config.sslCertificateFingerprint) { fingerprint ->
                detectedFingerprint = fingerprint
            }

            try {
                var effectiveConfig = config.copy(serverUrl = cleanServerUrl)

                // Step 1: Resolve canonical Nextcloud user_id via OCS (handles email logins & LDAP aliases)
                val credential = Credentials.basic(effectiveConfig.username, effectiveConfig.tokenOrPassword)
                val ocsUrl = "$cleanServerUrl/ocs/v2.php/cloud/user?format=json"
                val ocsRequest = Request.Builder()
                    .url(ocsUrl)
                    .get()
                    .header("OCS-APIRequest", "true")
                    .header("Accept", "application/json")
                    .header("Authorization", credential)
                    .header("User-Agent", USER_AGENT)
                    .build()

                try {
                    client.newCall(ocsRequest).execute().use { ocsResp ->
                        if (ocsResp.code == 401) {
                            return@withContext Resource.Error(
                                ErrorType.UNAUTHORIZED,
                                "Authentication failed (401). Verify username/password — if 2FA is active on Nextcloud, use an App Password."
                            )
                        }
                        if (ocsResp.isSuccessful) {
                            val body = ocsResp.body?.string().orEmpty()
                            if (body.isNotBlank()) {
                                val root = JSONObject(body)
                                val canonicalId = root.optJSONObject("ocs")
                                    ?.optJSONObject("data")
                                    ?.optString("id")
                                    ?.trim()
                                if (!canonicalId.isNullOrBlank()) {
                                    effectiveConfig = effectiveConfig.copy(username = canonicalId)
                                }
                            }
                        }
                    }
                } catch (e: TofuCertificateException) {
                    return@withContext sslFingerprintError(e.sha256Fingerprint, e)
                } catch (e: SSLHandshakeException) {
                    return@withContext sslHandshakeError(detectedFingerprint, e)
                } catch (_: Exception) {
                    // Continue directly to WebDAV probe if OCS endpoint is disabled/proxied differently
                }

                // Step 2: PROPFIND primary WebDAV root (/remote.php/dav/files/<user>)
                val propfindBody = """<?xml version="1.0" encoding="utf-8" ?>
                    <d:propfind xmlns:d="DAV:">
                        <d:prop><d:resourcetype/></d:prop>
                    </d:propfind>""".trimIndent().toRequestBody("application/xml; charset=utf-8".toMediaType())

                val davCredential = Credentials.basic(effectiveConfig.username, effectiveConfig.tokenOrPassword)
                val primaryRequest = Request.Builder()
                    .url(effectiveConfig.webDavBaseUrl)
                    .method("PROPFIND", propfindBody)
                    .header("Depth", "0")
                    .header("Authorization", davCredential)
                    .header("User-Agent", USER_AGENT)
                    .build()

                client.newCall(primaryRequest).execute().use { response ->
                    when {
                        response.code == 207 || response.isSuccessful -> {
                            return@withContext Resource.Success(effectiveConfig.copy(isLoggedIn = true))
                        }
                        response.code == 401 -> {
                            return@withContext Resource.Error(
                                ErrorType.UNAUTHORIZED,
                                "Authentication failed (401). If 2FA is enabled on Nextcloud, create an App Password in Settings -> Security."
                            )
                        }
                        response.code == 404 || response.code == 405 -> {
                            // Step 3: Probe legacy WebDAV path (/remote.php/webdav) before failing
                            val fallbackRequest = Request.Builder()
                                .url(effectiveConfig.legacyWebDavBaseUrl)
                                .method("PROPFIND", propfindBody)
                                .header("Depth", "0")
                                .header("Authorization", davCredential)
                                .header("User-Agent", USER_AGENT)
                                .build()

                            client.newCall(fallbackRequest).execute().use { fbResp ->
                                if (fbResp.code == 207 || fbResp.isSuccessful) {
                                    return@withContext Resource.Success(
                                        effectiveConfig.copy(useLegacyWebDav = true, isLoggedIn = true)
                                    )
                                }
                                if (fbResp.code == 401) {
                                    return@withContext Resource.Error(
                                        ErrorType.UNAUTHORIZED,
                                        "Authentication failed (401). Please check username/app password."
                                    )
                                }
                            }

                            return@withContext Resource.Error(
                                ErrorType.WEBDAV_UNREACHABLE,
                                "WebDAV endpoint not found (${response.code}). Verify Nextcloud URL and canonical username."
                            )
                        }
                        response.code in 500..599 -> {
                            return@withContext Resource.Error(
                                ErrorType.WEBDAV_UNREACHABLE,
                                "Nextcloud server error (${response.code} ${response.message})."
                            )
                        }
                        else -> {
                            return@withContext Resource.Error(
                                ErrorType.UNKNOWN,
                                "Unexpected server response: HTTP ${response.code} ${response.message}"
                            )
                        }
                    }
                }
            } catch (e: MalformedURLException) {
                Resource.Error(ErrorType.BAD_URL, "Malformed server URL: ${e.localizedMessage}", e)
            } catch (e: UnknownHostException) {
                Resource.Error(ErrorType.NO_NETWORK, "Server not found. Check URL spelling and headunit Wi-Fi / LTE.", e)
            } catch (e: SocketTimeoutException) {
                Resource.Error(ErrorType.TIMEOUT, "Connection timed out. Check server reachability or firewall.", e)
            } catch (e: SocketException) {
                Resource.Error(ErrorType.NO_NETWORK, "Network connection error: ${e.localizedMessage}", e)
            } catch (e: TofuCertificateException) {
                sslFingerprintError(e.sha256Fingerprint, e)
            } catch (e: SSLHandshakeException) {
                sslHandshakeError(detectedFingerprint, e)
            } catch (e: SSLException) {
                Resource.Error(ErrorType.SSL_ERROR, "SSL security error: ${e.localizedMessage}", e)
            } catch (e: IOException) {
                Resource.Error(ErrorType.UNKNOWN, "I/O connection error: ${e.localizedMessage}", e)
            } catch (e: Exception) {
                Resource.Error(ErrorType.UNKNOWN, "Failed to connect: ${e.localizedMessage}", e)
            }
        }

    private fun sslFingerprintError(rawHex: String, throwable: Throwable): Resource.Error {
        val formatted = TofuTrustManager.formatFingerprintWithColons(rawHex)
        return Resource.Error(
            ErrorType.SSL_ERROR,
            "Untrusted SSL certificate detected.\nSHA-256 Fingerprint:\n$formatted",
            throwable
        )
    }

    private fun sslHandshakeError(detectedFingerprint: String?, throwable: Throwable): Resource.Error {
        val fpMsg = if (!detectedFingerprint.isNullOrBlank()) {
            "\nSHA-256 Fingerprint:\n$detectedFingerprint"
        } else ""
        return Resource.Error(
            ErrorType.SSL_ERROR,
            "SSL Handshake failed. The server certificate is untrusted or self-signed.$fpMsg",
            throwable
        )
    }

    override suspend fun saveCredentials(config: ServerConfig) {
        encryptedDataStore.saveConfig(config.copy(isLoggedIn = true))
    }

    override suspend fun startLoginFlowV2(
        serverUrl: String,
        pinnedFingerprint: String?
    ): Resource<LoginFlowSession> = withContext(ioDispatcher) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val endpoint = "$cleanUrl/index.php/login/v2"

        var detectedFingerprint: String? = null
        val client = createClientForConfig(pinnedFingerprint) { fp ->
            detectedFingerprint = fp
        }

        val request = Request.Builder()
            .url(endpoint)
            .post("".toRequestBody(null))
            .header("OCS-APIRequest", "true")
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
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
        } catch (e: TofuCertificateException) {
            sslFingerprintError(e.sha256Fingerprint, e)
        } catch (e: SSLHandshakeException) {
            sslHandshakeError(detectedFingerprint, e)
        } catch (e: UnknownHostException) {
            Resource.Error(ErrorType.NO_NETWORK, "Server not found. Check Wi-Fi and server URL.", e)
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "Failed to start Login Flow v2: ${e.localizedMessage}", e)
        }
    }

    override suspend fun pollLoginFlowV2(
        token: String,
        endpoint: String,
        pinnedFingerprint: String?
    ): Resource<ServerConfig> = withContext(ioDispatcher) {
        var detectedFingerprint: String? = null
        val client = createClientForConfig(pinnedFingerprint) { fp ->
            detectedFingerprint = fp
        }

        val request = Request.Builder()
            .url("$endpoint?token=$token")
            .post("".toRequestBody(null))
            .header("OCS-APIRequest", "true")
            .header("User-Agent", USER_AGENT)
            .build()

        try {
            client.newCall(request).execute().use { response ->
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

                        val rawConfig = ServerConfig(
                            serverUrl = server.trimEnd('/'),
                            username = loginName,
                            tokenOrPassword = appPassword,
                            sslCertificateFingerprint = pinnedFingerprint,
                            isLoggedIn = true
                        )
                        // Validate & resolve canonical username/WebDAV path cleanly
                        when (val resolved = resolveAndTestConnection(rawConfig)) {
                            is Resource.Success -> Resource.Success(resolved.data)
                            else -> Resource.Success(rawConfig)
                        }
                    }
                    404 -> Resource.Loading
                    else -> {
                        Resource.Error(
                            ErrorType.UNKNOWN,
                            "Authorization polling failed with HTTP ${response.code}"
                        )
                    }
                }
            }
        } catch (e: TofuCertificateException) {
            sslFingerprintError(e.sha256Fingerprint, e)
        } catch (e: SSLHandshakeException) {
            sslHandshakeError(detectedFingerprint, e)
        } catch (e: Exception) {
            Resource.Error(ErrorType.UNKNOWN, "Error polling Login Flow v2: ${e.localizedMessage}", e)
        }
    }

    override suspend fun logout() {
        encryptedDataStore.clear()
    }
}
