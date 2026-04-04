package com.prgramed.econtacts

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
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.util.VCardParser
import com.prgramed.econtacts.navigation.ContactsNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var dialNumber by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val vcardContact = parseVCardIntent(intent)
        dialNumber = parseDialIntent(intent)

        setContent {
            GrapheneAppsTheme {
                ContactsNavHost(
                    modifier = Modifier.fillMaxSize(),
                    vcardContact = vcardContact,
                    dialNumber = dialNumber,
                    onDialHandled = { dialNumber = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newVcard = parseVCardIntent(intent)
        val newDial = parseDialIntent(intent)
        if (newVcard != null) {
            setIntent(intent)
            recreate()
        } else if (newDial != null) {
            dialNumber = newDial
        }
    }

    private fun parseDialIntent(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_DIAL && intent?.action != Intent.ACTION_CALL_BUTTON) return null
        val uri = intent.data ?: return null
        return uri.schemeSpecificPart?.replace("-", "")?.replace(" ", "")
    }

    private fun parseVCardIntent(intent: Intent?): Contact? {
        if (intent?.action != Intent.ACTION_VIEW || intent.data == null) return null
        val mimeType = intent.type ?: ""
        if (!mimeType.contains("vcard", ignoreCase = true)) return null

        return try {
            val uri = intent.data ?: return null
            val input = contentResolver.openInputStream(uri) ?: return null
            val vcfText = input.bufferedReader().readText()
            input.close()
            VCardParser.parse(vcfText)
        } catch (_: Exception) {
            null
        }
    }
}
