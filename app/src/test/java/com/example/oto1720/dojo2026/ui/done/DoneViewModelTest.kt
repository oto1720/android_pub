package com.example.oto1720.dojo2026.ui.done

import com.example.oto1720.dojo2026.domain.model.DoneItem
import com.example.oto1720.dojo2026.domain.usecase.ObserveDoneArticlesUseCase
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
class DoneViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    private lateinit var observeDoneArticlesUseCase: ObserveDoneArticlesUseCase

    private lateinit var viewModel: DoneViewModel

    private val doneItemsFlow = MutableSharedFlow<List<DoneItem>>(replay = 1)

    @Before
    fun setUp() {
        every { observeDoneArticlesUseCase() } returns doneItemsFlow
    }

    private fun createViewModel() {
        viewModel = DoneViewModel(observeDoneArticlesUseCase)
    }

    private fun createDoneItem(id: String) = DoneItem(
        articleId = id,
        title = "テスト記事 $id",
        url = "https://example.com/$id",
        tags = "android",
        savedAt = "2026-03-04T10:00:00Z",
        userMemo = "メモ",
        aiFeedback = "フィードバック",
    )

    // --- 初期状態 ---

    @Test
    fun `init - 初期状態はLoadingである`() = runTest {
        createViewModel()

        assertTrue(viewModel.uiState.value is DoneUiState.Loading)
    }

    // --- データ反映 ---

    @Test
    fun `observeDoneArticles - 記事リストが流れてくるとSuccessになる`() = runTest {
        createViewModel()
        val job = launch { viewModel.uiState.collect() }
        runCurrent()

        val items = listOf(createDoneItem("a1"), createDoneItem("a2"))
        doneItemsFlow.emit(items)
        runCurrent()

        val state = viewModel.uiState.value as DoneUiState.Success
        assertEquals(2, state.items.size)
        job.cancel()
    }

    @Test
    fun `observeDoneArticles - 空リストが流れてくるとSuccessかつ空になる`() = runTest {
        createViewModel()
        val job = launch { viewModel.uiState.collect() }
        runCurrent()

        doneItemsFlow.emit(emptyList())
        runCurrent()

        val state = viewModel.uiState.value as DoneUiState.Success
        assertTrue(state.items.isEmpty())
        job.cancel()
    }

    @Test
    fun `observeDoneArticles - 記事が増えるとSuccessが更新される`() = runTest {
        createViewModel()
        val job = launch { viewModel.uiState.collect() }
        runCurrent()

        doneItemsFlow.emit(listOf(createDoneItem("a1")))
        runCurrent()
        assertEquals(1, (viewModel.uiState.value as DoneUiState.Success).items.size)

        doneItemsFlow.emit(listOf(createDoneItem("a1"), createDoneItem("a2")))
        runCurrent()
        assertEquals(2, (viewModel.uiState.value as DoneUiState.Success).items.size)

        job.cancel()
    }

    // --- WhileSubscribed ---

    @Test
    fun `uiState - 購読者がいる間だけUseCaseのFlowを購読する`() = runTest {
        createViewModel()
        assertEquals(0, doneItemsFlow.subscriptionCount.value)

        val job = launch { viewModel.uiState.collect() }
        runCurrent()
        assertEquals(1, doneItemsFlow.subscriptionCount.value)

        job.cancel()
        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(0, doneItemsFlow.subscriptionCount.value)
    }
}
