package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.repository.AiRepository
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import com.example.oto1720.dojo2026.data.repository.FeedbackResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GenerateFeedbackUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var articleRepository: ArticleRepository

    @MockK
    private lateinit var digestRepository: DigestRepository

    @MockK
    private lateinit var aiRepository: AiRepository

    private val dummyArticle = ArticleEntity(
        id = "article1",
        title = "テスト記事",
        url = "https://example.com/article1",
        body = "記事本文",
        tags = "android",
        createdAt = "2026-03-04T00:00:00Z",
        cachedAt = "2026-03-04T00:00:00Z",
        status = ArticleStatus.TSUNDOKU,
    )

    private val useCase by lazy {
        GenerateFeedbackUseCase(articleRepository, digestRepository, aiRepository)
    }

    // --- 正常系 ---

    @Test
    fun `invoke - 成功時にResult_successでFeedbackResultが返る`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns dummyArticle
        coEvery { aiRepository.generateFeedback(any(), any(), any()) } returns Result.success(
            FeedbackResult("テストフィードバック", isUnderstandingSufficient = true)
        )
        coEvery { digestRepository.saveFeedback(any(), any(), any()) } returns Unit

        val result = useCase("article1", "問い", "メモ")

        assertTrue(result.isSuccess)
        assertEquals("テストフィードバック", result.getOrNull()?.feedback)
        assertEquals(true, result.getOrNull()?.isUnderstandingSufficient)
    }

    @Test
    fun `invoke - 成功時にsaveFeedbackで保存する`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns dummyArticle
        coEvery { aiRepository.generateFeedback(any(), any(), any()) } returns Result.success(
            FeedbackResult("フィードバック", isUnderstandingSufficient = true)
        )
        coEvery { digestRepository.saveFeedback(any(), any(), any()) } returns Unit

        useCase("article1", "問い", "メモ")

        coVerify(exactly = 1) { digestRepository.saveFeedback("article1", "フィードバック", true) }
    }

    @Test
    fun `invoke - 問いとメモを正しくAiRepositoryに渡す`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns dummyArticle
        coEvery { aiRepository.generateFeedback(any(), any(), any()) } returns Result.success(
            FeedbackResult("フィードバック", isUnderstandingSufficient = true)
        )
        coEvery { digestRepository.saveFeedback(any(), any(), any()) } returns Unit

        useCase("article1", "テスト問い", "テストメモ")

        coVerify(exactly = 1) { aiRepository.generateFeedback("記事本文", "テスト問い", "テストメモ") }
    }

    @Test
    fun `invoke - isUnderstandingSufficientがfalseの場合も正しく返る`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns dummyArticle
        coEvery { aiRepository.generateFeedback(any(), any(), any()) } returns Result.success(
            FeedbackResult("理解が不十分です", isUnderstandingSufficient = false)
        )
        coEvery { digestRepository.saveFeedback(any(), any(), any()) } returns Unit

        val result = useCase("article1", "問い", "メモ")

        assertEquals(false, result.getOrNull()?.isUnderstandingSufficient)
    }

    // --- エラー系 ---

    @Test
    fun `invoke - APIエラー時にResult_failureを返す`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns dummyArticle
        coEvery { aiRepository.generateFeedback(any(), any(), any()) } returns Result.failure(RuntimeException("API error"))

        val result = useCase("article1", "問い", "メモ")

        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke - APIエラー時はsaveFeedbackを呼ばない`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns dummyArticle
        coEvery { aiRepository.generateFeedback(any(), any(), any()) } returns Result.failure(RuntimeException("API error"))

        useCase("article1", "問い", "メモ")

        coVerify(exactly = 0) { digestRepository.saveFeedback(any(), any(), any()) }
    }
}
