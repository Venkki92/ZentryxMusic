package com.zentryx.music

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.zentryx.music.scanner.LibraryScanWorker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom Dagger-assisted WorkerFactory for WorkManager jobs.
 * Eliminates reliance on androidx.hilt.work annotation-processor map lookups,
 * guaranteeing R8 release compatibility on automotive headunits.
 */
@Singleton
class ZentryxWorkerFactory @Inject constructor(
    private val libraryScanWorkerFactory: LibraryScanWorker.Factory
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            LibraryScanWorker::class.java.name ->
                libraryScanWorkerFactory.create(appContext, workerParameters)
            else -> null
        }
    }
}
