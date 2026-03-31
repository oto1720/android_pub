# 3つの Generate UseCase（issue #23・#24・#25）

## このドキュメントで学ぶこと

- DB キャッシュ優先（Cache-First）パターンの実装
- `Result.onSuccess {}` を使った Result の連鎖
- `?.let { return ... }` による早期リターンパターン
- 3つの UseCase の責務と設計の違い
- `FeedbackResult` を使った複合型の返却

---

## 仕様変更: タイトル → 全文ベースの AI 生成

すべての AI UseCase は **記事タイトルではなく `ArticleEntity.body`（記事全文）** を AI に渡します。

| UseCase | 変更前 | 変更後 |
|---------|--------|--------|
| `GenerateSummaryUseCase` | `article.title` をプロンプトに渡す | `article.body` をプロンプトに渡す |
| `GenerateQuestionUseCase` | `article.title` をプロンプトに渡す | `article.body` をプロンプトに渡す |
| `GenerateFeedbackUseCase` | `question, userMemo` のみ | `article.body, question, userMemo` を渡す |

---

## 1. GenerateSummaryUseCase — DB キャッシュ優先

```kotlin
// domain/usecase/GenerateSummaryUseCase.kt
class GenerateSummaryUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val aiRepository: AiRepository,
) {
    suspend operator fun invoke(articleId: String): Result<String> {
        // 1. 記事を取得（存在しない場合は即失敗）
        val article = articleRepository.getArticleById(articleId)
            ?: return Result.failure(IllegalArgumentException("記事が見つかりません: $articleId"))

        // 2. DB に要約が保存済みなら API を呼ばずに返す
        article.aiSummary?.let { return Result.success(it) }

        // 3. body が取得できない場合はフォールバック
        val body = article.body
            ?: return Result.failure(IllegalStateException("記事本文が未取得です"))

        // 4. 未保存の場合は Gemini API で生成して DB に保存
        return aiRepository.generateSummary(body).onSuccess { summary ->
            articleRepository.saveSummary(articleId, summary)
        }
    }
}
```

### Cache-First パターンとは

「まず DB を確認し、あれば返す。なければ API を呼ぶ」戦略です。

```
invoke() が呼ばれる
    ↓
DBに aiSummary があるか？
    ├── YES → Result.success(aiSummary)  ← API 呼び出しなし
    └── NO  → body を取得して Gemini API で生成
                  ↓ 成功
              DB に保存（saveSummary）
                  ↓
              Result.success(summary)
```

**なぜ Cache-First にするのか**:
- Gemini API は有料で課金が発生する
- 同じ記事を2回タップしたとき、2回 API を呼ぶのは無駄
- LLM の生成には数秒かかるため、DB から即時返す方がユーザー体験が良い

---

## 2. GenerateQuestionUseCase — DigestEntity でキャッシュ

```kotlin
// domain/usecase/GenerateQuestionUseCase.kt
class GenerateQuestionUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val digestRepository: DigestRepository,
    private val aiRepository: AiRepository,
) {
    suspend operator fun invoke(articleId: String): Result<String> {
        // 1. 記事を取得
        val article = articleRepository.getArticleById(articleId)
            ?: return Result.failure(IllegalArgumentException("記事が見つかりません: $articleId"))

        // 2. DigestEntity に問いが保存済みなら返す
        digestRepository.getByArticleId(articleId)?.aiQuestion?.let {
            return Result.success(it)
        }

        // 3. body が取得できない場合はフォールバック
        val body = article.body
            ?: return Result.failure(IllegalStateException("記事本文が未取得です"))

        // 4. Gemini で生成して DigestEntity に保存
        return aiRepository.generateQuestion(body).onSuccess { question ->
            digestRepository.saveQuestion(articleId, question)
        }
    }
}
```

### GenerateSummaryUseCase との違い

| 観点 | GenerateSummaryUseCase | GenerateQuestionUseCase |
|------|----------------------|------------------------|
| キャッシュ場所 | `ArticleEntity.aiSummary` | `DigestEntity.aiQuestion` |
| キャッシュ確認 | `article.aiSummary?.let { }` | `digestRepository.getByArticleId()?.aiQuestion?.let { }` |
| 保存先メソッド | `articleRepository.saveSummary()` | `digestRepository.saveQuestion()` |

要約は「記事に紐づく情報」なので `ArticleEntity` に保存。
問いは「読了フローに紐づく情報」なので `DigestEntity` に保存。
責務に応じてデータの保存先が異なります。

---

