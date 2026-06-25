package online.db1k.safering.android

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import online.db1k.safering.android.data.local.AppDatabase
import online.db1k.safering.android.data.remote.SafeRingApi
import online.db1k.safering.android.data.repository.ScamRepository
import online.db1k.safering.android.service.BackgroundSyncWorker
import online.db1k.safering.android.ui.report.reportContext
import online.db1k.safering.android.util.Logger

class SafeRingApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var api: SafeRingApi
        private set
    lateinit var repository: ScamRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase (Analytics + Crashlytics)
        FirebaseApp.initializeApp(this)
        // Crashlytics is enabled by default once SDK is included;
        // explicit call here for clarity + future opt-out handling
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

        database = AppDatabase.getInstance(this)
        api = SafeRingApi.create()
        repository = ScamRepository(api, database)

        Logger.info("App initialized — Firebase Crashlytics + Analytics active", Logger.Category.APP)

        // Set report context (temporary — will use DI later)
        reportContext = this

        // Schedule background sync
        BackgroundSyncWorker.schedule(this)
    }
}
