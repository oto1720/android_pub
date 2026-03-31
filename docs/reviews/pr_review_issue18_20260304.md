# PR レビューレポート

**PR/ブランチ**: issue18 → main
**レビュー日時**: 2026-03-04
**変更規模**: +141 / -13 / 7ファイル（コードファイルのみ）

---

## 🎯 変更の概要

issue #18「DigestArticleUseCase 実装」の対応。`DigestRepository` / `DigestRepositoryImpl` を新規作成し、`DigestArticleUseCase` でメモ・フィードバックの `DigestEntity` 保存と `ArticleEntity.status → DONE` 更新をアトミックに実行する。`CheckAndMemoViewModel.consumeArticle()` を `DigestArticleUseCase` に差し替え、フィードバックフェーズのメモ・フィードバックを実際に永続化するようにした。

**変更種別**:
- [x] 新機能 (Feature) — `DigestRepository` / `DigestArticleUseCase` の新規実装
- [x] 関連修正 — `CheckAndMemoViewModel` の依存を `ArticleRepository` → `DigestArticleUseCase` に差し替え、`CheckAndMemoViewModelTest` を更新

---

## ✅ マージ判定

> **APPROVE**

完了条件（消化後に status が DONE になり、スロットが解放されること）を満たしている。`DigestEntity` へのメモ・フィードバック保存と status 更新が `DigestArticleUseCase` に集約され、責務の分離も明確。`CheckAndMemoViewModel` からデータ層への直接依存が排除された点も評価できる。指摘事項なし。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `data/repository/DigestRepository.kt` | Added | +12 | - | 🟢 問題なし |
| `data/repository/DigestRepositoryImpl.kt` | Added | +18 | - | 🟢 問題なし |
| `domain/usecase/DigestArticleUseCase.kt` | Added | +50 | - | 🟢 問題なし |
| `di/RepositoryModule.kt` | Modified | +6 | - | 🟢 問題なし |
| `ui/checkmemo/CheckAndMemoViewModel.kt` | Modified | +8 | -5 | 🟢 問題なし |
| `test/.../DigestArticleUseCaseTest.kt` | Added | +80 | - | 🟢 問題なし |
| `test/.../CheckAndMemoViewModelTest.kt` | Modified | +19 | -8 | 🟢 問題なし |

---

## 🔍 詳細レビュー

### data/repository/DigestRepository.kt — 🟢 問題なし

#### 良い点
- `saveDigest` / `observeAll` の2メソッドに絞ったミニマルなインターフェース設計 ✅
- `ArticleRepository` と同パターンで一貫性あり ✅

---

### data/repository/DigestRepositoryImpl.kt — 🟢 問題なし

#### 良い点
- `DigestDao.insert` への単純委譲で責務が明確 ✅

```kotlin
// ✅ シンプルな委譲実装
override suspend fun saveDigest(digest: DigestEntity) = digestDao.insert(digest)
```

---

### domain/usecase/DigestArticleUseCase.kt — 🟢 問題なし

#### 良い点
- `digestRepository.saveDigest()` → `articleRepository.updateStatus(DONE)` の順序で実行し、メモ保存→スロット解放の意図が読みやすい ✅
- `savedAt` を UTC ISO 8601 形式で設定 — `ArticleRepositoryImpl` と同じフォーマット定数を使用して一貫性を維持 ✅
- `DigestEntity` の `aiQuestion` フィールドは今回 null のまま保存 — issue21-25（AI UseCase）実装後に埋める設計 ✅
- KDoc コメントでスロット解放の仕組みを説明 ✅

```kotlin
// ✅ 保存 → ステータス更新の順序が明確
suspend operator fun invoke(articleId: String, memo: String, feedback: String) {
    digestRepository.saveDigest(
        DigestEntity(articleId = articleId, userMemo = memo, aiFeedback = feedback, savedAt = savedAt)
    )
    articleRepository.updateStatus(id = articleId, status = ArticleStatus.DONE)
}
```

---

### di/RepositoryModule.kt — 🟢 問題なし

