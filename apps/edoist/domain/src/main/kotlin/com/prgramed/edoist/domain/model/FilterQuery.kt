package com.prgramed.edoist.domain.model

data class FilterQuery(
    val priorities: Set<Priority> = emptySet(),
    val labelIds: Set<String> = emptySet(),
    val projectIds: Set<String> = emptySet(),
    val dueDateRange: DueDateRange? = null,
    val includeNoDate: Boolean = false,
    val searchText: String? = null,
) {
    enum class DueDateRange {
        TODAY,
        WEEK,
        OVERDUE,
        NO_DATE,
    }
}
