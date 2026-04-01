package dev.equran.data.repository

import dev.equran.data.db.dao.TopicDao
import dev.equran.data.db.entity.TopicEntity
import dev.equran.data.db.entity.TopicVerseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private data class TopicSeed(
    val nameEn: String, val nameAr: String, val description: String,
    val category: String, val order: Int, val verses: List<Pair<Int, Int>>,
)

@Singleton
class TopicSeeder @Inject constructor(private val topicDao: TopicDao) {

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (topicDao.count() > 0) return@withContext
        Timber.d("Seeding topics...")

        for (topic in TOPICS) {
            val id = topicDao.insertTopic(TopicEntity(
                nameEn = topic.nameEn, nameAr = topic.nameAr, description = topic.description,
                category = topic.category, displayOrder = topic.order,
            ))
            topicDao.insertVerses(topic.verses.map { (s, a) ->
                TopicVerseEntity(topicId = id, surah = s, ayah = a)
            })
        }
        Timber.d("Seeded ${TOPICS.size} topics")
    }

    companion object {
        private val TOPICS = listOf(
            // Faith & Belief
            TopicSeed("Tawheed (Oneness of God)", "التوحيد", "Verses affirming the absolute oneness and uniqueness of Allah", "Faith & Belief", 1,
                listOf(1 to 1, 2 to 163, 2 to 255, 3 to 2, 3 to 18, 4 to 48, 6 to 102, 6 to 103, 7 to 54, 16 to 51, 20 to 8, 21 to 22, 23 to 91, 28 to 88, 37 to 4, 39 to 4, 42 to 11, 57 to 3, 59 to 22, 59 to 23, 59 to 24, 112 to 1, 112 to 2, 112 to 3, 112 to 4)),
            TopicSeed("Faith (Iman)", "الإيمان", "Verses about belief in Allah, His angels, books, messengers, and the Last Day", "Faith & Belief", 2,
                listOf(2 to 3, 2 to 4, 2 to 177, 2 to 285, 3 to 179, 4 to 136, 8 to 2, 8 to 3, 8 to 4, 9 to 124, 23 to 1, 23 to 2, 23 to 3, 23 to 4, 32 to 15, 32 to 16, 49 to 7, 49 to 14, 49 to 15, 57 to 19)),
            TopicSeed("The Hereafter", "الآخرة", "Verses about the Day of Judgment, Paradise, and Hellfire", "Faith & Belief", 3,
                listOf(2 to 281, 3 to 185, 6 to 31, 14 to 42, 18 to 47, 18 to 48, 18 to 49, 20 to 15, 21 to 47, 23 to 99, 23 to 100, 39 to 68, 50 to 19, 50 to 20, 56 to 1, 56 to 2, 56 to 3, 69 to 13, 75 to 1, 78 to 17, 82 to 1, 99 to 1, 99 to 2, 99 to 3, 99 to 4, 99 to 5, 99 to 6, 99 to 7, 99 to 8)),
            TopicSeed("Qadr (Divine Decree)", "القدر", "Verses about predestination and divine will", "Faith & Belief", 4,
                listOf(3 to 26, 3 to 27, 6 to 17, 9 to 51, 13 to 8, 25 to 2, 33 to 38, 35 to 2, 36 to 82, 54 to 49, 57 to 22, 64 to 11)),
            // Worship & Practice
            TopicSeed("Prayer (Salah)", "الصلاة", "Verses about the importance, manner, and virtue of prayer", "Worship & Practice", 1,
                listOf(2 to 3, 2 to 43, 2 to 45, 2 to 110, 2 to 238, 4 to 43, 4 to 103, 5 to 6, 11 to 114, 14 to 31, 17 to 78, 20 to 14, 20 to 132, 23 to 2, 29 to 45, 62 to 9, 62 to 10, 73 to 20, 87 to 14, 87 to 15)),
            TopicSeed("Charity (Zakah & Sadaqah)", "الزكاة والصدقة", "Verses about giving in the way of Allah", "Worship & Practice", 2,
                listOf(2 to 43, 2 to 110, 2 to 177, 2 to 195, 2 to 261, 2 to 262, 2 to 263, 2 to 264, 2 to 267, 2 to 271, 2 to 274, 3 to 92, 9 to 60, 9 to 103, 57 to 7, 57 to 10, 57 to 11, 57 to 18, 63 to 10, 64 to 16)),
            TopicSeed("Fasting (Siyam)", "الصيام", "Verses about the obligation and benefits of fasting", "Worship & Practice", 3,
                listOf(2 to 183, 2 to 184, 2 to 185, 2 to 186, 2 to 187)),
            TopicSeed("Hajj & Umrah", "الحج والعمرة", "Verses about pilgrimage to Makkah", "Worship & Practice", 4,
                listOf(2 to 125, 2 to 127, 2 to 158, 2 to 196, 2 to 197, 2 to 198, 2 to 199, 2 to 200, 2 to 203, 3 to 96, 3 to 97, 5 to 2, 22 to 26, 22 to 27, 22 to 28, 22 to 29, 22 to 33, 22 to 36, 22 to 37)),
            TopicSeed("Remembrance of Allah (Dhikr)", "ذكر الله", "Verses about remembering Allah and keeping Him in mind", "Worship & Practice", 5,
                listOf(2 to 152, 3 to 41, 3 to 191, 7 to 205, 13 to 28, 18 to 24, 20 to 14, 29 to 45, 33 to 41, 33 to 42, 39 to 22, 39 to 23, 62 to 10, 73 to 8, 76 to 25, 87 to 15)),
            TopicSeed("Supplication (Dua)", "الدعاء", "Verses about calling upon Allah and recorded prayers of prophets", "Worship & Practice", 6,
                listOf(1 to 5, 1 to 6, 1 to 7, 2 to 127, 2 to 128, 2 to 186, 2 to 201, 2 to 286, 3 to 8, 3 to 9, 3 to 26, 3 to 27, 3 to 38, 3 to 147, 3 to 191, 3 to 194, 14 to 40, 14 to 41, 25 to 74, 40 to 60)),
            // Character & Ethics
            TopicSeed("Patience (Sabr)", "الصبر", "Verses about steadfastness and endurance in faith", "Character & Ethics", 1,
                listOf(2 to 45, 2 to 153, 2 to 155, 2 to 156, 2 to 157, 2 to 177, 3 to 17, 3 to 120, 3 to 146, 3 to 186, 3 to 200, 7 to 128, 8 to 46, 11 to 115, 13 to 22, 16 to 96, 16 to 126, 16 to 127, 31 to 17, 39 to 10, 42 to 43, 46 to 35, 103 to 3)),
            TopicSeed("Gratitude (Shukr)", "الشكر", "Verses about thankfulness to Allah for His blessings", "Character & Ethics", 2,
                listOf(2 to 152, 2 to 172, 3 to 145, 7 to 10, 14 to 7, 16 to 14, 16 to 78, 16 to 114, 27 to 19, 27 to 40, 29 to 17, 31 to 12, 34 to 13, 39 to 7, 39 to 66, 46 to 15, 55 to 13, 76 to 3)),
            TopicSeed("Forgiveness", "المغفرة", "Verses about seeking and granting forgiveness", "Character & Ethics", 3,
                listOf(2 to 199, 2 to 286, 3 to 31, 3 to 133, 3 to 135, 3 to 136, 4 to 48, 4 to 110, 4 to 116, 7 to 23, 11 to 3, 11 to 52, 11 to 90, 24 to 22, 39 to 53, 40 to 55, 42 to 25, 42 to 37, 42 to 40, 42 to 43, 64 to 14, 71 to 10)),
            TopicSeed("Repentance (Tawbah)", "التوبة", "Verses about turning back to Allah in sincere repentance", "Character & Ethics", 4,
                listOf(2 to 37, 2 to 54, 2 to 160, 3 to 89, 4 to 17, 4 to 18, 5 to 39, 5 to 74, 6 to 54, 9 to 104, 9 to 118, 11 to 3, 11 to 52, 11 to 61, 20 to 82, 24 to 31, 25 to 70, 25 to 71, 39 to 53, 42 to 25, 46 to 15, 66 to 8)),
            TopicSeed("Justice", "العدل", "Verses about establishing justice and fairness", "Character & Ethics", 5,
                listOf(4 to 58, 4 to 105, 4 to 135, 5 to 8, 5 to 42, 6 to 152, 7 to 29, 16 to 90, 38 to 26, 42 to 15, 49 to 9, 55 to 7, 55 to 8, 55 to 9, 57 to 25)),
            TopicSeed("Mercy (Rahmah)", "الرحمة", "Verses about the mercy of Allah and showing mercy to others", "Character & Ethics", 6,
                listOf(1 to 1, 1 to 3, 6 to 12, 6 to 54, 7 to 56, 7 to 156, 10 to 21, 10 to 58, 12 to 64, 12 to 87, 17 to 24, 21 to 107, 27 to 77, 36 to 58, 39 to 53, 40 to 7, 42 to 8, 57 to 28)),
            TopicSeed("Humility", "التواضع", "Verses about being humble before Allah and with people", "Character & Ethics", 7,
                listOf(2 to 45, 3 to 159, 15 to 88, 17 to 37, 22 to 34, 22 to 35, 25 to 63, 26 to 215, 31 to 18, 31 to 19, 57 to 16, 59 to 21)),
            TopicSeed("Truthfulness", "الصدق", "Verses about honesty and being truthful", "Character & Ethics", 8,
                listOf(2 to 177, 3 to 17, 5 to 119, 9 to 119, 17 to 80, 19 to 50, 19 to 54, 33 to 23, 33 to 24, 33 to 35, 39 to 33, 49 to 15)),
            // Social & Community
            TopicSeed("Family", "الأسرة", "Verses about family bonds, marriage, and raising children", "Social & Community", 1,
                listOf(2 to 228, 2 to 233, 4 to 1, 4 to 19, 4 to 34, 4 to 35, 13 to 38, 16 to 72, 17 to 23, 17 to 24, 25 to 74, 30 to 21, 31 to 13, 31 to 14, 31 to 15, 31 to 16, 31 to 17, 46 to 15, 64 to 14, 64 to 15, 66 to 6)),
            TopicSeed("Brotherhood & Unity", "الأخوة والوحدة", "Verses about Muslim unity and brotherhood", "Social & Community", 2,
                listOf(3 to 103, 3 to 105, 3 to 110, 6 to 159, 8 to 46, 8 to 63, 21 to 92, 23 to 52, 42 to 13, 49 to 9, 49 to 10, 49 to 13, 61 to 4)),
            TopicSeed("Good Manners (Adab)", "الآداب", "Verses about etiquette and proper conduct", "Social & Community", 3,
                listOf(2 to 83, 4 to 36, 4 to 86, 6 to 151, 17 to 23, 17 to 24, 17 to 26, 17 to 27, 17 to 28, 17 to 29, 17 to 32, 17 to 34, 17 to 36, 17 to 37, 24 to 27, 24 to 28, 24 to 58, 24 to 59, 25 to 63, 31 to 18, 31 to 19, 49 to 11, 49 to 12)),
            TopicSeed("Enjoining Good & Forbidding Evil", "الأمر بالمعروف والنهي عن المنكر", "Verses about the duty to promote virtue and prevent vice", "Social & Community", 4,
                listOf(3 to 104, 3 to 110, 3 to 114, 5 to 2, 5 to 78, 5 to 79, 7 to 157, 7 to 199, 9 to 71, 9 to 112, 16 to 90, 22 to 41, 31 to 17)),
            // Knowledge & Reflection
            TopicSeed("Knowledge & Learning", "العلم والتعلم", "Verses about the importance of seeking knowledge", "Knowledge & Reflection", 1,
                listOf(2 to 31, 2 to 32, 2 to 129, 2 to 151, 2 to 269, 3 to 18, 3 to 79, 4 to 113, 12 to 76, 16 to 43, 17 to 85, 20 to 114, 29 to 43, 35 to 28, 39 to 9, 58 to 11, 96 to 1, 96 to 2, 96 to 3, 96 to 4, 96 to 5)),
            TopicSeed("Reflection on Creation", "التفكر في الخلق", "Verses inviting reflection on the signs of Allah in creation", "Knowledge & Reflection", 2,
                listOf(2 to 164, 3 to 190, 3 to 191, 6 to 99, 10 to 5, 10 to 6, 13 to 2, 13 to 3, 13 to 4, 16 to 10, 16 to 11, 16 to 12, 16 to 13, 16 to 14, 16 to 15, 16 to 16, 21 to 30, 22 to 5, 30 to 20, 30 to 21, 30 to 22, 36 to 33, 36 to 34, 36 to 36, 41 to 53, 45 to 3, 45 to 4, 45 to 5, 51 to 20, 51 to 21, 88 to 17, 88 to 18, 88 to 19, 88 to 20)),
            TopicSeed("The Quran", "القرآن", "Verses about the Quran itself - its revelation, guidance, and virtues", "Knowledge & Reflection", 3,
                listOf(2 to 2, 2 to 185, 3 to 7, 4 to 82, 5 to 15, 5 to 16, 6 to 19, 6 to 155, 10 to 37, 10 to 57, 12 to 2, 15 to 87, 16 to 89, 17 to 9, 17 to 82, 17 to 88, 20 to 2, 25 to 1, 36 to 2, 38 to 29, 39 to 23, 41 to 42, 54 to 17, 56 to 77, 56 to 78, 56 to 79, 56 to 80)),
            // Struggles & Trials
            TopicSeed("Tests & Trials", "الابتلاء", "Verses about how Allah tests believers through hardship", "Struggles & Trials", 1,
                listOf(2 to 155, 2 to 156, 2 to 214, 3 to 140, 3 to 142, 3 to 186, 6 to 42, 7 to 94, 7 to 168, 21 to 35, 23 to 56, 29 to 2, 29 to 3, 47 to 31, 67 to 2, 76 to 2, 89 to 15, 89 to 16)),
            TopicSeed("Trust in Allah (Tawakkul)", "التوكل", "Verses about relying on Allah in all affairs", "Struggles & Trials", 2,
                listOf(3 to 159, 3 to 160, 3 to 173, 5 to 11, 5 to 23, 7 to 89, 8 to 49, 9 to 51, 9 to 129, 10 to 84, 10 to 85, 11 to 56, 11 to 88, 12 to 67, 13 to 30, 14 to 12, 25 to 58, 26 to 217, 27 to 79, 33 to 3, 33 to 48, 39 to 38, 58 to 10, 64 to 13, 65 to 3)),
            TopicSeed("Hardship & Ease", "العسر واليسر", "Verses about relief following difficulty", "Struggles & Trials", 3,
                listOf(2 to 185, 2 to 286, 4 to 28, 6 to 52, 12 to 87, 39 to 53, 65 to 2, 65 to 3, 65 to 4, 65 to 7, 93 to 5, 93 to 6, 93 to 7, 93 to 8, 94 to 1, 94 to 2, 94 to 3, 94 to 4, 94 to 5, 94 to 6)),
            // Prophets
            TopicSeed("Prophet Muhammad (PBUH)", "النبي محمد ﷺ", "Verses about the character, mission, and role of Prophet Muhammad", "Prophets", 1,
                listOf(3 to 31, 3 to 32, 3 to 144, 3 to 159, 4 to 80, 7 to 157, 7 to 158, 9 to 128, 21 to 107, 33 to 6, 33 to 21, 33 to 40, 33 to 45, 33 to 46, 33 to 56, 34 to 28, 42 to 48, 46 to 9, 48 to 8, 48 to 29, 68 to 4)),
            TopicSeed("Stories of the Prophets", "قصص الأنبياء", "Key verses mentioning various prophets and their missions", "Prophets", 2,
                listOf(2 to 124, 2 to 136, 3 to 33, 3 to 34, 3 to 45, 3 to 46, 6 to 84, 6 to 85, 6 to 86, 6 to 87, 11 to 25, 11 to 50, 11 to 61, 11 to 84, 12 to 4, 12 to 111, 14 to 35, 14 to 37, 19 to 16, 19 to 30, 19 to 51, 19 to 54, 19 to 56, 21 to 68, 21 to 69, 21 to 78, 21 to 79, 21 to 83, 21 to 87, 37 to 139, 38 to 17, 38 to 30, 38 to 41, 38 to 44, 38 to 45, 38 to 48)),
            // Protection & Refuge
            TopicSeed("Protection from Evil", "الاستعاذة", "Verses about seeking refuge in Allah from evil", "Protection & Refuge", 1,
                listOf(2 to 255, 2 to 256, 2 to 257, 3 to 36, 7 to 200, 7 to 201, 16 to 98, 23 to 97, 23 to 98, 40 to 56, 41 to 36, 113 to 1, 113 to 2, 113 to 3, 113 to 4, 113 to 5, 114 to 1, 114 to 2, 114 to 3, 114 to 4, 114 to 5, 114 to 6)),
            TopicSeed("Provision (Rizq)", "الرزق", "Verses about Allah as the sole provider and sustainer", "Protection & Refuge", 2,
                listOf(2 to 212, 3 to 27, 6 to 151, 10 to 31, 11 to 6, 15 to 20, 17 to 30, 17 to 31, 23 to 72, 27 to 64, 29 to 17, 29 to 60, 30 to 37, 34 to 36, 34 to 39, 35 to 3, 39 to 52, 42 to 12, 51 to 22, 51 to 58, 65 to 3)),
        )
    }
}