`bindDigestRepository` を `ArticleRepository` と同パターンで追加 ✅

---

### ui/checkmemo/CheckAndMemoViewModel.kt — 🟢 問題なし

#### 変更の意図
`ArticleRepository` への直接依存を排除し、`DigestArticleUseCase` に委譲することでビジネスロジックを UseCase 層に集約。

#### 良い点
- `consumeArticle()` に `as? FeedbackPhase ?: return` のガードを追加 — `submitMemo()` 前に「消化する」が呼ばれても安全 ✅
- `current.memo` / `current.feedback` を FeedbackPhase から取得して UseCase に渡す — フェーズオブジェクトが必要なデータをすべて保持しており凝集度が高い ✅

```kotlin
// ✅ FeedbackPhaseからメモ・フィードバックを取得して UseCase に渡す
fun consumeArticle() {
    val current = _uiState.value as? CheckAndMemoUiState.FeedbackPhase ?: return
    viewModelScope.launch {
        digestArticleUseCase(
            articleId = articleId,
            memo = current.memo,
            feedback = current.feedback,
        )
        _event.emit(CheckAndMemoEvent.Consumed)
    }
}
```

---

### test/.../DigestArticleUseCaseTest.kt — 🟢 問題なし

#### 良い点
- `slot<DigestEntity>()` を使って `saveDigest` に渡された entity の各フィールドを個別に検証 ✅
- `savedAt` が非空であることも確認 ✅
- `saveDigest` と `updateStatus` がそれぞれ1回ずつ呼ばれることを検証 ✅

---

### test/.../CheckAndMemoViewModelTest.kt — 🟢 問題なし

#### 良い点
- `consumeArticle - DigestArticleUseCaseを正しいパラメータで呼ぶ` で `articleId` / `memo` / `feedback` すべてを検証 ✅
- `consumeArticle - InputPhaseの状態では何もしない` で FeedbackPhase 前の呼び出しを防ぐガードを検証 ✅

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `CheckAndMemoViewModel` の依存が変わったため、`CheckAndMemoScreen` を通じて Hilt グラフが更新される（`DigestRepository` → `DigestRepositoryImpl` のバインディングが必要）→ `RepositoryModule` で対応済み ✅
- 積読スロット解放: `ArticleStatus.TSUNDOKU → DONE` に変わることで `ObserveTsundokuCountUseCase` のカウントが自動的に -1 される — 既存ロジックで対応済み

**破壊的変更**: `CheckAndMemoViewModel` のコンストラクタが変わるが、`hiltViewModel()` 経由で注入されるため呼び出し元への影響なし

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| DigestEntity にメモが保存される | ✅ |
| DigestEntity にフィードバックが保存される | ✅ |
| DigestEntity の articleId が正しい | ✅ |
| DigestEntity の savedAt が設定される | ✅ |
| articleRepository に DONE で updateStatus 呼ぶ | ✅ |
| saveDigest と updateStatus を各1回呼ぶ | ✅ |
| ViewModel: DigestArticleUseCase を正しいパラメータで呼ぶ | ✅ |
| ViewModel: Consumed イベントが発行される | ✅ |
| ViewModel: InputPhase では consumeArticle が何もしない | ✅ |
| 全ユニットテスト通過 | ✅ |

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。APPROVE です 👍

DigestArticleUseCase にメモ保存とステータス更新を集約し、
CheckAndMemoViewModel からデータ層への直接依存を排除した設計が
クリーンです。FeedbackPhase ガードやスロット自動解放も正しく機能します。

詳細は docs/reviews/pr_review_issue18_20260304.md を参照してください。
```

---

## ✅ チェックリスト

- [x] `DigestArticleUseCase` を作成
- [x] `ArticleEntity.status` を `DONE` に更新
- [x] `DigestEntity` にメモ・フィードバックを保存
- [x] 積読スロットの解放を確認（TSUNDOKU カウント -1）
- [x] `DigestArticleUseCaseTest` を追加（6ケース）
- [x] `CheckAndMemoViewModelTest` を更新（9ケース）

---
*Generated by Claude Code / pr-review skill*
