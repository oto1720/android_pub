package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddToTsundokuUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var repository: ArticleRepository

    private val useCase by lazy { AddToTsundokuUseCase(repository) }

    // --- スロット判定 ---

    @Test
    fun `invoke - スロットが空いているとき - trueを返す`() = runTest {
        coEvery {
            repository.updateStatusIfSlotAvailable(
                id = "article1",
                newStatus = ArticleStatus.TSUNDOKU,
                slotCountStatus = ArticleStatus.TSUNDOKU,
                maxSlots = AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS,
            )
        } returns true

        val result = useCase("article1")

        assertTrue(result)
    }

    @Test
    fun `invoke - スロットが満杯のとき - falseを返す`() = runTest {
        coEvery {
            repository.updateStatusIfSlotAvailable(
                id = any(),
                newStatus = ArticleStatus.TSUNDOKU,
                slotCountStatus = ArticleStatus.TSUNDOKU,
                maxSlots = AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS,
            )
        } returns false

        val result = useCase("article1")

        assertFalse(result)
    }

    // --- リポジトリ呼び出しの検証 ---

    @Test
    fun `invoke - 正しいパラメータでリポジトリを呼ぶ`() = runTest {
        coEvery { repository.updateStatusIfSlotAvailable(any(), any(), any(), any()) } returns true

        useCase("article-id-123")

        coVerify(exactly = 1) {
            repository.updateStatusIfSlotAvailable(
                id = "article-id-123",
                newStatus = ArticleStatus.TSUNDOKU,
                slotCountStatus = ArticleStatus.TSUNDOKU,
                maxSlots = AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS,
            )
        }
    }

}
