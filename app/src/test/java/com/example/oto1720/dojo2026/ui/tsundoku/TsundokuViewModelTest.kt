package com.example.oto1720.dojo2026.ui.tsundoku

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.domain.usecase.ObserveTsundokuArticlesUseCase
import com.example.oto1720.dojo2026.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class TsundokuViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    private lateinit var observeTsundokuArticlesUseCase: ObserveTsundokuArticlesUseCase

    private lateinit var viewModel: TsundokuViewModel

    private val tsundokuArticlesFlow = MutableSharedFlow<List<ArticleEntity>>(replay = 1)

    @Before
    fun setUp() {
        every { observeTsundokuArticlesUseCase() } returns tsundokuArticlesFlow
    }

    private fun createViewModel() {
        viewModel = TsundokuViewModel(observeTsundokuArticlesUseCase)
    }

    private fun createDummyArticle(id: String, title: String = "dummy title"): ArticleEntity {
        return ArticleEntity(
            id = id,
            title = title,
            url = "https://example.com/$id",
            tags = "android,kotlin",
            createdAt = "2026-03-04T00:00:00Z",
            cachedAt = "2026-03-04T00:00:00Z",
            status = ArticleStatus.TSUNDOKU,
            aiSummary = null,
        )
    }

    // --- 初期状態 ---

    @Test
    fun `init - 初期状態はLoadingである`() = runTest {
        createViewModel()

        assertTrue(viewModel.uiState.value is TsundokuUiState.Loading)
    }

    // --- 記事一覧の反映 ---

    @Test
    fun `observeTsundokuArticles - 記事リストが流れてくるとSuccessになる`() = runTest {
        createViewModel()
        val job = launch { viewModel.uiState.collect() }
        runCurrent()

        val articles = listOf(createDummyArticle("1", "article1"))
        tsundokuArticlesFlow.emit(articles)
        runCurrent()

        val state = viewModel.uiState.value as TsundokuUiState.Success
        assertEquals(articles, state.articles)
        job.cancel()
    }

    @Test
    fun `observeTsundokuArticles - 空リストが流れてくるとSuccessかつ空になる`() = runTest {
        createViewModel()
        val job = launch { viewModel.uiState.collect() }
        runCurrent()

        tsundokuArticlesFlow.emit(emptyList())
        runCurrent()

        val state = viewModel.uiState.value as TsundokuUiState.Success
        assertEquals(emptyList<ArticleEntity>(), state.articles)
        job.cancel()
    }

    @Test
    fun `observeTsundokuArticles - 記事が追加されるとSuccessが更新される`() = runTest {
        createViewModel()
        val job = launch { viewModel.uiState.collect() }
        runCurrent()

        // 1件目
        val articles1 = listOf(createDummyArticle("1", "article1"))
        tsundokuArticlesFlow.emit(articles1)
        runCurrent()
        assertEquals(1, (viewModel.uiState.value as TsundokuUiState.Success).articles.size)

        // 2件目追加
        val articles2 = articles1 + createDummyArticle("2", "article2")
        tsundokuArticlesFlow.emit(articles2)
        runCurrent()
        assertEquals(2, (viewModel.uiState.value as TsundokuUiState.Success).articles.size)

        job.cancel()
    }

    // --- WhileSubscribed 動作の確認 ---

    @Test
    fun `uiState - 購読者がいる間だけUseCaseのFlowを購読する`() = runTest {
        createViewModel()
        assertEquals(0, tsundokuArticlesFlow.subscriptionCount.value)

        val job = launch { viewModel.uiState.collect() }
        runCurrent()
        assertEquals(1, tsundokuArticlesFlow.subscriptionCount.value)

        job.cancel()
        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(0, tsundokuArticlesFlow.subscriptionCount.value)
    }
}
