# ポータル積読満杯ロック UI（issue #15）

## このドキュメントで学ぶこと

- 状態に応じた UI の出し分け（グレーアウト・ボタン無効化）
- Compose で条件付きスタイルを適用する方法
- バナー・カウンターバーの実装

---

## 1. 満杯状態の検知

```kotlin
// ui/portal/PortalScreen.kt
is PortalUiState.Success -> {
    val isSlotFull = tsundokuCount >= MAX_TSUNDOKU_SLOTS  // ← Boolean フラグ

    Column {
        PortalHeader(...)
        SlotsCounterBar(usedCount = tsundokuCount, maxSlots = MAX_TSUNDOKU_SLOTS)

        if (isSlotFull) {
            SlotFullBanner()  // ← 満杯のときだけバナーを表示
        }

        LazyColumn {
            items(uiState.articles) { article ->
                ArticleCard(
                    article = article,
                    isSlotFull = isSlotFull,  // ← 各カードに満杯フラグを渡す
                    onAddToSlot = { onAddToSlot(article.id) },
                )
            }
        }
    }
}
```

`tsundokuCount` は `PortalViewModel` が `ObserveTsundokuCountUseCase` で監視しているので、
積読数が増えるたびに自動的に Compose が再描画されます。

---

## 2. ArticleCard のグレーアウト

```kotlin
// ui/portal/PortalScreen.kt
@Composable
internal fun ArticleCard(
    article: ArticleEntity,
    isSlotFull: Boolean,    // ← 満杯フラグを受け取る
    onAddToSlot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            // 満杯: グレー背景 / 通常: 白背景
            containerColor = if (isSlotFull)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column {
            // 画像エリア
            Box(modifier = Modifier.height(160.dp).background(...)) {
                if (isSlotFull) {
                    // グレーオーバーレイ + テキスト
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "スロット制限中", ...)
                    }
                } else {
                    // 通常: トレンドバッジを表示
                    Card(...) { Text("トレンド") }
                }
            }

            // タイトル
            Text(
                text = article.title,
                color = if (isSlotFull)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)  // 薄く
                else
                    MaterialTheme.colorScheme.onSurface,
            )

            // 追加ボタン
            Button(
                onClick = { if (!isSlotFull) onAddToSlot() },
                enabled = !isSlotFull,    // ← 満杯時はタップ不可
                colors = if (isSlotFull) {
                    ButtonDefaults.buttonColors(
                        // 無効状態の見た目
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                Text(
                    text = if (isSlotFull) "スロットがいっぱいです" else "スロットに追加",
                )
            }
        }
    }
}
```

### `enabled = !isSlotFull` の効果

`Button(enabled = false)` にすると:
- タップに反応しない
- `disabledContainerColor` / `disabledContentColor` が自動的に適用される
- アクセシビリティ上も「操作不可」として認識される

### `copy(alpha = 0.4f)` でテキストを薄くする

```kotlin
MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
```

`Color.copy()` は色の一部パラメータだけ変えた新しい Color を返します。
`alpha = 0.4f` は「40% の不透明度」（60% 透明）を意味します。

---

## 3. SlotFullBanner（満杯バナー）

```kotlin
@Composable
private fun SlotFullBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "積読スロットが満杯です",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "スロット一覧から記事を消化すると追加できます",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
        }
    }
}
```

### `errorContainer` / `onErrorContainer` とは

Material3 のカラーシステムでは「Container」カラーペアが用意されています。

| カラー | 使用箇所 |
|--------|---------|
| `errorContainer` | エラー系の背景色（薄い赤） |
| `onErrorContainer` | `errorContainer` の上に乗るテキスト・アイコン色（暗い赤） |

`on～` カラーを使うと、背景との対比が保証されてアクセシビリティを確保できます。

---

## 4. SlotsCounterBar（残スロット表示バー）

```kotlin
@Composable
private fun SlotsCounterBar(usedCount: Int, maxSlots: Int, modifier: Modifier = Modifier) {
    val availableCount = (maxSlots - usedCount).coerceIn(0, maxSlots)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, modifier = Modifier.size(12.dp), tint = primary)
            Text(text = "空きスロット", color = primary)
        }
        Text(text = "$availableCount / $maxSlots", color = primary)
    }
}
```

### `coerceIn(0, maxSlots)` とは

値を指定した範囲に収める関数です。

```kotlin
(-1).coerceIn(0, 5)  // → 0  (下限未満は 0 に)
  3 .coerceIn(0, 5)  // → 3  (範囲内はそのまま)
  7 .coerceIn(0, 5)  // → 5  (上限超過は 5 に)
```

`usedCount` が何らかの理由で `maxSlots` を超えた場合でも、表示が `-1 / 5` のような不正な値にならないよう防御しています。

---

## まとめ：満杯ロック UI の設計思想

```
UI の状態変化:

tsundokuCount: 0〜4（スロット空き）
  → SlotsCounterBar: "空きスロット N/5"
  → SlotFullBanner: 非表示
  → ArticleCard: 通常表示、ボタン有効

tsundokuCount: 5（スロット満杯）
  → SlotsCounterBar: "空きスロット 0/5"
  → SlotFullBanner: 表示（赤背景バナー）
  → ArticleCard: グレーアウト、ボタン無効
```

`isSlotFull` という1つの Boolean フラグから複数の UI 要素が変化します。
このように「単一の真実の源（Single Source of Truth）」から UI を派生させることで、
表示の不整合を防ぎます。
