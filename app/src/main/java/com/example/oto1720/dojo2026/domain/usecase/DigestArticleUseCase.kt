package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import com.example.oto1720.dojo2026.util.ISO_8601_FORMAT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/**
 * 記事を消化する（読了確定）UseCase。
 *
 * 1. メモ・フィードバックを [DigestEntity] に保存する。
 * 2. [ArticleEntity.status] を [ArticleStatus.DONE] に更新する。
 *    これにより積読スロットが自動的に解放される（TSUNDOKU カウント -1）。
 */
class DigestArticleUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val digestRepository: DigestRepository,
) {
    /**
     * @param articleId 消化する記事のID
     * @param memo ユーザーが入力したメモ
     * @param feedback AIが生成したフィードバック
     */
    suspend operator fun invoke(
        articleId: String,
        memo: String,
        feedback: String,
    ) {
        val savedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        digestRepository.saveDigest(
            DigestEntity(
                articleId = articleId,
                userMemo = memo,
                aiFeedback = feedback,
                savedAt = savedAt,
            )
        )
        articleRepository.updateStatus(id = articleId, status = ArticleStatus.DONE)
    }

}
