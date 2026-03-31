# 理解度チェック・メモ入力・フィードバック UI（issue #17）

## このドキュメントで学ぶこと

- `QuestionMode` / `FeedbackMode` のコンテンツ実装
- `OutlinedTextField` を使ったメモ入力フォームの実装
- `verticalScroll` でスクロール可能なコンテンツを作る
- `remember { mutableStateOf("") }` によるローカル状態管理

---

## 1. QuestionModeContent — 問いとメモ入力

```kotlin
// ui/digest/DigestScreen.kt
@Composable
private fun QuestionModeContent(
    question: String,
    onSubmitMemo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var memo by remember { mutableStateOf("") }   // ← ローカル状態

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),   // ← スクロール可能に
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FocusQuestionBlock(question = question)     // ← AI の問い表示

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "あなたの考察", ...)

            // メモ入力フォーム
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("簡潔な要約を入力してください...") },
                minLines = 4,
                maxLines = 8,
                shape = RoundedCornerShape(12.dp),
            )

            // 送信ボタン（メモが空なら無効）
            Button(
                onClick = { onSubmitMemo(memo) },
                modifier = Modifier.fillMaxWidth(),
                enabled = memo.isNotBlank(),    // ← 空文字では送信できない
            ) {
                Text(text = "考察を送信")
            }
        }
    }
}
```

### `remember { mutableStateOf("") }` — ローカル状態

メモの入力内容は「この画面だけで持つ一時的な状態」です。
ViewModel に持たせず、Composable のローカル状態として管理します。

```kotlin
var memo by remember { mutableStateOf("") }
// ↑ remember: リコンポーズをまたいで値を保持する
// ↑ mutableStateOf: 変更を検知して recompose を起こす
// ↑ by: property delegation（memo.value ではなく memo でアクセスできる）
```

**ViewModel に持たせない理由**:
ユーザーが画面を戻って入力内容を捨てることが想定されており、
永続化する必要がないため。

### `enabled = memo.isNotBlank()`

空白のみのメモを送信できないようにします。

```kotlin
"".isNotBlank()        // → false（空文字）
"   ".isNotBlank()     // → false（空白のみ）
"Kotlin は楽しい".isNotBlank()  // → true
```

`isEmpty()` は空文字列だけを `false` とするため、スペースだけの文字列を弾くには `isNotBlank()` を使います。

### `verticalScroll(rememberScrollState())`

コンテンツが画面に収まらない場合にスクロールできるようにします。
`rememberScrollState()` でスクロール位置の状態を保持します。

```kotlin
Column(
    modifier = Modifier.verticalScroll(rememberScrollState())
) {
    // ← この Column 内のコンテンツが縦スクロールできる
}
```

---

## 2. FocusQuestionBlock — AI の問いを表示する共通ブロック

```kotlin
@Composable
private fun FocusQuestionBlock(question: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "AIからの質問",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = question,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "これはAIが生成した提案です",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
    }
}
```

`QuestionMode` と `FeedbackMode` の両方で同じ問いを表示するため、共通コンポーザブルとして切り出しています。
「コンポーザブルの再利用」の典型的なパターンです。

---

## 3. FeedbackModeContent — フィードバックと消化ボタン

```kotlin
@Composable
private fun FeedbackModeContent(
    question: String,
    memo: String,
    feedback: String,
    onConsumeArticle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 1. 問いの再表示
        FocusQuestionBlock(question = question)

        // 2. ユーザーの考察（読み取り専用表示）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "あなたの考察", style = labelMedium, color = onSurfaceVariant)
                Text(text = memo, style = bodyMedium)
            }
        }

        // 3. AI フィードバック + 消化ボタン
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "フィードバック", color = primary)
                Text(text = feedback)
                Button(
                    onClick = onConsumeArticle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "習得済みとしてマーク")
                }
            }
        }
    }
}
```

### Surface コンポーネントの使い方

`Surface` は「背景色・形状・影」を持つコンテナです。`Card` と似ていますが、より柔軟にカスタマイズできます。

```
Surface（背景: surfaceVariant 薄め） → ユーザーの考察（既入力、変更不可）
Surface（背景: surface）            → AI フィードバック + 消化ボタン
```

背景色を変えることで、「自分が入力した内容」と「AI が返したフィードバック」を視覚的に区別しています。

---

## 4. 画面遷移のまとめ

```
ユーザー操作             UiState の遷移            表示されるコンテンツ
─────────────────────────────────────────────────────────────────
初期                    WebLoading               ProgressIndicator
                              ↓ loadArticle() 完了
                         ReadMode                 ArticleWebView
                         BottomBar: 「読了宣言」
                              ↓ 「読了宣言」タップ
                        QuestionMode              FocusQuestionBlock
                                                 + OutlinedTextField（memo入力）
                                                 + 「考察を送信」ボタン
                              ↓ 「考察を送信」タップ
                        FeedbackMode              FocusQuestionBlock
                                                 + 考察表示（読み取り専用）
                                                 + フィードバック表示
                                                 + 「習得済みとしてマーク」ボタン
                              ↓ 「習得済みとしてマーク」タップ
                       （NavigateToDone イベント）  → 血肉リスト画面へ遷移
```
