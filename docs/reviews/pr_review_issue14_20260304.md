# PR レビューレポート

**PR/ブランチ**: issue14 → main
**レビュー日時**: 2026-03-04
**変更規模**: +421 / +0 / 5ファイル（コードファイルのみ）

---

## 🎯 変更の概要

issue #14「積読スロット UI 実装」の対応。積読リスト画面（最大5スロット）を `TsundokuScreen` / `TsundokuViewModel` として実装する。`LazyVerticalGrid` で2カラムのスロット表示を行い、埋まったスロットはカード、空きスロットは破線カードで表示する。

**変更種別**:
- [x] 新機能 (Feature) — domain/usecase層・ui/tsundoku層の新規実装

---

## ✅ マージ判定

> **APPROVE**

`FilledSlotCard` 内の `LazyRow` → `Row` へのリネーム（M-1）を修正済み。5スロット固定グリッド・破線カード・AI要約ボタンすべて実装されており、完了条件を満たしている。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `domain/usecase/ObserveTsundokuArticlesUseCase.kt` | Added | +15 | - | 🟢 問題なし |
| `ui/tsundoku/TsundokuUiState.kt` | Added | +8 | - | 🟢 問題なし |
| `ui/tsundoku/TsundokuViewModel.kt` | Added | +25 | - | 🟢 問題なし |
| `ui/tsundoku/TsundokuScreen.kt` | Modified | +213 | -8 | 🟠 要修正 |
| `test/.../TsundokuViewModelTest.kt` | Added | +138 | - | 🟢 問題なし |

---

## 🔍 詳細レビュー

### domain/usecase/ObserveTsundokuArticlesUseCase.kt — 🟢 問題なし

#### 変更の意図
`ArticleRepository.observeTsundokuArticles()` を UseCase でラップし、`TsundokuViewModel` がリポジトリに直接依存しない設計にする。

#### 良い点
- `ObserveTsundokuCountUseCase` と同じ構造で一貫性 ✅
- `@Inject constructor` + `@Singleton` なし（UseCase はスコープ不要） ✅

---

### ui/tsundoku/TsundokuUiState.kt — 🟢 問題なし

#### 良い点
- `Loading` / `Success` の2状態で十分。Room Flow は常に emit するため `Error` 状態は不要 ✅
- `PortalUiState` と同じ sealed interface パターンで一貫性 ✅

---

### ui/tsundoku/TsundokuViewModel.kt — 🟢 問題なし

#### 変更の意図
`ObserveTsundokuArticlesUseCase` を `stateIn` で `StateFlow` に変換し、ライフサイクルに安全な状態管理を行う。

#### 良い点
- `SharingStarted.WhileSubscribed(5_000)` で `PortalViewModel` と統一 ✅
- `init` ブロック不要のシンプルな実装 ✅
- `initialValue = TsundokuUiState.Loading` で初期表示のローディング状態を保証 ✅

```kotlin
// ✅ stateIn でFlowをStateFlowに変換
val uiState: StateFlow<TsundokuUiState> = observeTsundokuArticlesUseCase()
    .map { TsundokuUiState.Success(it) }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TsundokuUiState.Loading,
    )
```

---

### ui/tsundoku/TsundokuScreen.kt — 🟠 要修正

#### 変更の意図
プレースホルダーを完全な実装に置き換え。`hiltViewModel()` で ViewModel を注入し、5スロット固定のグリッド UI を構築する。

#### 良い点
- `TsundokuScreen` / `TsundokuScreenContent` の分離で `PortalScreen` と同パターン ✅
- `private const val MAX_TSUNDOKU_SLOTS = AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS` で単一ソース ✅
- `drawBehind` + `PathEffect.dashPathEffect` で破線ボーダーを正しく実装 ✅
- `List(MAX_TSUNDOKU_SLOTS) { index -> articles.getOrNull(index) }` で5枠固定スロットを生成 ✅

#### 指摘事項

**[🟠 M-1] `LazyRow` を `LazyVerticalGrid` の子 Composable 内で使用している** (`TsundokuScreen.kt:138`)

Compose の Lazy レイアウトはネスト計測を制限する。`LazyVerticalGrid` の各セル内で `LazyRow` を使うと、内部の Lazy リストが正しい高さで計測できずクラッシュや表示崩れを引き起こす可能性がある。`tags.take(2)` は最大2件固定のため `LazyRow` は不要で、`Row` で十分。

```kotlin
// ❌ 現在：LazyRow inside LazyVerticalGrid
LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    items(tags.take(2)) { tag ->
        ...
    }
}
```

```kotlin
// ✅ 修正後：Row に置き換え（最大2件固定のため Lazy 不要）
Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    tags.take(2).forEach { tag ->
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = "#$tag",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
```

**[🟡 m-2] `EmptySlotCard` の `aspectRatio` によるグリッド行高さの不一致** (`TsundokuScreen.kt:188`)

`EmptySlotCard` は `aspectRatio(0.8f)` で高さを固定しているが、`FilledSlotCard` は内容に応じた高さになる。同じグリッド行に filled と empty が並ぶとき、grid の行高さは両者の最大値になるため empty カードの下部が余白だらけになる場合がある。`FilledSlotCard` 側も `fillMaxHeight()` を使うか、または empty カードの `aspectRatio` を削除して `fillMaxHeight(1f)` にするとよりきれいに揃う。

```kotlin
// 🟡 現在（filled/empty で高さ計算基準が異なる）
.aspectRatio(0.8f)

// 💡 改善案：aspectRatio を削除して Grid の行高さに合わせる
// Box に minHeight の制約だけ与える
.defaultMinSize(minHeight = 150.dp)
```

---

### test/.../TsundokuViewModelTest.kt — 🟢 問題なし

#### 良い点
- `MainDispatcherRule` + `MutableSharedFlow<List<ArticleEntity>>(replay = 1)` で `PortalViewModelTest` と同じパターン ✅
- `WhileSubscribed` の開始・停止を `advanceTimeBy(5_001L)` で検証 ✅
- 記事追加時の状態更新（1→2件）を検証するシナリオを含む ✅

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `AppNavHost.kt` — `TsundokuScreen` は既にインポート済み・変更なし
- Hilt DI — `ObserveTsundokuArticlesUseCase` は `@Inject constructor` のため自動解決

**破壊的変更**: なし

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| init: 初期状態は Loading | ✅ |
| 記事が流れてくると Success になる | ✅ |
| 空リストで Success かつ空になる | ✅ |
| 記事追加時に Success が更新される | ✅ |
| WhileSubscribed: 購読者がいる間だけ Flow を購読する | ✅ |

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。1件の必須修正があります。

必須修正：
- FilledSlotCard 内の LazyRow → Row に置き換え
  （LazyVerticalGrid 内での Lazy ネストはアンチパターン。
  tags.take(2) は最大2件固定のため Row で十分）

軽微な改善（任意）：
- EmptySlotCard の aspectRatio を削除して minHeight + fillMaxHeight を使うと
  FilledSlotCard との行高さがより自然に揃います（m-2）

詳細は docs/reviews/pr_review_issue14_20260304.md を参照してください。
```

---

## ✅ チェックリスト

- [x] `TsundokuScreen` Composable を実装
- [x] 5枠固定スロットのグリッドUI（空き枠は破線カード）
- [x] 各スロットに記事タイトル・タグを表示
- [x] 「AI要約」ボタンをカードに配置
- [x] `TsundokuViewModel` を実装（StateFlow管理）
- [x] **[M-1] FilledSlotCard の LazyRow を Row に置き換え（修正済み）**

---
*Generated by Claude Code / pr-review skill*
