# PR レビューレポート

**PR/ブランチ**: `issue82`
**レビュー日時**: 2026-03-05
**変更規模**: +201 / -9 / 6ファイル

---

## 🎯 変更の概要

DigestScreen に Markwon ライブラリを使った Markdown レンダリング機能を追加する。
`article.body` が存在する場合にトップバーへ "MD表示 / Web表示" 切り替えボタンを表示し、
オフライン時は自動的に Markdown モードへフォールバックする。

**変更種別**:
- [x] 新機能 (Feature)
- [ ] バグ修正 (Bug Fix)
- [ ] リファクタリング
- [ ] パフォーマンス改善
- [x] テスト追加
- [ ] ドキュメント

---

## ✅ マージ判定

> **APPROVE**

設計・実装ともに適切で Critical / Major な問題なし。
`AndroidView` + `Markwon` の組み合わせ、オフライン自動フォールバック、
`SharingStarted.Eagerly` による初期値の即時読み取りなど、技術的判断が正確。
Minor な改善提案はあるが、マージ可。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `gradle/libs.versions.toml` | Modified | +2 | 0 | 🟢 問題なし |
| `app/build.gradle.kts` | Modified | +1 | 0 | 🟢 問題なし |
| `DigestUiState.kt` | Modified | +3 | -1 | 🟢 問題なし |
| `DigestViewModel.kt` | Modified | +35 | -3 | 🟢 問題なし |
| `DigestScreen.kt` | Modified | +62 | -8 | 🟡 軽微（後述） |
| `DigestViewModelTest.kt` | Modified | +95 | -1 | 🟢 問題なし |

---

## 🔍 詳細レビュー

### gradle/libs.versions.toml & app/build.gradle.kts

#### 変更の意図
Markwon 4.6.2 を version catalog に追加し、`implementation(libs.markwon.core)` でアプリに組み込む。

#### 指摘事項
特になし。version catalog の記述パターンが既存ライブラリと統一されている。

---

### DigestUiState.kt

#### 変更の意図
`ReadMode` に `isMarkdownMode: Boolean = false` を追加し、表示モードを状態として保持する。

#### 指摘事項
特になし。デフォルト値 `false`（WebView 表示）が適切で、既存コードへの影響なし。

---

### DigestViewModel.kt

#### 変更の意図
- `NetworkMonitor` を DI で受け取り、`SharingStarted.Eagerly` で `isOnline` を即時評価
- `init` でオフライン時の自動 Markdown フォールバックを収集
- `loadArticle()` で初期読み込み時にオフライン状態を反映
- `toggleViewMode()` を追加

#### 良い点

```kotlin
// SharingStarted.Eagerly で stateIn することで、
// suspend 関数から戻った直後でも isOnline.value が正しい値を持つ
val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = true,
    )
```

非同期フローを `SharingStarted.Eagerly` で `StateFlow` に変換し、同期的に `isOnline.value` を読めるようにしている点が正確。

```kotlin
fun toggleViewMode() {
    val current = _uiState.value as? DigestUiState.ReadMode ?: return
    _uiState.value = current.copy(isMarkdownMode = !current.isMarkdownMode)
}
```

ReadMode 以外での呼び出しを安全にガードしている。

#### 指摘事項（Minor）

**[🟡 M-1] `isOnline` を public StateFlow として公開する必要があるか検討** (`DigestViewModel.kt:49`)

```kotlin
// 現在
val isOnline: StateFlow<Boolean> = networkMonitor.isOnline.stateIn(...)
```

```kotlin
// 検討案: Screenから直接使わないなら private でも可
private val isOnline: StateFlow<Boolean> = networkMonitor.isOnline.stateIn(...)
```

> 現状 `DigestScreen.kt` で `viewModel.isOnline` を参照していないなら `private` にしてカプセル化を強化できる。
> ただし、将来の利用を想定した公開でも問題はなく、テストでの参照もないため、任意の改善。

---

### DigestScreen.kt

#### 変更の意図
- `DigestTopBar` に `isMarkdownMode` / `onToggleViewMode` を追加して切り替えボタンを表示
- ReadMode の本文描画を WebView と Markdown で切り替え
- `MarkdownContent` Composable を追加（`AndroidView` + `TextView` + `Markwon`）

#### 良い点

```kotlin
onToggleViewMode = if (readMode?.article?.body != null) onToggleViewMode else null,
```

