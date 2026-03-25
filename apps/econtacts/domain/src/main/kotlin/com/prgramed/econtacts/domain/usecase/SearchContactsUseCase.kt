package com.prgramed.econtacts.domain.usecase

import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
) {
    operator fun invoke(query: String): Flow<List<Contact>> = contactRepository.search(query)
}
