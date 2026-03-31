package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.repository.AiRepository
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import com.example.oto1720.dojo2026.domain.model.FeedbackResult
import javax.inject.Inject

/**
 * ユーザーのメモに対するAIフィードバックと理解度判定を生成するUseCase。
 *
 * 記事本文・問い・ユーザー回答をもとに Gemini API でフィードバックを生成し、
 * 理解度判定（[FeedbackResult.isUnderstandingSufficient]）とともに保存して返す。
 *
 * @param articleId 対象の記事ID
 * @param question AI が生成した問い
 * @param userMemo ユーザーが入力したメモ
 * @return [FeedbackResult]。エラー時は [Result.failure]
 */
class GenerateFeedbackUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val digestRepository: DigestRepository,
    private val aiRepository: AiRepository,
) {
    suspend operator fun invoke(
        articleId: String,
        question: String,
        userMemo: String,
    ): Result<FeedbackResult> {
        val article = articleRepository.getArticleById(articleId)
            ?: return Result.failure(IllegalArgumentException("記事が見つかりません: $articleId"))
        val content = article.body ?: article.title
        return aiRepository.generateFeedback(content, question, userMemo).onSuccess { result ->
            digestRepository.saveFeedback(
                articleId = articleId,
                feedback = result.feedback,
                isUnderstandingSufficient = result.isUnderstandingSufficient,
            )
        }
    }
}
