# PR レビューレポート

**PR/ブランチ**: issue13 → main
**レビュー日時**: 2026-03-03
**変更規模**: +120 / +15 / 7ファイル（コードファイルのみ）

---

## 🎯 変更の概要

issue #13「AddToTsundokuUseCase 実装」の対応。積読スロットへの記事追加ビジネスロジックを UseCase に実装する。スロット上限チェックと書き込みを Room の SQL でアトミックに実行し、5件制限を保証する。

**変更種別**:
- [x] 新機能 (Feature) — domain/usecase 層の実装
- [x] 関連修正 — data 層の Repository/DAO 拡張、PortalViewModel 更新

---

## ✅ マージ判定

> **APPROVE**

issue #13 の完了条件（5件制限が正しく機能すること）を満たしている。レビュー指摘事項（`currentStatus` パラメータ名の曖昧さ）を `slotCountStatus` にリネームして修正済み。アトミックな SQL 実装とテストカバレッジも適切。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `domain/usecase/AddToTsundokuUseCase.kt` | Added | +32 | - | 🟢 問題なし |
| `data/local/dao/ArticleDao.kt` | Modified | +12 | -1 | 🟢 問題なし |
| `data/repository/ArticleRepository.kt` | Modified | +7 | -2 | 🟢 問題なし |
| `data/repository/ArticleRepositoryImpl.kt` | Modified | +8 | -5 | 🟢 問題なし |
| `ui/portal/PortalViewModel.kt` | Modified | +25 | -3 | 🟡 軽微 |
| `ui/portal/PortalUiState.kt` | Modified | +5 | - | 🟢 問題なし |
| `test/.../AddToTsundokuUseCaseTest.kt` | Added | +65 | - | 🟢 問題なし |
| `test/.../ArticleRepositoryImplTest.kt` | Modified | +30 | +1 | 🟢 問題なし |
| `test/.../PortalViewModelTest.kt` | Modified | +35 | - | 🟢 問題なし |

---

## 🔍 詳細レビュー

### domain/usecase/AddToTsundokuUseCase.kt — 🟢 問題なし

#### 変更の意図
積読スロット追加の UseCase を実装。スロット満杯時は `Boolean(false)` を返す設計（仕様の `SlotFullException` からの設計変更）。

#### 良い点
- `MAX_TSUNDOKU_SLOTS = 5` を `companion object` の定数として公開し、UI 層から参照可能 ✅
- `operator fun invoke` で UseCase 慣例に準拠 ✅
- スロットチェックと書き込みを Repository 経由でアトミックに実行 ✅
- 仕様の例外スロー設計を Boolean 返り値 + ViewModel イベント設計に改善（例外を制御フローに使わない原則） ✅

```kotlin
// ✅ アトミックなスロット確認・書き込み
suspend operator fun invoke(articleId: String): Boolean {
    return repository.updateStatusIfSlotAvailable(
        id = articleId,
        newStatus = ArticleStatus.TSUNDOKU,
        slotCountStatus = ArticleStatus.TSUNDOKU,
        maxSlots = MAX_TSUNDOKU_SLOTS,
    )
}
```

---

### data/local/dao/ArticleDao.kt — 🟢 問題なし

#### 変更の意図
`updateStatusIfSlotAvailable` クエリとパラメータ名を明確化。

#### 良い点
- 「カウントしてから書く」を 1 本の SQL にまとめ TOCTOU 競合を回避 ✅
- パラメータ名を `currentStatus` → `slotCountStatus` にリネームし「スロット数をカウントする対象のステータス」という意図を明確化 ✅

```kotlin
// ✅ スロット確認と書き込みをアトミックに実行
@Query("""
    UPDATE articles SET status = :newStatus
    WHERE id = :articleId
    AND (SELECT COUNT(*) FROM articles WHERE status = :slotCountStatus) < :maxSlots
""")
```

---

### ui/portal/PortalViewModel.kt — 🟡 軽微

#### 変更の意図
`AddToTsundokuUseCase` を DI で注入し、`addToTsundoku()` メソッドを追加。スロット満杯時に `PortalEvent.SlotFull` を SharedFlow で通知。

