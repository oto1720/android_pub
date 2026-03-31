package com.example.oto1720.dojo2026.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.oto1720.dojo2026.data.local.entity.RemoteKeyEntity

@Dao
interface RemoteKeyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(remoteKey: RemoteKeyEntity)

    @Query("SELECT * FROM remote_keys WHERE label = :label")
    suspend fun remoteKeyByLabel(label: String): RemoteKeyEntity?

    @Query("DELETE FROM remote_keys WHERE label = :label")
    suspend fun deleteByLabel(label: String)
}
