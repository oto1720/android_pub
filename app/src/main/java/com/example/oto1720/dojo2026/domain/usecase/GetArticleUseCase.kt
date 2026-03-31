package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import javax.inject.Inject

/**
 * 指定IDの記事を1件取得するUseCase。
 */
class GetArticleUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    suspend operator fun invoke(id: String): ArticleEntity? = repository.getArticleById(id)
}
