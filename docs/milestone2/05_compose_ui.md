# Jetpack Compose による UI 実装（issue #12）

## このドキュメントで学ぶこと

- PortalScreen の Composable 分割設計
- `sealed interface` と `when` による状態ドリブン UI
- `Scaffold` + `SnackbarHost` によるスナックバー表示
- `LaunchedEffect` を使ったイベント処理
- `collectAsState` による ViewModel との接続
- `rememberSaveable` による状態の保存

---

## 1. Composable の構成図

```
PortalScreen（最上位）
│  ViewModelと接続、Scaffoldでラップ
│
└── PortalScreenContent（状態に応じて切り替え）
    │
    ├── [Loading状態] CircularProgressIndicator
    │
    ├── [Error状態] エラーメッセージ + リトライボタン
    │
    └── [Success状態]
         ├── PortalHeader
         │    ├── タイトル「Tech-Digest ポータル」
         │    ├── OutlinedTextField（検索バー）
         │    └── LazyRow
         │         └── FilterChipPill × n（タグチップ）
         ├── SlotsCounterBar（空きスロット表示）
         └── LazyColumn
              ├── ArticleCard × n
              │    ├── 画像エリア（グラデーション）
              │    ├── タイトル・タグ・読了時間
              │    └── 「スロットに追加」ボタン
              └── EndOfListIndicator（一覧末尾メッセージ）
```

---

## 2. PortalScreen — ViewModel との接続

```kotlin
@Composable
fun PortalScreen(
    onNavigateToDigest: (articleId: String) -> Unit,
    onNavigateToTsundoku: () -> Unit,
    onNavigateToDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortalViewModel = hiltViewModel(),
) {
    // StateFlow を Compose の State に変換
    val uiState by viewModel.uiState.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val tsundokuCount by viewModel.tsundokuCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // イベントを監視してスナックバーを表示
    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                PortalEvent.SlotFull -> snackbarHostState.showSnackbar("スロットがいっぱいです")
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        PortalScreenContent(
            ...
            modifier = Modifier.padding(innerPadding),
        )
    }
}
```

### `collectAsState()` とは

ViewModel の `StateFlow` を、Compose が監視できる `State` に変換します。

```kotlin
val uiState by viewModel.uiState.collectAsState()
// ↑ uiState が変わるたびに、この Composable が再コンポーズされる
```

```
StateFlow が更新
    → collectAsState() が変化を検知
    → by キーワードで State として扱う
    → Compose が recompose（UI を再描画）
```

### `LaunchedEffect(Unit)` — コンポーズ時に1回だけ起動

```kotlin
LaunchedEffect(Unit) {  // ← Unit は「一度だけ実行」を意味するキー
    viewModel.event.collect { event ->
        // イベントが来るたびに実行される
        snackbarHostState.showSnackbar("...")
    }
}
```

`LaunchedEffect` は Composable が画面に表示された時点でコルーチンを起動します。
キーに `Unit` を指定すると「初回表示時に1回だけ起動」します。
画面が消えると自動的にコルーチンはキャンセルされます。

### `Scaffold` + `SnackbarHost`

```kotlin
Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
) { innerPadding ->
    // コンテンツ
}
```

- `Scaffold` は Material3 の画面骨格を提供（TopBar / BottomBar / FAB / Snackbar のスロットを持つ）
- `innerPadding` は Scaffold の内側のパディング（ナビゲーションバーなどとの衝突を防ぐ）
- `Modifier.padding(innerPadding)` でコンテンツが適切な位置に配置される

---

## 3. PortalScreenContent — 状態ドリブン UI

```kotlin
@Composable
internal fun PortalScreenContent(
    uiState: PortalUiState,
    ...
) {
    when (uiState) {
        is PortalUiState.Loading -> {
            // ローディングスピナー
            CircularProgressIndicator()
        }
        is PortalUiState.Error -> {
            // エラーメッセージ + リトライボタン
            Text(text = uiState.message)  // ← Smart Cast：uiState が Error とわかっている
            Button(onClick = onRetry) { Text("リトライ") }
        }
        is PortalUiState.Success -> {
            var searchQuery by rememberSaveable { mutableStateOf("") }
            // ...記事一覧の表示
        }
    }
}
```

### `is PortalUiState.Success` 後の Smart Cast

```kotlin
is PortalUiState.Success -> {
    // ここでは uiState は自動的に Success 型として扱われる
    val articles = uiState.articles     // ✅ キャストなしでアクセス可能
    val tags = uiState.availableTags    // ✅
}
```

Kotlin の Smart Cast（スマートキャスト）が機能します。

