package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveTsundokuCountUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    operator fun invoke(): Flow<Int> = repository
        .observeTsundokuArticles()
        .map { it.size.coerceAtMost(AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS) }
}
