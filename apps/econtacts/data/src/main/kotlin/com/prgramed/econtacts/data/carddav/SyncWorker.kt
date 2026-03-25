package com.prgramed.econtacts.data.carddav

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.prgramed.econtacts.domain.repository.CardDavRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val cardDavRepository: CardDavRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val syncResult = cardDavRepository.sync()
        return if (syncResult.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "econtacts_carddav_sync"
    }
}
