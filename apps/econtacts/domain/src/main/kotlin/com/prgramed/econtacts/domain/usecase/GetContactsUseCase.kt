package com.prgramed.econtacts.domain.usecase

import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
) {
    operator fun invoke(): Flow<List<Contact>> = contactRepository.getAll()
}
