package com.example.oto1720.dojo2026.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DigestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(digest: DigestEntity)

    @Update
    suspend fun update(digest: DigestEntity)

    @Delete
    suspend fun delete(digest: DigestEntity)

    @Query("SELECT * FROM digests WHERE articleId = :articleId")
    suspend fun getByArticleId(articleId: String): DigestEntity?

    @Query("SELECT * FROM digests ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<DigestEntity>>
}
