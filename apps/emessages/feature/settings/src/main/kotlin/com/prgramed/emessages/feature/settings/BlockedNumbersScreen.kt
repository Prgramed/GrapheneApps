package com.prgramed.emessages.feature.settings

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BlockedNumber(val id: Long, val number: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var blockedNumbers by remember { mutableStateOf<List<BlockedNumber>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var numberToAdd by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            blockedNumbers = withContext(Dispatchers.IO) { loadBlockedNumbers(context) }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                numberToAdd = ""
            },
            title = { Text("Block a number") },
            text = {
                OutlinedTextField(
                    value = numberToAdd,
                    onValueChange = { numberToAdd = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (numberToAdd.isNotBlank()) {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    blockNumber(context, numberToAdd.trim())
                                }
                                showAddDialog = false
                                numberToAdd = ""
                                refresh()
                            }
                        }
                    },
                ) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    numberToAdd = ""
                }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Blocked numbers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Block a number")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (blockedNumbers.isEmpty()) {
                Text(
                    text = "No blocked numbers",
                    modifier = Modifier
                        .padding(32.dp)
                        .align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(blockedNumbers, key = { it.id }) { blocked ->
                        ListItem(
                            headlineContent = { Text(blocked.number) },
                            trailingContent = {
                                IconButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            unblockNumber(context, blocked.id)
                                        }
                                        refresh()
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Unblock",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun loadBlockedNumbers(context: Context): List<BlockedNumber> {
    val list = mutableListOf<BlockedNumber>()
    try {
        context.contentResolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(
                BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
            ),
            null, null, null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
            val numIdx = cursor.getColumnIndexOrThrow(
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
            )
            while (cursor.moveToNext()) {
                list.add(BlockedNumber(cursor.getLong(idIdx), cursor.getString(numIdx) ?: ""))
            }
        }
    } catch (_: Exception) {
    }
    return list
}

private fun blockNumber(context: Context, number: String) {
    try {
        val values = ContentValues().apply {
            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        }
        context.contentResolver.insert(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI, values,
        )
    } catch (_: Exception) {
    }
}

private fun unblockNumber(context: Context, id: Long) {
    try {
        val uri = ContentUris.withAppendedId(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI, id,
        )
        context.contentResolver.delete(uri, null, null)
    } catch (_: Exception) {
    }
}
