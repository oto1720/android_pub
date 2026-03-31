package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Room にキャッシュされたポータル記事を Flow で監視する UseCase。
 *
 * オフライン時や API 失敗時のキャッシュ表示に使用する。
 */
class ObservePortalArticlesUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    operator fun invoke(): Flow<List<ArticleEntity>> = repository.observePortalArticles()
}
