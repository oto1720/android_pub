package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GetTrendArticlesUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var repository: ArticleRepository

    private val useCase by lazy { GetTrendArticlesUseCase(repository) }

    // --- 正常系: tag = null ---

    @Test
    fun `invoke - tag_null - refreshPortalArticlesにnullのqueryを渡す`() = runTest {
        coEvery { repository.refreshPortalArticles(query = null) } returns Result.success(Unit)
        coEvery { repository.observePortalArticles() } returns flowOf(listOf(createEntity("1", tags = "Kotlin")))

        useCase(tag = null)

        coVerify(exactly = 1) { repository.refreshPortalArticles(query = null) }
    }

    @Test
    fun `invoke - tag_null - 全記事を返す`() = runTest {
        val articles = listOf(createEntity("1", tags = "Android"), createEntity("2", tags = "Kotlin"))
        coEvery { repository.refreshPortalArticles(query = null) } returns Result.success(Unit)
        coEvery { repository.observePortalArticles() } returns flowOf(articles)

        val result = useCase(tag = null)

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `invoke - tag_null - 0件でも正常に返す`() = runTest {
        coEvery { repository.refreshPortalArticles(query = null) } returns Result.success(Unit)
        coEvery { repository.observePortalArticles() } returns flowOf(emptyList())

        val result = useCase(tag = null)

        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrNull()?.size)
    }

    // --- 正常系: tag 指定 ---

    @Test
    fun `invoke - tag指定 - "tag_タグ名"のqueryをrefreshPortalArticlesに渡す`() = runTest {
        coEvery { repository.refreshPortalArticles(query = "tag:Android") } returns Result.success(Unit)
        coEvery { repository.observePortalArticles() } returns flowOf(emptyList())

        useCase(tag = "Android")

        coVerify(exactly = 1) { repository.refreshPortalArticles(query = "tag:Android") }
    }

    @Test
    fun `invoke - tag指定 - タグが一致する記事のみ返す`() = runTest {
        val articles = listOf(
            createEntity("1", tags = "Android,Kotlin"),
            createEntity("2", tags = "iOS"),
            createEntity("3", tags = "Android"),
        )
        coEvery { repository.refreshPortalArticles(query = "tag:Android") } returns Result.success(Unit)
        coEvery { repository.observePortalArticles() } returns flowOf(articles)

        val result = useCase(tag = "Android")

        assertEquals(2, result.getOrNull()?.size)
        assertEquals(setOf("1", "3"), result.getOrNull()?.map { it.id }?.toSet())
    }

    @Test
    fun `invoke - tag指定 - 大文字小文字を無視してフィルタリングする`() = runTest {
        val articles = listOf(createEntity("1", tags = "android"), createEntity("2", tags = "iOS"))
        coEvery { repository.refreshPortalArticles(query = "tag:Android") } returns Result.success(Unit)
        coEvery { repository.observePortalArticles() } returns flowOf(articles)

        val result = useCase(tag = "Android")

        assertEquals(1, result.getOrNull()?.size)
        assertEquals("1", result.getOrNull()?.first()?.id)
    }

    @Test
    fun `invoke - tag指定 - tagsにスペースが含まれても正しくフィルタリングする`() = runTest {
        val articles = listOf(
            createEntity("1", tags = " Android , Kotlin "),
            createEntity("2", tags = "iOS"),
        )
        coEvery { repository.refreshPortalArticles(query = "tag:Android") } returns Result.success(Unit)
        coEvery { repository.observePortalArticles() } returns flowOf(articles)

        val result = useCase(tag = "Android")

        assertEquals(1, result.getOrNull()?.size)
        assertEquals("1", result.getOrNull()?.first()?.id)
    }

    // --- エラー系 ---

    @Test
    fun `invoke - APIエラー - Result_failureを返す`() = runTest {
        val exception = RuntimeException("Network Error")
        coEvery { repository.refreshPortalArticles(any()) } returns Result.failure(exception)

        val result = useCase(tag = null)

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `invoke - APIエラー時 - observePortalArticlesを呼ばない`() = runTest {
        coEvery { repository.refreshPortalArticles(any()) } returns Result.failure(RuntimeException())

        useCase(tag = null)

        coVerify(exactly = 0) { repository.observePortalArticles() }
    }

    @Test
    fun `invoke - observePortalArticlesが例外をthrow - Result_failureを返す`() = runTest {
        val exception = RuntimeException("DB Error")
        coEvery { repository.refreshPortalArticles(query = null) } returns Result.success(Unit)
        coEvery { repository.observePortalArticles() } throws exception

        val result = useCase(tag = null)

        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    // --- ヘルパー ---

    private fun createEntity(
        id: String,
        tags: String = "",
        title: String = "title_$id",
    ) = ArticleEntity(
        id = id,
        title = title,
        url = "https://qiita.com/$id",
        tags = tags,
        createdAt = "2026-03-03T00:00:00Z",
        cachedAt = "2026-03-03T00:00:00Z",
        status = ArticleStatus.PORTAL,
    )
}
