package com.prgramed.econtacts.feature.contacts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

data class SimInfo(
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val phoneAccountHandle: PhoneAccountHandle?,
)

@Suppress("DEPRECATION")
fun loadAvailableSims(context: Context): List<SimInfo> = try {
    val subManager = context.getSystemService(SubscriptionManager::class.java)
        ?: return emptyList()
    val telecomManager = context.getSystemService(TelecomManager::class.java)
        ?: return emptyList()
    val subscriptions = subManager.activeSubscriptionInfoList ?: emptyList()
    val phoneAccounts = try {
        telecomManager.callCapablePhoneAccounts
    } catch (_: SecurityException) {
        emptyList()
    }
    subscriptions.map { sub ->
        val handle = phoneAccounts.find { account ->
            account.id == sub.subscriptionId.toString() || account.id == sub.iccId
        }
        SimInfo(
            subscriptionId = sub.subscriptionId,
            displayName = sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
            carrierName = sub.carrierName?.toString() ?: "",
            phoneAccountHandle = handle,
        )
    }
} catch (_: SecurityException) {
    emptyList()
}

@SuppressLint("MissingPermission")
fun placeCallWithSim(context: Context, number: String, accountHandle: PhoneAccountHandle?) {
    try {
        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return
        val uri = Uri.parse("tel:$number")
        val extras = Bundle()
        if (accountHandle != null) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
        }
        telecomManager.placeCall(uri, extras)
    } catch (_: Exception) {
        context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }
}

fun placeCallDefault(context: Context, number: String) {
    val hasPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CALL_PHONE,
    ) == PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
        context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    } else {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }
}

@Composable
fun SimPickerDialog(
    sims: List<SimInfo>,
    onSimSelected: (SimInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select SIM") },
        text = {
            Column {
                sims.forEachIndexed { index, sim ->
                    TextButton(
                        onClick = { onSimSelected(sim) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = "SIM ${index + 1}: ${sim.displayName}",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (sim.carrierName.isNotBlank()) {
                                Text(
                                    text = sim.carrierName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (index < sims.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
