package com.example.oto1720.dojo2026.data.repository

import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import kotlinx.coroutines.flow.Flow

interface DigestRepository {

    /** 消化記録を保存する（既存のものがあれば上書き）。 */
    suspend fun saveDigest(digest: DigestEntity)

    /** すべての消化記録をFlowで監視する。 */
    fun observeAll(): Flow<List<DigestEntity>>

    /** articleId に対応する消化記録を1件取得する。存在しない場合は null を返す。 */
    suspend fun getByArticleId(articleId: String): DigestEntity?

    /**
     * 問いを保存する。既存の [DigestEntity] があれば [DigestEntity.aiQuestion] のみ更新し、
     * なければ新規作成する。
     */
    suspend fun saveQuestion(articleId: String, question: String)

    /**
     * フィードバックと理解度判定を保存する。既存の [DigestEntity] があれば対象フィールドのみ更新し、
     * なければ新規作成する。
     */
    suspend fun saveFeedback(
        articleId: String,
        feedback: String,
        isUnderstandingSufficient: Boolean?,
    )
}
