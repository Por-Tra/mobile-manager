package com.lucas.mobilemanager.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.lucas.mobilemanager.data.db.EventLogDao
import com.lucas.mobilemanager.data.db.ProfileDao
import com.lucas.mobilemanager.data.db.MobileManagerDatabase
import com.lucas.mobilemanager.data.repository.DataStoreUserPreferencesRepository
import com.lucas.mobilemanager.data.repository.RoomProfileRepository
import com.lucas.mobilemanager.data.repository.RoomEventLogRepository
import com.lucas.mobilemanager.domain.repository.EventLogRepository
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.UserPreferencesRepository
import com.lucas.mobilemanager.domain.time.SystemTimeProvider
import com.lucas.mobilemanager.domain.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MobileManagerDatabase =
        Room.databaseBuilder(context, MobileManagerDatabase::class.java, MobileManagerDatabase.NAME)
            .build()

    @Provides
    fun provideEventLogDao(db: MobileManagerDatabase): EventLogDao = db.eventLogDao()

    @Provides
    fun provideProfileDao(db: MobileManagerDatabase): ProfileDao = db.profileDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("user_preferences")
        }

    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds
    abstract fun bindEventLogRepository(impl: RoomEventLogRepository): EventLogRepository

    @Binds
    abstract fun bindProfileRepository(impl: RoomProfileRepository): ProfileRepository

    @Binds
    abstract fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository,
    ): UserPreferencesRepository
}
