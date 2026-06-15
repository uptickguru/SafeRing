package com.safering.android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.safering.android.data.local.AppDatabase
import com.safering.android.data.local.dao.CallLogDao
import com.safering.android.data.local.dao.ScamNumberDao
import com.safering.android.data.local.dao.ScamPrefixDao
import com.safering.android.data.local.dao.SmsLogDao
import com.safering.android.data.remote.ApiService
import com.safering.android.data.repository.ScamRepository
import com.safering.android.domain.ml.NumberClassifier
import com.safering.android.domain.ml.SmsClassifier
import com.safering.android.domain.ml.SpeechAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "safering_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "safering.db"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideScamNumberDao(database: AppDatabase): ScamNumberDao {
        return database.scamNumberDao()
    }

    @Provides
    fun provideScamPrefixDao(database: AppDatabase): ScamPrefixDao {
        return database.scamPrefixDao()
    }

    @Provides
    fun provideCallLogDao(database: AppDatabase): CallLogDao {
        return database.callLogDao()
    }

    @Provides
    fun provideSmsLogDao(database: AppDatabase): SmsLogDao {
        return database.smsLogDao()
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideNumberClassifier(@ApplicationContext context: Context): NumberClassifier {
        return NumberClassifier(context)
    }

    @Provides
    @Singleton
    fun provideSmsClassifier(@ApplicationContext context: Context): SmsClassifier {
        return SmsClassifier(context)
    }

    @Provides
    @Singleton
    fun provideSpeechAnalyzer(@ApplicationContext context: Context): SpeechAnalyzer {
        return SpeechAnalyzer(context)
    }

    @Provides
    @Singleton
    fun provideScamRepository(
        apiService: ApiService,
        scamNumberDao: ScamNumberDao,
        scamPrefixDao: ScamPrefixDao,
        callLogDao: CallLogDao,
        smsLogDao: SmsLogDao
    ): ScamRepository {
        return ScamRepository(apiService, scamNumberDao, scamPrefixDao, callLogDao, smsLogDao)
    }
}
