package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.repository.AiRepository
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GenerateSummaryUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var articleRepository: ArticleRepository

    @MockK
    private lateinit var aiRepository: AiRepository

    private val useCase by lazy { GenerateSummaryUseCase(articleRepository, aiRepository) }

    private fun buildArticle(id: String = "article1", aiSummary: String? = null) = ArticleEntity(
        id = id,
        title = "テスト記事",
        url = "https://example.com",
        tags = "android",
        createdAt = "2026-01-01T00:00:00Z",
        cachedAt = "2026-01-01T00:00:00Z",
        status = "TSUNDOKU",
        aiSummary = aiSummary,
    )

    // --- DB に要約が保存済みの場合 ---

    @Test
    fun `invoke - aiSummaryがDBに保存済みのときAPIを呼ばずDBの値を返す`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle(aiSummary = "既存要約")

        val result = useCase("article1")

        assertTrue(result.isSuccess)
        assertEquals("既存要約", result.getOrNull())
        coVerify(exactly = 0) { aiRepository.generateSummary(any()) }
    }

    @Test
    fun `invoke - aiSummaryがDBに保存済みのときsaveSummaryを呼ばない`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle(aiSummary = "既存要約")

        useCase("article1")

        coVerify(exactly = 0) { articleRepository.saveSummary(any(), any()) }
    }

    // --- DB に要約がない場合（API 呼び出し） ---

    @Test
    fun `invoke - aiSummaryがnullのときAPIを呼んで要約を返す`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle(aiSummary = null)
        coEvery { aiRepository.generateSummary(any()) } returns Result.success("新規要約")
        coEvery { articleRepository.saveSummary(any(), any()) } returns Unit

        val result = useCase("article1")

        assertTrue(result.isSuccess)
        assertEquals("新規要約", result.getOrNull())
    }

    @Test
    fun `invoke - API成功時にsaveSummaryで保存する`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle(aiSummary = null)
        coEvery { aiRepository.generateSummary(any()) } returns Result.success("新規要約")
        coEvery { articleRepository.saveSummary(any(), any()) } returns Unit

        useCase("article1")

        coVerify(exactly = 1) { articleRepository.saveSummary("article1", "新規要約") }
    }

    // --- エラーケース ---

    @Test
    fun `invoke - 記事が存在しないときResult_failureを返す`() = runTest {
        coEvery { articleRepository.getArticleById("unknown") } returns null

        val result = useCase("unknown")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `invoke - APIエラー時にResult_failureを返す`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle(aiSummary = null)
        coEvery { aiRepository.generateSummary(any()) } returns Result.failure(RuntimeException("API error"))

        val result = useCase("article1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke - APIエラー時はsaveSummaryを呼ばない`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle(aiSummary = null)
        coEvery { aiRepository.generateSummary(any()) } returns Result.failure(RuntimeException("API error"))

        useCase("article1")

        coVerify(exactly = 0) { articleRepository.saveSummary(any(), any()) }
    }
}
