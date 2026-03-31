# AI生成中ローディング表示（issue #31）

## このドキュメントで学ぶこと

- `isLoading` フラグを UiState に持たせて「処理中」を表現する方法
- ボタン内に `CircularProgressIndicator` を埋め込むパターン
- `SummaryDialogState` による AI 要約ダイアログの 4状態管理
- `data class` の `copy()` で一部フラグだけ変更する方法

---

## 1. isLoading フラグの設計

```kotlin
// ui/digest/DigestUiState.kt
data class ReadMode(
    val article: ArticleEntity,
    val isLoading: Boolean = false,           // ← 読了宣言ボタンのスピナー制御
    val isMarkdownMode: Boolean = false,
    val summaryDialogState: SummaryDialogState = SummaryDialogState.Hidden,
) : DigestUiState

data class QuestionMode(
    val article: ArticleEntity,
    val question: String,
    val isLoading: Boolean = false,           // ← 考察送信ボタンのスピナー制御
) : DigestUiState
```

### isLoading の状態遷移

```
ReadMode(isLoading = false)
    ↓ 「読了宣言」ボタンタップ
ReadMode(isLoading = true)   ← スピナー表示・ボタン無効化
    ↓ 問い生成 API 完了
QuestionMode(isLoading = false)  ← 画面遷移

QuestionMode(isLoading = false)
    ↓ 「考察を送信」ボタンタップ
QuestionMode(isLoading = true)   ← スピナー表示・ボタン無効化
    ↓ フィードバック生成 API 完了
FeedbackMode(...)
```

---

## 2. DigestViewModel での isLoading 更新

```kotlin
// ui/digest/DigestViewModel.kt（概念コード）
fun showQuestion() {
    val current = _uiState.value as? DigestUiState.ReadMode ?: return
    _uiState.value = current.copy(isLoading = true)      // ← ローディング開始

    viewModelScope.launch {
        generateQuestionUseCase(current.article.body ?: current.article.title)
            .onSuccess { question ->
                _uiState.value = DigestUiState.QuestionMode(
                    article = current.article,
                    question = question,
                )
            }
            .onFailure {
                _uiState.value = current.copy(isLoading = false)  // ← 失敗時に戻す
            }
    }
}
```

`copy(isLoading = true)` で「記事・その他フラグはそのまま、isLoading だけ true」な
新しい `ReadMode` オブジェクトを生成します。

---

## 3. ボタン内 CircularProgressIndicator

```kotlin
// ui/digest/DigestScreen.kt — 読了宣言ボタン
Button(
    onClick = onShowQuestion,
    enabled = !isLoading && isOnline,   // ← isLoading 中はタップ不可
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
        )
    } else {
        Text(text = "読了宣言", style = MaterialTheme.typography.labelLarge)
    }
}
```

### `if (isLoading)` でコンテンツを差し替えるパターン

```
isLoading = false:  [読了宣言]      ← テキスト
isLoading = true:   [○]            ← スピナー（ボタン無効化）
```

ボタンのサイズを変えずに「処理中」を表現できます。
スピナーサイズを `18.dp`・`strokeWidth = 2.dp` で小さくしているのは
ボタン高さに収まるようにするためです。

---

## 4. 考察送信ボタンの同じパターン

```kotlin
// ui/digest/DigestScreen.kt — 考察を送信ボタン
Button(
    onClick = { onSubmitMemo(memo) },
    enabled = memo.isNotBlank() && !isLoading,  // ← テキスト未入力 or ローディング中は無効
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
        )
    } else {
        Text(text = "考察を送信", style = MaterialTheme.typography.labelLarge)
    }
}
```

`memo.isNotBlank() && !isLoading` という条件で
「入力なし」と「処理中」の両方でボタンを無効化しています。

---

## 5. SummaryDialogState — 4状態の管理

```kotlin
// ui/digest/DigestUiState.kt
sealed interface SummaryDialogState {
    data object Hidden : SummaryDialogState   // ← ダイアログを表示しない
    data object Loading : SummaryDialogState  // ← API 呼び出し中
    data class Loaded(val summary: String) : SummaryDialogState  // ← 要約テキストを表示
    data class Error(val message: String) : SummaryDialogState   // ← エラーメッセージを表示
}
```

### 状態遷移図

```
Hidden
  ↓ 「AI要約」ボタンタップ
Loading
  ├── 成功 → Loaded(summary = "...")
  └── 失敗 → Error(message = "...")
        ↓ 「閉じる」タップ
      Hidden
```

---

## 6. SummaryDialog — AlertDialog 内での状態切り替え

```kotlin
// ui/digest/DigestScreen.kt
@Composable
private fun SummaryDialog(
    state: SummaryDialogState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "AI要約") },
        text = {
            when (state) {
                is SummaryDialogState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is SummaryDialogState.Loaded -> {
                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is SummaryDialogState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SummaryDialogState.Hidden -> {}
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "閉じる") }
        },
    )
}
```

### ダイアログの表示制御

```kotlin
// DigestScreenContent 内
if (uiState.summaryDialogState != SummaryDialogState.Hidden) {
    SummaryDialog(
        state = uiState.summaryDialogState,
        onDismiss = onDismissSummary,
    )
}
```

`Hidden` 以外のときだけ `SummaryDialog` を配置します。
こうすることで `AlertDialog` の表示/非表示を `if` で管理できます。

---

## 7. DigestViewModel での SummaryDialogState 更新

```kotlin
// ui/digest/DigestViewModel.kt（概念コード）
fun showSummary() {
    val current = _uiState.value as? DigestUiState.ReadMode ?: return
    _uiState.value = current.copy(
        summaryDialogState = SummaryDialogState.Loading  // ← ダイアログ表示 + ローディング
    )

    viewModelScope.launch {
        generateSummaryUseCase(current.article)
            .onSuccess { summary ->
                _uiState.update {
                    (it as? DigestUiState.ReadMode)?.copy(
                        summaryDialogState = SummaryDialogState.Loaded(summary)
                    ) ?: it
                }
            }
            .onFailure { error ->
                _uiState.update {
                    (it as? DigestUiState.ReadMode)?.copy(
                        summaryDialogState = SummaryDialogState.Error(error.message ?: "エラー")
                    ) ?: it
                }
            }
    }
}

fun dismissSummary() {
    val current = _uiState.value as? DigestUiState.ReadMode ?: return
    _uiState.value = current.copy(summaryDialogState = SummaryDialogState.Hidden)
}
```

---

## 8. ローディング管理のまとめ

| AI 操作 | isLoading の場所 | スピナーの場所 |
|---------|---------------|------------|
| 読了宣言（問い生成） | `ReadMode.isLoading` | 読了宣言ボタン内 |
| 考察送信（フィードバック生成） | `QuestionMode.isLoading` | 考察を送信ボタン内 |
| AI 要約表示 | `ReadMode.summaryDialogState = Loading` | AlertDialog 内 |

「どの処理が動いているか」を UiState のフラグで管理することで、
ViewModel のコルーチンと UI の状態が常に同期されます。
