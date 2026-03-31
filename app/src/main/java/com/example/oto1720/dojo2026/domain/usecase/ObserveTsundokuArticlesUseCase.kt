package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 積読記事リストをFlowで監視するUseCase。
 */
class ObserveTsundokuArticlesUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    operator fun invoke(): Flow<List<ArticleEntity>> = repository.observeTsundokuArticles()
}
