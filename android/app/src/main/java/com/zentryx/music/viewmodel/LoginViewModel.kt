package com.zentryx.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zentryx.music.models.ErrorType
import com.zentryx.music.models.Resource
import com.zentryx.music.models.ServerConfig
import com.zentryx.music.repository.AuthRepository
import com.zentryx.music.repository.LoginFlowSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class PendingTofuAction {
    NONE,
    TEST,
    MANUAL_LOGIN,
    FLOW_V2
}

data class LoginUiState(
    val serverUrl: String = "https://cloud.zentryx.bg",
    val username: String = "",
    val password: String = "",
    val musicFolder: String = "/Music",
    val isTesting: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isPollingLoginFlow: Boolean = false,
    val loginFlowSession: LoginFlowSession? = null,
    val testSuccessMessage: String? = null,
    val errorMessage: String? = null,
    val errorType: ErrorType? = null,
    val untrustedCertificateFingerprint: String? = null,
    val pinnedFingerprint: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null
    private var pendingAction: PendingTofuAction = PendingTofuAction.NONE
    private var pendingBrowserLauncher: ((String) -> Unit)? = null
    private var pendingManualSuccessCallback: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            authRepository.serverConfig.collect { config ->
                if (config != null && config.isLoggedIn) {
                    _uiState.update {
                        it.copy(
                            serverUrl = config.serverUrl,
                            username = config.username,
                            musicFolder = config.musicFolderPath,
                            pinnedFingerprint = config.sslCertificateFingerprint,
                            isLoggedIn = true
                        )
                    }
                }
            }
        }
    }

    fun onServerUrlChanged(value: String) {
        _uiState.update { it.copy(serverUrl = value, errorMessage = null, testSuccessMessage = null) }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null, testSuccessMessage = null) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null, testSuccessMessage = null) }
    }

    fun onMusicFolderChanged(value: String) {
        _uiState.update { it.copy(musicFolder = value) }
    }

    private fun sanitizeServerUrl(rawUrl: String): String {
        var cleanUrl = rawUrl.trim().trimEnd('/')
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        if (cleanUrl.contains("www.cloud.zentryx.bg")) {
            cleanUrl = cleanUrl.replace("www.cloud.zentryx.bg", "cloud.zentryx.bg")
        }
        return cleanUrl
    }

    private fun buildConfigFromCurrentInput(): ServerConfig {
        val cleanUrl = sanitizeServerUrl(_uiState.value.serverUrl)
        return ServerConfig(
            serverUrl = cleanUrl,
            username = _uiState.value.username.trim(),
            tokenOrPassword = _uiState.value.password,
            sslCertificateFingerprint = _uiState.value.pinnedFingerprint,
            musicFolderPath = _uiState.value.musicFolder.trim().ifEmpty { "/Music" }
        )
    }

    fun testConnection() {
        val config = buildConfigFromCurrentInput()
        if (config.serverUrl.isBlank() || config.username.isBlank() || config.tokenOrPassword.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please enter Server URL, Username, and Password.",
                    errorType = ErrorType.BAD_URL
                )
            }
            return
        }

        pendingAction = PendingTofuAction.TEST
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, errorMessage = null, testSuccessMessage = null) }
            when (val result = authRepository.resolveAndTestConnection(config)) {
                is Resource.Success -> {
                    val resolved = result.data
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            username = resolved.username,
                            testSuccessMessage = buildString {
                                append("WebDAV connected (${resolved.username})")
                                if (resolved.useLegacyWebDav) append(" · Legacy WebDAV")
                            },
                            errorMessage = null
                        )
                    }
                }
                is Resource.Error -> {
                    val fingerprint = extractFingerprintFromMessage(result.message)
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            errorMessage = result.message,
                            errorType = result.errorType,
                            untrustedCertificateFingerprint = fingerprint
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun loginManual(onSuccess: () -> Unit) {
        val config = buildConfigFromCurrentInput()
        if (config.serverUrl.isBlank() || config.username.isBlank() || config.tokenOrPassword.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "All fields are required for manual login.",
                    errorType = ErrorType.BAD_URL
                )
            }
            return
        }

        pendingAction = PendingTofuAction.MANUAL_LOGIN
        pendingManualSuccessCallback = onSuccess
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, errorMessage = null, testSuccessMessage = null) }
            when (val result = authRepository.resolveAndTestConnection(config)) {
                is Resource.Success -> {
                    val resolved = result.data.copy(
                        sslCertificateFingerprint = _uiState.value.pinnedFingerprint,
                        musicFolderPath = config.musicFolderPath,
                        isLoggedIn = true
                    )
                    authRepository.saveCredentials(resolved)
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            isLoggedIn = true,
                            username = resolved.username
                        )
                    }
                    onSuccess()
                }
                is Resource.Error -> {
                    val fingerprint = extractFingerprintFromMessage(result.message)
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            errorMessage = result.message,
                            errorType = result.errorType,
                            untrustedCertificateFingerprint = fingerprint
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    fun startLoginFlowV2(onOpenBrowser: (url: String) -> Unit) {
        val rawUrl = _uiState.value.serverUrl.trim()
        if (rawUrl.isBlank()) {
            _uiState.update {
                it.copy(
                    errorMessage = "Please enter your Nextcloud Server URL first.",
                    errorType = ErrorType.BAD_URL
                )
            }
            return
        }
        val cleanUrl = sanitizeServerUrl(rawUrl)
        val pinned = _uiState.value.pinnedFingerprint
        pendingAction = PendingTofuAction.FLOW_V2
        pendingBrowserLauncher = onOpenBrowser

        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, errorMessage = null) }
            when (val sessionResult = authRepository.startLoginFlowV2(cleanUrl, pinned)) {
                is Resource.Success -> {
                    val session = sessionResult.data
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            isPollingLoginFlow = true,
                            loginFlowSession = session
                        )
                    }
                    onOpenBrowser(session.loginUrl)
                    startPolling(session)
                }
                is Resource.Error -> {
                    val fingerprint = extractFingerprintFromMessage(sessionResult.message)
                    _uiState.update {
                        it.copy(
                            isLoggingIn = false,
                            errorMessage = sessionResult.message,
                            errorType = sessionResult.errorType,
                            untrustedCertificateFingerprint = fingerprint
                        )
                    }
                }
                is Resource.Loading -> Unit
            }
        }
    }

    private fun startPolling(session: LoginFlowSession) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (_uiState.value.isPollingLoginFlow) {
                delay(2000)
                val pinned = _uiState.value.pinnedFingerprint
                when (val pollResult = authRepository.pollLoginFlowV2(session.token, session.pollEndpoint, pinned)) {
                    is Resource.Success -> {
                        val finalConfig = pollResult.data.copy(
                            sslCertificateFingerprint = pinned ?: pollResult.data.sslCertificateFingerprint,
                            musicFolderPath = _uiState.value.musicFolder.trim().ifEmpty { "/Music" }
                        )
                        authRepository.saveCredentials(finalConfig)
                        _uiState.update {
                            it.copy(
                                isPollingLoginFlow = false,
                                loginFlowSession = null,
                                isLoggedIn = true,
                                serverUrl = finalConfig.serverUrl,
                                username = finalConfig.username,
                                pinnedFingerprint = finalConfig.sslCertificateFingerprint
                            )
                        }
                        break
                    }
                    is Resource.Error -> {
                        val fingerprint = extractFingerprintFromMessage(pollResult.message)
                        _uiState.update {
                            it.copy(
                                isPollingLoginFlow = false,
                                errorMessage = pollResult.message,
                                errorType = pollResult.errorType,
                                untrustedCertificateFingerprint = fingerprint
                            )
                        }
                        break
                    }
                    is Resource.Loading -> {
                        // Continue waiting
                    }
                }
            }
        }
    }

    fun cancelLoginFlow() {
        pollJob?.cancel()
        _uiState.update { it.copy(isPollingLoginFlow = false, loginFlowSession = null) }
    }

    fun acceptUntrustedCertificate(fingerprint: String) {
        _uiState.update {
            it.copy(
                pinnedFingerprint = fingerprint,
                untrustedCertificateFingerprint = null,
                errorMessage = null
            )
        }
        when (pendingAction) {
            PendingTofuAction.MANUAL_LOGIN -> {
                pendingManualSuccessCallback?.let { loginManual(it) } ?: testConnection()
            }
            PendingTofuAction.FLOW_V2 -> {
                pendingBrowserLauncher?.let { startLoginFlowV2(it) } ?: testConnection()
            }
            else -> testConnection()
        }
    }

    fun dismissUntrustedCertificate() {
        _uiState.update { it.copy(untrustedCertificateFingerprint = null) }
    }

    private fun extractFingerprintFromMessage(msg: String): String? {
        val marker = "SHA-256 Fingerprint:\n"
        if (msg.contains(marker)) {
            return msg.substringAfter(marker).trim().lines().firstOrNull()
        }
        return null
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
    }
}
