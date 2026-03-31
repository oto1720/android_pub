package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.repository.AiRepository
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import javax.inject.Inject

/**
 * 記事の3行要約を生成するUseCase。
 *
 * DBに要約が保存済みの場合はAPIを呼ばずDBから返す。
 * 未保存の場合はGemini APIで生成し、DBに保存してから返す。
 *
 * @param articleId 要約対象の記事ID
 * @return 生成または取得した要約文字列。エラー時は [Result.failure]
 */
class GenerateSummaryUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository,
) {
    suspend operator fun invoke(articleId: String): Result<String> {
        val article = articleRepository.getArticleById(articleId)
            ?: return Result.failure(IllegalArgumentException("記事が見つかりません: $articleId"))

        article.aiSummary?.let { return Result.success(it) }

        val content = article.body ?: article.title
        return aiRepository.generateSummary(content).onSuccess { summary ->
            articleRepository.saveSummary(articleId, summary)
        }
    }
}
