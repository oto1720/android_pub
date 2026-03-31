# ViewModel・StateFlow・イベント設計（issue #11）

## このドキュメントで学ぶこと

- `StateFlow` と `SharedFlow` の違いと使い分け
- `UiState` パターン（sealed interface）
- `WhileSubscribed` による効率的なリソース管理
- イベント（一度きりの通知）の設計
- コルーチンのキャンセルとジョブ管理

---

## 1. PortalViewModel 全体像

```kotlin
// ui/portal/PortalViewModel.kt
@HiltViewModel
class PortalViewModel @Inject constructor(
    private val getTrendArticlesUseCase: GetTrendArticlesUseCase,
    private val addToTsundokuUseCase: AddToTsundokuUseCase,
    observeTsundokuCountUseCase: ObserveTsundokuCountUseCase,
) : ViewModel() {

    // 1. UI 状態（Loading / Success / Error）
    private val _uiState = MutableStateFlow<PortalUiState>(PortalUiState.Loading)
    val uiState: StateFlow<PortalUiState> = _uiState.asStateFlow()

    // 2. 選択中のタグ
    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    // 3. 積読数（UseCase の Flow を StateFlow に変換）
    val tsundokuCount: StateFlow<Int> = observeTsundokuCountUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    // 4. 一度きりのイベント（スロット満杯通知）
    private val _event = MutableSharedFlow<PortalEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val event: SharedFlow<PortalEvent> = _event.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadArticles()
    }
}
```

---

## 2. StateFlow — 状態を保持する Flow

### `MutableStateFlow` と `StateFlow` の使い分け

```kotlin
// 書き込み可能（private で ViewModel 内部だけ変更できる）
private val _uiState = MutableStateFlow<PortalUiState>(PortalUiState.Loading)

// 読み取り専用（public で外部に公開する）
val uiState: StateFlow<PortalUiState> = _uiState.asStateFlow()
```

**なぜ private/public に分けるのか？**

```kotlin
// ViewModel 外から直接書き換えられると困る
viewModel._uiState.value = PortalUiState.Error("悪意のある変更")  // ← これを防ぐ
```

`asStateFlow()` で読み取り専用の `StateFlow` に変換して公開することで、外部からの書き換えを防ぎます。

### StateFlow の特徴

```
StateFlow:
  - 常に「最新の値」を1つ保持する
  - 購読者（collector）が現れると即座に現在の値を受け取れる
  - .value で現在の値を同期的に読める

LiveData との違い:
  - StateFlow はコルーチン上で動く
  - Compose との相性が良い（collectAsState() で使える）
```

---

## 3. UiState パターン — sealed interface

```kotlin
// ui/portal/PortalUiState.kt
sealed interface PortalUiState {
    data object Loading : PortalUiState
    data class Success(
        val articles: List<ArticleEntity>,
        val availableTags: List<String> = emptyList(),
    ) : PortalUiState
    data class Error(val message: String) : PortalUiState
}
```

### `sealed interface` を使う理由

UI の状態を「取りうるパターンが有限」として表現します。

```kotlin
// PortalScreen 側での使い方
when (uiState) {
    is PortalUiState.Loading  -> { CircularProgressIndicator() }
    is PortalUiState.Success  -> { ArticleList(uiState.articles) }
    is PortalUiState.Error    -> { ErrorScreen(uiState.message) }
    // else は不要！sealed なので全ケースを網羅できる
}
```

`sealed` は「このファイル外でサブクラスを作れない」という制約で、`when` 式を完全に網羅できます。
`when` の `else` が不要なので、新しい状態を追加したときにコンパイルエラーで気づけます。

### `data object` vs `data class`

```kotlin
data object Loading : PortalUiState   // ← フィールドなし（Loading 状態に持たせるデータがない）
data class Error(val message: String) // ← フィールドあり（エラーメッセージを持つ）
```

---

## 4. `WhileSubscribed` — 効率的なリソース管理

```kotlin
val tsundokuCount: StateFlow<Int> = observeTsundokuCountUseCase()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),  // ← ここが重要
        initialValue = 0,
    )
```

### `stateIn` とは

`Flow<Int>` を `StateFlow<Int>` に変換します。

