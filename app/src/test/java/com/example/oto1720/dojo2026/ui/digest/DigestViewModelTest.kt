package com.example.oto1720.dojo2026.ui.digest

import androidx.lifecycle.SavedStateHandle
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.network.NetworkMonitor
import com.example.oto1720.dojo2026.domain.usecase.AddToTsundokuUseCase
import com.example.oto1720.dojo2026.domain.usecase.DigestArticleUseCase
import com.example.oto1720.dojo2026.domain.model.FeedbackResult
import com.example.oto1720.dojo2026.domain.usecase.GenerateFeedbackUseCase
import com.example.oto1720.dojo2026.domain.usecase.GenerateQuestionUseCase
import com.example.oto1720.dojo2026.domain.usecase.GenerateSummaryUseCase
import com.example.oto1720.dojo2026.domain.usecase.GetArticleUseCase
import com.example.oto1720.dojo2026.rules.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DigestViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    private lateinit var getArticleUseCase: GetArticleUseCase

    @MockK
    private lateinit var addToTsundokuUseCase: AddToTsundokuUseCase

    @MockK
    private lateinit var digestArticleUseCase: DigestArticleUseCase

    @MockK
    private lateinit var generateQuestionUseCase: GenerateQuestionUseCase

    @MockK
    private lateinit var generateFeedbackUseCase: GenerateFeedbackUseCase

    @MockK
    private lateinit var generateSummaryUseCase: GenerateSummaryUseCase

    @MockK
    private lateinit var networkMonitor: NetworkMonitor

    @Before
    fun setUp() {
        every { networkMonitor.isOnline } returns flowOf(true)
    }

    private fun createViewModel(articleId: String = "article1"): DigestViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("articleId" to articleId))
        return DigestViewModel(
            savedStateHandle,
            getArticleUseCase,
            addToTsundokuUseCase,
            digestArticleUseCase,
            generateQuestionUseCase,
            generateFeedbackUseCase,
            generateSummaryUseCase,
            networkMonitor,
        )
    }

    private fun createDummyArticle(
        id: String = "article1",
        status: String = ArticleStatus.TSUNDOKU,
    ) = ArticleEntity(
        id = id,
        title = "テスト記事",
        url = "https://example.com/$id",
        tags = "android,kotlin",
        createdAt = "2026-03-04T00:00:00Z",
        cachedAt = "2026-03-04T00:00:00Z",
        status = status,
        aiSummary = null,
    )

    // --- 初期ロード ---

    @Test
    fun `init - 初期状態はWebLoadingである`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value is DigestUiState.WebLoading)
    }

    @Test
    fun `init - 記事が存在する場合はReadModeになる`() = runTest {
        val article = createDummyArticle(id = "article1")
        coEvery { getArticleUseCase("article1") } returns article

        val viewModel = createViewModel("article1")
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.ReadMode
        assertEquals(article, state.article)
    }

    @Test
    fun `init - 記事が存在しない場合はErrorになる`() = runTest {
        coEvery { getArticleUseCase(any()) } returns null

        val viewModel = createViewModel()
        runCurrent()

        assertTrue(viewModel.uiState.value is DigestUiState.Error)
    }

    @Test
    fun `init - 正しいarticleIdでUseCaseを呼ぶ`() = runTest {
        coEvery { getArticleUseCase("specific-id") } returns createDummyArticle(id = "specific-id")

        createViewModel("specific-id")
        runCurrent()

        coVerify(exactly = 1) { getArticleUseCase("specific-id") }
    }

    // --- addToTsundoku ---

    @Test
    fun `addToTsundoku - 追加成功時にAddedToTsundokuイベントが発行される`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { addToTsundokuUseCase(any()) } returns true
        val viewModel = createViewModel()
        runCurrent()

        val events = mutableListOf<DigestEvent>()
        val collectJob = launch { viewModel.event.collect { events.add(it) } }

        viewModel.addToTsundoku()
        runCurrent()

        assertEquals(listOf(DigestEvent.AddedToTsundoku), events)
        collectJob.cancel()
    }

    @Test
    fun `addToTsundoku - スロット満杯時にSlotFullイベントが発行される`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { addToTsundokuUseCase(any()) } returns false
        val viewModel = createViewModel()
        runCurrent()

        val events = mutableListOf<DigestEvent>()
        val collectJob = launch { viewModel.event.collect { events.add(it) } }

        viewModel.addToTsundoku()
        runCurrent()

        assertEquals(listOf(DigestEvent.SlotFull), events)
        collectJob.cancel()
    }

    // --- showQuestion ---

    @Test
    fun `showQuestion - ReadModeからQuestionModeへ遷移する`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("AIの問い")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.showQuestion()
        runCurrent()

        assertTrue(viewModel.uiState.value is DigestUiState.QuestionMode)
    }

    @Test
    fun `showQuestion - QuestionModeに記事が引き継がれる`() = runTest {
        val article = createDummyArticle()
        coEvery { getArticleUseCase(any()) } returns article
        coEvery { generateQuestionUseCase(any()) } returns Result.success("AIの問い")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.showQuestion()
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.QuestionMode
        assertEquals(article, state.article)
    }

    @Test
    fun `showQuestion - AI生成の問いがQuestionModeに設定される`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("AIが生成した問い")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.showQuestion()
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.QuestionMode
        assertEquals("AIが生成した問い", state.question)
    }

    @Test
    fun `showQuestion - GenerateQuestionUseCaseを呼ぶ`() = runTest {
        coEvery { getArticleUseCase("article1") } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
        val viewModel = createViewModel("article1")
        runCurrent()

        viewModel.showQuestion()
        runCurrent()

        coVerify(exactly = 1) { generateQuestionUseCase("article1") }
    }

    @Test
    fun `showQuestion - APIエラー時はフォールバック問いでQuestionModeへ遷移する`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.failure(RuntimeException("error"))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.showQuestion()
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.QuestionMode
        assertTrue(state.question.isNotBlank())
    }

    @Test
    fun `showQuestion - ReadMode以外では何もしない`() = runTest {
        coEvery { getArticleUseCase(any()) } returns null
        val viewModel = createViewModel()
        runCurrent()

        viewModel.showQuestion()
        runCurrent()

        assertTrue(viewModel.uiState.value is DigestUiState.Error)
    }

    @Test
    fun `showQuestion - 問い生成中はReadModeのisLoadingがtrueになる`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("AIの問い")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.showQuestion()
        // runCurrent() を呼ぶ前: isLoading = true の状態を確認
        val state = viewModel.uiState.value as DigestUiState.ReadMode
        assertTrue(state.isLoading)
    }

    // --- submitMemo ---

    @Test
    fun `submitMemo - QuestionModeからFeedbackModeへ遷移する`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(FeedbackResult("フィードバック", true))
        val viewModel = createViewModel()
        runCurrent()
        viewModel.showQuestion()
        runCurrent()

        viewModel.submitMemo("テストメモ")
        runCurrent()

        assertTrue(viewModel.uiState.value is DigestUiState.FeedbackMode)
    }

    @Test
    fun `submitMemo - メモがFeedbackModeに引き継がれる`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(FeedbackResult("フィードバック", true))
        val viewModel = createViewModel()
        runCurrent()
        viewModel.showQuestion()
        runCurrent()

        viewModel.submitMemo("テストメモ")
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.FeedbackMode
        assertEquals("テストメモ", state.memo)
    }

    @Test
    fun `submitMemo - AI生成のフィードバックがFeedbackModeに設定される`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(FeedbackResult("AIのフィードバック", true))
        val viewModel = createViewModel()
        runCurrent()
        viewModel.showQuestion()
        runCurrent()

        viewModel.submitMemo("テストメモ")
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.FeedbackMode
        assertEquals("AIのフィードバック", state.feedback)
    }

    @Test
    fun `submitMemo - 問いがFeedbackModeに引き継がれる`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("AIの問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(FeedbackResult("フィードバック", true))
        val viewModel = createViewModel()
        runCurrent()
        viewModel.showQuestion()
        runCurrent()

        viewModel.submitMemo("テストメモ")
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.FeedbackMode
        assertEquals("AIの問い", state.question)
    }

    @Test
    fun `submitMemo - GenerateFeedbackUseCaseを問いとメモで呼ぶ`() = runTest {
        coEvery { getArticleUseCase("article1") } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("AIの問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(FeedbackResult("フィードバック", true))
        val viewModel = createViewModel("article1")
        runCurrent()
        viewModel.showQuestion()
        runCurrent()

        viewModel.submitMemo("テストメモ")
        runCurrent()

        coVerify(exactly = 1) { generateFeedbackUseCase("article1", "AIの問い", "テストメモ") }
    }

    @Test
    fun `submitMemo - フィードバック生成中はQuestionModeのisLoadingがtrueになる`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(FeedbackResult("フィードバック", true))
        val viewModel = createViewModel()
        runCurrent()
        viewModel.showQuestion()
        runCurrent()

        viewModel.submitMemo("テストメモ")
        // runCurrent() を呼ぶ前: isLoading = true の状態を確認
        val state = viewModel.uiState.value as DigestUiState.QuestionMode
        assertTrue(state.isLoading)
    }

    @Test
    fun `submitMemo - isUnderstandingSufficientがfalseの時canDigestがfalseになる`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(
            FeedbackResult("理解が不十分です", isUnderstandingSufficient = false)
        )
        val viewModel = createViewModel()
        runCurrent()
        viewModel.showQuestion()
        runCurrent()

        viewModel.submitMemo("浅いメモ")
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.FeedbackMode
        assertEquals(false, state.canDigest)
    }

    @Test
    fun `submitMemo - APIエラー時はフォールバックフィードバックでFeedbackModeへ遷移する`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.failure(RuntimeException("error"))
        val viewModel = createViewModel()
        runCurrent()
        viewModel.showQuestion()
        runCurrent()

        viewModel.submitMemo("テストメモ")
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.FeedbackMode
        assertTrue(state.feedback.isNotBlank())
    }

    // --- consumeArticle ---

    @Test
    fun `consumeArticle - DigestArticleUseCaseを正しいパラメータで呼ぶ`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("AIの問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(FeedbackResult("AIのフィードバック", true))
        coEvery { digestArticleUseCase(any(), any(), any()) } returns Unit
        val viewModel = createViewModel("article1")
        runCurrent()
        viewModel.showQuestion()
        runCurrent()
        viewModel.submitMemo("テストメモ")
        runCurrent()

        viewModel.consumeArticle()
        runCurrent()

        coVerify(exactly = 1) {
            digestArticleUseCase(
                articleId = "article1",
                memo = "テストメモ",
                feedback = "AIのフィードバック",
            )
        }
    }

    @Test
    fun `consumeArticle - NavigateToDoneイベントが発行される`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
        coEvery { generateFeedbackUseCase(any(), any(), any()) } returns Result.success(FeedbackResult("フィードバック", true))
        coEvery { digestArticleUseCase(any(), any(), any()) } returns Unit
        val viewModel = createViewModel()
        runCurrent()
        viewModel.showQuestion()
        runCurrent()
        viewModel.submitMemo("テストメモ")
        runCurrent()

        val events = mutableListOf<DigestEvent>()
        val collectJob = launch { viewModel.event.collect { events.add(it) } }

        viewModel.consumeArticle()
        runCurrent()

        assertEquals(listOf(DigestEvent.NavigateToDone), events)
        collectJob.cancel()
    }

    @Test
    fun `consumeArticle - FeedbackMode以外では何もしない`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        val viewModel = createViewModel()
        runCurrent()

        viewModel.consumeArticle()
        runCurrent()

        coVerify(exactly = 0) { digestArticleUseCase(any(), any(), any()) }
    }

    // --- toggleViewMode ---

    @Test
    fun `toggleViewMode - WebView表示からMarkdown表示に切り替わる`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        val viewModel = createViewModel()
        runCurrent()

        viewModel.toggleViewMode()

        val state = viewModel.uiState.value as DigestUiState.ReadMode
        assertTrue(state.isMarkdownMode)
    }

    @Test
    fun `toggleViewMode - Markdown表示からWebView表示に切り替わる`() = runTest {
        coEvery { getArticleUseCase(any()) } returns createDummyArticle()
        val viewModel = createViewModel()
        runCurrent()
        viewModel.toggleViewMode() // WebView → Markdown

        viewModel.toggleViewMode() // Markdown → WebView

        val state = viewModel.uiState.value as DigestUiState.ReadMode
        assertFalse(state.isMarkdownMode)
    }

    @Test
    fun `toggleViewMode - ReadMode以外では何もしない`() = runTest {
        coEvery { getArticleUseCase(any()) } returns null
        val viewModel = createViewModel()
        runCurrent()
        assertTrue(viewModel.uiState.value is DigestUiState.Error)

        viewModel.toggleViewMode() // ErrorMode なので無視される

        assertTrue(viewModel.uiState.value is DigestUiState.Error)
    }

    // --- オフライン時の自動 Markdown フォールバック ---

    @Test
    fun `init - オフライン時にbodyがある場合はisMarkdownModeがtrueになる`() = runTest {
        val article = createDummyArticle().copy(body = "# Markdown本文")
        coEvery { getArticleUseCase(any()) } returns article
        every { networkMonitor.isOnline } returns flowOf(false)

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.ReadMode
        assertTrue(state.isMarkdownMode)
    }

    @Test
    fun `init - オフライン時にbodyがない場合はisMarkdownModeがfalseのまま`() = runTest {
        val article = createDummyArticle().copy(body = null)
        coEvery { getArticleUseCase(any()) } returns article
        every { networkMonitor.isOnline } returns flowOf(false)

        val viewModel = createViewModel()
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.ReadMode
        assertFalse(state.isMarkdownMode)
    }

    @Test
    fun `isOnline - オフラインになったときbodyがあればisMarkdownModeがtrueになる`() = runTest {
        val article = createDummyArticle().copy(body = "# Markdown本文")
        coEvery { getArticleUseCase(any()) } returns article
        val onlineFlow = MutableStateFlow(true)
        every { networkMonitor.isOnline } returns onlineFlow

        val viewModel = createViewModel()
        runCurrent()
        assertFalse((viewModel.uiState.value as DigestUiState.ReadMode).isMarkdownMode)

        onlineFlow.value = false
        runCurrent()

        val state = viewModel.uiState.value as DigestUiState.ReadMode
        assertTrue(state.isMarkdownMode)
    }
}
