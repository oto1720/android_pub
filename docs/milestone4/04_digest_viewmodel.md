# DigestViewModel・UiState 遷移・SavedStateHandle（issue #19）

## このドキュメントで学ぶこと

- `SavedStateHandle` によるナビゲーション引数の安全な取得
- `_uiState.update {}` による状態の安全な遷移
- 多段階 UiState（5状態）の設計
- `PortalViewModel` との SharedFlow イベント設計の共通点と違い

---

## 1. DigestViewModel 全体像

```kotlin
// ui/digest/DigestViewModel.kt
@HiltViewModel
class DigestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,              // ← ナビゲーション引数
    private val getArticleUseCase: GetArticleUseCase,
    private val addToTsundokuUseCase: AddToTsundokuUseCase,
    private val digestArticleUseCase: DigestArticleUseCase,
) : ViewModel() {

    private val articleId: String = checkNotNull(savedStateHandle["articleId"])

    private val _uiState = MutableStateFlow<DigestUiState>(DigestUiState.WebLoading)
    val uiState: StateFlow<DigestUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<DigestEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val event: SharedFlow<DigestEvent> = _event.asSharedFlow()

    init {
        loadArticle()
    }
}
```

---

## 2. SavedStateHandle — Navigation 引数の取得

```kotlin
private val articleId: String = checkNotNull(savedStateHandle["articleId"])
```

### なぜ `SavedStateHandle` を使うのか

Navigation Compose では画面遷移時に引数を URL パラメータとして渡します。

```kotlin
// navigation/AppNavHost.kt（Navigation 設定）
composable("digest/{articleId}") { backStackEntry ->
    DigestScreen(articleId = backStackEntry.arguments?.getString("articleId") ?: "")
}
```

ViewModel が `SavedStateHandle` を使えば、Navigation の引数を直接取得できます。

```kotlin
// ViewModel での取得
savedStateHandle["articleId"]  // → "abc123" など
```

### `checkNotNull` の役割

```kotlin
checkNotNull(savedStateHandle["articleId"])
```

`savedStateHandle["articleId"]` が `null` の場合、
`checkNotNull` は `IllegalStateException` を投げてクラッシュします。

これは「articleId なしに DigestScreen は成立しない」という契約を表しています。
`null` を許容した場合、後続の処理で何度も null チェックが必要になるため、
起動時に即座に検出する設計の方がバグを見つけやすいです。

---

## 3. DigestUiState — 5段階の状態定義

```kotlin
// ui/digest/DigestUiState.kt
sealed interface DigestUiState {
    data object WebLoading : DigestUiState      // 記事をDBから取得中

    data class ReadMode(                        // 記事表示中
        val article: ArticleEntity
    ) : DigestUiState

    data class QuestionMode(                    // 読了宣言後・問いとメモ入力
        val article: ArticleEntity,
        val question: String,
    ) : DigestUiState

    data class FeedbackMode(                    // メモ送信後・フィードバック表示
        val article: ArticleEntity,
        val question: String,
        val memo: String,
        val feedback: String,
    ) : DigestUiState

    data class Error(val message: String) : DigestUiState  // 記事取得失敗
}
```

### 状態が「積み上がっていく」設計

各状態が前の状態のデータを引き継ぐ設計になっています。

```
ReadMode(article)
    ↓ + question
QuestionMode(article, question)
    ↓ + memo
FeedbackMode(article, question, memo, feedback)
```

`FeedbackMode` になると `article`, `question`, `memo`, `feedback` がすべて揃います。
これにより「消化時に必要なデータが全部ある」ことがコンパイル時に保証されます。

---

## 4. UiState の遷移ロジック

### `_uiState.update {}` パターン

```kotlin
// ReadMode → QuestionMode への遷移
fun showQuestion() {
    _uiState.update { current ->
        if (current is DigestUiState.ReadMode) {
            DigestUiState.QuestionMode(
                article = current.article,
                question = PLACEHOLDER_QUESTION,
            )
        } else current   // ← 想定外の状態なら何もしない
    }
}
```