```
observeTsundokuCountUseCase()  →  Flow<Int>  (冷たい、collect されるまで実行されない)
    ↓ stateIn(...)
tsundokuCount                  →  StateFlow<Int>  (温かい、常に値を保持)
```

### `WhileSubscribed(5_000)` の動き

```
Compose が tsundokuCount を collect 開始
    → 上流 Flow（DB 監視）が起動する
    → DB が変わるたびに新しい値が流れてくる

ユーザーがホームボタンを押す（アプリが背景へ）
    → Compose が collect を停止
    → 5秒間待機（WhileSubscribed(5_000) ← 5000ms）
    → 5秒後も collect がなければ上流 Flow を停止（DB 監視を止める）

ユーザーがアプリに戻ってくる
    → Compose が collect を再開
    → 上流 Flow が再起動
```

**5秒の猶予を設ける理由**：画面回転や再コンポーズで一時的に collect が止まっても、すぐに Flow を停止しないことで無駄な再起動を防ぎます。

---

## 5. SharedFlow — 一度きりのイベント

### StateFlow との違い

| 観点 | StateFlow | SharedFlow |
|------|-----------|-----------|
| 値の保持 | 常に最新値を1つ保持 | 保持しない（デフォルト） |
| 用途 | 「今の状態」を表す | 「一度きりの出来事」を通知する |
| 例 | Loading / Success / Error | スナックバー表示 / ダイアログ表示 |

### この実装でのイベント設計

```kotlin
// ViewModel 側
private val _event = MutableSharedFlow<PortalEvent>(
    extraBufferCapacity = 1,         // 1件まで溜めておける
    onBufferOverflow = BufferOverflow.DROP_OLDEST,  // 溢れたら古いものを捨てる
)
val event: SharedFlow<PortalEvent> = _event.asSharedFlow()

fun addToTsundoku(articleId: String) {
    viewModelScope.launch {
        val added = addToTsundokuUseCase(articleId)
        if (!added) _event.emit(PortalEvent.SlotFull)  // ← イベント送出
    }
}
```

```kotlin
// Screen 側
LaunchedEffect(Unit) {
    viewModel.event.collect { event ->
        when (event) {
            PortalEvent.SlotFull -> snackbarHostState.showSnackbar("スロットがいっぱいです")
        }
    }
}
```

### なぜ `StateFlow` を使わないのか

```kotlin
// ❌ StateFlow でイベントを扱う場合の問題
private val _event = MutableStateFlow<PortalEvent?>(null)

// → Compose が collect を開始したとき、前回のイベントを再受信してしまう
// → 画面回転したらスナックバーがまた表示される（意図していない動作）
```

`SharedFlow` は「保持しない」ため、collect していた時点に送出されたイベントしか受け取りません。

---

## 6. ジョブのキャンセルと管理

```kotlin
private var loadJob: Job? = null

private fun loadArticlesInternal(tag: String?) {
    loadJob?.cancel()  // ← 前回のリクエストをキャンセル
    loadJob = viewModelScope.launch {
        _uiState.value = PortalUiState.Loading
        // ...
    }
}
```

### なぜキャンセルが必要か

```
タグを高速に切り替えると...

タグ=null → API リクエスト A 開始（200ms かかる）
タグ=Android → API リクエスト B 開始（200ms かかる）

A が先に終わったら? → A の結果で uiState が更新される
B が後に終わったら? → B の結果で uiState が再更新される（正しい）

でも A が B より遅く終わる場合:
B の結果 → A の結果（古いデータで上書き）← ❌ バグ！
```

`loadJob?.cancel()` により、**新しいリクエストが来たら古いリクエストを強制終了**します。
常に「最後のリクエスト」の結果だけが表示されます。

---

## まとめ：ViewModel の状態管理パターン

```
外部に公開するもの          │  内部でのみ使うもの
────────────────────────────┼────────────────────────
StateFlow<PortalUiState>    │  MutableStateFlow<PortalUiState>
StateFlow<String?>          │  MutableStateFlow<String?>
StateFlow<Int>              │  （stateIn で変換）
SharedFlow<PortalEvent>     │  MutableSharedFlow<PortalEvent>
```

**命名規則**: 内部は `_uiState`（アンダースコア付き）、公開は `uiState`（アンダースコアなし）
