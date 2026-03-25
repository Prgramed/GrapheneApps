package com.prgramed.emessages.data.sim

import android.telephony.SubscriptionManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.prgramed.emessages.domain.model.SimInfo
import com.prgramed.emessages.domain.repository.SimRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimRepositoryImpl @Inject constructor(
    private val subscriptionManager: SubscriptionManager,
    private val dataStore: DataStore<Preferences>,
) : SimRepository {

    override fun getActiveSimsFlow(): Flow<List<SimInfo>> = flow {
        emit(loadSims())
    }.flowOn(Dispatchers.IO)

    override fun isDualSim(): Boolean = loadSims().size >= 2

    override fun getDefaultSmsSubscriptionId(): Int =
        SubscriptionManager.getDefaultSmsSubscriptionId()

    override fun getPreferredSim(threadId: Long): Flow<Int?> =
        dataStore.data.map { prefs ->
            prefs[intPreferencesKey("sim_pref_$threadId")]
        }

    override suspend fun setPreferredSim(threadId: Long, subscriptionId: Int) {
        dataStore.edit { prefs ->
            prefs[intPreferencesKey("sim_pref_$threadId")] = subscriptionId
        }
    }

    @Suppress("DEPRECATION")
    private fun loadSims(): List<SimInfo> = try {
        val subs = subscriptionManager.activeSubscriptionInfoList ?: emptyList()
        subs.map { sub ->
            SimInfo(
                subscriptionId = sub.subscriptionId,
                displayName = sub.displayName?.toString() ?: "SIM ${sub.simSlotIndex + 1}",
                slotIndex = sub.simSlotIndex,
                carrierName = sub.carrierName?.toString() ?: "",
            )
        }
    } catch (_: SecurityException) {
        emptyList()
    }
}
