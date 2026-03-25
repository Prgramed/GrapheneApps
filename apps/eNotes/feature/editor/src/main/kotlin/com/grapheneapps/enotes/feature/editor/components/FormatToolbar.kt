package com.grapheneapps.enotes.feature.editor.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun FormatToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrikethrough: () -> Unit,
    onHeading: () -> Unit,
    onBulletList: () -> Unit,
    onNumberedList: () -> Unit,
    onChecklist: () -> Unit,
    onCodeBlock: () -> Unit = {},
    onDivider: () -> Unit = {},
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            ToolbarButton(Icons.Default.Undo, "Undo", onUndo)
            ToolbarButton(Icons.Default.Redo, "Redo", onRedo)
            ToolbarDivider()
            ToolbarButton(Icons.Default.FormatBold, "Bold", onBold)
            ToolbarButton(Icons.Default.FormatItalic, "Italic", onItalic)
            ToolbarButton(Icons.Default.FormatUnderlined, "Underline", onUnderline)
            ToolbarButton(Icons.Default.FormatStrikethrough, "Strikethrough", onStrikethrough)
            ToolbarDivider()
            ToolbarButton(Icons.Default.Title, "Heading", onHeading)
            ToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet List", onBulletList)
            ToolbarButton(Icons.Default.FormatListNumbered, "Numbered List", onNumberedList)
            ToolbarButton(Icons.Default.CheckBox, "Checklist", onChecklist)
            ToolbarButton(Icons.Default.Code, "Code Block", onCodeBlock)
            ToolbarButton(Icons.Default.HorizontalRule, "Divider", onDivider)
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Spacer(Modifier.width(12.dp))
}
