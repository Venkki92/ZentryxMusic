package com.zentryx.music.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class TofuTrustManagerTest {

    // Test self-signed X.509 certificate (CN=test.zentryx.local) for JVM TOFU verification
    private val testCertPem = """
        -----BEGIN CERTIFICATE-----
        MIICFjCCAX+gAwIBAgIUXWoBUDyfA6EJYEwq+PPUN4lMQIkwDQYJKoZIhvcNAQEL
        BQAwHTEbMBkGA1UEAwwSdGVzdC56ZW50cnl4LmxvY2FsMB4XDTI2MTExODE1NTM1
        MloXDTM2MTExNTE1NTM1MlowHTEbMBkGA1UEAwwSdGVzdC56ZW50cnl4LmxvY2Fs
        MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQC/qJhmllmUGJ1plflohd5JG+JK
        lUUr2Xt2UufzMRJsxUtnPA41L9Q7+TdZOOH+A7IQagrn5p/G1Ij+NQVB54C2rU6G
        HOKYYlmqfW9QRPF8d0rrvHEZztuWTt5EaN/KaMfCle+5v+aSyNoFo2DS8hZR7KcG
        ID8q9nUcnFiyiCqk6wIDAQABo1MwUTAdBgNVHQ4EFgQUXai8VF0LNsfkRWNwolsm
        3dceWRkwHwYDVR0jBBgwFoAUXai8VF0LNsfkRWNwolsm3dceWRkwDwYDVR0TAQH/
        BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOBgQA83352krGWloQPuZc3lmZHltP3RzRx
        oS7hKjJeYeUOMrrSa+yeraA1B9diM5cpIrVEXysiehR2KID8P82WCAaSnMH1ZrZl
        3NNHQjJHeyjpO07O0qoqo/pDrjPu+GiXe3TpNNVF5ayQm/miX1p5/jbtvT9RuW+y
        HrlKDRRli0Mkhg==
        -----END CERTIFICATE-----
    """.trimIndent()

    @Test
    fun formatAndVerifyDynamicPinningBehavior() {
        val formatted = TofuTrustManager.formatFingerprintWithColons("aabbccddeeff")
        assertEquals("aa:bb:cc:dd:ee:ff", formatted)
    }

    @Test
    fun dynamicFingerprintProviderAllowsCertImmediatelyOncePinned() {
        var currentPin: String? = null
        var capturedUntrusted: String? = null

        val trustManager = TofuTrustManager(
            pinnedFingerprintProvider = { currentPin },
            onUntrustedCertFound = { fp -> capturedUntrusted = fp }
        )

        val cf = CertificateFactory.getInstance("X.509")
        val cert = cf.generateCertificate(ByteArrayInputStream(testCertPem.toByteArray())) as X509Certificate

        // 1. Before pinning, untrusted cert throws TofuCertificateException and captures SHA-256 fingerprint
        try {
            trustManager.checkServerTrusted(arrayOf(cert), "RSA")
            fail("Expected TofuCertificateException when no fingerprint is pinned")
        } catch (expected: TofuCertificateException) {
            assertNotNull(capturedUntrusted)
            assertEquals(
                expected.sha256Fingerprint,
                TofuTrustManager.calculateSha256Fingerprint(cert)
            )
        }

        // 2. Dynamically update provider with colon-formatted lowercase fingerprint -> must succeed immediately
        currentPin = capturedUntrusted?.lowercase()
        trustManager.checkServerTrusted(arrayOf(cert), "RSA")
    }
}
