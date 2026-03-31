# PR レビューレポート

**PR/ブランチ**: issue11 → main
**レビュー日時**: 2026-03-03
**変更規模**: +220 / +15 / 8ファイル（コードファイルのみ）

---

## 🎯 変更の概要

issue #11「PortalViewModel 実装」の対応。ポータル画面の ViewModel を実装し、記事取得・タグ選択・積読スロット数の3つの StateFlow を公開する。

**変更種別**:
- [x] 新機能 (Feature) — ui/portal 層の実装

---

## ✅ マージ判定

> **APPROVE**

issue #11 の完了条件（PortalViewModel が記事リストを StateFlow で公開すること）を満たしている。レビュー指摘事項（Repository 直接依存・`loadArticles` 設計・競合リクエスト対策）をすべて修正済み。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `gradle/libs.versions.toml` | Modified | +4 | - | 🟢 問題なし |
| `app/build.gradle.kts` | Modified | +2 | - | 🟢 問題なし |
| `data/repository/ArticleRepository.kt` | Modified | +3 | - | 🟢 問題なし |
| `data/repository/ArticleRepositoryImpl.kt` | Modified | +3 | - | 🟢 問題なし |
| `ui/portal/PortalUiState.kt` | Added | +9 | - | 🟢 問題なし |
| `domain/usecase/ObserveTsundokuCountUseCase.kt` | Added | +19 | - | 🟢 問題なし |
| `ui/portal/PortalViewModel.kt` | Added | +55 | - | 🟡 軽微 |
| `test/ui/portal/PortalViewModelTest.kt` | Added | +125 | - | 🟢 問題なし |

---

## 🔍 詳細レビュー

### ui/portal/PortalUiState.kt — 🟢 問題なし

#### 変更の意図
ポータル画面の UI 状態を sealed interface で表現。

#### 良い点
- `sealed interface` で状態の網羅チェックを型システムに委譲 ✅
- `data object Loading` / `data class Success` / `data class Error` の3状態が明確 ✅

```kotlin
// ✅ sealed interface で状態遷移を型で表現
sealed interface PortalUiState {
    data object Loading : PortalUiState
    data class Success(val articles: List<ArticleEntity>) : PortalUiState
    data class Error(val message: String) : PortalUiState
}
```

---

### domain/usecase/ObserveTsundokuCountUseCase.kt — 🟢 問題なし

#### 変更の意図
積読記事数の観測ロジックを UseCase に切り出し、ViewModel が Repository に直接依存しないようにする。

#### 良い点
- `Flow<Int>` を返す non-suspend UseCase として Clean Architecture の境界を維持 ✅
- `MAX_TSUNDOKU_SLOTS = 5` の上限ロジックをカプセル化 ✅
- `operator fun invoke` パターン ✅

```kotlin
// ✅ 積読数の上限管理を UseCase にカプセル化
operator fun invoke(): Flow<Int> = repository
    .observeTsundokuArticles()
    .map { it.size.coerceAtMost(MAX_TSUNDOKU_SLOTS) }
```

---

### ui/portal/PortalViewModel.kt — 🟡 軽微

#### 変更の意図
`GetTrendArticlesUseCase` と `ObserveTsundokuCountUseCase` を DI で受け取り、`uiState` / `selectedTag` / `tsundokuCount` の3つの StateFlow を公開する。

#### 良い点
- Repository 直接依存をなくし UseCase を通じてアクセス ✅
- `loadArticles()` を public no-arg に整理し `loadArticlesInternal(tag)` に委譲する設計 ✅
- `loadJob?.cancel()` で連続タップによる競合リクエストをキャンセル ✅
- `SharingStarted.WhileSubscribed(5_000)` でバックグラウンド時の不要な購読を自動解除 ✅
- KDoc コメントで各メソッドの意図を明記 ✅

```kotlin
// ✅ 競合リクエスト対策
private fun loadArticlesInternal(tag: String?) {
    loadJob?.cancel()
    loadJob = viewModelScope.launch { ... }
}
```

#### 指摘事項

**[🟡 m-1] `PortalUiState.Success` が `ArticleEntity` を直接保持** (`PortalUiState.kt:7`)

