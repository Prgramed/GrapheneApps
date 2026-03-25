package com.prgramed.econtacts.data.di

import com.prgramed.econtacts.data.carddav.CardDavRepositoryImpl
import com.prgramed.econtacts.data.calllog.CallLogRepositoryImpl
import com.prgramed.econtacts.data.contacts.ContactRepositoryImpl
import com.prgramed.econtacts.data.duplicates.DuplicateRepositoryImpl
import com.prgramed.econtacts.data.groups.GroupRepositoryImpl
import com.prgramed.econtacts.data.speeddial.SpeedDialRepositoryImpl
import com.prgramed.econtacts.data.vcard.VCardRepositoryImpl
import com.prgramed.econtacts.domain.repository.CallLogRepository
import com.prgramed.econtacts.domain.repository.CardDavRepository
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.repository.DuplicateRepository
import com.prgramed.econtacts.domain.repository.GroupRepository
import com.prgramed.econtacts.domain.repository.SpeedDialRepository
import com.prgramed.econtacts.domain.repository.VCardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindContactRepository(
        impl: ContactRepositoryImpl,
    ): ContactRepository

    @Binds
    abstract fun bindCallLogRepository(
        impl: CallLogRepositoryImpl,
    ): CallLogRepository

    @Binds
    abstract fun bindGroupRepository(
        impl: GroupRepositoryImpl,
    ): GroupRepository

    @Binds
    abstract fun bindDuplicateRepository(
        impl: DuplicateRepositoryImpl,
    ): DuplicateRepository

    @Binds
    abstract fun bindSpeedDialRepository(
        impl: SpeedDialRepositoryImpl,
    ): SpeedDialRepository

    @Binds
    abstract fun bindVCardRepository(
        impl: VCardRepositoryImpl,
    ): VCardRepository

    @Binds
    abstract fun bindCardDavRepository(
        impl: CardDavRepositoryImpl,
    ): CardDavRepository
}
