package com.grapheneapps.enotes.feature.editor.model

import java.util.UUID

data class RichTextDocument(
    val blocks: List<Block> = listOf(Block.Paragraph(id = UUID.randomUUID().toString(), text = "")),
) {
    fun plainText(): String = blocks.joinToString("\n") { it.text }
}

sealed class Block {
    abstract val id: String
    abstract val text: String
    abstract val spans: List<SpanInfo>

    data class Paragraph(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
    ) : Block()

    data class Heading(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
        val level: Int = 1, // 1=title, 2=heading, 3=subheading
    ) : Block()

    data class BulletItem(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
        val indent: Int = 0,
    ) : Block()

    data class NumberedItem(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
        val number: Int = 1,
        val indent: Int = 0,
    ) : Block()

    data class ChecklistItem(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
        val checked: Boolean = false,
    ) : Block()

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
        val attachmentId: String,
        val localPath: String? = null,
    ) : Block()

    data class CodeBlock(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
        val language: String? = null,
    ) : Block()

    data class Table(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
        val rows: List<List<String>> = listOf(listOf("", ""), listOf("", "")),
    ) : Block()

    data class Divider(
        override val id: String = UUID.randomUUID().toString(),
        override val text: String = "",
        override val spans: List<SpanInfo> = emptyList(),
    ) : Block()
}

data class SpanInfo(
    val start: Int,
    val end: Int,
    val style: SpanStyle,
)

enum class SpanStyle {
    BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, CODE,
}
