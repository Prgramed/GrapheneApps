package dev.egallery.data.repository

import dev.egallery.data.db.dao.PersonDao
import dev.egallery.data.db.entity.PersonEntity
import dev.egallery.domain.model.Person
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val personDao: PersonDao,
) : PersonRepository {

    override fun observeAll(): Flow<List<Person>> =
        personDao.getAll().distinctUntilChanged().map { entities -> entities.map { it.toDomain() } }
}

fun PersonEntity.toDomain(): Person = Person(
    id = id,
    name = name,
    coverPhotoId = coverPhotoId,
    photoCount = photoCount,
)

fun Person.toEntity(): PersonEntity = PersonEntity(
    id = id,
    name = name,
    coverPhotoId = coverPhotoId,
    photoCount = photoCount,
)
