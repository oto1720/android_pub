package com.example.oto1720.dojo2026.ui.portal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.network.NetworkMonitor
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.domain.model.SortOrder
import com.example.oto1720.dojo2026.domain.usecase.AddToTsundokuUseCase
import com.example.oto1720.dojo2026.domain.usecase.GenerateQuestionUseCase
import com.example.oto1720.dojo2026.domain.usecase.ObserveTsundokuCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PortalViewModel @Inject constructor(
    private val repository: ArticleRepository,
    private val addToTsundokuUseCase: AddToTsundokuUseCase,
    private val generateQuestionUseCase: GenerateQuestionUseCase,
    observeTsundokuCountUseCase: ObserveTsundokuCountUseCase,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.BY_DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    /** タグ・ソート順に応じた無限スクロール対応の記事 Flow。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val portalArticles: Flow<PagingData<ArticleEntity>> =
        combine(_selectedTag, _sortOrder) { tag, order -> tag to order }
            .flatMapLatest { (tag, order) -> repository.getPortalPager(tag, order) }
            .cachedIn(viewModelScope)

    /** ヘッダーのタグフィルタチップ用タグ一覧。 */
    val availableTags: StateFlow<List<String>> = repository.observePortalTagStrings()
        .map { tagStrings ->
            tagStrings
                .flatMap { it.split(",").map(String::trim).filter(String::isNotEmpty) }
                .distinct()
                .sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** ネットワーク接続状態（true: オンライン、false: オフライン） */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    /** 積読中の記事数（上限は ObserveTsundokuCountUseCase が管理） */
    val tsundokuCount: StateFlow<Int> = observeTsundokuCountUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    private val _event = MutableSharedFlow<PortalEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val event: SharedFlow<PortalEvent> = _event.asSharedFlow()

    init {
        // オフライン遷移を監視してSnackbarイベントを発行する
        viewModelScope.launch {
            isOnline
                .drop(1) // 初期値（true）をスキップし、変化のみに反応する
                .collect { online ->
                    if (!online) {
                        _event.emit(PortalEvent.ShowOfflineMessage)
                    }
                }
        }
    }

    /** タグを選択して記事をフィルタリングする。null を渡すとトレンド全体を取得。 */
    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    /** ソート順を変更する。 */
    fun onSortOrderChange(order: SortOrder) {
        _sortOrder.value = order
    }

    /** 記事を積読スロットに追加する。スロット満杯の場合はイベントで通知する。 */
    fun addToTsundoku(articleId: String) {
        viewModelScope.launch {
            val added = addToTsundokuUseCase(articleId)
            if (added) {
                _event.emit(PortalEvent.AddedToTsundoku)
                // スロットに入れたら削除できないため、必ず読むことが確定する。
                // 読了宣言時の待ち時間をなくすため、バックグラウンドで問いを事前生成する。
                // 失敗しても読了宣言時に再生成されるため、エラーは無視する。
                launch { generateQuestionUseCase(articleId) }
            } else {
                _event.emit(PortalEvent.SlotFull)
            }
        }
    }
}
