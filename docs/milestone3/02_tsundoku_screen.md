# 積読スロット UI・TsundokuViewModel（issue #14）

## このドキュメントで学ぶこと

- Flow → StateFlow 変換（`stateIn`）のシンプルなパターン
- Compose で「固定スロット数 + 実データ」を組み合わせる方法
- `drawBehind` による破線ボーダーの描き方

---

## 1. TsundokuViewModel — シンプルな状態管理

```kotlin
// ui/tsundoku/TsundokuViewModel.kt
@HiltViewModel
class TsundokuViewModel @Inject constructor(
    observeTsundokuArticlesUseCase: ObserveTsundokuArticlesUseCase,
) : ViewModel() {

    val uiState: StateFlow<TsundokuUiState> = observeTsundokuArticlesUseCase()
        .map { TsundokuUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TsundokuUiState.Loading,
        )
}
```

### PortalViewModel との比較

PortalViewModel は `MutableStateFlow` で状態を自分で管理していましたが、
TsundokuViewModel は UseCase の Flow をそのまま `stateIn` で変換するだけです。

```
PortalViewModel（複雑）:
  _uiState = MutableStateFlow(Loading)  ← 自分で管理
  loadArticles() で _uiState.value = Success(...)  ← 自分で更新

TsundokuViewModel（シンプル）:
  useCase().map { Success(it) }.stateIn(...)  ← Flow を変換するだけ
```

**なぜシンプルにできるのか**:
積読一覧は Room の Flow をそのまま表示するだけでよく、追加・削除などの操作は他の画面（PortalViewModel / DigestViewModel）が行うため、TsundokuViewModel 自身は「監視するだけ」でよいからです。

### TsundokuUiState

```kotlin
// ui/tsundoku/TsundokuUiState.kt
sealed interface TsundokuUiState {
    data object Loading : TsundokuUiState
    data class Success(val articles: List<ArticleEntity>) : TsundokuUiState
}
```

Error 状態がないのは、Room の Flow は通常エラーにならないため（DB 自体が壊れた場合のみ）。

---

## 2. TsundokuScreen — 5スロット固定 UI

### スロットデータの生成

```kotlin
// ui/tsundoku/TsundokuScreen.kt
is TsundokuUiState.Success -> {
    val slots = List(MAX_TSUNDOKU_SLOTS) { index ->
        uiState.articles.getOrNull(index)   // ← 記事があれば ArticleEntity、なければ null
    }
    val usedCount = uiState.articles.size
```

| index | `articles.getOrNull(index)` | 表示 |
|-------|-----------------------------|------|
| 0 | ArticleEntity(id="abc") | OccupiedSlotCard（記事あり） |
| 1 | ArticleEntity(id="def") | OccupiedSlotCard（記事あり） |
| 2 | null | EmptySlotCard（空きスロット） |
| 3 | null | EmptySlotCard（空きスロット） |
| 4 | null | EmptySlotCard（空きスロット） |

`getOrNull` は範囲外インデックスで例外を投げず `null` を返します。
これで「常に5行のリスト」を生成できます。

### LazyColumn でスロット一覧

```kotlin
LazyColumn(
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    itemsIndexed(slots) { index, article ->
        val slotNumber = index + 1
        if (article != null) {
            OccupiedSlotCard(article = article, slotNumber = slotNumber, ...)
        } else {
            EmptySlotCard(slotNumber = slotNumber)
        }
    }
}
```

`itemsIndexed` は `items` と違い、`index` も取得できます。スロット番号表示に使用しています。

---

## 3. 破線ボーダーカード（EmptySlotCard）

```kotlin
// ui/tsundoku/TsundokuScreen.kt
@Composable
private fun EmptySlotCard(slotNumber: Int, modifier: Modifier = Modifier) {
    val dashedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .drawBehind {                       // ← Canvas を使って自分でボーダーを描く
                drawRoundRect(
                    color = dashedColor,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                            phase = 0f,
                        ),
                    ),
                )
            }
    ) { ... }
}
```

### `drawBehind` とは

Compose の Modifier は `drawBehind { }` で「コンポーザブルの描画領域に対して自由にキャンバス描画できる」拡張を提供します。

```
通常の Card コンポーネント → 破線ボーダーは設定できない
drawBehind → Canvas API で自由に描ける
```

### `PathEffect.dashPathEffect` の引数

```kotlin
PathEffect.dashPathEffect(
    intervals = floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
    //                       ↑ 線の長さ    ↑ 隙間の長さ
    phase = 0f,  // ← 開始オフセット（0 = 最初から線が始まる）
)
```

```
結果:
─────     ─────     ─────     ─────
 8dp    6dp  8dp   6dp  8dp  6dp
（線）  （隙）（線）（隙）（線）（隙）
```

---

## 4. OccupiedSlotCard — 記事ありスロット

```kotlin
@Composable
private fun OccupiedSlotCard(
    article: ArticleEntity,
    slotNumber: Int,
    onStartLearning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(...) {
        Column(modifier = Modifier.padding(16.dp)) {
            // スロット番号バッジ
            Card(colors = CardDefaults.cardColors(containerColor = primary)) {
                Text(text = "スロット %02d".format(slotNumber), ...)
            }

            // 記事タイトル（2行まで表示）
            Text(
                text = article.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // メタ情報（追加日）
            Text(text = formatSlotMeta(article.cachedAt))

            // 学習開始ボタン
            Button(onClick = onStartLearning) {
                Icon(Icons.Default.PlayArrow, ...)
                Text("学習を開始")
            }
        }
    }
}
```

### `"%02d".format(slotNumber)` の意味

- `%d` : 整数を文字列化
- `02` : 最低2桁、足りない場合は0で埋める

```
slotNumber = 1  → "01"
slotNumber = 10 → "10"
```

---

## 5. 時間フォーマット関数

```kotlin
private fun formatAddedAgo(isoDate: String): String {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val cached = format.parse(isoDate) ?: return "追加日不明"
        val now = Date()
        val diffMs = now.time - cached.time
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)

        when {
            diffHours < 1 -> "たった今追加"
            diffDays == 0L -> "今日追加"
            diffDays == 1L -> "昨日追加"
            diffDays < 7  -> "${diffDays}日前に追加"
            else           -> "一週間以上前"
        }
    } catch (e: Exception) {
        "追加日不明"  // ← パース失敗時のフォールバック
    }
}
```

`try-catch` で全体を囲み、日付パースが失敗してもクラッシュしないようにしています。
外部データ（DBに保存されたISO日付文字列）は不正な形式が混入する可能性があるため、防御的に実装しています。
