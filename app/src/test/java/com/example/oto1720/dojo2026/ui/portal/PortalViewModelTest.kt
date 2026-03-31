package com.example.oto1720.dojo2026.ui.portal

import androidx.paging.PagingData
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.network.NetworkMonitor
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.domain.model.SortOrder
import com.example.oto1720.dojo2026.domain.usecase.AddToTsundokuUseCase
import com.example.oto1720.dojo2026.domain.usecase.GenerateQuestionUseCase
import com.example.oto1720.dojo2026.domain.usecase.ObserveTsundokuCountUseCase
import com.example.oto1720.dojo2026.rules.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class PortalViewModelTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @MockK
    private lateinit var repository: ArticleRepository

    @MockK
    private lateinit var addToTsundokuUseCase: AddToTsundokuUseCase

    @MockK
    private lateinit var generateQuestionUseCase: GenerateQuestionUseCase

    @MockK
    private lateinit var observeTsundokuCountUseCase: ObserveTsundokuCountUseCase

    @MockK
    private lateinit var networkMonitor: NetworkMonitor

    private lateinit var viewModel: PortalViewModel

    private val tsundokuCountFlow = MutableSharedFlow<Int>(replay = 1)

    @Before
    fun setUp() {
        coEvery { addToTsundokuUseCase(any()) } returns true
        coEvery { generateQuestionUseCase(any()) } returns Result.success("テスト問い")
        every { observeTsundokuCountUseCase() } returns tsundokuCountFlow
        every { networkMonitor.isOnline } returns flowOf(true)
        every { repository.observePortalTagStrings() } returns flowOf(emptyList())
        every { repository.getPortalPager(any(), any()) } returns flowOf(PagingData.empty())
    }

    private fun createViewModel() {
        viewModel = PortalViewModel(
            repository,
            addToTsundokuUseCase,
            generateQuestionUseCase,
            observeTsundokuCountUseCase,
            networkMonitor,
        )
    }

    private fun createDummyArticle(
        id: String,
        title: String,
        createdAt: String = "2026-03-05T00:00:00Z",
        likesCount: Int = 0,
    ): ArticleEntity {
        return ArticleEntity(
            id = id,
            title = title,
            url = "https://example.com/$id",
            tags = "test,dummy",
            createdAt = createdAt,
            cachedAt = "2026-03-05T00:00:00Z",
            status = ArticleStatus.PORTAL,
            aiSummary = null,
            likesCount = likesCount,
        )
    }

    // --- addToTsundoku ---

    @Test
    fun `addToTsundoku - 追加成功時はAddedToTsundokuイベントが発行される`() = runTest {
        coEvery { addToTsundokuUseCase(any()) } returns true
        createViewModel()
        runCurrent()
        val receivedEvents = mutableListOf<PortalEvent>()
        val collectJob = launch { viewModel.event.collect { receivedEvents.add(it) } }
        viewModel.addToTsundoku("article1")
        runCurrent()
        assertEquals(listOf(PortalEvent.AddedToTsundoku), receivedEvents)
        collectJob.cancel()
    }

    @Test
    fun `addToTsundoku - スロット満杯時にSlotFullイベントが発行される`() = runTest {
        coEvery { addToTsundokuUseCase(any()) } returns false
        createViewModel()
        runCurrent()
        val receivedEvents = mutableListOf<PortalEvent>()
        val collectJob = launch { viewModel.event.collect { receivedEvents.add(it) } }
        viewModel.addToTsundoku("article1")
        runCurrent()
        assertEquals(listOf(PortalEvent.SlotFull), receivedEvents)
        collectJob.cancel()
    }

    @Test
    fun `addToTsundoku - 追加成功時にgenerateQuestionUseCaseが呼ばれる`() = runTest {
        coEvery { addToTsundokuUseCase(any()) } returns true
        createViewModel()
        viewModel.addToTsundoku("article1")
        advanceUntilIdle()
        coVerify { generateQuestionUseCase("article1") }
    }

    @Test
    fun `addToTsundoku - スロット満杯時にgenerateQuestionUseCaseが呼ばれない`() = runTest {
        coEvery { addToTsundokuUseCase(any()) } returns false
        createViewModel()
        viewModel.addToTsundoku("article1")
        advanceUntilIdle()
        coVerify(exactly = 0) { generateQuestionUseCase(any()) }
    }

    // --- tsundokuCount ---

    @Test
    fun `tsundokuCount - UseCaseからの値を正しく反映する`() = runTest {
        createViewModel()
        val job = launch { viewModel.tsundokuCount.collect() }
        runCurrent()
        assertEquals(0, viewModel.tsundokuCount.value)
        tsundokuCountFlow.emit(5)
        runCurrent()
        assertEquals(5, viewModel.tsundokuCount.value)
        tsundokuCountFlow.emit(10)
        runCurrent()
        assertEquals(10, viewModel.tsundokuCount.value)
        job.cancel()
    }

    @Test
    fun `tsundokuCount - 購読者がいる間だけUseCaseのFlowを購読する`() = runTest {
        createViewModel()
        assertEquals(0, tsundokuCountFlow.subscriptionCount.value)
        val job1 = launch { viewModel.tsundokuCount.collect() }
        runCurrent()
        assertEquals(1, tsundokuCountFlow.subscriptionCount.value)
        val job2 = launch { viewModel.tsundokuCount.collect() }
        runCurrent()
        assertEquals(1, tsundokuCountFlow.subscriptionCount.value)
        job1.cancel()
        runCurrent()
        assertEquals(1, tsundokuCountFlow.subscriptionCount.value)
        job2.cancel()
        runCurrent()
    }

    @Test
    fun `tsundokuCount - 最後の購読者がいなくなって5秒後に購読を停止する`() = runTest {
        createViewModel()
        assertEquals(0, tsundokuCountFlow.subscriptionCount.value)
        val job = launch { viewModel.tsundokuCount.collect() }
        runCurrent()
        assertEquals(1, tsundokuCountFlow.subscriptionCount.value)
        job.cancel()
        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(1, tsundokuCountFlow.subscriptionCount.value)
        advanceTimeBy(2L)
        runCurrent()
        assertEquals(0, tsundokuCountFlow.subscriptionCount.value)
    }

    // --- isOnline ---

    @Test
    fun `isOnline - NetworkMonitorのisOnlineの値を反映する`() = runTest {
        every { networkMonitor.isOnline } returns flowOf(false)
        createViewModel()
        val job = launch { viewModel.isOnline.collect() }
        runCurrent()
        assertEquals(false, viewModel.isOnline.value)
        job.cancel()
    }

    @Test
    fun `isOnline - オフラインになったときShowOfflineMessageイベントが発行される`() = runTest {
        val onlineFlow = MutableStateFlow(true)
        every { networkMonitor.isOnline } returns onlineFlow
        createViewModel()
        val receivedEvents = mutableListOf<PortalEvent>()
        val collectJob = launch { viewModel.event.collect { receivedEvents.add(it) } }
        runCurrent()
        onlineFlow.value = false
        runCurrent()
        assertTrue(receivedEvents.contains(PortalEvent.ShowOfflineMessage))
        collectJob.cancel()
    }

    @Test
    fun `isOnline - 起動時にオンラインの場合ShowOfflineMessageは発行されない`() = runTest {
        createViewModel()
        val receivedEvents = mutableListOf<PortalEvent>()
        val collectJob = launch { viewModel.event.collect { receivedEvents.add(it) } }
        runCurrent()
        assertTrue(receivedEvents.none { it is PortalEvent.ShowOfflineMessage })
        collectJob.cancel()
    }

    // --- sortOrder ---

    @Test
    fun `init - デフォルトのsortOrderはBY_DATE_DESCである`() = runTest {
        createViewModel()
        assertEquals(SortOrder.BY_DATE_DESC, viewModel.sortOrder.value)
    }

    @Test
    fun `onSortOrderChange - BY_LIKES_DESCに変更するとsortOrderの値が更新される`() = runTest {
        createViewModel()
        viewModel.onSortOrderChange(SortOrder.BY_LIKES_DESC)
        assertEquals(SortOrder.BY_LIKES_DESC, viewModel.sortOrder.value)
    }

    @Test
    fun `onSortOrderChange - BY_DATE_DESCに戻すとsortOrderの値が更新される`() = runTest {
        createViewModel()
        viewModel.onSortOrderChange(SortOrder.BY_LIKES_DESC)
        viewModel.onSortOrderChange(SortOrder.BY_DATE_DESC)
        assertEquals(SortOrder.BY_DATE_DESC, viewModel.sortOrder.value)
    }

    // --- selectTag ---

    @Test
    fun `selectTag - タグを選択するとselectedTagの値が更新される`() = runTest {
        createViewModel()
        viewModel.selectTag("Kotlin")
        assertEquals("Kotlin", viewModel.selectedTag.value)
    }

    @Test
    fun `selectTag - nullを渡すとselectedTagがnullになる`() = runTest {
        createViewModel()
        viewModel.selectTag("Kotlin")
        viewModel.selectTag(null)
        assertEquals(null, viewModel.selectedTag.value)
    }
}