`update {}` は現在の値を受け取って新しい値を返すラムダです。
スレッドセーフに状態を更新できます。

```kotlin
// update を使わない場合（スレッドセーフでない可能性）
_uiState.value = DigestUiState.QuestionMode(...)

// update を使う場合（スレッドセーフ）
_uiState.update { current -> DigestUiState.QuestionMode(...) }
```

### 「想定外の状態では何もしない」というパターン

```kotlin
_uiState.update { current ->
    if (current is DigestUiState.ReadMode) {
        ...
    } else current   // ← 現在の状態をそのまま返す
}
```

例えば `showQuestion()` が二重に呼ばれた場合（すでに `QuestionMode`）、
`else current` で現在の状態をそのまま返すため、表示は変わりません。

### 4つの UiState 遷移

```kotlin
// WebLoading → ReadMode
private fun loadArticle() {
    viewModelScope.launch {
        val article = getArticleUseCase(articleId)
        _uiState.value = if (article != null) {
            DigestUiState.ReadMode(article)
        } else {
            DigestUiState.Error("記事が見つかりません")
        }
    }
}

// ReadMode → QuestionMode
fun showQuestion() {
    _uiState.update { current ->
        if (current is DigestUiState.ReadMode) {
            DigestUiState.QuestionMode(
                article = current.article,
                question = PLACEHOLDER_QUESTION,
            )
        } else current
    }
}

// QuestionMode → FeedbackMode
fun submitMemo(memo: String) {
    _uiState.update { current ->
        if (current is DigestUiState.QuestionMode) {
            DigestUiState.FeedbackMode(
                article = current.article,
                question = current.question,
                memo = memo,
                feedback = PLACEHOLDER_FEEDBACK,
            )
        } else current
    }
}

// FeedbackMode → （NavigateToDone イベント）
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
```

---

## 5. DigestEvent — 3種類のイベント

```kotlin
sealed interface DigestEvent {
    data object AddedToTsundoku : DigestEvent   // 積読追加成功 → 積読画面へ
    data object SlotFull : DigestEvent          // 積読追加失敗 → Snackbar
    data object NavigateToDone : DigestEvent    // 消化完了 → 血肉リスト画面へ
}
```

| イベント | 発生源 | UI の反応 |
|----------|--------|---------|
| `AddedToTsundoku` | `addToTsundoku()` 成功 | `onNavigateToTsundoku()` |
| `SlotFull` | `addToTsundoku()` 失敗 | Snackbar 表示 |
| `NavigateToDone` | `consumeArticle()` 完了 | `onNavigateToDone()` |

---

## 6. PortalViewModel との比較

| 観点 | PortalViewModel | DigestViewModel |
|------|----------------|----------------|
| UiState 数 | 3（Loading/Success/Error） | 5（WebLoading/ReadMode/Question/Feedback/Error） |
| 状態更新 | `_uiState.value = ...` | `_uiState.update { ... }` |
| Flow 監視 | `observeTsundokuCountUseCase()` | なし（1回取得のみ） |
| Job 管理 | `loadJob?.cancel()` でキャンセル | キャンセル不要（DB取得は1回） |
| イベント数 | 1（SlotFull） | 3（AddedToTsundoku/SlotFull/NavigateToDone） |
| ナビゲーション引数 | なし | `SavedStateHandle["articleId"]` |

### `update` を使う場面

複数のコルーチンが同時に状態を更新する可能性がある場合は `update` を使います。
PortalViewModel のように「フラグの更新は1つのコルーチンだけ」なら `.value = ...` でも安全です。
DigestViewModel は `showQuestion()`, `submitMemo()`, `consumeArticle()` が
それぞれ異なるユーザー操作から呼ばれるため、`update` でスレッドセーフにしています。
