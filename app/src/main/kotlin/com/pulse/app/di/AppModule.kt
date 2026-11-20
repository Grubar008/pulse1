package com.pulse.app.di

import android.content.Context
import androidx.room.Room
import com.pulse.app.data.db.DbKeyProvider
import com.pulse.app.data.db.PulseDatabase
import com.pulse.app.data.prefs.PulseSettings
import com.pulse.app.data.source.DataSource
import com.pulse.app.data.source.file.FileImportSource
import com.pulse.app.data.source.gateway.SecureTokenStore
import com.pulse.app.data.source.health.HealthConnectSource
import com.pulse.app.data.source.zepp.ZeppDirectSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier annotation class HealthSource
@Qualifier annotation class GarminSource
@Qualifier annotation class ZeppSource
@Qualifier annotation class FileSource

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettings(@ApplicationContext context: Context): PulseSettings = PulseSettings(context)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PulseDatabase {
        SQLiteDatabase.loadLibs(context)
        val factory = SupportFactory(DbKeyProvider(context).passphrase(), null, false)
        return Room.databaseBuilder(context, PulseDatabase::class.java, "pulse.db")
            .openHelperFactory(factory)
            .build()
    }

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): SecureTokenStore =
        SecureTokenStore(context)

    @Provides
    @Singleton
    fun provideHealthConnectSource(@ApplicationContext context: Context): HealthConnectSource =
        HealthConnectSource(context)

    @Provides
    @Singleton
    @HealthSource
    fun provideHealthSource(src: HealthConnectSource): DataSource = src

    @Provides
    @Singleton
    @FileSource
    fun provideFileSource(): DataSource = FileImportSource()
}
