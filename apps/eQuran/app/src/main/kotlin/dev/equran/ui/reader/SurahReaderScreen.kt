package dev.equran.ui.reader

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.equran.domain.model.AyahWithTranslations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurahReaderScreen(
    onBack: () -> Unit,
    onNavigateToSurah: (Int) -> Unit,
    viewModel: SurahReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val bookmarks by viewModel.bookmarkedAyahs.collectAsState()
    val memorized by viewModel.memorizedAyahs.collectAsState()
    val bookmarkedAyahSet = bookmarks.map { it.ayah }.toSet()
    val bookmarkNotes = bookmarks.associate { it.ayah to (it.note ?: "") }
    val memorizedAyahSet = memorized.map { it.ayah }.toSet()
    val listState = rememberLazyListState()

    // Note editor dialog state
    var noteDialogAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) } // surah, ayah
    var noteText by remember { mutableStateOf("") }

    // Bottom sheet states
    var showTafsirSheet by remember { mutableStateOf(false) }
    var showWordByWordSheet by remember { mutableStateOf(false) }
    val tafsirText by viewModel.tafsirText.collectAsState()
    val tafsirLoading by viewModel.tafsirLoading.collectAsState()
    val words by viewModel.words.collectAsState()
    val wordsLoading by viewModel.wordsLoading.collectAsState()

    if (showTafsirSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTafsirSheet = false; viewModel.clearTafsir() },
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Tafsir", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (tafsirLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (tafsirText != null) {
                    Text(tafsirText!!, style = MaterialTheme.typography.bodyMedium, lineHeight = 24.sp)
                } else {
                    Text("No tafsir available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showWordByWordSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWordByWordSheet = false; viewModel.clearWords() },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Word by Word", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (wordsLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (words.isNotEmpty()) {
                    words.forEach { word ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(word.translation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                word.transliteration?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                            Text(word.arabic, fontSize = 22.sp, textAlign = TextAlign.End)
                        }
                        HorizontalDivider()
                    }
                } else {
                    Text("No data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    noteDialogAyah?.let { (surah, ayah) ->
        AlertDialog(
            onDismissRequest = { noteDialogAyah = null },
            title = { Text("Bookmark Note — $surah:$ayah") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Add a note...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // Ensure bookmarked first, then save note
                    if (ayah !in bookmarkedAyahSet) {
                        viewModel.toggleBookmark(surah, ayah)
                    }
                    viewModel.updateBookmarkNote(surah, ayah, noteText)
                    noteDialogAyah = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { noteDialogAyah = null }) { Text("Cancel") }
            },
        )
    }

    // Scroll to specific ayah if requested
    LaunchedEffect(state.scrollToAyah, state.ayahs) {
        if (state.scrollToAyah > 0 && state.ayahs.isNotEmpty()) {
            val index = state.ayahs.indexOfFirst { it.ayah == state.scrollToAyah }
            if (index >= 0) listState.animateScrollToItem(index + 1) // +1 for header
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.surahMeta?.englishName ?: "",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.surahMeta != null) {
                            Text(
                                text = "${state.surahMeta!!.englishNameTranslation} \u2022 ${state.surahMeta!!.numberOfAyahs} verses",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Arabic surah name
                    state.surahMeta?.let {
                        Text(
                            text = it.name,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Bismillah (not for Surah 1 Al-Fatihah or Surah 9 At-Tawbah)
                val surahNum = state.surahMeta?.number ?: 0
                if (surahNum != 1 && surahNum != 9) {
                    item(key = "bismillah") {
                        Text(
                            text = "\u0628\u0650\u0633\u0652\u0645\u0650 \u0627\u0644\u0644\u0651\u064E\u0647\u0650 \u0627\u0644\u0631\u0651\u064E\u062D\u0652\u0645\u064E\u0640\u0646\u0650 \u0627\u0644\u0631\u0651\u064E\u062D\u0650\u064A\u0645\u0650",
                            fontSize = 28.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }

                // Verses
                items(state.ayahs, key = { "${it.surah}:${it.ayah}" }) { ayah ->
                    AyahItem(
                        ayah = ayah,
                        fontSize = state.fontSize,
                        isBookmarked = ayah.ayah in bookmarkedAyahSet,
                        isMemorized = ayah.ayah in memorizedAyahSet,
                        onBookmarkClick = { viewModel.toggleBookmark(ayah.surah, ayah.ayah) },
                        onBookmarkLongClick = {
                            noteText = bookmarkNotes[ayah.ayah] ?: ""
                            noteDialogAyah = ayah.surah to ayah.ayah
                        },
                        onMemorizeClick = { viewModel.toggleMemorized(ayah.surah, ayah.ayah) },
                        onCopyClick = { viewModel.copyVerse(ayah) },
                        onShareClick = { viewModel.shareVerse(ayah) },
                        onTafsirClick = {
                            viewModel.loadTafsir(ayah.surah, ayah.ayah)
                            showTafsirSheet = true
                        },
                        onWordByWordClick = {
                            viewModel.loadWordByWord(ayah.surah, ayah.ayah)
                            showWordByWordSheet = true
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }

                // Navigation footer
                item(key = "nav_footer") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (viewModel.hasPrevious) {
                            IconButton(onClick = { onNavigateToSurah(viewModel.previousSurah) }) {
                                Icon(Icons.Default.NavigateBefore, "Previous surah")
                            }
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                        if (viewModel.hasNext) {
                            IconButton(onClick = { onNavigateToSurah(viewModel.nextSurah) }) {
                                Icon(Icons.Default.NavigateNext, "Next surah")
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AyahItem(
    ayah: AyahWithTranslations,
    fontSize: Float = 26f,
    isBookmarked: Boolean = false,
    isMemorized: Boolean = false,
    onBookmarkClick: () -> Unit = {},
    onBookmarkLongClick: () -> Unit = {},
    onMemorizeClick: () -> Unit = {},
    onCopyClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onTafsirClick: () -> Unit = {},
    onWordByWordClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Verse number + action icons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${ayah.ayah}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .combinedClickable(
                        onClick = onBookmarkClick,
                        onLongClick = onBookmarkLongClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    "Bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onMemorizeClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Psychology,
                    "Memorize",
                    tint = if (isMemorized) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onCopyClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ContentCopy, "Copy", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onShareClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Share, "Share", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onTafsirClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MenuBook, "Tafsir", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onWordByWordClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Translate, "Word by Word", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Arabic text
        Text(
            text = ayah.arabicText,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.8f).sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )

        // Translations
        ayah.translations.forEach { translation ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = translation.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
        }
    }
}
