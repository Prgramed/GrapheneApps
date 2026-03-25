package com.prgramed.econtacts.domain.usecase

import com.prgramed.econtacts.domain.repository.ContactRepository
import javax.inject.Inject

class DeleteContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
) {
    suspend operator fun invoke(ids: List<Long>) = contactRepository.delete(ids)
}