`body == null` のときはボタン自体を非表示にしており、ガード条件がシンプルかつ正確。

```kotlin
@Composable
private fun MarkdownContent(markdown: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                textSize = 15f
                setLineSpacing(0f, 1.5f)
            }
        },
        update = { textView ->
            Markwon.create(textView.context).setMarkdown(textView, markdown)
        },
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
    )
}
```

`AndroidView` の `factory` / `update` の責務分離が適切。スクロールは Compose 側の `verticalScroll` で扱うことで、
`TextView` の独自スクロールとの競合を回避している。

#### 指摘事項（Minor）

**[🟡 M-2] `Markwon.create()` をリコンポーズごとに生成している** (`DigestScreen.kt:update`)

```kotlin
// ❌ update が呼ばれるたびに Markwon インスタンスを生成
update = { textView ->
    Markwon.create(textView.context).setMarkdown(textView, markdown)
},
```

```kotlin
// ✅ factory で一度だけ生成してタグで保持
factory = { context ->
    val markwon = Markwon.create(context)
    TextView(context).apply {
        textSize = 15f
        setLineSpacing(0f, 1.5f)
        tag = markwon
    }
},
update = { textView ->
    @Suppress("UNCHECKED_CAST")
    (textView.tag as Markwon).setMarkdown(textView, markdown)
},
```

> `Markwon.create()` は軽量だが、`update` ラムダは `markdown` が変わるたびに呼ばれる。
> 将来的にコンテンツが動的に変わるケースを想定すると、インスタンスをキャッシュする方がベター。
> 現状では静的コンテンツのためパフォーマンス上の問題は小さく、必須修正ではない。

---

### DigestViewModelTest.kt

#### 変更の意図
`NetworkMonitor` のモックを追加し、`toggleViewMode` とオフラインフォールバックのテストを 6 件追加。

#### 良い点

```kotlin
@Before
fun setUp() {
    every { networkMonitor.isOnline } returns flowOf(true)
}
```

デフォルトをオンライン（`flowOf(true)`）とすることで、既存テストへの影響ゼロを担保している。

```kotlin
@Test
fun `isOnline - オフラインになったときbodyがあればisMarkdownModeがtrueになる`() = runTest {
    val onlineFlow = MutableStateFlow(true)
    every { networkMonitor.isOnline } returns onlineFlow
    // ...
    onlineFlow.value = false
    runCurrent()
    assertTrue(state.isMarkdownMode)
}
```

`MutableStateFlow` を使って実行時にオフラインへ切り替え、動的フォールバックを検証しているのが正確。

#### 指摘事項
特になし。6 件すべてが意味のある境界値テストになっている。

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `DigestScreen.kt` — `DigestScreenContent` の signature 変更（`onToggleViewMode` 追加）
- `DigestTopBar` — 新パラメータ追加（デフォルト値あり、既存呼び出し箇所は問題なし）
- `DigestViewModel.kt` — constructor に `NetworkMonitor` 追加（Hilt 経由で自動 DI）

**破壊的変更 (Breaking Change)**: なし
（`DigestTopBar` の新パラメータはデフォルト値付き。`DigestScreenContent` は internal なため外部から参照不可）

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| 既存テストがパスするか | ✅ 全 157 件 PASS |
| 新機能のテストが追加されているか | ✅ 6 件追加 |
| エッジケースのテスト | ✅ body=null / ReadMode以外 / 動的オフライン切り替え |

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。APPROVE します。

Minor な改善提案が 2 件ありますが、いずれも必須修正ではありません。
- M-1: `isOnline` を private にするか検討
- M-2: `Markwon.create()` を factory でキャッシュする

テストカバレッジも適切で、オフライン→Markdown 自動フォールバックの
動的テストも含まれており、品質が高いと判断します。
```

**インラインコメント候補**:
- `DigestViewModel.kt:49`: `isOnline` を `private` にすることでカプセル化が強化できます（任意）
- `DigestScreen.kt (update lambda)`: `Markwon.create()` を `factory` でキャッシュし `tag` に保存すると再利用できます（任意）

---

## ✅ チェックリスト

- [x] Critical issue がすべて解決されている（Critical なし）
- [x] テストが追加・更新されている（6 件追加）
- [x] 破壊的変更がある場合、マイグレーション手順が記載されている（破壊的変更なし）
- [x] セルフレビュー済み

---
*Generated by Claude Code / pr-review skill*
