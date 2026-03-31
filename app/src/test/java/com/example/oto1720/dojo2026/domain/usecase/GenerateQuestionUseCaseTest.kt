package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import com.example.oto1720.dojo2026.data.repository.AiRepository
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GenerateQuestionUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var articleRepository: ArticleRepository

    @MockK
    private lateinit var digestRepository: DigestRepository

    @MockK
    private lateinit var aiRepository: AiRepository

    private val useCase by lazy { GenerateQuestionUseCase(articleRepository, digestRepository, aiRepository) }

    private fun buildArticle(id: String = "article1") = ArticleEntity(
        id = id,
        title = "テスト記事",
        url = "https://example.com",
        tags = "android",
        createdAt = "2026-01-01T00:00:00Z",
        cachedAt = "2026-01-01T00:00:00Z",
        status = "TSUNDOKU",
    )

    private fun buildDigest(aiQuestion: String? = null) = DigestEntity(
        articleId = "article1",
        aiQuestion = aiQuestion,
        savedAt = "2026-01-01T00:00:00Z",
    )

    // --- DB にキャッシュがある場合 ---

    @Test
    fun `invoke - aiQuestionがDBに保存済みのときAPIを呼ばず返す`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle()
        coEvery { digestRepository.getByArticleId("article1") } returns buildDigest(aiQuestion = "キャッシュ問い")

        val result = useCase("article1")

        assertTrue(result.isSuccess)
        assertEquals("キャッシュ問い", result.getOrNull())
        coVerify(exactly = 0) { aiRepository.generateQuestion(any()) }
    }

    @Test
    fun `invoke - aiQuestionがキャッシュ済みのときsaveQuestionを呼ばない`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle()
        coEvery { digestRepository.getByArticleId("article1") } returns buildDigest(aiQuestion = "キャッシュ問い")

        useCase("article1")

        coVerify(exactly = 0) { digestRepository.saveQuestion(any(), any()) }
    }

    // --- DB にキャッシュがない場合（API 呼び出し） ---

    @Test
    fun `invoke - DigestEntityがないときAPIを呼んで問いを返す`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle()
        coEvery { digestRepository.getByArticleId("article1") } returns null
        coEvery { aiRepository.generateQuestion(any()) } returns Result.success("新規問い")
        coEvery { digestRepository.saveQuestion(any(), any()) } returns Unit

        val result = useCase("article1")

        assertTrue(result.isSuccess)
        assertEquals("新規問い", result.getOrNull())
    }

    @Test
    fun `invoke - DigestEntityはあるがaiQuestionがnullのときAPIを呼ぶ`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle()
        coEvery { digestRepository.getByArticleId("article1") } returns buildDigest(aiQuestion = null)
        coEvery { aiRepository.generateQuestion(any()) } returns Result.success("新規問い")
        coEvery { digestRepository.saveQuestion(any(), any()) } returns Unit

        val result = useCase("article1")

        assertTrue(result.isSuccess)
        assertEquals("新規問い", result.getOrNull())
    }

    @Test
    fun `invoke - API成功時にsaveQuestionで保存する`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle()
        coEvery { digestRepository.getByArticleId("article1") } returns null
        coEvery { aiRepository.generateQuestion(any()) } returns Result.success("新規問い")
        coEvery { digestRepository.saveQuestion(any(), any()) } returns Unit

        useCase("article1")

        coVerify(exactly = 1) { digestRepository.saveQuestion("article1", "新規問い") }
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
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle()
        coEvery { digestRepository.getByArticleId("article1") } returns null
        coEvery { aiRepository.generateQuestion(any()) } returns Result.failure(RuntimeException("API error"))

        val result = useCase("article1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `invoke - APIエラー時はsaveQuestionを呼ばない`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns buildArticle()
        coEvery { digestRepository.getByArticleId("article1") } returns null
        coEvery { aiRepository.generateQuestion(any()) } returns Result.failure(RuntimeException("API error"))

        useCase("article1")

        coVerify(exactly = 0) { digestRepository.saveQuestion(any(), any()) }
    }
}
