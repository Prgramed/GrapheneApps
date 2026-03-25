package com.prgramed.econtacts.feature.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var simPickerNumber by remember { mutableStateOf<String?>(null) }
    var availableSims by remember { mutableStateOf<List<SimInfo>>(emptyList()) }

    if (simPickerNumber != null) {
        SimPickerDialog(
            sims = availableSims,
            onSimSelected = { sim ->
                placeCallWithSim(context, simPickerNumber!!, sim.phoneAccountHandle)
                simPickerNumber = null
            },
            onDismiss = { simPickerNumber = null },
        )
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Refresh contact data when returning from edit screen
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onBack()
    }

    LaunchedEffect(uiState.shareUri) {
        uiState.shareUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/x-vcard"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share contact"))
            viewModel.onShareHandled()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete contact?") },
            text = { Text("This will permanently delete this contact.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteContact()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val contact = uiState.contact
                    if (contact != null) {
                        IconButton(onClick = { viewModel.toggleStar() }) {
                            Icon(
                                if (contact.starred) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (contact.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onEdit(contact.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.shareVCard() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.contact == null -> {
                    Text("Contact not found", modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    val contact = uiState.contact!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (contact.photoUri != null) {
                                AsyncImage(
                                    model = contact.photoUri,
                                    contentDescription = "Contact photo",
                                    modifier = Modifier.size(96.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Text(
                                    text = contact.displayName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = contact.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            val firstPhone = contact.phoneNumbers.firstOrNull()
                            val firstEmail = contact.emails.firstOrNull()

                            FilledTonalButton(
                                onClick = {
                                    firstPhone?.let {
                                        context.startActivity(
                                            Intent(Intent.ACTION_CALL, Uri.parse("tel:${it.number}")),
                                        )
                                    }
                                },
                                enabled = firstPhone != null,
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Call", modifier = Modifier.padding(start = 4.dp))
                            }

                            FilledTonalButton(
                                onClick = {
                                    firstPhone?.let {
                                        context.startActivity(
                                            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${it.number}")),
                                        )
                                    }
                                },
                                enabled = firstPhone != null,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Message", modifier = Modifier.padding(start = 4.dp))
                            }

                            FilledTonalButton(
                                onClick = {
                                    firstEmail?.let {
                                        context.startActivity(
                                            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${it.address}")),
                                        )
                                    }
                                },
                                enabled = firstEmail != null,
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Email", modifier = Modifier.padding(start = 4.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()

                        // Phone numbers — tap to call (default SIM), long-press for SIM choice
                        if (contact.phoneNumbers.isNotEmpty()) {
                            SectionHeader("Phone")
                            contact.phoneNumbers.forEach { phone ->
                                PhoneNumberRow(
                                    number = phone.number,
                                    label = phone.type.name.lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                    onCall = {
                                        placeCallDefault(context, phone.number)
                                    },
                                    onCallWithSimChoice = {
                                        val sims = loadAvailableSims(context)
                                        if (sims.size >= 2) {
                                            availableSims = sims
                                            simPickerNumber = phone.number
                                        } else {
                                            placeCallDefault(context, phone.number)
                                        }
                                    },
                                    onMessage = {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_SENDTO,
                                                Uri.parse("smsto:${phone.number}"),
                                            ),
                                        )
                                    },
                                )
                            }
                            HorizontalDivider()
                        }

                        // Emails
                        if (contact.emails.isNotEmpty()) {
                            SectionHeader("Email")
                            contact.emails.forEach { email ->
                                DetailRow(
                                    label = email.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                    value = email.address,
                                )
                            }
                            HorizontalDivider()
                        }

                        // Organization & Title
                        if (!contact.organization.isNullOrBlank() || !contact.title.isNullOrBlank()) {
                            SectionHeader("Work")
                            if (!contact.organization.isNullOrBlank()) {
                                DetailRow(label = "Organization", value = contact.organization!!)
                            }
                            if (!contact.title.isNullOrBlank()) {
                                DetailRow(label = "Title", value = contact.title!!)
                            }
                            HorizontalDivider()
                        }

                        // Birthday
                        if (!contact.birthday.isNullOrBlank()) {
                            SectionHeader("Birthday")
                            DetailRow(label = "Date", value = contact.birthday!!)
                            HorizontalDivider()
                        }

                        // Addresses
                        if (contact.addresses.isNotEmpty()) {
                            SectionHeader("Address")
                            contact.addresses.forEach { addr ->
                                val parts = listOfNotNull(
                                    addr.street.ifBlank { null },
                                    addr.city.ifBlank { null },
                                    addr.region.ifBlank { null },
                                    addr.postalCode.ifBlank { null },
                                    addr.country.ifBlank { null },
                                ).joinToString(", ")
                                DetailRow(
                                    label = addr.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                    value = parts,
                                )
                            }
                            HorizontalDivider()
                        }

                        // Websites
                        if (contact.websites.isNotEmpty()) {
                            SectionHeader("Website")
                            contact.websites.forEach { url ->
                                DetailRow(label = "URL", value = url)
                            }
                            HorizontalDivider()
                        }

                        // Groups
                        if (contact.groups.isNotEmpty()) {
                            SectionHeader("Groups")
                            contact.groups.forEach { group ->
                                Text(
                                    text = group.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            HorizontalDivider()
                        }

                        // Note
                        if (!contact.note.isNullOrBlank()) {
                            SectionHeader("Note")
                            Text(
                                text = contact.note!!,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            HorizontalDivider()
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhoneNumberRow(
    number: String,
    label: String,
    onCall: () -> Unit,
    onCallWithSimChoice: () -> Unit,
    onMessage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onCall,
                onLongClick = onCallWithSimChoice,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = number, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCall) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onMessage) {
            Icon(
                Icons.AutoMirrored.Filled.Message,
                contentDescription = "Message",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