## 3. GenerateFeedbackUseCase — キャッシュなし + 理解度評価

```kotlin
// domain/usecase/GenerateFeedbackUseCase.kt
class GenerateFeedbackUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val digestRepository: DigestRepository,
    private val aiRepository: AiRepository,
) {
    suspend operator fun invoke(
        articleId: String,
        question: String,
        userMemo: String,
    ): Result<FeedbackResult> {
        // 1. 記事全文を取得（フィードバックの根拠として必要）
        val article = articleRepository.getArticleById(articleId)
            ?: return Result.failure(IllegalArgumentException("記事が見つかりません: $articleId"))
        val body = article.body
            ?: return Result.failure(IllegalStateException("記事本文が未取得です"))

        // 2. Gemini でフィードバック + 理解度評価を生成
        return aiRepository.generateFeedback(body, question, userMemo).onSuccess { result ->
            digestRepository.saveFeedback(
                articleId = articleId,
                memo = userMemo,
                feedback = result.feedback,
                isUnderstandingSufficient = result.isUnderstandingSufficient,
            )
        }
    }
}
```

### 戻り値が `Result<FeedbackResult>` な理由

フィードバックには「テキスト」と「理解度評価」の2つの情報が必要です。
`FeedbackResult` にまとめることで ViewModel が両方の値を受け取り、
「消化する」ボタンの有効/無効制御に利用できます。

```
GenerateFeedbackUseCase の戻り値フロー:

Gemini API → "JUDGMENT: OK\nFEEDBACK: <text>"
    ↓ AiRepositoryImpl.parseFeedbackResponse()
FeedbackResult(feedback = "<text>", isUnderstandingSufficient = true)
    ↓ onSuccess { result ->
DB 保存（memo + feedback + isUnderstandingSufficient）
    ↓ 呼び出し元に返る
Result.success(FeedbackResult(...))
    ↓ ViewModel
isUnderstandingSufficient に基づいて「消化する」ボタンを有効/無効
```

### なぜフィードバックはキャッシュ確認をしないのか

フィードバックは「ユーザーのメモ（`userMemo`）への返答」です。
メモの内容が変わるたびに新しいフィードバックが必要なため、
DB キャッシュのチェックをしていません（毎回 Gemini API を呼ぶ）。

```
要約・問い:  同じ記事なら同じ内容 → キャッシュが有効
フィードバック: ユーザーのメモに依存する → キャッシュは意味がない
```

---

## 4. 3つの UseCase の比較まとめ

```
GenerateSummaryUseCase:
  依存: ArticleRepository + AiRepository
  AI 入力: article.body（全文）
  キャッシュ: ArticleEntity.aiSummary（記事単位）
  入力: articleId
  流れ: DB確認 → ある → 返す / ない → API生成 → DB保存 → 返す

GenerateQuestionUseCase:
  依存: ArticleRepository + DigestRepository + AiRepository
  AI 入力: article.body（全文）
  キャッシュ: DigestEntity.aiQuestion（消化記録単位）
  入力: articleId
  流れ: 記事取得 → DB確認 → ある → 返す / ない → API生成 → DB保存 → 返す

GenerateFeedbackUseCase:
  依存: ArticleRepository + DigestRepository + AiRepository
  AI 入力: article.body（全文）+ question + userMemo
  キャッシュ: なし（毎回生成）
  入力: articleId + question + userMemo
  流れ: 記事取得 → API生成（フィードバック+理解度評価） → DB保存 → 返す
```

### Result を返す UseCase を呼ぶ側（ViewModel）

```kotlin
// DigestViewModel での使い方（イメージ）
viewModelScope.launch {
    generateFeedbackUseCase(articleId, question, memo)
        .onSuccess { result ->
            _uiState.update {
                FeedbackMode(
                    article = ...,
                    question = question,
                    feedback = result.feedback,
                    canDigest = result.isUnderstandingSufficient,
                )
            }
        }
        .onFailure { error ->
            // エラー時はエラー状態に遷移
            _uiState.update { ErrorState(message = error.message) }
        }
}
```

### UI での「消化する」ボタン制御

```kotlin
// DigestScreen での「消化する」ボタン（イメージ）
Button(
    onClick = { viewModel.digestArticle() },
    enabled = uiState.canDigest,  // isUnderstandingSufficient が true のときのみ有効
) {
    Text("消化する")
}

if (!uiState.canDigest) {
    Text(
        text = "もう少し理解を深めてから再挑戦してみましょう！",
        color = MaterialTheme.colorScheme.error,
    )
}
```
