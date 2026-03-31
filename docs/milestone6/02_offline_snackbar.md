# オフライン時の Snackbar 通知・Markdown フォールバック（issue #27）

## このドキュメントで学ぶこと

- ViewModel での「初回以外の変化だけに反応」する `.drop(1)` パターン
- オフライン時の自動フォールバック（WebView → Markdown）
- `isOnline` フラグをボタンの `enabled` に繋げるパターン

---

## 1. PortalViewModel — オフライン遷移の監視

```kotlin
// ui/portal/PortalViewModel.kt
val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

init {
    // オフライン遷移を監視してSnackbarイベントを発行する
    viewModelScope.launch {
        isOnline
            .drop(1)   // ← 初期値（true）をスキップし、変化のみに反応する
            .collect { online ->
                if (!online) {
                    _event.emit(PortalEvent.ShowOfflineMessage)
                }
            }
    }
}
```

### `.drop(1)` が必要な理由

```
drop(1) なしの場合:
  isOnline の初期値 true が流れる
    → collect 実行（online = true）
    → if (!online) は false なので何もしない ← 問題なさそうに見えるが...

実際の問題:
  画面回転などで Composable が再コンポーズ
    → isOnline.stateIn の initialValue = true が再度流れる
    → Snackbar がまた表示される（意図していない動作）
```

```
drop(1) ありの場合:
  isOnline の初期値 true → drop(1) で捨てられる
  実際の変化（true → false）だけが collect される
  → ユーザーが実際にオフラインになったときだけ Snackbar が表示される
```

### `PortalEvent.ShowOfflineMessage`

```kotlin
// ui/portal/PortalUiState.kt
sealed interface PortalEvent {
    data object SlotFull : PortalEvent
    data object ShowOfflineMessage : PortalEvent  // ← Milestone 6 で追加
}
```

イベントを `SharedFlow` で送ることで、Snackbar 表示という「一度きりの UI アクション」を
ViewModel から UI に通知できます。

---

## 2. PortalScreen — Snackbar の表示

```kotlin
// ui/portal/PortalScreen.kt
LaunchedEffect(Unit) {
    viewModel.event.collect { event ->
        when (event) {
            PortalEvent.SlotFull ->
                snackbarHostState.showSnackbar("スロットがいっぱいです")
            PortalEvent.ShowOfflineMessage ->
                snackbarHostState.showSnackbar("オフラインです。キャッシュを表示しています")
        }
    }
}
```

`PagingData` は Room（ローカル DB）からデータを取得するため、
オフライン時もキャッシュされた記事の表示を継続できます。
Snackbar でユーザーに「キャッシュを表示中」と伝えるだけで、
コード上は何も追加の処理が不要です。

---

## 3. DigestViewModel — Markdown への自動フォールバック

```kotlin
// ui/digest/DigestViewModel.kt
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
```

### なぜ Markdown フォールバックが必要か

記事詳細画面は WebView で外部 URL を表示するため、オフライン時は真っ白になります。
記事の本文（`ArticleEntity.body`）が DB に保存されている場合、
Markdown 形式でオフラインでも読めるように自動切替します。

```
オフライン検知
    ↓
ReadMode かつ article.body が非 null か確認
    ├── YES → isMarkdownMode = true に更新
    │           → DigestScreen が MarkdownContent を表示
    └── NO  → 何もしない（bodyがない記事は対応不可）
```

### `data class` の `copy()` で部分更新

```kotlin
_uiState.value = current.copy(isMarkdownMode = true)
```

`data class` の `copy()` は「指定したフィールドだけ変えた新しいオブジェクト」を返します。
`article`, `isLoading`, `summaryDialogState` はそのままで、`isMarkdownMode` だけ変えられます。

---

## 4. ReadMode の UiState 設計（Milestone 6 で拡張）

```kotlin
// ui/digest/DigestUiState.kt
data class ReadMode(
    val article: ArticleEntity,
    val isLoading: Boolean = false,
    val isMarkdownMode: Boolean = false,        // ← Milestone 6 で追加
    val summaryDialogState: SummaryDialogState = SummaryDialogState.Hidden,
) : DigestUiState
```

`ReadMode` に複数のフラグを持たせることで、「記事を読んでいる状態」の細かい変化を
1つの状態クラスで管理できます。

```
ReadMode(isMarkdownMode = false) → WebView 表示
ReadMode(isMarkdownMode = true)  → Markdown 表示
ReadMode(isLoading = true)       → 問い生成中（読了宣言ボタンにスピナー）
```

---

## 5. DigestScreen — WebView/Markdown の切り替え

```kotlin
// ui/digest/DigestScreen.kt
is DigestUiState.ReadMode -> {
    if (uiState.isMarkdownMode && uiState.article.body != null) {
        MarkdownContent(
            markdown = uiState.article.body,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    } else {
        ArticleWebView(
            url = uiState.article.url,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}
```

`isMarkdownMode` フラグで表示するコンポーザブルを切り替えます。
これにより WebView と Markdown の状態を別の UiState にせず、ReadMode のフラグで管理できます。

### TopAppBar の切り替えボタン

```kotlin
// DigestTopBar の actions
actions = {
    if (onToggleViewMode != null) {
        TextButton(onClick = onToggleViewMode) {
            Text(
                text = if (isMarkdownMode) "Web表示" else "MD表示",
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

`onToggleViewMode` が `null` の場合（`article.body` がない記事）はボタンを非表示にします。
関数参照を nullable にすることで「このアクションが使えるかどうか」を型で表現しています。

---

## 6. AI ボタンのオフライン制御

```kotlin
// DigestBottomBar
Button(
    onClick = onShowQuestion,
    enabled = !isLoading && isOnline,  // ← オフライン時は無効化
) {
    if (isLoading) {
        CircularProgressIndicator(...)
    } else {
        Text("読了宣言")
    }
}
```

`isOnline` を `enabled` に繋げることで、オフライン時に AI 機能のボタンを無効化します。
Gemini API はネットワーク必須なため、タップさせないことでユーザーが混乱しません。
