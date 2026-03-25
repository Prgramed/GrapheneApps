package com.prgramed.edoist.data.di

import com.prgramed.edoist.data.nlp.NaturalDateParserImpl
import com.prgramed.edoist.data.notification.TaskAlarmScheduler
import com.prgramed.edoist.data.repository.FilterRepositoryImpl
import com.prgramed.edoist.data.repository.LabelRepositoryImpl
import com.prgramed.edoist.data.repository.ProjectRepositoryImpl
import com.prgramed.edoist.data.repository.TaskRepositoryImpl
import com.prgramed.edoist.data.repository.UserPreferencesDataStore
import com.prgramed.edoist.domain.repository.FilterRepository
import com.prgramed.edoist.domain.repository.LabelRepository
import com.prgramed.edoist.domain.repository.ProjectRepository
import com.prgramed.edoist.domain.repository.TaskRepository
import com.prgramed.edoist.domain.repository.UserPreferencesRepository
import com.prgramed.edoist.domain.scheduler.TaskReminderScheduler
import com.prgramed.edoist.domain.usecase.NaturalDateParser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    abstract fun bindLabelRepository(impl: LabelRepositoryImpl): LabelRepository

    @Binds
    abstract fun bindFilterRepository(impl: FilterRepositoryImpl): FilterRepository

    @Binds
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesDataStore,
    ): UserPreferencesRepository

    @Binds
    abstract fun bindNaturalDateParser(impl: NaturalDateParserImpl): NaturalDateParser

    @Binds
    abstract fun bindTaskReminderScheduler(impl: TaskAlarmScheduler): TaskReminderScheduler
}
