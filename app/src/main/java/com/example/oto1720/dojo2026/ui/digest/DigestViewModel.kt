package com.example.oto1720.dojo2026.ui.digest

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oto1720.dojo2026.data.network.NetworkMonitor
import com.example.oto1720.dojo2026.domain.model.FeedbackResult
import com.example.oto1720.dojo2026.domain.usecase.AddToTsundokuUseCase
import com.example.oto1720.dojo2026.domain.usecase.DigestArticleUseCase
import com.example.oto1720.dojo2026.domain.usecase.GenerateFeedbackUseCase
import com.example.oto1720.dojo2026.domain.usecase.GenerateQuestionUseCase
import com.example.oto1720.dojo2026.domain.usecase.GenerateSummaryUseCase
import com.example.oto1720.dojo2026.domain.usecase.GetArticleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DigestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArticleUseCase: GetArticleUseCase,
    private val addToTsundokuUseCase: AddToTsundokuUseCase,
    private val digestArticleUseCase: DigestArticleUseCase,
    private val generateQuestionUseCase: GenerateQuestionUseCase,
    private val generateFeedbackUseCase: GenerateFeedbackUseCase,
    private val generateSummaryUseCase: GenerateSummaryUseCase,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val articleId: String = checkNotNull(savedStateHandle["articleId"])

    private val _uiState = MutableStateFlow<DigestUiState>(DigestUiState.WebLoading)
    val uiState: StateFlow<DigestUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<DigestEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val event: SharedFlow<DigestEvent> = _event.asSharedFlow()

    /** ネットワーク接続状態（true: オンライン、false: オフライン） */
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    init {
        loadArticle()
        // オフライン時は自動的に Markdown 表示にフォールバック
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (!online) {
                    val current = _uiState.value
                    if (current is DigestUiState.ReadMode && current.article.body != null) {
                        _uiState.value = current.copy(isMarkdownMode = true)
                    }
                }
            }
        }
    }

    private fun loadArticle() {
        viewModelScope.launch {
            val article = getArticleUseCase(articleId)
            _uiState.value = if (article != null) {
                val isOffline = !isOnline.value
                DigestUiState.ReadMode(
                    article = article,
                    isMarkdownMode = isOffline && article.body != null,
                )
            } else {
                DigestUiState.Error("記事が見つかりません")
            }
        }
    }

    /** AI要約ダイアログを開く。キャッシュがあればAPIを呼ばずDBから返す。 */
    fun showSummary() {
        val current = _uiState.value as? DigestUiState.ReadMode ?: return
        _uiState.value = current.copy(summaryDialogState = SummaryDialogState.Loading)
        viewModelScope.launch {
            generateSummaryUseCase(articleId)
                .onSuccess { summary ->
                    val c = _uiState.value as? DigestUiState.ReadMode ?: return@onSuccess
                    _uiState.value = c.copy(summaryDialogState = SummaryDialogState.Loaded(summary))
                }
                .onFailure {
                    val c = _uiState.value as? DigestUiState.ReadMode ?: return@onFailure
                    _uiState.value = c.copy(summaryDialogState = SummaryDialogState.Error("要約の取得に失敗しました"))
                }
        }
    }

    /** AI要約ダイアログを閉じる。 */
    fun dismissSummary() {
        val current = _uiState.value as? DigestUiState.ReadMode ?: return
        _uiState.value = current.copy(summaryDialogState = SummaryDialogState.Hidden)
    }

    /** WebView と Markdown 表示を切り替える。ReadMode のときのみ動作。 */
    fun toggleViewMode() {
        val current = _uiState.value as? DigestUiState.ReadMode ?: return
        _uiState.value = current.copy(isMarkdownMode = !current.isMarkdownMode)
    }

    /** 記事を積読スロットに追加する。成功時は AddedToTsundoku、満杯時は SlotFull を発行。 */
    fun addToTsundoku() {
        viewModelScope.launch {
            val added = addToTsundokuUseCase(articleId)
            _event.emit(if (added) DigestEvent.AddedToTsundoku else DigestEvent.SlotFull)
        }
    }

    /** 読了宣言: Gemini API で問いを生成し ReadMode → QuestionMode へ遷移する。 */
    fun showQuestion() {
        val current = _uiState.value as? DigestUiState.ReadMode ?: return
        _uiState.value = current.copy(isLoading = true)
        viewModelScope.launch {
            val question = generateQuestionUseCase(articleId)
                .getOrDefault(PLACEHOLDER_QUESTION)
            _uiState.value = DigestUiState.QuestionMode(
                article = current.article,
                question = question,
            )
        }
    }

    /** メモ送信: Gemini API でフィードバック・理解度判定を生成し QuestionMode → FeedbackMode へ遷移する。 */
    fun submitMemo(memo: String) {
        val current = _uiState.value as? DigestUiState.QuestionMode ?: return
        _uiState.value = current.copy(isLoading = true)
        viewModelScope.launch {
            val result = generateFeedbackUseCase(
                articleId = articleId,
                question = current.question,
                userMemo = memo,
            ).getOrDefault(FeedbackResult(PLACEHOLDER_FEEDBACK, isUnderstandingSufficient = true))
            _uiState.value = DigestUiState.FeedbackMode(
                article = current.article,
                question = current.question,
                memo = memo,
                feedback = result.feedback,
                canDigest = result.isUnderstandingSufficient,
            )
        }
    }

    /** メモ・フィードバックを保存して記事を消化済みにし、NavigateToDone を発行。 */
    fun consumeArticle() {
        val current = _uiState.value as? DigestUiState.FeedbackMode ?: return
        viewModelScope.launch {
            digestArticleUseCase(
                articleId = articleId,
                memo = current.memo,
                feedback = current.feedback,
            )
            _event.emit(DigestEvent.NavigateToDone)
        }
    }

    companion object {
        private const val PLACEHOLDER_QUESTION =
            "この記事で最も印象に残った内容を、自分の言葉で説明できますか？"
        private const val PLACEHOLDER_FEEDBACK =
            "メモを記録しました。記事の内容を自分の言葉でアウトプットすることで、理解が深まります。"
    }
}
