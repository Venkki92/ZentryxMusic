package com.zentryx.music.models

/**
 * State and metrics for the background WorkManager library scan.
 */
data class ScanProgress(
    val status: ScanStatus = ScanStatus.IDLE,
    val currentFolder: String = "",
    val filesDiscovered: Int = 0,
    val filesProcessed: Int = 0,
    val percentComplete: Float = 0f,
    val errorMessage: String? = null
) {
    val isScanning: Boolean get() = status == ScanStatus.SCANNING || status == ScanStatus.CONNECTING

    companion object {
        val Idle = ScanProgress()
    }
}

enum class ScanStatus {
    IDLE,
    CONNECTING,
    SCANNING,
    COMPLETED,
    CANCELLED,
    ERROR
}