### `rememberSaveable` — 再コンポーズをまたいで状態を保持

```kotlin
var searchQuery by rememberSaveable { mutableStateOf("") }
```

| 関数 | 保持タイミング |
|------|--------------|
| `remember` | 再コンポーズをまたいで保持（画面回転で消える） |
| `rememberSaveable` | 画面回転・プロセス再起動後も保持される |

検索クエリはユーザーが入力したテキストなので、画面回転後も消えないように `rememberSaveable` を使います。

---

## 4. FilterChipPill — アクセシビリティに配慮したタグチップ

```kotlin
@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        shape = RoundedCornerShape(9999.dp),  // ← 完全な丸（pill 形状）
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,      // ← 枠線なし
            selectedBorderColor = Color.Transparent,
        ),
    )
}
```

### `FilterChip` を使う理由（`Card` でなく）

Material3 の `FilterChip` は選択状態のセマンティクスを自動的に持ちます。

```
FilterChip(selected = true)
→ アクセシビリティツリーに「チェックボックス、選択済み」と伝わる
→ スクリーンリーダーが正しく読み上げる

Card(onClick = { })
→ アクセシビリティツリーに「ボタン」として伝わる（選択状態が伝わらない）
```

### `9999.dp` で pill 形状に

```kotlin
shape = RoundedCornerShape(9999.dp)
```

角丸の半径を非常に大きくすることで、完全な pill（薬錠）形状になります。
Material3 では `CircleShape` と同等の見た目になります。

---

## 5. ArticleCard — スロット状態に応じた表示切り替え

```kotlin
@Composable
internal fun ArticleCard(
    article: ArticleEntity,
    isSlotFull: Boolean,
    onClick: () -> Unit,
    onAddToSlot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // 画像エリア（グラデーション + スロット満杯時のオーバーレイ）
            Box(modifier = Modifier.height(160.dp).background(Brush.verticalGradient(...))) {
                if (isSlotFull) {
                    // オーバーレイ: 「スロット制限中」
                } else {
                    // 「トレンド」バッジ
                }
            }

            // テキストエリア
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = article.title, maxLines = 2)
                Text(text = "読了まで 5分")  // TODO: 実データで計算

                // スロット追加ボタン
                Button(
                    onClick = onAddToSlot,
                    enabled = !isSlotFull,  // ← スロット満杯時は無効化
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(text = if (isSlotFull) "スロットがいっぱいです" else "スロットに追加")
                }
            }
        }
    }
}
```

### `enabled = !isSlotFull` によるボタン制御

```kotlin
Button(
    onClick = onAddToSlot,
    enabled = !isSlotFull,  // false のとき onClick は呼ばれない
)
```

`enabled = false` にすると：
- ボタンがグレイアウトする（Material3 が自動でスタイル変更）
- タップしても `onClick` が呼ばれない

---

## 6. `LazyColumn` と `key` パラメータ

```kotlin
LazyColumn {
    items(uiState.articles, key = { it.id }) { article ->
        ArticleCard(...)
    }
    item {
        EndOfListIndicator()
    }
}
```

### `key = { it.id }` が重要な理由

```kotlin
// key なし
items(articles) { article -> ArticleCard(...) }
// → 記事が更新されると全カードが再コンポーズされる

// key あり
items(articles, key = { it.id }) { article -> ArticleCard(...) }
// → id が変わっていない記事は再コンポーズされない（パフォーマンス向上）
```

---

## 7. Composable の命名と visibility（公開範囲）

| 修飾子 | 意味 | 使用例 |
|--------|------|--------|
| `public`（なし） | どこからでも呼べる | `PortalScreen`（外部から呼ばれる） |
| `internal` | 同モジュール内のみ | `PortalScreenContent`（テストから呼べる） |
| `private` | 同ファイル内のみ | `PortalHeader`, `FilterChipPill`（内部実装） |

`internal` にしている理由：`PortalScreenContent` はテストで直接呼び出したいから。
ViewModel を使わずに UI 状態を直接渡してテストできます。

---

## まとめ：Compose の基本パターン

```
データの流れ（上から下へ）
PortalViewModel
  StateFlow ──→ collectAsState() ──→ PortalScreenContent (引数)
                                              ↓
                                     ArticleCard (引数)

イベントの流れ（下から上へ）
ArticleCard.onAddToSlot
  ↑ ラムダ
PortalScreenContent.onAddToSlot
  ↑ ラムダ
PortalScreen → viewModel.addToTsundoku(articleId)
```

**State は上から下へ、イベントは下から上へ** — これが Compose の単方向データフロー（UDF）の原則です。
