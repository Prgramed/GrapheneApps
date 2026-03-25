package com.prgramed.econtacts.domain.usecase

import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.repository.ContactRepository
import javax.inject.Inject

class SaveContactUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
) {
    suspend operator fun invoke(contact: Contact): Long {
        return if (contact.id == 0L) {
            contactRepository.insert(contact)
        } else {
            contactRepository.update(contact)
            contact.id
        }
    }
}
