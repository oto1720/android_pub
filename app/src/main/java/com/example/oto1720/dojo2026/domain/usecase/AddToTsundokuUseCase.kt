package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import javax.inject.Inject

/**
 * 記事を積読スロットに追加するUseCase。
 * スロットが満杯（5件）の場合は何もしない。
 * カウント確認と書き込みはDAOレベルでアトミックに行い競合を防ぐ。
 */
class AddToTsundokuUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    companion object {
        const val MAX_TSUNDOKU_SLOTS = 5
    }

    /**
     * @param articleId 追加する記事のID
     * @return 追加成功 true、スロット満杯で追加不可 false
     */
    suspend operator fun invoke(articleId: String): Boolean {
        return repository.updateStatusIfSlotAvailable(
            id = articleId,
            newStatus = ArticleStatus.TSUNDOKU,
            slotCountStatus = ArticleStatus.TSUNDOKU,
            maxSlots = MAX_TSUNDOKU_SLOTS,
        )
    }
}
