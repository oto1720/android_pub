package com.example.oto1720.dojo2026.ui.done

import androidx.lifecycle.SavedStateHandle
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import com.example.oto1720.dojo2026.rules.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DoneDetailViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    private lateinit var articleRepository: ArticleRepository

    @MockK
    private lateinit var digestRepository: DigestRepository

    private fun createViewModel(articleId: String = "article1"): DoneDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("articleId" to articleId))
        return DoneDetailViewModel(savedStateHandle, articleRepository, digestRepository)
    }

    private fun createArticle(id: String = "article1") = ArticleEntity(
        id = id,
        title = "テスト記事",
        url = "https://example.com/$id",
        tags = "android,kotlin",
        createdAt = "2026-03-04T00:00:00Z",
        cachedAt = "2026-03-04T00:00:00Z",
        status = ArticleStatus.DONE,
        aiSummary = null,
    )

    private fun createDigest(articleId: String = "article1") = DigestEntity(
        articleId = articleId,
        userMemo = "テストメモ",
        aiFeedback = "テストフィードバック",
        savedAt = "2026-03-04T10:00:00Z",
    )

    // --- 初期状態 ---

    @Test
    fun `init - 初期状態はLoadingである`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns createArticle()
        coEvery { digestRepository.getByArticleId(any()) } returns createDigest()
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value is DoneDetailUiState.Loading)
    }

    // --- データ取得成功 ---

    @Test
    fun `init - 記事と消化記録が両方存在する場合はSuccessになる`() = runTest {
        coEvery { articleRepository.getArticleById("article1") } returns createArticle()
        coEvery { digestRepository.getByArticleId("article1") } returns createDigest()

        val viewModel = createViewModel("article1")
        runCurrent()

        assertTrue(viewModel.uiState.value is DoneDetailUiState.Success)
    }

    @Test
    fun `init - Successに全フィールドが正しく設定される`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns createArticle()
        coEvery { digestRepository.getByArticleId(any()) } returns createDigest()

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.uiState.value as DoneDetailUiState.Success
        assertEquals("テスト記事", state.item.title)
        assertEquals("テストメモ", state.item.userMemo)
        assertEquals("テストフィードバック", state.item.aiFeedback)
    }

    // --- エラー ---

    @Test
    fun `init - 記事が存在しない場合はErrorになる`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns null
        coEvery { digestRepository.getByArticleId(any()) } returns createDigest()

        val viewModel = createViewModel()
        runCurrent()

        assertTrue(viewModel.uiState.value is DoneDetailUiState.Error)
    }

    @Test
    fun `init - 消化記録が存在しない場合はErrorになる`() = runTest {
        coEvery { articleRepository.getArticleById(any()) } returns createArticle()
        coEvery { digestRepository.getByArticleId(any()) } returns null

        val viewModel = createViewModel()
        runCurrent()

        assertTrue(viewModel.uiState.value is DoneDetailUiState.Error)
    }
}
