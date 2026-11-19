package com.zentryx.music.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zentryx.music.models.ErrorType
import com.zentryx.music.ui.theme.StatusError
import com.zentryx.music.ui.theme.StatusSuccess
import com.zentryx.music.viewmodel.LoginViewModel

/**
 * 1024x600 Landscape-optimized Login Screen for AutoChips AC8259P / Mercedes-Benz S203 headunit.
 * Features a split two-column layout with large touch targets (>= 54dp) suitable for driving ergonomics.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
    onUseLocalOnly: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var passwordVisible by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    // Trigger navigation cleanly when Nextcloud Login Flow v2 polling completes
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            onLoginSuccess()
        }
    }

    // Trust-On-First-Use (TOFU) SSL Pinning Alert Dialog
    if (uiState.untrustedCertificateFingerprint != null) {
        val fingerprint = uiState.untrustedCertificateFingerprint!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissUntrustedCertificate() },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = accent) },
            title = { Text("Trust Self-Signed Certificate?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "An untrusted or self-signed certificate was presented by the server. " +
                                "Verify that this SHA-256 fingerprint matches your Nextcloud host:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = fingerprint,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.acceptUntrustedCertificate(fingerprint) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Trust Certificate", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUntrustedCertificate() }) {
                    Text("Cancel", color = accent)
                }
            }
        )
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // LEFT COLUMN: Branding & One-Touch Login Flow
        // ==========================================
        Surface(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accent,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.Black)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Zentryx Music",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Nextcloud Streamer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Connect directly to your private Nextcloud library over WebDAV.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }

                // Status or Diagnostic Banner
                if (uiState.errorMessage != null) {
                    StatusCard(
                        icon = Icons.Default.Error,
                        iconTint = StatusError,
                        title = when (uiState.errorType) {
                            ErrorType.BAD_URL -> "Invalid Server URL"
                            ErrorType.NO_NETWORK -> "Network Unreachable"
                            ErrorType.SSL_ERROR -> "Certificate Untrusted"
                            ErrorType.UNAUTHORIZED -> "Invalid Credentials"
                            ErrorType.WEBDAV_UNREACHABLE -> "WebDAV Not Found"
                            ErrorType.TIMEOUT -> "Connection Timed Out"
                            else -> "Connection Error"
                        },
                        message = uiState.errorMessage!!,
                        backgroundColor = StatusError.copy(alpha = 0.12f)
                    )
                } else if (uiState.testSuccessMessage != null) {
                    StatusCard(
                        icon = Icons.Default.CheckCircle,
                        iconTint = StatusSuccess,
                        title = "Connected",
                        message = uiState.testSuccessMessage!!,
                        backgroundColor = StatusSuccess.copy(alpha = 0.12f)
                    )
                } else if (uiState.isPollingLoginFlow) {
                    StatusCard(
                        icon = Icons.Default.Refresh,
                        iconTint = accent,
                        title = "Waiting for Browser Approval",
                        message = "Authorize Zentryx Music in your browser. Polling Nextcloud server…",
                        backgroundColor = accent.copy(alpha = 0.12f),
                        showProgress = true
                    )
                } else {
                    StatusCard(
                        icon = Icons.Default.Security,
                        iconTint = accent,
                        title = "Hardware Keystore Protected",
                        message = "Passwords and tokens are encrypted with AES-256-GCM in Android KeyStore.",
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Quick Browser Login (Login Flow v2) Button
                Column {
                    Button(
                        onClick = {
                            viewModel.startLoginFlowV2 { loginUrl ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp), // Ergonomic 54dp automotive touch target
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        enabled = !uiState.isLoggingIn && !uiState.isPollingLoginFlow
                    ) {
                        if (uiState.isPollingLoginFlow) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Authorizing in browser…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log in via Browser (Flow v2)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (uiState.isPollingLoginFlow) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = { viewModel.cancelLoginFlow() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel Browser Login", color = StatusError, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onUseLocalOnly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Local USB / Device Audio Only", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // ==========================================
        // RIGHT COLUMN: Manual Credentials Form
        // ==========================================
        Surface(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Manual Credentials / App Password",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Server URL input
                OutlinedTextField(
                    value = uiState.serverUrl,
                    onValueChange = viewModel::onServerUrlChanged,
                    label = { Text("Nextcloud URL") },
                    placeholder = { Text("https://cloud.example.com") },
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null, tint = accent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent)
                )

                // Username input
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = viewModel::onUsernameChanged,
                    label = { Text("Username or Email") },
                    placeholder = { Text("alice or alice@example.com") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = accent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent)
                )

                // Password / App-password input
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = { Text("Password or App Password") },
                    placeholder = { Text("••••••••••••") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = accent) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent)
                )

                // Music Folder Root input
                OutlinedTextField(
                    value = uiState.musicFolder,
                    onValueChange = viewModel::onMusicFolderChanged,
                    label = { Text("Default Music Folders (comma-separated)") },
                    placeholder = { Text("/WoW Radio, /Music") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, tint = accent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Test Connection button
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.testConnection()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !uiState.isTesting && !uiState.isLoggingIn
                    ) {
                        if (uiState.isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing…", fontSize = 13.sp)
                        } else {
                            Text("Test WebDAV", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Sign In Button
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.loginManual(onLoginSuccess)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        enabled = !uiState.isTesting && !uiState.isLoggingIn
                    ) {
                        if (uiState.isLoggingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Signing In…", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Sign In", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    backgroundColor: Color,
    showProgress: Boolean = false
) {
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = iconTint, strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
            }
        }
    }
}
