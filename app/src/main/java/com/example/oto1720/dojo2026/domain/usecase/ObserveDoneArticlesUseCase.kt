package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import com.example.oto1720.dojo2026.domain.model.DoneItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * 消化済み記事と消化記録を結合してFlowで返すUseCase。
 * DONE ステータスの ArticleEntity と DigestEntity を articleId で JOIN する。
 */
class ObserveDoneArticlesUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val digestRepository: DigestRepository,
) {
    operator fun invoke(): Flow<List<DoneItem>> =
        combine(
            articleRepository.observeDoneArticles(),
            digestRepository.observeAll(),
        ) { articles, digests ->
            val digestMap = digests.associateBy { it.articleId }
            articles.mapNotNull { article ->
                digestMap[article.id]?.let { digest ->
                    DoneItem(
                        articleId = article.id,
                        title = article.title,
                        url = article.url,
                        tags = article.tags,
                        savedAt = digest.savedAt,
                        userMemo = digest.userMemo.orEmpty(),
                        aiFeedback = digest.aiFeedback.orEmpty(),
                    )
                }
            }
        }
}
