package com.prgramed.econtacts.feature.contactedit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.prgramed.econtacts.domain.model.AddressType
import com.prgramed.econtacts.domain.model.EmailType
import com.prgramed.econtacts.domain.model.PhoneType
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        if (success) {
            cameraUri?.let { viewModel.onPhotoSelected(it.toString()) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.onPhotoSelected(it.toString()) }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onSaved()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.contactId == 0L) "New contact" else "Edit contact")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isLoading,
                    ) {
                        Text("Save")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Error
            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Photo
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (uiState.photoUri != null) {
                        AsyncImage(
                            model = uiState.photoUri,
                            contentDescription = "Contact photo",
                            modifier = Modifier.size(80.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            text = uiState.displayName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                        try {
                            val photoFile = File(context.cacheDir, "contact_photo_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                photoFile,
                            )
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        } catch (_: Exception) {
                            // Failed to launch camera
                        }
                    }) {
                        Text("Camera")
                    }
                    TextButton(onClick = {
                        galleryLauncher.launch("image/*")
                    }) {
                        Text("Gallery")
                    }
                    if (uiState.photoUri != null) {
                        TextButton(onClick = {
                            viewModel.onPhotoSelected(null)
                        }) {
                            Text("Remove")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Name
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = { viewModel.onNameChanged(it) },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Phone numbers
            Text("Phone numbers", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            uiState.phoneNumbers.forEachIndexed { index, phone ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = phone.number,
                        onValueChange = { viewModel.onPhoneChanged(index, it) },
                        label = { Text("Phone") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PhoneTypePicker(
                        selected = phone.type,
                        onSelected = { viewModel.onPhoneTypeChanged(index, it) },
                    )
                    if (uiState.phoneNumbers.size > 1) {
                        IconButton(onClick = { viewModel.removePhone(index) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Remove")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            TextButton(onClick = { viewModel.addPhone() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Add phone")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Emails
            Text("Emails", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            uiState.emails.forEachIndexed { index, email ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = email.address,
                        onValueChange = { viewModel.onEmailChanged(index, it) },
                        label = { Text("Email") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    EmailTypePicker(
                        selected = email.type,
                        onSelected = { viewModel.onEmailTypeChanged(index, it) },
                    )
                    IconButton(onClick = { viewModel.removeEmail(index) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Remove")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            TextButton(onClick = { viewModel.addEmail() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Add email")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Organization & Title
            Text("Organization", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.organization,
                onValueChange = { viewModel.onOrganizationChanged(it) },
                label = { Text("Company") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.onTitleChanged(it) },
                label = { Text("Job title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Birthday
            OutlinedTextField(
                value = uiState.birthday,
                onValueChange = { viewModel.onBirthdayChanged(it) },
                label = { Text("Birthday") },
                placeholder = { Text("YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Addresses
            Text("Addresses", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            uiState.addresses.forEachIndexed { index, addr ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AddressTypePicker(
                            selected = addr.type,
                            onSelected = { viewModel.onAddressTypeChanged(index, it) },
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.removeAddress(index) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Remove address")
                        }
                    }
                    OutlinedTextField(
                        value = addr.street,
                        onValueChange = { viewModel.onAddressFieldChanged(index, "street", it) },
                        label = { Text("Street") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = addr.city,
                            onValueChange = { viewModel.onAddressFieldChanged(index, "city", it) },
                            label = { Text("City") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = addr.region,
                            onValueChange = { viewModel.onAddressFieldChanged(index, "region", it) },
                            label = { Text("Region") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = addr.postalCode,
                            onValueChange = { viewModel.onAddressFieldChanged(index, "postalCode", it) },
                            label = { Text("Postal code") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = addr.country,
                            onValueChange = { viewModel.onAddressFieldChanged(index, "country", it) },
                            label = { Text("Country") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }
            }

            TextButton(onClick = { viewModel.addAddress() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Add address")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Websites
            Text("Websites", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            uiState.websites.forEachIndexed { index, url ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { viewModel.onWebsiteChanged(index, it) },
                        label = { Text("URL") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(onClick = { viewModel.removeWebsite(index) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Remove")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            TextButton(onClick = { viewModel.addWebsite() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(" Add website")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Note
            OutlinedTextField(
                value = uiState.note,
                onValueChange = { viewModel.onNoteChanged(it) },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneTypePicker(
    selected: PhoneType,
    onSelected: (PhoneType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.width(120.dp),
    ) {
        OutlinedTextField(
            value = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PhoneType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressTypePicker(
    selected: AddressType,
    onSelected: (AddressType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.width(120.dp),
    ) {
        OutlinedTextField(
            value = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AddressType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailTypePicker(
    selected: EmailType,
    onSelected: (EmailType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.width(120.dp),
    ) {
        OutlinedTextField(
            value = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EmailType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}
