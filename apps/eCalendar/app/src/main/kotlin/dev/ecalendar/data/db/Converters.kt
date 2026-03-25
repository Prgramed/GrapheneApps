package dev.ecalendar.data.db

import androidx.room.TypeConverter
import dev.ecalendar.domain.model.AccountType
import dev.ecalendar.domain.model.SyncOp

class Converters {

    @TypeConverter
    fun fromAccountType(type: AccountType): String = type.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromSyncOp(op: SyncOp): String = op.name

    @TypeConverter
    fun toSyncOp(value: String): SyncOp = SyncOp.valueOf(value)
}
