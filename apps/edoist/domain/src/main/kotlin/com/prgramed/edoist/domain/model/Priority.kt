package com.prgramed.edoist.domain.model

enum class Priority(val value: Int, val displayName: String, val colorArgb: Long) {
    P1(1, "Priority 1", 0xFFD93025),
    P2(2, "Priority 2", 0xFFEB8909),
    P3(3, "Priority 3", 0xFF246FE0),
    P4(4, "Priority 4", 0xFF808080);

    companion object {
        fun fromValue(value: Int): Priority = entries.firstOrNull { it.value == value } ?: P4
    }
}
