package online.db1k.safering.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import online.db1k.safering.android.data.local.models.CallLogEntity
import online.db1k.safering.android.data.local.models.ScamNumberEntity
import online.db1k.safering.android.data.local.models.SmsLogEntity

@Database(
    entities = [ScamNumberEntity::class, CallLogEntity::class, SmsLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scamNumberDao(): ScamNumberDao
    abstract fun callLogDao(): CallLogDao
    abstract fun smsLogDao(): SmsLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "safering.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
