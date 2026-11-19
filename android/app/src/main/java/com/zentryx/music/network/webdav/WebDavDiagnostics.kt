package com.zentryx.music.network.webdav

/**
 * Step-by-step Nextcloud WebDAV scan diagnostic models and permission decoders.
 */
enum class DiagnosticStepStatus {
    RUNNING,
    PASS,
    WARN,
    FAIL
}

data class DiagnosticProbeEntry(
    val id: String,
    val stepTitle: String,
    val targetPath: String,
    val httpStatusCode: Int?,
    val permissionsLabel: String,
    val rawPermissions: String? = null,
    val detailMessage: String,
    val latencyMs: Long = 0L,
    val status: DiagnosticStepStatus = DiagnosticStepStatus.RUNNING
)

data class ScanDiagnosticsState(
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val currentStepText: String = "",
    val summaryMessage: String? = null,
    val entries: List<DiagnosticProbeEntry> = emptyList()
)

data class WebDavProbeResult(
    val url: String,
    val httpStatusCode: Int?,
    val selfPermissions: String?,
    val davHeader: String?,
    val items: List<WebDavItem>,
    val latencyMs: Long,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = httpStatusCode == 207 || (httpStatusCode in 200..299)
}

object WebDavPermissionDecoder {
    fun decode(rawPermissions: String?, httpStatusCode: Int?): String {
        val raw = rawPermissions?.trim()?.uppercase()
        if (!raw.isNullOrEmpty()) {
            val capabilities = buildList {
                if ('G' in raw) add("Read")
                if ('W' in raw) add("Write")
                if ('C' in raw) add("Create Files")
                if ('K' in raw) add("Create Folders")
                if ('D' in raw) add("Delete")
                if ('N' in raw) add("Rename")
                if ('V' in raw) add("Move")
                if ('S' in raw) add("Shared")
                if ('M' in raw) add("External Mount")
            }
            val summary = if (capabilities.isEmpty()) "Accessible" else capabilities.joinToString(", ")
            return "$summary [$raw]"
        }
        return when (httpStatusCode) {
            207, 200 -> "Readable (PROPFIND 207 OK)"
            401 -> "Unauthorized (401 — check credentials / app password)"
            403 -> "Forbidden (403 — folder access denied)"
            404 -> "Not Found (404 — folder missing)"
            405 -> "Method Not Allowed (405)"
            in 500..599 -> "Server Error (HTTP $httpStatusCode)"
            null -> "Connection / SSL Failed"
            else -> "HTTP $httpStatusCode"
        }
    }
}
