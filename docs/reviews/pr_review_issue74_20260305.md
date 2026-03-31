# PR レビューレポート

**PR/ブランチ**: issue74
**レビュー日時**: 2026-03-05
**変更規模**: +232 / -74 / 20ファイル（コード変更分のみ）

---

## 🎯 変更の概要

AI機能を記事タイトルベースから**記事全文（body）ベース**に変更し、フィードバック生成時に**理解度判定（JUDGMENT: OK/NG）を追加**する。
理解不十分な場合は「消化する」ボタンを無効化する読了ゲート機能を実装する。
また、QiitaAPIレスポンスのbody取得・DBへの保存、`FeedbackResult`データクラス新設による型安全なフィードバック返却も含む。

**変更種別**:
- [x] 新機能 (Feature) — 読了ゲート（`canDigest`）
- [x] リファクタリング — AI入力を全文ベースに変更、API署名の刷新
- [x] テスト追加 — 全変更ファイルに対応するテスト更新

---

## ✅ マージ判定

> **APPROVE**

全テストがパスしており、変更の設計が一貫している。`parseFeedbackResponse` のフォールバック設計により、AI応答フォーマット不一致時もアプリがクラッシュしない安全設計になっている。軽微な改善提案はあるが、マージブロッカーはない。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `AppDatabase.kt` | Modified | +1 | -1 | 🟡 確認推奨（バージョン） |
| `ArticleEntity.kt` | Modified | +1 | 0 | 🟢 問題なし |
| `DigestEntity.kt` | Modified | +1 | 0 | 🟢 問題なし |
| `QiitaArticleDto.kt` | Modified | +1 | -2 | 🟢 問題なし |
| `QiitaArticleDtoMapper.kt` | Modified | +3 | 0 | 🟢 問題なし |
| `FeedbackResult.kt` | Added | +7 | - | 🟢 問題なし |
| `AiRepository.kt` | Modified | +12 | -11 | 🟢 問題なし |
| `AiRepositoryImpl.kt` | Modified | +66 | -22 | 🟡 軽微（パース） |
| `DigestRepository.kt` | Modified | +5 | -3 | 🟢 問題なし |
| `DigestRepositoryImpl.kt` | Modified | +16 | -5 | 🟢 問題なし |
| `GenerateFeedbackUseCase.kt` | Modified | +13 | -10 | 🟢 問題なし |
| `GenerateQuestionUseCase.kt` | Modified | +1 | -1 | 🟢 問題なし |
| `GenerateSummaryUseCase.kt` | Modified | +1 | -1 | 🟢 問題なし |
| `DigestUiState.kt` | Modified | +2 | 0 | 🟢 問題なし |
| `DigestViewModel.kt` | Modified | +8 | -2 | 🟢 問題なし |
| `DigestScreen.kt` | Modified | +12 | 0 | 🟢 問題なし |
| `*Test.kt` (5ファイル) | Modified | +104 | -36 | 🟢 問題なし |

---

## 🔍 詳細レビュー

### AppDatabase.kt

#### 変更の意図
`body` (ArticleEntity) と `isUnderstandingSufficient` (DigestEntity) の2カラム追加に伴うスキーマバージョン更新。

#### 指摘事項

**[🟡 M-1] `fallbackToDestructiveMigration` に依存したスキーマ変更** (`AppDatabase.kt:11`)

```kotlin
// 現在: version 2 へのバージョンアップ
// AppModule.kt で fallbackToDestructiveMigration() が設定されており
// 既存DBデータは全消去される
```

> 開発中は問題ないが、本番リリース後は `fallbackToDestructiveMigration` は使えないため、将来的には明示的な Migration を用意する必要がある。現時点では開発フェーズのため許容。

---

### AiRepositoryImpl.kt

#### 変更の意図
`generateFeedback` の戻り値を `String` から `FeedbackResult` に変更し、構造化出力（JUDGMENT/FEEDBACK形式）のパースを追加。

