package com.prgramed.edoist.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.prgramed.edoist.data.database.EDoistDatabase
import com.prgramed.edoist.data.database.dao.FilterDao
import com.prgramed.edoist.data.database.dao.LabelDao
import com.prgramed.edoist.data.database.dao.ProjectDao
import com.prgramed.edoist.data.database.dao.SectionDao
import com.prgramed.edoist.data.database.dao.SyncMetadataDao
import com.prgramed.edoist.data.database.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EDoistDatabase =
        Room.databaseBuilder(context, EDoistDatabase::class.java, "edoist_database")
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        val id = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        val color = 0xFF808080.toLong()
                        db.execSQL(
                            """
                            INSERT INTO projects (id, name, color, icon_name, is_inbox, is_archived, default_view, sort_order, created_at_millis, updated_at_millis)
                            VALUES ('$id', 'Inbox', $color, '', 1, 0, 'LIST', 0, $now, $now)
                            """.trimIndent(),
                        )
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Self-heal duplicate inbox projects: move tasks to the oldest
                        // inbox, then delete the rest. Protects against edge cases where
                        // multiple inboxes were created across imports or migrations.
                        db.execSQL(
                            """
                            UPDATE tasks SET project_id = (
                                SELECT id FROM projects WHERE is_inbox = 1 ORDER BY created_at_millis ASC LIMIT 1
                            ) WHERE project_id IN (
                                SELECT id FROM projects WHERE is_inbox = 1 ORDER BY created_at_millis ASC LIMIT -1 OFFSET 1
                            )
                            """.trimIndent(),
                        )
                        db.execSQL(
                            """
                            DELETE FROM projects WHERE is_inbox = 1 AND id != (
                                SELECT id FROM projects WHERE is_inbox = 1 ORDER BY created_at_millis ASC LIMIT 1
                            )
                            """.trimIndent(),
                        )
                    }
                },
            )
            .build()

    @Provides
    fun provideTaskDao(database: EDoistDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideProjectDao(database: EDoistDatabase): ProjectDao = database.projectDao()

    @Provides
    fun provideSectionDao(database: EDoistDatabase): SectionDao = database.sectionDao()

    @Provides
    fun provideLabelDao(database: EDoistDatabase): LabelDao = database.labelDao()

    @Provides
    fun provideFilterDao(database: EDoistDatabase): FilterDao = database.filterDao()

    @Provides
    fun provideSyncMetadataDao(database: EDoistDatabase): SyncMetadataDao =
        database.syncMetadataDao()
}
