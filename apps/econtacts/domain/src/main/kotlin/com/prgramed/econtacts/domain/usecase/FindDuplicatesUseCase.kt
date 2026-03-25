package com.prgramed.econtacts.domain.usecase

import com.prgramed.econtacts.domain.model.DuplicateGroup
import com.prgramed.econtacts.domain.repository.DuplicateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FindDuplicatesUseCase @Inject constructor(
    private val duplicateRepository: DuplicateRepository,
) {
    operator fun invoke(): Flow<List<DuplicateGroup>> = duplicateRepository.findDuplicates()
}