UI 層が Data 層のエンティティに直接依存している。将来 UI モデルへの変換が必要になる可能性があるが、現時点ではプロジェクト規模が小さく過剰設計になるため許容。

```kotlin
// 現在（許容範囲）
data class Success(val articles: List<ArticleEntity>) : PortalUiState

// 💡 将来の拡張案: UI モデルを導入する場合
data class ArticleUiModel(val id: String, val title: String, val url: String, val tags: List<String>)
data class Success(val articles: List<ArticleUiModel>) : PortalUiState
```

> **現フェーズでは問題なし。UI モデルへの変換は画面実装フェーズで要検討。**

---

### test/ui/portal/PortalViewModelTest.kt — 🟢 問題なし

#### 変更の意図
MockK で UseCase をモックし、ViewModel の uiState・selectedTag・tsundokuCount の動作をユニットテスト。

#### 良い点
- `ObserveTsundokuCountUseCase` をモックして Repository 非依存のテスト ✅
- `SharingStarted.WhileSubscribed` に対応するため `backgroundScope` で購読を開始 ✅
- `StandardTestDispatcher` + `advanceUntilIdle()` で非同期処理を制御 ✅
- 初期 Loading 状態・成功・失敗・タグ選択・積読カウントを9ケースで網羅 ✅
- `setUp()` でデフォルトスタブを設定し冗長なモック設定を排除 ✅

```kotlin
// ✅ WhileSubscribed に対応した購読起動
viewModel.tsundokuCount.launchIn(backgroundScope)
advanceUntilIdle()
assertEquals(3, viewModel.tsundokuCount.value)
```

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `ArticleRepository` インターフェースに `observeTsundokuArticles()` を追加（実装は `ArticleRepositoryImpl` 済み）
- `ObserveTsundokuCountUseCase` は Hilt モジュール不要（`@Inject constructor` で自動バインド）
- `PortalViewModel` は後続の PortalFragment/Screen から Hilt により注入される

**破壊的変更**: なし（新規追加のみ）

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| 初期状態が `Loading` | ✅ |
| init 時に `getTrendArticlesUseCase(tag = null)` が1回呼ばれる | ✅ |
| 成功時に `PortalUiState.Success` に遷移 | ✅ |
| 失敗時に `PortalUiState.Error` に遷移（メッセージ確認） | ✅ |
| `selectTag("Android")` で `selectedTag` が更新される | ✅ |
| `selectTag("Android")` でタグ付きの UseCase が呼ばれる | ✅ |
| `selectTag(null)` でトレンド全体を取得 | ✅ |
| `tsundokuCount` が UseCase の値を反映する | ✅ |
| `tsundokuCount` が UseCase 上限値をそのまま公開する | ✅ |

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。APPROVE です 👍

UseCase の適切な分離、StateFlow の正しい利用、競合リクエスト対策まで
設計が丁寧に整理されています。

主な変更点：
- ObserveTsundokuCountUseCase を導入し ViewModel の Repository 直接依存を解消
- loadArticles() を no-arg に整理し loadArticlesInternal(tag) に委譲
- loadJob?.cancel() で連続タップ時の競合リクエストをキャンセル
- SharingStarted.WhileSubscribed(5_000) でバックグラウンド時の購読を自動解除

PortalUiState.Success が ArticleEntity を直接保持している点は
現フェーズでは許容範囲ですが、画面実装フェーズで UI モデルへの
変換を検討してください（m-1）。

詳細は docs/reviews/pr_review_issue11_20260303.md を参照してください。
```

---

## ✅ チェックリスト

- [x] `PortalUiState` が sealed interface で定義されている
- [x] `uiState: StateFlow<PortalUiState>` が公開されている
- [x] `selectedTag: StateFlow<String?>` が公開されている
- [x] `tsundokuCount: StateFlow<Int>` が公開されている（上限5）
- [x] `loadArticles()` でリトライ可能
- [x] `selectTag(tag)` でタグ選択と再ロードが連動
- [x] UseCase を通じて Repository にアクセス（直接依存なし）
- [x] 競合リクエスト対策（loadJob?.cancel()）
- [x] ユニットテストが追加されている（9ケース）
- [x] `SharingStarted.WhileSubscribed(5_000)` のテスト対応（backgroundScope）

---
*Generated by Claude Code / pr-review skill*
