package com.grapheneapps.enotes.data.joplin

import com.grapheneapps.enotes.data.db.dao.FolderDao
import com.grapheneapps.enotes.data.db.dao.NoteDao
import com.grapheneapps.enotes.data.db.entity.FolderEntity
import com.grapheneapps.enotes.data.db.entity.NoteEntity
import com.grapheneapps.enotes.data.sync.WebDavClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val imported: Int = 0,
    val skippedEncrypted: Int = 0,
    val errors: Int = 0,
)

@Singleton
class JoplinImporter @Inject constructor(
    private val webDavClient: WebDavClient,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
) {
    suspend fun import(
        webDavUrl: String,
        username: String,
        password: String,
        onProgress: (step: String, current: Int, total: Int) -> Unit = { _, _, _ -> },
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = webDavUrl.trimEnd('/')
            var imported = 0
            var skippedEncrypted = 0
            var errors = 0

            // Step 1: Discover files
            onProgress("Discovering files…", 0, 0)
            val entries = webDavClient.propfind(baseUrl, username, password)
            Timber.d("PROPFIND returned ${entries.size} entries from $baseUrl")
            if (entries.isEmpty()) {
                // Debug: log raw PROPFIND response
                val rawResponse = webDavClient.propfindRaw(baseUrl, username, password)
                Timber.d("Raw PROPFIND response (first 2000 chars): ${rawResponse?.take(2000)}")
            }
            entries.take(10).forEach { Timber.d("  entry: name='${it.name}' href='${it.href}' isCollection=${it.isCollection}") }

            val mdFiles = entries.filter { it.name.endsWith(".md") && !it.isCollection }
            Timber.d("Found ${mdFiles.size} .md files")

            // Step 2: Download and classify
            onProgress("Downloading notes…", 0, mdFiles.size)
            val items = mutableListOf<JoplinItem>()
            mdFiles.forEachIndexed { index, entry ->
                try {
                    val url = when {
                        entry.href.startsWith("http") -> entry.href
                        entry.href.startsWith("/") -> {
                            // Absolute path — combine with server origin
                            val origin = baseUrl.substringBefore("://") + "://" +
                                baseUrl.substringAfter("://").substringBefore("/")
                            origin + entry.href
                        }
                        else -> "$baseUrl/${entry.href}"
                    }
                    Timber.d("Downloading: $url (href=${entry.href})")
                    val bytes = webDavClient.get(url, username, password) ?: return@forEachIndexed
                    val content = String(bytes)
                    val item = JoplinParser.parse(content)
                    if (item != null) items.add(item)
                } catch (e: Exception) {
                    errors++
                    Timber.w("Failed to parse ${entry.name}: ${e.message}")
                }
                onProgress("Downloading notes…", index + 1, mdFiles.size)
            }

            val noteCount = items.filterIsInstance<JoplinItem.Note>().size
            val notebookCount = items.filterIsInstance<JoplinItem.Notebook>().size
            val tagCount = items.filterIsInstance<JoplinItem.Tag>().size
            val noteTagCount = items.filterIsInstance<JoplinItem.NoteTag>().size
            Timber.d("Parsed items: $noteCount notes, $notebookCount notebooks, $tagCount tags, $noteTagCount note-tags, ${items.size} total (from ${mdFiles.size} files)")

            // Step 3: Build folder hierarchy
            onProgress("Building folders…", 0, 0)
            val folders = items.filterIsInstance<JoplinItem.Notebook>()
            val folderMap = mutableMapOf<String, String>() // joplinId → eNotesId
            for (folder in folders) {
                val eNotesId = UUID.randomUUID().toString()
                folderMap[folder.id] = eNotesId
                val parentENotesId = folder.parentId?.let { folderMap[it] }
                folderDao.upsert(
                    FolderEntity(
                        id = eNotesId,
                        name = folder.title,
                        parentId = parentENotesId,
                    ),
                )
            }

            // Step 4: Import notes
            val notes = items.filterIsInstance<JoplinItem.Note>()
            onProgress("Importing notes…", 0, notes.size)
            for ((index, note) in notes.withIndex()) {
                if (note.isEncrypted) {
                    skippedEncrypted++
                    onProgress("Importing notes…", index + 1, notes.size)
                    continue
                }

                val folderId = note.parentId?.let { folderMap[it] }
                val tags = items.filterIsInstance<JoplinItem.NoteTag>()
                    .filter { it.noteId == note.id }
                    .mapNotNull { noteTag ->
                        items.filterIsInstance<JoplinItem.Tag>().find { it.id == noteTag.tagId }?.title
                    }

                val title = if (note.isTodo) "[${if (note.todoCompleted) "x" else " "}] ${note.title}" else note.title

                noteDao.upsert(
                    NoteEntity(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        bodyJson = note.body, // Stored as markdown; UI converts on display
                        bodyText = note.body.take(5000),
                        folderId = folderId,
                        tags = tags.joinToString(","),
                        createdAt = note.createdTime,
                        editedAt = note.updatedTime,
                        syncStatus = "LOCAL_ONLY",
                    ),
                )
                imported++
                onProgress("Importing notes…", index + 1, notes.size)
            }

            val result = ImportResult(imported, skippedEncrypted, errors)
            Timber.d("Joplin import complete: $result")
            Result.success(result)
        } catch (e: Exception) {
            Timber.e("Joplin import failed: ${e.message}")
            Result.failure(e)
        }
    }
}
