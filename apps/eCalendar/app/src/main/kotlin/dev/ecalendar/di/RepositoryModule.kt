package dev.ecalendar.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ecalendar.data.repository.AccountRepositoryImpl
import dev.ecalendar.data.repository.CalendarRepositoryImpl
import dev.ecalendar.domain.repository.AccountRepository
import dev.ecalendar.domain.repository.CalendarRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository

    @Binds
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository
}
