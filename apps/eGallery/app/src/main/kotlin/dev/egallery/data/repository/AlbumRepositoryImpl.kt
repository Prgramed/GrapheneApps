package dev.egallery.data.repository

import dev.egallery.api.ImmichPhotoService
import dev.egallery.data.CredentialStore
import dev.egallery.data.db.dao.AlbumDao
import dev.egallery.data.db.dao.AlbumMediaDao
import dev.egallery.data.db.entity.AlbumEntity
import dev.egallery.domain.model.Album
import dev.egallery.domain.model.AlbumType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val albumMediaDao: AlbumMediaDao,
    private val immichApi: ImmichPhotoService,
    private val credentialStore: CredentialStore,
) : AlbumRepository {

    override fun observeAll(): Flow<List<Album>> =
        albumDao.getAll().distinctUntilChanged().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: String): Album? =
        albumDao.getById(id)?.toDomain()

    override suspend fun create(name: String): Result<Album> {
        return try {
            val response = immichApi.createAlbum(mapOf("albumName" to name))

            val entity = AlbumEntity(
                id = response.id,
                name = response.albumName,
                photoCount = 0,
                type = "MANUAL",
            )
            albumDao.insert(entity)

            val album = entity.toDomain()
            Timber.d("Created album: $name (id=${response.id})")
            Result.success(album)
        } catch (e: Exception) {
            // If server unreachable, create locally with temp ID
            val tempId = "temp-${System.currentTimeMillis()}"
            val entity = AlbumEntity(id = tempId, name = name, photoCount = 0, type = "MANUAL")
            albumDao.insert(entity)
            Timber.w(e, "Created album locally (server unreachable): $name (tempId=$tempId)")
            Result.success(entity.toDomain())
        }
    }

    override suspend fun rename(id: String, name: String): Result<Unit> {
        return try {
            immichApi.updateAlbum(id, mapOf("albumName" to name))

            albumDao.updateName(id, name)
            Timber.d("Renamed album $id to: $name")
            Result.success(Unit)
        } catch (e: Exception) {
            // Update locally even if server unreachable
            albumDao.updateName(id, name)
            Timber.w(e, "Renamed album locally (server unreachable): $id -> $name")
            Result.success(Unit)
        }
    }

    override suspend fun delete(id: String): Result<Unit> {
        return try {
            if (!id.startsWith("temp-")) {
                immichApi.deleteAlbum(id)
            }

            albumMediaDao.deleteByAlbum(id)
            albumDao.deleteById(id)
            Timber.d("Deleted album: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            // Delete locally even if server unreachable
            albumMediaDao.deleteByAlbum(id)
            albumDao.deleteById(id)
            Timber.w(e, "Deleted album locally (server unreachable): $id")
            Result.success(Unit)
        }
    }
}

fun AlbumEntity.toDomain(): Album = Album(
    id = id,
    name = name,
    coverPhotoId = coverPhotoId,
    photoCount = photoCount,
    type = AlbumType.valueOf(type),
)

fun Album.toEntity(): AlbumEntity = AlbumEntity(
    id = id,
    name = name,
    coverPhotoId = coverPhotoId,
    photoCount = photoCount,
    type = type.name,
)
