package com.prgramed.emessages

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.grapheneapps.core.designsystem.theme.GrapheneAppsTheme
import com.prgramed.emessages.navigation.MessagesNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var sendToAddress by mutableStateOf<String?>(null)
    private var sharedMessageText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            GrapheneAppsTheme {
                MessagesNavHost(
                    modifier = Modifier.fillMaxSize(),
                    sendToAddress = sendToAddress,
                    sharedMessageText = sharedMessageText,
                    onSendToHandled = {
                        sendToAddress = null
                        sharedMessageText = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SENDTO -> {
                val uri = intent.data ?: return
                val address = uri.schemeSpecificPart?.replace("-", "")?.replace(" ", "")
                if (!address.isNullOrBlank()) {
                    sendToAddress = address
                }
            }
            Intent.ACTION_SEND -> {
                // Shared text from other apps — open new message with the text pre-filled
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    sharedMessageText = sharedText
                    sendToAddress = "" // trigger new message screen
                }
            }
        }
    }
}
