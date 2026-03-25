package com.prgramed.econtacts.data.speeddial

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.prgramed.econtacts.domain.model.SpeedDial
import com.prgramed.econtacts.domain.repository.SpeedDialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeedDialRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SpeedDialRepository {

    override fun getAll(): Flow<List<SpeedDial>> = dataStore.data.map { prefs ->
        (2..9).mapNotNull { key ->
            val value = prefs[stringPreferencesKey("speed_dial_$key")] ?: return@mapNotNull null
            parseSpeedDial(key, value)
        }
    }

    override suspend fun set(key: Int, contactId: Long, number: String, displayName: String) {
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("speed_dial_$key")] = "$contactId|$number|$displayName"
        }
    }

    override suspend fun remove(key: Int) {
        dataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("speed_dial_$key"))
        }
    }

    private fun parseSpeedDial(key: Int, value: String): SpeedDial? {
        val parts = value.split("|")
        if (parts.size < 3) return null
        return SpeedDial(
            key = key,
            contactId = parts[0].toLongOrNull() ?: return null,
            phoneNumber = parts[1],
            displayName = parts[2],
        )
    }
}
