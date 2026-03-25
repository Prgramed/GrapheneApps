package com.prgramed.emessages.data.di

import com.prgramed.emessages.data.contact.ContactLookupRepositoryImpl
import com.prgramed.emessages.data.conversation.ConversationRepositoryImpl
import com.prgramed.emessages.data.message.MessageRepositoryImpl
import com.prgramed.emessages.data.sim.SimRepositoryImpl
import com.prgramed.emessages.domain.repository.ContactLookupRepository
import com.prgramed.emessages.domain.repository.ConversationRepository
import com.prgramed.emessages.domain.repository.MessageRepository
import com.prgramed.emessages.domain.repository.SimRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindConversationRepository(
        impl: ConversationRepositoryImpl,
    ): ConversationRepository

    @Binds
    abstract fun bindMessageRepository(
        impl: MessageRepositoryImpl,
    ): MessageRepository

    @Binds
    abstract fun bindContactLookupRepository(
        impl: ContactLookupRepositoryImpl,
    ): ContactLookupRepository

    @Binds
    abstract fun bindSimRepository(
        impl: SimRepositoryImpl,
    ): SimRepository
}
