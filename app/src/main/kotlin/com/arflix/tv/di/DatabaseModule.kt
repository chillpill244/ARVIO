package com.arflix.tv.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.arflix.tv.data.db.ArflixDatabase
import com.arflix.tv.data.db.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN download_type TEXT NOT NULL DEFAULT 'FILE'")
            db.execSQL("ALTER TABLE downloads ADD COLUMN stream_keys TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ArflixDatabase =
        Room.databaseBuilder(context, ArflixDatabase::class.java, ArflixDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDownloadDao(db: ArflixDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
