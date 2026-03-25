package com.prgramed.edoist.domain.usecase

import com.prgramed.edoist.domain.model.RecurrenceRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import javax.inject.Inject

data class ParsedDate(
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val recurrenceRule: RecurrenceRule? = null,
    val remainingText: String,
)

interface NaturalDateParser {
    fun parse(input: String): ParsedDate
}

class ParseNaturalDateUseCase @Inject constructor(
    private val naturalDateParser: NaturalDateParser,
) {
    operator fun invoke(input: String): ParsedDate = naturalDateParser.parse(input)
}
