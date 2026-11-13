package com.zentryx.music.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zentryx.music.navigation.Screen
import com.zentryx.music.navigation.ZentryxNavHost
import com.zentryx.music.security.EncryptedDataStore
import com.zentryx.music.swc.SteeringWheelManager
import com.zentryx.music.ui.theme.ZentryxLime
import com.zentryx.music.ui.theme.ZentryxTheme
import com.zentryx.music.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-Activity architecture host for Zentryx Music.
 * Locked to Landscape on 1024x600 AutoChips AC8259P / Mercedes-Benz S203 headunit.
 * Directly restores Library and previous playback position on ignition ON within 2 seconds.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var encryptedDataStore: EncryptedDataStore

    @Inject
    lateinit var steeringWheelManager: SteeringWheelManager

    @Inject
    lateinit var playerManager: com.zentryx.music.player.ZentryxPlayerManager

    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        steeringWheelManager.attachPlayerManager(playerManager)
        handleIntent(intent)

        setContent {
            ZentryxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val startDestination by produceState<String?>(initialValue = null) {
                        value = if (encryptedDataStore.hasCredentials()) {
                            Screen.Library.route
                        } else {
                            Screen.Login.route
                        }
                    }

                    if (startDestination == null) {
                        // Fast splash/loader for sub-2s startup
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ZentryxLime)
                        }
                    } else {
                        ZentryxNavHost(startDestination = startDestination!!)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == "zentryx" && uri.host == "login-callback") {
            // Trigger login flow completion if returned via browser deep-link
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
