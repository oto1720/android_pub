package com.example.oto1720.dojo2026.data.repository

import com.example.oto1720.dojo2026.data.local.dao.ArticleDao
import com.example.oto1720.dojo2026.data.local.dao.RemoteKeyDao
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.remote.api.QiitaApiService
import com.example.oto1720.dojo2026.data.remote.dto.QiitaArticleDto
import com.example.oto1720.dojo2026.data.remote.dto.UserDto
import com.example.oto1720.dojo2026.domain.model.AppError
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ArticleRepositoryImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var apiService: QiitaApiService

    @MockK(relaxUnitFun = true) // suspend fun で戻り値が Unit のものはスタブしなくてもよい
    private lateinit var articleDao: ArticleDao

    @MockK(relaxUnitFun = true)
    private lateinit var remoteKeyDao: RemoteKeyDao

    private lateinit var repository: ArticleRepositoryImpl

    @Before
    fun setUp() {
        coEvery { articleDao.getIdsByStatuses(any()) } returns emptyList()
        repository = ArticleRepositoryImpl(apiService, articleDao, remoteKeyDao)
    }

    @Test
    fun `refreshPortalArticles - 正常系 - 古いPORTAL記事が削除される`() = runTest {
        // Arrange
        coEvery { apiService.getItems(any(), any(), any()) } returns emptyList()

        // Act
        repository.refreshPortalArticles(null)

        // Assert
        coVerify(exactly = 1) { articleDao.deleteByStatus(ArticleStatus.PORTAL) }
    }

    @Test
    fun `refreshPortalArticles - 正常系 - APIレスポンスがDBに保存される`() = runTest {
        // Arrange
        val dtos = listOf(
            createDummyDto(id = "1", title = "title1"),
            createDummyDto(id = "2", title = "title2")
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns dtos
        val entitySlot = slot<List<ArticleEntity>>()

        // Act
        repository.refreshPortalArticles(null)

        // Assert
        coVerify(exactly = 1) { articleDao.insertAll(capture(entitySlot)) }
        assertEquals(2, entitySlot.captured.size)
        assertEquals("1", entitySlot.captured[0].id)
        assertEquals("title1", entitySlot.captured[0].title)
        assertEquals(ArticleStatus.PORTAL, entitySlot.captured[0].status)
    }

    @Test
    fun `refreshPortalArticles - エラー系 - APIエラー時にResult_failureを返す`() = runTest {
        // Arrange
        val exception = RuntimeException("Network Error")
        coEvery { apiService.getItems(any(), any(), any()) } throws exception

        // Act
        val result = repository.refreshPortalArticles(null)

        // Assert: AppError.UnknownError にマッピングされることを確認
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.UnknownError)
        assertEquals("Network Error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `refreshPortalArticles - 積読・消化済みの記事は上書きされない`() = runTest {
        // Arrange: id=1 は既に積読済み
        coEvery { articleDao.getIdsByStatuses(any()) } returns listOf("1")
        val dtos = listOf(
            createDummyDto(id = "1", title = "title1"),
            createDummyDto(id = "2", title = "title2")
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns dtos
        val entitySlot = slot<List<ArticleEntity>>()

        // Act
        repository.refreshPortalArticles(null)

        // Assert: id=1 は除外され、id=2 のみ挿入される
        coVerify(exactly = 1) { articleDao.insertAll(capture(entitySlot)) }
        assertEquals(1, entitySlot.captured.size)
        assertEquals("2", entitySlot.captured[0].id)
    }

    @Test
    fun `refreshPortalArticles - id_nullのDTOが除外される`() = runTest {
        // Arrange
        val dtos = listOf(
            createDummyDto(id = "1", title = "title1"),
            createDummyDto(id = null, title = "title_null"), // id が null の DTO
            createDummyDto(id = "2", title = "title2")
        )
        coEvery { apiService.getItems(any(), any(), any()) } returns dtos
        val entitySlot = slot<List<ArticleEntity>>()

        // Act
        repository.refreshPortalArticles(null)

        // Assert
        coVerify(exactly = 1) { articleDao.insertAll(capture(entitySlot)) }
        assertEquals(2, entitySlot.captured.size)
        assertTrue(entitySlot.captured.none { it.title == "title_null" })
    }

    @Test
    fun `refreshPortalArticles - 401エラー時にAppError_Unauthorizedを返す`() = runTest {
        // Arrange
        val response = Response.error<List<QiitaArticleDto>>(401, "Unauthorized".toResponseBody())
        coEvery { apiService.getItems(any(), any(), any()) } throws HttpException(response)

        // Act
        val result = repository.refreshPortalArticles(null)

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.Unauthorized)
    }

    @Test
    fun `refreshPortalArticles - 429エラー時にAppError_RateLimitExceededを返す`() = runTest {
        // Arrange
        val response = Response.error<List<QiitaArticleDto>>(429, "Too Many Requests".toResponseBody())
        coEvery { apiService.getItems(any(), any(), any()) } throws HttpException(response)

        // Act
        val result = repository.refreshPortalArticles(null)

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.RateLimitExceeded)
    }

    @Test
    fun `refreshPortalArticles - IOExceptionのときAppError_NetworkErrorを返す`() = runTest {
        // Arrange
        coEvery { apiService.getItems(any(), any(), any()) } throws java.io.IOException("timeout")

        // Act
        val result = repository.refreshPortalArticles(null)

        // Assert
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.NetworkError)
    }

    @Test
    fun `updateStatusIfSlotAvailable - DAOが1を返したとき - trueを返す`() = runTest {
        coEvery { articleDao.updateStatusIfSlotAvailable(any(), any(), any(), any()) } returns 1

        val result = repository.updateStatusIfSlotAvailable(
            id = "article1",
            newStatus = ArticleStatus.TSUNDOKU,
            slotCountStatus = ArticleStatus.TSUNDOKU,
            maxSlots = 5,
        )

        assertTrue(result)
    }

    @Test
    fun `updateStatusIfSlotAvailable - DAOが0を返したとき - falseを返す`() = runTest {
        coEvery { articleDao.updateStatusIfSlotAvailable(any(), any(), any(), any()) } returns 0

        val result = repository.updateStatusIfSlotAvailable(
            id = "article1",
            newStatus = ArticleStatus.TSUNDOKU,
            slotCountStatus = ArticleStatus.TSUNDOKU,
            maxSlots = 5,
        )

        assertFalse(result)
    }

    private fun createDummyDto(
        id: String?,
        title: String = "dummy title",
        url: String = "https://example.com",
        createdAt: String = "2026-03-03T00:00:00Z"
    ): QiitaArticleDto {
        return QiitaArticleDto(
            id = id,
            title = title,
            url = url,
            body = null,
            createdAt = createdAt,
            likesCount = 0,
            tags = emptyList(),
            user = UserDto(id = "user1", profileImageUrl = "")
        )
    }
}
