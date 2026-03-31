package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GetDoneArticlesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var articleRepository: ArticleRepository

    @MockK
    private lateinit var digestRepository: DigestRepository

    private val useCase by lazy { GetDoneArticlesUseCase(articleRepository, digestRepository) }

    private fun createArticle(id: String) = ArticleEntity(
        id = id,
        title = "テスト記事 $id",
        url = "https://example.com/$id",
        tags = "android,kotlin",
        createdAt = "2026-03-04T00:00:00Z",
        cachedAt = "2026-03-04T00:00:00Z",
        status = ArticleStatus.DONE,
        aiSummary = null,
    )

    private fun createDigest(articleId: String) = DigestEntity(
        articleId = articleId,
        userMemo = "メモ $articleId",
        aiFeedback = "フィードバック $articleId",
        savedAt = "2026-03-04T10:00:00Z",
    )

    // --- 正常系 ---

    @Test
    fun `invoke - DoneItemに全フィールドが正しく設定される`() = runTest {
        every { articleRepository.observeDoneArticles() } returns flowOf(listOf(createArticle("a1")))
        every { digestRepository.observeAll() } returns flowOf(listOf(createDigest("a1")))

        val result = useCase().first()

        assertEquals("a1", result[0].articleId)
        assertEquals("テスト記事 a1", result[0].title)
        assertEquals("https://example.com/a1", result[0].url)
        assertEquals("メモ a1", result[0].userMemo)
        assertEquals("フィードバック a1", result[0].aiFeedback)
        assertEquals("2026-03-04T10:00:00Z", result[0].savedAt)
    }

    @Test
    fun `invoke - 複数記事がすべて結合される`() = runTest {
        every { articleRepository.observeDoneArticles() } returns
            flowOf(listOf(createArticle("a1"), createArticle("a2")))
        every { digestRepository.observeAll() } returns
            flowOf(listOf(createDigest("a1"), createDigest("a2")))

        val result = useCase().first()

        assertEquals(2, result.size)
    }

    // --- 欠損データ ---

    @Test
    fun `invoke - DigestEntityが存在しない記事は結果に含まれない`() = runTest {
        every { articleRepository.observeDoneArticles() } returns flowOf(listOf(createArticle("a1")))
        every { digestRepository.observeAll() } returns flowOf(emptyList())

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `invoke - DONE記事がゼロのときは空リストを返す`() = runTest {
        every { articleRepository.observeDoneArticles() } returns flowOf(emptyList())
        every { digestRepository.observeAll() } returns flowOf(listOf(createDigest("a1")))

        val result = useCase().first()

        assertTrue(result.isEmpty())
    }
}
