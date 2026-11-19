package com.zentryx.music.network.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavDiagnosticsTest {

    @Test
    fun `decode formats full Nextcloud folder permissions`() {
        val decoded = WebDavPermissionDecoder.decode("rgdnvckm", 207)
        assertTrue(decoded.contains("Read"))
        assertTrue(decoded.contains("Create Folders"))
        assertTrue(decoded.contains("Delete"))
        assertTrue(decoded.contains("Rename"))
        assertTrue(decoded.contains("Move"))
        assertTrue(decoded.contains("External Mount"))
        assertTrue(decoded.endsWith("[RGDNVCKM]"))
    }

    @Test
    fun `decode formats read-only shared mount permissions`() {
        val decoded = WebDavPermissionDecoder.decode("GS", 207)
        assertEquals("Read, Shared [GS]", decoded)
    }

    @Test
    fun `decode falls back to HTTP status descriptions when oc permissions omitted`() {
        assertEquals("Readable (PROPFIND 207 OK)", WebDavPermissionDecoder.decode(null, 207))
        assertTrue(WebDavPermissionDecoder.decode("", 401).contains("401"))
        assertTrue(WebDavPermissionDecoder.decode(null, 403).contains("Forbidden"))
        assertTrue(WebDavPermissionDecoder.decode(null, 404).contains("Not Found"))
        assertEquals("Connection / SSL Failed", WebDavPermissionDecoder.decode(null, null))
    }
}
