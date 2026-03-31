# 記事詳細画面 UI・WebView 埋め込み（issue #16）

## このドキュメントで学ぶこと

- `Scaffold` の `bottomBar` を条件付きで表示する方法
- `AndroidView` を使って Compose に従来の View（WebView）を埋め込む方法
- 記事の `status` によって UI を出し分けるパターン

---

## 1. DigestScreen の全体構造

```kotlin
// ui/digest/DigestScreen.kt
@Composable
fun DigestScreen(
    articleId: String,
    onNavigateToTsundoku: () -> Unit,
    onNavigateToDone: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DigestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                DigestEvent.AddedToTsundoku -> onNavigateToTsundoku()
                DigestEvent.SlotFull        -> snackbarHostState.showSnackbar("スロットがいっぱいです")
                DigestEvent.NavigateToDone  -> onNavigateToDone()
            }
        }
    }

    DigestScreenContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onAddToTsundoku = viewModel::addToTsundoku,
        onShowQuestion  = viewModel::showQuestion,
        onSubmitMemo    = viewModel::submitMemo,
        onConsumeArticle = viewModel::consumeArticle,
        ...
    )
}
```

イベント受信には `LaunchedEffect(Unit)` を使います。`Unit` を key にすることで、
Composable がコンポーズされたとき1回だけ起動するコルーチンになります。

---

## 2. Scaffold の bottomBar を条件付き表示

```kotlin
// ui/digest/DigestScreen.kt
@Composable
internal fun DigestScreenContent(...) {
    Scaffold(
        topBar = { DigestTopBar(article = article, onNavigateBack = onNavigateBack) },
        bottomBar = {
            // ReadMode のときだけ BottomBar を表示
            if (uiState is DigestUiState.ReadMode) {
                DigestBottomBar(
                    article = uiState.article,
                    onAddToTsundoku = onAddToTsundoku,
                    onShowQuestion = onShowQuestion,
                )
            }
        },
    ) { innerPadding ->
        when (uiState) { ... }
    }
}
```

`Scaffold` の `bottomBar` に空を渡す代わりに、条件付きで `DigestBottomBar` を入れています。
`QuestionMode` や `FeedbackMode` では BottomBar が消えて、画面全体をコンテンツエリアとして使えます。

---

## 3. status による BottomBar の出し分け

```kotlin
@Composable
private fun DigestBottomBar(article: ArticleEntity, ...) {
    Surface(modifier = modifier.fillMaxWidth(), shadowElevation = 8.dp) {
        when (article.status) {
            ArticleStatus.PORTAL -> {
                // ポータルから来た場合: 「積読に追加」ボタン
                Button(onClick = onAddToTsundoku, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "積読に追加")
                }
            }
            ArticleStatus.TSUNDOKU -> {
                // 積読から来た場合: 「AI要約」「読了宣言」ボタン
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { /* TODO: issue21 */ },
                        modifier = Modifier.weight(1f),
                        enabled = false,    // ← Milestone 5 で実装予定
                    ) {
                        Text("AI要約")
                    }
                    Button(
                        onClick = onShowQuestion,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("読了宣言")
                    }
                }
            }
            else -> {} // DONE 状態では何も表示しない
        }
    }
}
```

### なぜ `status` で出し分けるのか

同じ `DigestScreen` を2つの遷移元から使っているため:

| 遷移元 | 記事の status | 表示するボタン |
|--------|--------------|--------------|
| ポータル画面 | PORTAL | 積読に追加 |
| 積読一覧画面 | TSUNDOKU | AI要約・読了宣言 |

`status` をフラグとして使うことで、同一コンポーザブルで2パターンに対応できます。

---

## 4. WebView の埋め込み（AndroidView）

```kotlin
@Composable
private fun ArticleWebView(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            // ← Compose の外（従来の Android View）を生成する
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                loadUrl(url)
            }
        },
        update = { webView ->
            // ← url が変わったときだけ再ロードする
            if (webView.url != url) webView.loadUrl(url)
        },
        modifier = modifier,
    )
}
```

### `AndroidView` とは

Compose は宣言的 UI ですが、既存の Android View（`WebView`, `MapView` 等）を使いたいときに `AndroidView` を使います。

```
Compose の世界
  ↓ AndroidView
従来 Android View の世界（WebView）
```

#### `factory` と `update` の役割

| パラメータ | タイミング | 用途 |
|-----------|-----------|------|
| `factory` | 初回コンポーズ時のみ | View を生成する |
| `update` | 引数が変わるたび | View の状態を更新する |

`factory` は高コストな初期化（WebView の生成）を1回だけ行い、
`update` は `url` が変わったときだけ `loadUrl` を呼びます。

```kotlin
// update での工夫
update = { webView ->
    if (webView.url != url) webView.loadUrl(url)
    // ↑ 同じ URL なら loadUrl を呼ばない（不要なリロードを防ぐ）
}
```

---

## 5. TopAppBar でタイトルとタグを表示

```kotlin
@Composable
private fun DigestTopBar(article: ArticleEntity?, onNavigateBack: () -> Unit) {
    TopAppBar(
        title = {
            if (article != null) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 記事タイトル（1行）
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // タグ一覧（最大3件）
                    val tags = article.tags.split(",").map(String::trim).filter(String::isNotEmpty)
                    if (tags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(tags.take(3)) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "#$tag",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
            }
        },
    )
}
```

### `article: ArticleEntity?` が nullable な理由

`DigestUiState.WebLoading` や `DigestUiState.Error` のときは記事データがまだ取得できていません。
TopAppBar は常に表示されるため、`article` が null のケースを考慮する必要があります。

```kotlin
val article = when (uiState) {
    is DigestUiState.ReadMode     -> uiState.article
    is DigestUiState.QuestionMode -> uiState.article
    is DigestUiState.FeedbackMode -> uiState.article
    else -> null  // WebLoading / Error
}
```

### `tags.take(3)` でタグを最大3件に絞る

TopAppBar は高さが限られているため、タグが多すぎると見切れます。
`.take(3)` で先頭3件だけ表示することで、UIが崩れないようにしています。
