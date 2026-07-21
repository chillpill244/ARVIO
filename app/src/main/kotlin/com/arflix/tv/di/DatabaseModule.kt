package com.arflix.tv.di

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arflix.tv.data.db.ArflixDatabase
import com.arflix.tv.data.db.DownloadDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN download_type TEXT NOT NULL DEFAULT 'FILE'")
            db.execSQL("ALTER TABLE downloads ADD COLUMN stream_keys TEXT")
        }
    }

    single<ArflixDatabase> {
        Room.databaseBuilder(androidContext(), ArflixDatabase::class.java, ArflixDatabase.DATABASE_NAME)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }

    single<DownloadDao> { get<ArflixDatabase>().downloadDao() }
}