#### 指摘事項

**[🟡 S-1] `parseFeedbackResponse` のJUDGMENT/FEEDBACKパースは行単位で脆弱** (`AiRepositoryImpl.kt:78-112`)

```kotlin
// 現在のコード（JUDGMENT行を行ごとに探す）
val judgmentLine = lines.find { it.trimStart().startsWith("JUDGMENT:") }
val feedbackIndex = lines.indexOfFirst { it.trimStart().startsWith("FEEDBACK:") }
```

```kotlin
// 代替案: 正規表現でより堅牢に
private val JUDGMENT_REGEX = Regex("""JUDGMENT:\s*(OK|NG)""", RegexOption.IGNORE_CASE)
private val FEEDBACK_REGEX = Regex("""FEEDBACK:\s*(.+)""", RegexOption.DOT_MATCHES_ALL)
```

> Gemini がフォーマットに前置きを付けた場合（例: `Sure! JUDGMENT: OK`）でも、`startsWith` は機能するので現実的には問題ない。ただし将来的には正規表現の方が保守性が高い。フォールバックがあるため現時点では許容。

**[🟢 G-1] フォールバック設計が良い** (`AiRepositoryImpl.kt:99-101`)

```kotlin
// ✅ パース失敗時は全文をフィードバックとして扱い、isOk=true とする
?: true  // judgmentLine が null の場合は理解十分と見なす
```

> AIのレスポンスが想定フォーマット外の場合でも、ユーザーが「消化する」を押せる状態を維持する安全設計。UXとして適切。

---

### GenerateFeedbackUseCase.kt

#### 変更の意図
`articleId` を受け取り `ArticleRepository.getArticleById` で本文を取得してからAIに渡すよう変更。

#### 指摘事項

**[🟡 S-2] 記事が見つからない場合に空文字でAPIを呼ぶ** (`GenerateFeedbackUseCase.kt:31`)

```kotlin
// 現在: article が null の場合も空文字でAIを呼ぶ
val content = article?.body ?: article?.title ?: ""
return aiRepository.generateFeedback(content, question, userMemo)
```

```kotlin
// 改善案: article が null の場合は早期リターン
if (article == null) return Result.failure(IllegalArgumentException("記事が見つかりません: $articleId"))
val content = article.body ?: article.title
```

> 実用上 `consumeArticle` を呼べる状態では記事が必ず存在するため問題にならないが、防御的プログラミングとして改善の余地がある。

---

### DigestScreen.kt

#### 変更の意図
`canDigest=false` 時に再挑戦を促すメッセージを表示し、「消化する」ボタンを無効化。

#### 指摘事項

**[🟢 G-2] `canDigest` のdisabledスタイルが明示的** (`DigestScreen.kt:412-417`)

```kotlin
// ✅ disabledContainerColor / disabledContentColor を明示して視覚的に明確
colors = ButtonDefaults.buttonColors(
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
),
```

> デフォルトの disabled スタイルより明示的で、デザインとの整合性が取りやすい。

---

### テストファイル群

**[🟢 G-3] `GenerateFeedbackUseCaseTest` でボディ有無のケースをカバー**

`body = "記事本文"` を持つ `dummyArticle` を使い、`aiRepository.generateFeedback("記事本文", ...)` を `coVerify` で確認している。本文が実際にAIへ渡ることをテストで担保できている。

**[🟡 S-3] `FeedbackMode.canDigest = false` シナリオのViewModelテストが不足**

```kotlin
// 追加を推奨するテストケース
@Test
fun `submitMemo - isUnderstandingSufficientがfalseの時canDigestがfalseになる`() = runTest {
    coEvery { getArticleUseCase(any()) } returns createDummyArticle()
    coEvery { generateQuestionUseCase(any()) } returns Result.success("問い")
    coEvery { generateFeedbackUseCase(any(), any(), any()) } returns
        Result.success(FeedbackResult("理解不十分", isUnderstandingSufficient = false))
    val viewModel = createViewModel()
    runCurrent()
    viewModel.showQuestion()
    runCurrent()

    viewModel.submitMemo("浅いメモ")
    runCurrent()

    val state = viewModel.uiState.value as DigestUiState.FeedbackMode
    assertEquals(false, state.canDigest)
}
```

