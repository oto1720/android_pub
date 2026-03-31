package com.example.oto1720.dojo2026.data.repository

import androidx.paging.PagingData
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

interface ArticleRepository {

    /** ポータル記事をFlowで監視する */
    fun observePortalArticles(): Flow<List<ArticleEntity>>

    /**
     * Paging3 で無限スクロール対応したポータル記事の Flow を返す。
     * RemoteMediator が Qiita API からページフェッチし Room にキャッシュする。
     *
     * @param tag フィルタリングするタグ（null でトレンド全体）
     * @param sortOrder 表示順（新着順 / いいね順）
     */
    fun getPortalPager(tag: String?, sortOrder: SortOrder): Flow<PagingData<ArticleEntity>>

    /** ポータル記事の tags 文字列（カンマ区切り）を Flow で監視する（タグフィルタチップ用） */
    fun observePortalTagStrings(): Flow<List<String>>

    /** 積読記事をFlowで監視する */
    fun observeTsundokuArticles(): Flow<List<ArticleEntity>>

    /** 消化済み記事をFlowで監視する */
    fun observeDoneArticles(): Flow<List<ArticleEntity>>

    /**
     * Qiita APIから記事を取得してRoomに保存する。
     * 既存のPORTAL記事を削除してから新しい記事を挿入する。
     *
     * @param query 検索クエリ（例: "tag:android"）。nullの場合はトレンド記事を取得。
     * @return 成功時は [Result.success]、APIエラー等の場合は [Result.failure]
     */
    suspend fun refreshPortalArticles(query: String? = null): Result<Unit>

    /**
     * 指定 ID の記事を1件取得する。存在しない場合は null を返す。
     */
    suspend fun getArticleById(id: String): ArticleEntity?

    /**
     * 記事のステータスを更新する（積読追加・消化完了など）。
     */
    suspend fun updateStatus(id: String, status: String)

    /**
     * スロット上限を守りながら記事のステータスを更新する。
     * @return 更新成功 true、スロット満杯で更新不可 false
     */
    /**
     * @param slotCountStatus スロット数をカウントする対象のステータス（通常 TSUNDOKU）
     */
    suspend fun updateStatusIfSlotAvailable(
        id: String,
        newStatus: String,
        slotCountStatus: String,
        maxSlots: Int,
    ): Boolean

    /**
     * 記事のAI要約を保存する。
     */
    suspend fun saveSummary(id: String, summary: String)
}
