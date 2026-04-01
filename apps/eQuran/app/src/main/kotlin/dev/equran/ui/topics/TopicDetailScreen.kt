package dev.equran.ui.topics

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun TopicDetailScreen(
    onBack: () -> Unit,
    onVerseClick: (surah: Int, ayah: Int) -> Unit,
    viewModel: TopicDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val bookmarks by viewModel.allBookmarks.collectAsState()
    val memorized by viewModel.allMemorized.collectAsState()
    val bookmarkedSet = bookmarks.map { it.surah to it.ayah }.toSet()
    val memorizedSet = memorized.map { it.surah to it.ayah }.toSet()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.topic?.nameEn ?: "", style = MaterialTheme.typography.titleMedium)
                        if (state.topic?.nameAr != null) {
                            Text(state.topic!!.nameAr!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // Topic info header
                item(key = "header") {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.topic?.description != null) {
                            Text(
                                text = state.topic!!.description!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = "${state.verses.size} verses from ${state.surahCount} surahs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    HorizontalDivider()
                }

                items(state.verses, key = { "${it.surah}:${it.ayah}" }) { ayah ->
                    TopicAyahItem(
                        ayah = ayah, fontSize = state.fontSize, onVerseClick = onVerseClick,
                        isBookmarked = (ayah.surah to ayah.ayah) in bookmarkedSet,
                        isMemorized = (ayah.surah to ayah.ayah) in memorizedSet,
                        onBookmarkClick = { viewModel.toggleBookmark(ayah.surah, ayah.ayah) },
                        onMemorizeClick = { viewModel.toggleMemorized(ayah.surah, ayah.ayah) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun TopicAyahItem(
    ayah: AyahWithTranslations,
    fontSize: Float,
    onVerseClick: (Int, Int) -> Unit,
    isBookmarked: Boolean = false,
    isMemorized: Boolean = false,
    onBookmarkClick: () -> Unit = {},
    onMemorizeClick: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Verse reference + action icons
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${ayah.surah}:${ayah.ayah}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onBookmarkClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    "Bookmark",
                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onMemorizeClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Psychology,
                    "Memorize",
                    tint = if (isMemorized) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = ayah.arabicText,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.8f).sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )

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
