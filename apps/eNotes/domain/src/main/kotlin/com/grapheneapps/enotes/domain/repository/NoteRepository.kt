package com.grapheneapps.enotes.domain.repository

import com.grapheneapps.enotes.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeAll(): Flow<List<Note>>
    fun observeByFolder(folderId: String): Flow<List<Note>>
    fun observeDeleted(): Flow<List<Note>>
    suspend fun getById(id: String): Note?
    suspend fun save(note: Note)
    suspend fun softDelete(id: String)
    suspend fun restore(id: String)
    suspend fun permanentlyDelete(id: String)
    fun search(query: String): Flow<List<Note>>
    fun observeLocked(): Flow<List<Note>>
    fun observeConflicts(): Flow<List<Note>>
    fun observeAllByCreatedAt(): Flow<List<Note>>
    fun observeAllByTitle(): Flow<List<Note>>
    fun observeAllTags(): Flow<List<String>>
}
