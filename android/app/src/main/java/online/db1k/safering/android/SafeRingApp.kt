package online.db1k.safering.android

import android.app.Application
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.repository.ScamRepository
import online.db1k.safering.android.service.BackgroundSyncWorker
import online.db1k.safering.android.ui.report.reportContext

class SafeRingApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var api: SafeRingApi
        private set
    lateinit var repository: ScamRepository
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        api = SafeRingApi.create()
        repository = ScamRepository(api, database)

        // Set report context (temporary — will use DI later)
        reportContext = this

        // Schedule background sync
        BackgroundSyncWorker.schedule(this)
    }
}
