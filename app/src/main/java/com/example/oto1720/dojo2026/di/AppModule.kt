package com.example.oto1720.dojo2026.di

import android.content.Context
import androidx.room.Room
import com.example.oto1720.dojo2026.data.local.AppDatabase
import com.example.oto1720.dojo2026.data.local.dao.ArticleDao
import com.example.oto1720.dojo2026.data.local.dao.DigestDao
import com.example.oto1720.dojo2026.data.local.dao.RemoteKeyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tech_digest.db",
        ).fallbackToDestructiveMigration()
         .build()

    @Provides
    @Singleton
    fun provideArticleDao(db: AppDatabase): ArticleDao = db.articleDao()

    @Provides
    @Singleton
    fun provideDigestDao(db: AppDatabase): DigestDao = db.digestDao()

    @Provides
    @Singleton
    fun provideRemoteKeyDao(db: AppDatabase): RemoteKeyDao = db.remoteKeyDao()
}
