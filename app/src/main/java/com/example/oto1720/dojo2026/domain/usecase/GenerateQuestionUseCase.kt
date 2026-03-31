package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.repository.AiRepository
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import javax.inject.Inject

/**
 * 理解度チェックの問いを生成するUseCase。
 *
 * DBに問いが保存済みの場合はAPIを呼ばずDBから返す。
 * 未保存の場合はGemini APIで生成し、[DigestEntity.aiQuestion] に保存してから返す。
 *
 * @param articleId 問い生成対象の記事ID
 * @return 生成または取得した問い文字列。エラー時は [Result.failure]
 */
class GenerateQuestionUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val digestRepository: DigestRepository,
    private val aiRepository: AiRepository,
) {
    suspend operator fun invoke(articleId: String): Result<String> {
        val article = articleRepository.getArticleById(articleId)
            ?: return Result.failure(IllegalArgumentException("記事が見つかりません: $articleId"))

        digestRepository.getByArticleId(articleId)?.aiQuestion?.let {
            return Result.success(it)
        }

        val content = article.body ?: article.title
        return aiRepository.generateQuestion(content).onSuccess { question ->
            digestRepository.saveQuestion(articleId, question)
        }
    }
}