#### 良い点
- `MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)` で UI イベントを安全に扱う ✅
- `activeLoadTag` ラッパーで `null` と「未ロード」を区別してロード重複を防ぐ ✅
- `PortalEvent` sealed interface で UI イベントの型を保証 ✅

#### 指摘事項

**[🟡 m-1] `addToTsundoku` の例外ハンドリングなし** (`PortalViewModel.kt:72-77`)

Repository / DAO で例外が発生した場合（DB エラー等）に `viewModelScope.launch` がクラッシュする。現フェーズでは DB エラーは稀なため許容範囲だが、将来的に `try-catch` または `runCatching` でエラーイベントを発行することを推奨。

```kotlin
// 現在（許容範囲）
fun addToTsundoku(articleId: String) {
    viewModelScope.launch {
        val added = addToTsundokuUseCase(articleId)
        if (!added) _event.emit(PortalEvent.SlotFull)
    }
}
```

---

### test/.../AddToTsundokuUseCaseTest.kt — 🟢 問題なし

#### 良い点
- スロット空き・満杯・パラメータ検証・定数の4軸でカバー ✅
- テストコメントで「スロット判定」「リポジトリ呼び出し検証」「定数の確認」セクションに分離 ✅
- `maxSlots = AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS` で定数参照（マジックナンバー排除） ✅

---

### test/.../ArticleRepositoryImplTest.kt — 🟢 問題なし

#### 良い点
- `updateStatusIfSlotAvailable` の `true`/`false` 変換を両方テスト ✅
- 既存テストと同じパターンで一貫性を維持 ✅

---

### test/.../PortalViewModelTest.kt — 🟢 問題なし

#### 良い点
- `addToTsundoku` 成功時にイベント非発行・満杯時に `SlotFull` イベント発行を検証 ✅
- `SharedFlow` を `launch { collect {} }` でサブスクライブしてイベント収集 ✅

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `ArticleRepository.updateStatusIfSlotAvailable` のパラメータ名変更（破壊的変更なし）
- `PortalViewModel` が `AddToTsundokuUseCase` を受け取るよう変更（DI グラフは Hilt が自動解決）

**破壊的変更**: なし

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| UseCase: スロット空き時 `true` | ✅ |
| UseCase: スロット満杯時 `false` | ✅ |
| UseCase: 正しいパラメータでリポジトリ呼び出し | ✅ |
| UseCase: `MAX_TSUNDOKU_SLOTS = 5` | ✅ |
| Repository: DAO が 1 → `true` | ✅ |
| Repository: DAO が 0 → `false` | ✅ |
| ViewModel: 成功時 `SlotFull` 非発行 | ✅ |
| ViewModel: 満杯時 `SlotFull` 発行 | ✅ |

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。APPROVE です 👍

アトミックな SQL によるスロット管理、Boolean によるフロー制御、
SharedFlow を使ったイベント設計はいずれも適切です。

修正点：
- currentStatus → slotCountStatus にリネーム
  （「スロット数をカウントする対象のステータス」の意図を明確化）
- AddToTsundokuUseCaseTest のマジックナンバー修正
  (5 → AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS)
- ArticleRepositoryImplTest に updateStatusIfSlotAvailable のテストを追加

軽微な改善として、addToTsundoku で DB エラー時の例外ハンドリングを
将来的に追加することを検討してください（m-1）。

詳細は docs/reviews/pr_review_issue13_20260303.md を参照してください。
```

---

## ✅ チェックリスト

- [x] `AddToTsundokuUseCase` を作成
- [x] 追加前にスロット数（< 5）をチェック（SQL でアトミックに）
- [x] `ArticleEntity.status` を `TSUNDOKU` に更新
- [x] スロット満杯の場合は `PortalEvent.SlotFull` でイベント通知（Boolean + event 設計）
- [x] `MAX_TSUNDOKU_SLOTS = 5` の定数化と公開
- [x] `slotCountStatus` で意図明確なパラメータ命名
- [x] `AddToTsundokuUseCaseTest` (5ケース) を追加
- [x] `ArticleRepositoryImplTest` に `updateStatusIfSlotAvailable` テスト追加
- [x] `PortalViewModelTest` に `addToTsundoku` テスト追加

---
*Generated by Claude Code / pr-review skill*
