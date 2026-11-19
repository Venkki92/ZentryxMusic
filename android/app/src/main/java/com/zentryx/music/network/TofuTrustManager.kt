package com.zentryx.music.network

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Custom X509TrustManager implementing Trust-On-First-Use (TOFU).
 * Validates against system CAs first. If unverified, dynamically resolves the current
 * pinned SHA-256 fingerprint via [pinnedFingerprintProvider] so certs pinned at runtime
 * take effect immediately without recreating OkHttpClients or restarting ExoPlayer.
 */
class TofuTrustManager(
    private val pinnedFingerprintProvider: () -> String?,
    private val onUntrustedCertFound: ((sha256Fingerprint: String) -> Unit)? = null
) : X509TrustManager {

    constructor(
        pinnedFingerprint: String?,
        onUntrustedCertFound: ((sha256Fingerprint: String) -> Unit)? = null
    ) : this(
        pinnedFingerprintProvider = { pinnedFingerprint },
        onUntrustedCertFound = onUntrustedCertFound
    )

    private val defaultTrustManager: X509TrustManager = run {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        factory.trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        defaultTrustManager.checkClientTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Certificate chain is empty")
        }

        try {
            // First check standard system & user root CAs
            defaultTrustManager.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            val leafCert = chain[0]
            val calculatedFingerprint = calculateSha256Fingerprint(leafCert)
            val normalizedPinned = pinnedFingerprintProvider()
                ?.replace(":", "")
                ?.trim()

            // If user previously pinned this exact fingerprint, allow it
            if (!normalizedPinned.isNullOrBlank() &&
                calculatedFingerprint.equals(normalizedPinned, ignoreCase = true)
            ) {
                return
            }

            // Notify listener of the fingerprint so the UI can prompt the user
            val formatted = formatFingerprintWithColons(calculatedFingerprint)
            onUntrustedCertFound?.invoke(formatted)
            throw TofuCertificateException(
                "Certificate is untrusted. SHA-256: $formatted",
                calculatedFingerprint
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return defaultTrustManager.acceptedIssuers
    }

    companion object {
        fun calculateSha256Fingerprint(cert: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(cert.encoded)
            return hash.joinToString("") { "%02X".format(it) }
        }

        fun formatFingerprintWithColons(hex: String): String {
            val clean = hex.replace(":", "").trim()
            return clean.chunked(2).joinToString(":")
        }
    }
}

class TofuCertificateException(
    message: String,
    val sha256Fingerprint: String
) : CertificateException(message)