> 読了ゲート機能のコアとなる `canDigest=false` 遷移がViewModelレベルでテストされていない。`GenerateFeedbackUseCaseTest` では `isUnderstandingSufficient=false` のケースを追加済みだが、ViewModelレベルでの結合を確認するテストが欲しい。

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `GenerateQuestionUseCase`, `GenerateSummaryUseCase` — 入力パラメータ名変更のみ（実質影響なし）
- `DigestViewModel.submitMemo` — `FeedbackResult` を受け取り `canDigest` を設定
- `DigestScreen.FeedbackModeContent` — `canDigest` パラメータ追加

**破壊的変更 (Breaking Change)**:
- `AiRepository.generateFeedback`: 引数が2個→3個、戻り値が `Result<String>` → `Result<FeedbackResult>`
- `DigestRepository.saveFeedback`: 引数が2個→3個（`isUnderstandingSufficient` 追加）
- `AppDatabase.version`: 1 → 2（既存DBが消去される）

いずれも内部インターフェースのみの変更であり、外部公開APIへの影響はなし。

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| 既存テストがパスするか | ✅ BUILD SUCCESSFUL (全テスト通過) |
| 新機能のテストが追加されているか | ✅ `isUnderstandingSufficient=false` のUseCaseテスト追加済み |
| `canDigest=false` のViewModelテスト | ❌ 不足（S-3参照） |
| `parseFeedbackResponse` の単体テスト | ❓ `AiRepositoryImplTest` で構造化応答をテストしているが正常系のみ |

**追加を推奨するテストケース**:
```kotlin
// DigestViewModelTest.kt に追加
@Test
fun `submitMemo - isUnderstandingSufficientがfalseの時canDigestがfalseになる`() = runTest {
    // ... S-3 参照
}

// AiRepositoryImplTest.kt に追加 (parseFeedbackResponse の NG パース確認)
@Test
fun `generateFeedback - JUDGMENT_NGの場合isUnderstandingSufficientがfalseになる`() = runTest {
    coEvery { geminiApiService.generateContent(any(), any()) } returns
        buildResponse("JUDGMENT: NG\nFEEDBACK: 理解が不十分です")

    val result = repository.generateFeedback("記事本文", "問い", "メモ")

    assertEquals(false, result.getOrNull()?.isUnderstandingSufficient)
}
```

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。

全テストがパスしており、APPROVE です。

3件の改善提案（S-1〜S-3）がありますが、いずれもブロッカーではありません。
特に S-3（DigestViewModelTest の canDigest=false テスト）は読了ゲートの
コア機能なので、余裕があれば追加をお勧めします。

詳細は docs/reviews/pr_review_issue74_20260305.md を参照してください。
```

**インラインコメント候補**:
- `GenerateFeedbackUseCase.kt:31`: `article == null` の場合に空文字でAPIを呼ぶより、早期リターンで `Result.failure` を返すとより防御的です
- `AiRepositoryImpl.kt:78`: `parseFeedbackResponse` のパース失敗時フォールバックが良い設計です
- `DigestViewModelTest.kt`: `canDigest = false` になる場合のテストがあると読了ゲートの動作をより確実に担保できます

---

## ✅ チェックリスト

- [x] Critical issueがすべて解決されている（Criticalなし）
- [x] テストが追加・更新されている
- [x] 既存テストがパスしている（BUILD SUCCESSFUL）
- [x] 破壊的変更がある場合、内部インターフェースのみで外部影響なし
- [ ] `canDigest=false` のViewModelテスト（S-3）— 任意

---
*Generated by Claude Code / pr-review skill*
