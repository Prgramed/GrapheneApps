package com.grapheneapps.enotes.feature.editor.model

import org.json.JSONArray
import org.json.JSONObject

object DocumentSerializer {

    fun toJson(doc: RichTextDocument): String {
        val arr = JSONArray()
        doc.blocks.forEach { block ->
            val obj = JSONObject()
            obj.put("id", block.id)
            obj.put("text", block.text)
            obj.put("spans", spansToJson(block.spans))

            when (block) {
                is Block.Paragraph -> obj.put("type", "paragraph")
                is Block.Heading -> {
                    obj.put("type", "heading")
                    obj.put("level", block.level)
                }
                is Block.BulletItem -> {
                    obj.put("type", "bullet")
                    obj.put("indent", block.indent)
                }
                is Block.NumberedItem -> {
                    obj.put("type", "numbered")
                    obj.put("number", block.number)
                    obj.put("indent", block.indent)
                }
                is Block.ChecklistItem -> {
                    obj.put("type", "checklist")
                    obj.put("checked", block.checked)
                }
                is Block.Image -> {
                    obj.put("type", "image")
                    obj.put("attachmentId", block.attachmentId)
                    block.localPath?.let { obj.put("localPath", it) }
                }
                is Block.CodeBlock -> {
                    obj.put("type", "code")
                    block.language?.let { obj.put("language", it) }
                }
                is Block.Table -> {
                    obj.put("type", "table")
                    val rowsArr = org.json.JSONArray()
                    block.rows.forEach { row ->
                        val cellsArr = org.json.JSONArray()
                        row.forEach { cellsArr.put(it) }
                        rowsArr.put(cellsArr)
                    }
                    obj.put("rows", rowsArr)
                }
                is Block.Divider -> obj.put("type", "divider")
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    fun fromJson(json: String): RichTextDocument {
        if (json.isBlank() || !json.startsWith("[")) {
            // Markdown/plain text fallback — parse as markdown blocks
            val blocks = MarkdownConverter.parseMarkdown(json)
            return RichTextDocument(blocks = blocks.ifEmpty { listOf(Block.Paragraph()) })
        }
        return try {
            val arr = JSONArray(json)
            val blocks = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                val rawText = obj.optString("text", "")
                // Strip any inline HTML tags that leaked from Joplin imports
                val text = rawText.replace(Regex("<[^>]+>"), "").trim()
                val spans = spansFromJson(obj.optJSONArray("spans"))

                when (obj.optString("type", "paragraph")) {
                    "heading" -> Block.Heading(id = id, text = text, spans = spans, level = obj.optInt("level", 1))
                    "bullet" -> Block.BulletItem(id = id, text = text, spans = spans, indent = obj.optInt("indent", 0))
                    "numbered" -> Block.NumberedItem(id = id, text = text, spans = spans, number = obj.optInt("number", 1), indent = obj.optInt("indent", 0))
                    "checklist" -> Block.ChecklistItem(id = id, text = text, spans = spans, checked = obj.optBoolean("checked", false))
                    "image" -> Block.Image(id = id, text = text, spans = spans, attachmentId = obj.optString("attachmentId", ""), localPath = obj.optString("localPath", null))
                    "code" -> Block.CodeBlock(id = id, text = text, spans = spans, language = obj.optString("language", null))
                    "table" -> {
                        val rowsArr = obj.optJSONArray("rows")
                        val rows = if (rowsArr != null) {
                            (0 until rowsArr.length()).map { r ->
                                val cellsArr = rowsArr.getJSONArray(r)
                                (0 until cellsArr.length()).map { c -> cellsArr.optString(c, "") }
                            }
                        } else listOf(listOf("", ""), listOf("", ""))
                        Block.Table(id = id, text = text, spans = spans, rows = rows)
                    }
                    "divider" -> Block.Divider(id = id)
                    else -> Block.Paragraph(id = id, text = text, spans = spans)
                }
            }
            RichTextDocument(blocks = blocks.ifEmpty { listOf(Block.Paragraph()) })
        } catch (_: Exception) {
            RichTextDocument(blocks = listOf(Block.Paragraph(text = json)))
        }
    }

    private fun spansToJson(spans: List<SpanInfo>): JSONArray {
        val arr = JSONArray()
        spans.forEach { span ->
            val obj = JSONObject()
            obj.put("start", span.start)
            obj.put("end", span.end)
            obj.put("style", span.style.name)
            arr.put(obj)
        }
        return arr
    }

    private fun spansFromJson(arr: JSONArray?): List<SpanInfo> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val obj = arr.getJSONObject(i)
                SpanInfo(
                    start = obj.getInt("start"),
                    end = obj.getInt("end"),
                    style = SpanStyle.valueOf(obj.getString("style")),
                )
            } catch (_: Exception) { null }
        }
    }
}
