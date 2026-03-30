package dev.egallery.data.repository

import dev.egallery.domain.model.Person
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    fun observeAll(): Flow<List<Person>>
}
