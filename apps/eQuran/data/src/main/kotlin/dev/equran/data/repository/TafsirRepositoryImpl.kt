package dev.equran.data.repository

import dev.equran.data.api.AlQuranCloudApi
import dev.equran.data.api.QuranComApi
import dev.equran.data.db.dao.TafsirDao
import dev.equran.data.db.entity.TafsirCacheEntity
import dev.equran.domain.repository.TafsirRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val QURAN_COM_TAFSIRS = mapOf(
    "en.ibn-kathir" to 169,
    "en.maarif" to 168,
)

private val ALQURAN_CLOUD_TAFSIRS = setOf("ar.muyassar", "ar.jalalayn", "ar.qurtubi")

private const val TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

@Singleton
class TafsirRepositoryImpl @Inject constructor(
    private val tafsirDao: TafsirDao,
    private val quranComApi: QuranComApi,
    private val alQuranCloudApi: AlQuranCloudApi,
) : TafsirRepository {

    override suspend fun getTafsir(surah: Int, ayah: Int, edition: String): String? {
        // Check cache
        val cached = tafsirDao.get(surah, ayah, edition)
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < TTL_MS) {
            return cached.text
        }

        // Fetch from API
        return try {
            val text = when {
                edition in QURAN_COM_TAFSIRS -> {
                    val apiId = QURAN_COM_TAFSIRS[edition]!!
                    val response = quranComApi.getTafsir(apiId, "$surah:$ayah")
                    response.tafsir?.text?.stripHtml()
                }
                edition in ALQURAN_CLOUD_TAFSIRS -> {
                    val response = alQuranCloudApi.getTafsir("$surah:$ayah", edition)
                    response.data?.text
                }
                else -> null
            }

            if (text != null) {
                tafsirDao.upsert(TafsirCacheEntity(surah = surah, ayah = ayah, edition = edition, text = text))
            }
            text
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch tafsir $edition for $surah:$ayah")
            cached?.text // Return stale cache on error
        }
    }

    private fun String.stripHtml(): String =
        replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").replace("&amp;", "&").trim()
}
