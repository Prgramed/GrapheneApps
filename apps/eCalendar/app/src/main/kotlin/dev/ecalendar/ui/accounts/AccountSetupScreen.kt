package dev.ecalendar.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ecalendar.domain.model.AccountType
import dev.ecalendar.ui.accounts.ZohoRegion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSetupScreen(
    viewModel: AccountSetupViewModel,
    onDismiss: () -> Unit,
) {
    val accountType by viewModel.accountType.collectAsStateWithLifecycle()
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val username by viewModel.username.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val zohoRegion by viewModel.zohoRegion.collectAsStateWithLifecycle()
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Edit Account" else "Add Account") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Account type selector
            Text(
                "Account type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountType.entries.forEach { type ->
                    val label = when (type) {
                        AccountType.SYNOLOGY -> "Synology"
                        AccountType.ZOHO -> "Zoho"
                        AccountType.ICAL_SUBSCRIPTION -> "iCal"
                    }
                    val icon = when (type) {
                        AccountType.SYNOLOGY -> Icons.Default.Storage
                        AccountType.ZOHO -> Icons.Default.Cloud
                        AccountType.ICAL_SUBSCRIPTION -> Icons.Default.Subscriptions
                    }
                    FilterChip(
                        selected = accountType == type,
                        onClick = { viewModel.setAccountType(type) },
                        label = { Text(label) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Type-specific fields
            when (accountType) {
                AccountType.SYNOLOGY -> {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { viewModel.setDisplayName(it) },
                        label = { Text("Display name") },
                        placeholder = { Text("My Synology") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { viewModel.setServerUrl(it) },
                        label = { Text("CalDAV URL") },
                        placeholder = { Text("https://nas.example.com/caldav/") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { viewModel.setUsername(it) },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.setPassword(it) },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }

                AccountType.ZOHO -> {
                    Text(
                        "Region",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ZohoRegion.entries.forEach { region ->
                            FilterChip(
                                selected = zohoRegion == region,
                                onClick = { viewModel.setZohoRegion(region) },
                                label = { Text(region.label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { viewModel.setUsername(it) },
                        label = { Text("Zoho email") },
                        placeholder = { Text("user@${zohoRegion.host.removePrefix("zoho.")}") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CalDAV URL: https://calendar.${zohoRegion.host}/caldav/…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.setPassword(it) },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Regular Zoho password. If 2FA is enabled, generate an application password at accounts.zoho.* → Security.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }

                AccountType.ICAL_SUBSCRIPTION -> {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { viewModel.setDisplayName(it) },
                        label = { Text("Display name") },
                        placeholder = { Text("Shared Calendar") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { viewModel.setServerUrl(it) },
                        label = { Text("iCal URL (.ics)") },
                        placeholder = { Text("https://calendar.example.com/cal.ics") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Read-only subscription — events are fetched but cannot be edited",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Verify button
            Button(
                onClick = { viewModel.verifyAndSave() },
                enabled = setupState !is SetupState.Verifying,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (setupState is SetupState.Verifying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Verifying...")
                } else {
                    Text(if (viewModel.isEditMode) "Save Changes" else "Verify & Save")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Result
            when (val s = setupState) {
                is SetupState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = s.message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is SetupState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Account added successfully!",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Discovered ${s.calendars.size} calendar(s):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            s.calendars.forEach { cal ->
                                Text(
                                    text = "  \u2022 ${cal.displayName}${if (!cal.isWritable) " (read-only)" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Done")
                    }
                }

                else -> {}
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
