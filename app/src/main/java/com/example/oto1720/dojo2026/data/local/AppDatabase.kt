package com.example.oto1720.dojo2026.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.oto1720.dojo2026.data.local.dao.ArticleDao
import com.example.oto1720.dojo2026.data.local.dao.DigestDao
import com.example.oto1720.dojo2026.data.local.dao.RemoteKeyDao
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import com.example.oto1720.dojo2026.data.local.entity.RemoteKeyEntity

@Database(
    entities = [ArticleEntity::class, DigestEntity::class, RemoteKeyEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun digestDao(): DigestDao
    abstract fun remoteKeyDao(): RemoteKeyDao
}
