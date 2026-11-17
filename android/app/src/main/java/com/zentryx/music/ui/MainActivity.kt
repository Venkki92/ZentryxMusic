package com.zentryx.music.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zentryx.music.navigation.Screen
import com.zentryx.music.navigation.ZentryxNavHost
import com.zentryx.music.player.CockpitThemeMode
import com.zentryx.music.player.MbuxAccentColor
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.security.EncryptedDataStore
import com.zentryx.music.swc.SteeringWheelManager
import com.zentryx.music.ui.theme.ZentryxTheme
import com.zentryx.music.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-Activity host for Zentryx Music native Android headunit app.
 * Locked to Landscape on 1024x600 AutoChips AC8259P / Mercedes-Benz S203 display.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var encryptedDataStore: EncryptedDataStore

    @Inject
    lateinit var steeringWheelManager: SteeringWheelManager

    @Inject
    lateinit var playerManager: com.zentryx.music.player.ZentryxPlayerManager

    @Inject
    lateinit var settingsPreferences: SettingsPreferences

    private val loginViewModel: LoginViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            playerManager.onVisualizerPermissionUpdated()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        steeringWheelManager.attachPlayerManager(playerManager)
        handleIntent(intent)
        requestAutomotivePermissionsIfNeeded()

        setContent {
            val accentColor by settingsPreferences.accentColorFlow.collectAsStateWithLifecycle(initialValue = MbuxAccentColor.LIME)
            val cockpitThemeMode by settingsPreferences.cockpitThemeModeFlow.collectAsStateWithLifecycle(initialValue = CockpitThemeMode.NIGHT)
            val nightBrightness by settingsPreferences.nightBrightnessFlow.collectAsStateWithLifecycle(initialValue = 1.0f)
            val amberFilterEnabled by settingsPreferences.amberFilterEnabledFlow.collectAsStateWithLifecycle(initialValue = false)

            ZentryxTheme(
                accentColor = accentColor,
                cockpitThemeMode = cockpitThemeMode
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val startDestination by produceState<String?>(initialValue = null) {
                            value = if (encryptedDataStore.hasCredentials()) {
                                Screen.Library.route
                            } else {
                                Screen.Login.route
                            }
                        }

                        if (startDestination == null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(accentColor.hexColor))
                            }
                        } else {
                            ZentryxNavHost(startDestination = startDestination!!)
                        }
                    }

                    // Non-blocking Warm W203 Amber Night Comfort Overlay
                    if (amberFilterEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x1FF59E0B))
                        )
                    }

                    // Non-blocking In-App Night Dimmer Overlay
                    val dimAlpha = (1f - nightBrightness).coerceIn(0f, 0.65f)
                    if (dimAlpha > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = dimAlpha))
                        )
                    }
                }
            }
        }
    }

    private fun requestAutomotivePermissionsIfNeeded() {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "zentryx" && uri.host == "login-callback") {
            loginViewModel.testConnection()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (steeringWheelManager.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (steeringWheelManager.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
