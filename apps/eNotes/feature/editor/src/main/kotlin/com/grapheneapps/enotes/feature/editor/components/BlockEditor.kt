package com.grapheneapps.enotes.feature.editor.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grapheneapps.enotes.feature.editor.model.Block

@Composable
fun BlockEditor(
    block: Block,
    shouldFocus: Boolean = false,
    onTextChanged: (String) -> Unit,
    onEnter: () -> Unit,
    onDelete: () -> Unit,
    onCheckToggle: (() -> Unit)? = null,
    onPasteLines: ((List<String>) -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(shouldFocus) {
        if (shouldFocus) {
            try { focusRequester.requestFocus() } catch (_: Exception) { }
        }
    }

    val autoDir = androidx.compose.ui.text.style.TextDirection.Content

    val textStyle = when (block) {
        is Block.Heading -> when (block.level) {
            1 -> TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, textDirection = autoDir)
            2 -> TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textDirection = autoDir)
            3 -> TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, textDirection = autoDir)
            else -> TextStyle(fontSize = 16.sp, textDirection = autoDir)
        }
        else -> TextStyle(fontSize = 16.sp, textDirection = autoDir)
    }

    val prefix = when (block) {
        is Block.BulletItem -> "\u2022  "
        is Block.NumberedItem -> "${block.number}.  "
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = when (block) {
                    is Block.BulletItem -> (16 + block.indent * 16).dp
                    is Block.NumberedItem -> (16 + block.indent * 16).dp
                    is Block.ChecklistItem -> 8.dp
                    else -> 16.dp
                },
                end = 16.dp,
                top = when (block) {
                    is Block.Heading -> if (block.level == 1) 12.dp else 8.dp
                    else -> 2.dp
                },
                bottom = 2.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // Checklist checkbox
        if (block is Block.ChecklistItem) {
            IconButton(
                onClick = { onCheckToggle?.invoke() },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    if (block.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (block.checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // Bullet/number prefix
        if (prefix != null) {
            androidx.compose.material3.Text(
                text = prefix,
                style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }

        // Zero-width space sentinel to detect backspace at start of block
        val sentinel = "\u200B"
        val displayText = sentinel + block.text

        var tfv by remember(block.id, block.text) {
            mutableStateOf(TextFieldValue(
                text = displayText,
                selection = TextRange(displayText.length),
            ))
        }

        BasicTextField(
            value = tfv,
            onValueChange = { newValue ->
                val raw = newValue.text
                when {
                    // Sentinel was deleted → backspace at start
                    !raw.startsWith(sentinel) -> {
                        onDelete()
                    }
                    raw.contains('\n') -> {
                        val clean = raw.removePrefix(sentinel)
                        val lines = clean.split('\n')
                        if (lines.size > 2 && onPasteLines != null) {
                            // Multi-line paste — first line updates current block, rest create new blocks
                            onTextChanged(lines.first())
                            onPasteLines(lines.drop(1))
                        } else {
                            // Single Enter key press
                            val beforeNewline = clean.substringBefore('\n')
                            onTextChanged(beforeNewline)
                            onEnter()
                        }
                    }
                    else -> {
                        tfv = newValue
                        val clean = raw.removePrefix(sentinel)
                        onTextChanged(clean)
                    }
                }
            },
            textStyle = textStyle.merge(
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (block is Block.ChecklistItem && block.checked) TextDecoration.LineThrough else null,
                ),
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
    }
}
