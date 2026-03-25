package com.prgramed.edoist.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.prgramed.edoist.data.database.dao.FilterDao
import com.prgramed.edoist.data.database.dao.LabelDao
import com.prgramed.edoist.data.database.dao.ProjectDao
import com.prgramed.edoist.data.database.dao.SectionDao
import com.prgramed.edoist.data.database.dao.SyncMetadataDao
import com.prgramed.edoist.data.database.dao.TaskDao
import com.prgramed.edoist.data.database.entity.FilterEntity
import com.prgramed.edoist.data.database.entity.LabelEntity
import com.prgramed.edoist.data.database.entity.ProjectEntity
import com.prgramed.edoist.data.database.entity.SectionEntity
import com.prgramed.edoist.data.database.entity.SyncMetadataEntity
import com.prgramed.edoist.data.database.entity.TaskEntity
import com.prgramed.edoist.data.database.entity.TaskLabelCrossRef

@Database(
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        SectionEntity::class,
        LabelEntity::class,
        TaskLabelCrossRef::class,
        FilterEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EDoistDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun sectionDao(): SectionDao
    abstract fun labelDao(): LabelDao
    abstract fun filterDao(): FilterDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
