package com.grapheneapps.enotes.feature.editor.model
// No imports needed — same package

object MarkdownConverter {

    fun toRichTextJson(markdown: String): String {
        val blocks = parseMarkdown(markdown)
        val doc = RichTextDocument(blocks = blocks.ifEmpty { listOf(Block.Paragraph()) })
        return DocumentSerializer.toJson(doc)
    }

    private val htmlTagRegex = Regex("<[^>]+>")

    fun parseMarkdown(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = markdown.lines()
        var i = 0
        var numberedCounter = 1

        while (i < lines.size) {
            // Strip inline HTML tags (e.g. <span>, </span>, <div>, <br>)
            val line = lines[i].replace(htmlTagRegex, "").trimEnd()
            when {
                // Fenced code block
                line.trimStart().startsWith("```") -> {
                    val language = line.trimStart().removePrefix("```").trim().ifEmpty { null }
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    blocks.add(Block.CodeBlock(text = codeLines.joinToString("\n"), language = language))
                }
                // Headings
                line.startsWith("### ") -> blocks.add(Block.Heading(text = line.removePrefix("### "), level = 3))
                line.startsWith("## ") -> blocks.add(Block.Heading(text = line.removePrefix("## "), level = 2))
                line.startsWith("# ") -> blocks.add(Block.Heading(text = line.removePrefix("# "), level = 1))
                // Checkbox
                line.trimStart().startsWith("- [x] ") -> blocks.add(Block.ChecklistItem(text = line.trimStart().removePrefix("- [x] "), checked = true))
                line.trimStart().startsWith("- [ ] ") -> blocks.add(Block.ChecklistItem(text = line.trimStart().removePrefix("- [ ] "), checked = false))
                // Bullet
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                    val indent = (line.length - line.trimStart().length) / 2
                    val text = line.trimStart().removePrefix("- ").removePrefix("* ")
                    blocks.add(Block.BulletItem(text = text, indent = indent))
                }
                // Numbered
                line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
                    val text = line.trimStart().replace(Regex("^\\d+\\.\\s"), "")
                    blocks.add(Block.NumberedItem(text = text, number = numberedCounter++))
                }
                // Horizontal rule
                line.trim().matches(Regex("^-{3,}$|^\\*{3,}$|^_{3,}$")) -> {
                    blocks.add(Block.Divider())
                }
                // Empty line
                line.isBlank() -> {
                    numberedCounter = 1
                    // Skip empty lines between blocks
                }
                // Regular paragraph
                else -> {
                    numberedCounter = 1
                    blocks.add(Block.Paragraph(text = line))
                }
            }
            i++
        }

        return blocks
    }

    fun toMarkdown(doc: RichTextDocument): String {
        return doc.blocks.joinToString("\n") { block ->
            when (block) {
                is Block.Heading -> "${"#".repeat(block.level)} ${block.text}"
                is Block.BulletItem -> "${"  ".repeat(block.indent)}- ${block.text}"
                is Block.NumberedItem -> "${"  ".repeat(block.indent)}${block.number}. ${block.text}"
                is Block.ChecklistItem -> "- [${if (block.checked) "x" else " "}] ${block.text}"
                is Block.CodeBlock -> "```${block.language ?: ""}\n${block.text}\n```"
                is Block.Divider -> "---"
                is Block.Image -> "![](attachment:${block.attachmentId})"
                is Block.Table -> block.rows.joinToString("\n") { row -> "| ${row.joinToString(" | ")} |" }
                is Block.Paragraph -> block.text
            }
        }
    }
}
