package com.safering.android.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.safering.android.data.local.dao.CallLogDao
import com.safering.android.data.local.dao.ScamNumberDao
import com.safering.android.data.local.dao.ScamPrefixDao
import com.safering.android.data.local.dao.SmsLogDao
import com.safering.android.data.local.entity.CallLogEntity
import com.safering.android.data.local.entity.ScamNumberEntity
import com.safering.android.data.local.entity.ScamPrefixEntity
import com.safering.android.data.local.entity.ScamReportEntity
import com.safering.android.data.local.entity.SmsLogEntity
import com.safering.android.data.local.entity.ScamNumberEntity.Companion.TABLE_SCAM_NUMBERS
import com.safering.android.data.local.entity.ScamPrefixEntity.Companion.TABLE_SCAM_PREFIXES
import com.safering.android.data.local.entity.CallLogEntity.Companion.TABLE_CALL_LOGS
import com.safering.android.data.local.entity.SmsLogEntity.Companion.TABLE_SMS_LOGS
import com.safering.android.data.local.entity.ScamReportEntity.Companion.TABLE_SCAM_REPORTS
import java.util.concurrent.Executors

@Database(
    entities = [
        ScamNumberEntity::class,
        ScamPrefixEntity::class,
        ScamReportEntity::class,
        CallLogEntity::class,
        SmsLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scamNumberDao(): ScamNumberDao
    abstract fun scamPrefixDao(): ScamPrefixDao
    abstract fun callLogDao(): CallLogDao
    abstract fun smsLogDao(): SmsLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: android.content.Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "safering.db"
            )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Executors.newSingleThreadExecutor().execute {
                            // Pre-populated in production via initial asset load
                            // For now, DB starts empty — SyncWorker fetches on first sync
                        }
                    }
                })
                .build()
        }
    }
}
