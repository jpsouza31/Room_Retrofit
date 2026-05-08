package com.app.room_retrofit.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.room_retrofit.data.repository.PokedexRepository
import com.app.room_retrofit.util.Resource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class PokedexSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: PokedexRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val limit = 20
        val totalCached = repository.getNextPageOffset()
        val pages = if (totalCached == 0) 1 else (totalCached + limit - 1) / limit

        for (page in 0 until pages) {
            val result = repository.fetchPage(limit = limit, offset = page * limit)
            if (result is Resource.Error) {
                return if (result.isOffline) Result.retry() else Result.failure()
            }
        }
        return Result.success()
    }
}
