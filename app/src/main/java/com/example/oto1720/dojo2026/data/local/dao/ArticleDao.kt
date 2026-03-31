package com.example.oto1720.dojo2026.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: ArticleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    @Update
    suspend fun update(article: ArticleEntity)

    @Delete
    suspend fun delete(article: ArticleEntity)

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: String): ArticleEntity?

    @Query("SELECT * FROM articles ORDER BY cachedAt DESC")
    fun observeAll(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE status = :status ORDER BY cachedAt DESC")
    fun observeByStatus(status: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatusSortedByDate(status: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE status = :status ORDER BY likesCount DESC")
    fun observeByStatusSortedByLikes(status: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE status = :status ORDER BY createdAt DESC")
    fun pagingSourceByDateDesc(status: String): PagingSource<Int, ArticleEntity>

    @Query("SELECT * FROM articles WHERE status = :status ORDER BY likesCount DESC")
    fun pagingSourceByLikesDesc(status: String): PagingSource<Int, ArticleEntity>

    /** ヘッダーのタグフィルタチップ用に、各記事の tags 文字列（カンマ区切り）を返す */
    @Query("SELECT tags FROM articles WHERE status = :status")
    fun observeTagStrings(status: String): Flow<List<String>>

    @Query("UPDATE articles SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /**
     * スロット空き確認と status 更新をアトミックに行う。
     * @return 更新した行数（1: 成功、0: スロット満杯 or 対象なし）
     */
    @Query("""
        UPDATE articles SET status = :newStatus
        WHERE id = :articleId
        AND (SELECT COUNT(*) FROM articles WHERE status = :slotCountStatus) < :maxSlots
    """)
    suspend fun updateStatusIfSlotAvailable(
        articleId: String,
        newStatus: String,
        slotCountStatus: String,
        maxSlots: Int,
    ): Int

    @Query("UPDATE articles SET aiSummary = :summary WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String)

    @Query("DELETE FROM articles WHERE status = :status")
    suspend fun deleteByStatus(status: String)

    /**
     * 積読・消化済みなど、上書きしてはいけない記事のID一覧を取得する。
     * refreshPortalArticles で API 取得結果を挿入する際、これらの記事を除外するために使用する。
     */
    @Query("SELECT id FROM articles WHERE status IN (:statuses)")
    suspend fun getIdsByStatuses(statuses: List<String>): List<String>
}
