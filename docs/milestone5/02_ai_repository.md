# AiRepository 実装（issue #22）

## このドキュメントで学ぶこと

- `runCatching` を使ったエラーハンドリング
- `Result<T>` 型の設計と使い方
- プロンプトエンジニアリングの実装パターン
- `trimIndent()` で複数行文字列をきれいに書く方法
- 構造化レスポンスのパース（JUDGMENT / FEEDBACK フォーマット）

---

## 1. AiRepository インターフェース

```kotlin
// data/repository/AiRepository.kt

/** フィードバックと理解度評価をペアで保持するデータクラス */
data class FeedbackResult(
    val feedback: String,
    val isUnderstandingSufficient: Boolean,
)

interface AiRepository {

    /** 記事本文をもとに理解度確認の問いを生成する */
    suspend fun generateQuestion(articleBody: String): Result<String>

    /** 記事本文・問い・ユーザーメモをもとにフィードバックと理解度評価を生成する */
    suspend fun generateFeedback(
        articleBody: String,
        question: String,
        userMemo: String,
    ): Result<FeedbackResult>

    /** 記事本文をもとに要約を生成する */
    suspend fun generateSummary(articleBody: String): Result<String>
}
```

### 仕様変更: タイトル → 全文

Milestone 5 の初期実装では `articleTitle` を受け取っていましたが、仕様変更により **`articleBody`（記事全文）** を受け取るように変更しました。

| 関数 | 変更前 | 変更後 |
|------|--------|--------|
| `generateQuestion` | `articleTitle: String` | `articleBody: String` |
| `generateSummary` | `articleTitle: String` | `articleBody: String` |
| `generateFeedback` | `question, userMemo` | `articleBody, question, userMemo` |

### FeedbackResult — なぜ文字列でなくデータクラスを返すか

フィードバックには「テキスト」と「理解度評価（OK/NG）」の2つの情報が含まれます。
これを1つにまとめる `FeedbackResult` を返すことで、UseCase 側が両方の値を安全に受け取れます。

```kotlin
// UseCase 側での扱い方
aiRepository.generateFeedback(body, question, memo)
    .onSuccess { result ->
        val feedback = result.feedback
        val canDigest = result.isUnderstandingSufficient
        // canDigest に基づいてボタンの有効/無効を制御
    }
    .onFailure { error -> /* 失敗処理 */ }
```

---

## 2. AiRepositoryImpl — プロンプトの組み立て

```kotlin
// data/repository/AiRepositoryImpl.kt
@Singleton
class AiRepositoryImpl @Inject constructor(
    private val geminiApiService: GeminiApiService,
) : AiRepository {

    override suspend fun generateQuestion(articleBody: String): Result<String> = runCatching {
        val truncatedBody = articleBody.take(MAX_BODY_LENGTH)
        val prompt = """
            以下の技術記事を読んだ読者に対して、理解度を確認する質問を日本語で1つ作成してください。
            質問は記事の核心的な内容・実装方法・概念理解を問う具体的なものにしてください。
            タイトルだけでなく記事本文の内容に基づいた質問を作成してください。
            質問文のみを回答してください。

            記事本文:
            $truncatedBody
        """.trimIndent()

        val response = geminiApiService.generateContent(
            model = MODEL_NAME,
            request = buildRequest(prompt),
        )
        response.firstText() ?: throw IllegalStateException("AIレスポンスが空です")
    }

    override suspend fun generateFeedback(
        articleBody: String,
        question: String,
        userMemo: String,
    ): Result<FeedbackResult> = runCatching {
        val truncatedBody = articleBody.take(MAX_BODY_LENGTH)
        val prompt = """
            以下の技術記事の内容・質問・ユーザーの回答を評価してください。

            評価の基準:
            - 記事の核心的な概念を理解しているか
            - 質問に対する回答が記事の内容と整合しているか

            出力形式（必ずこの形式で回答してください）:
            JUDGMENT: OK
            FEEDBACK: <フィードバック本文>

            または理解が不十分な場合:
            JUDGMENT: NG
            FEEDBACK: <不足している点の指摘と再挑戦を促すメッセージ>

            フィードバックは励ましの言葉を含め、200文字以内にしてください。

            記事本文:
            $truncatedBody

            質問: $question
            ユーザーの回答: $userMemo
        """.trimIndent()

        val response = geminiApiService.generateContent(
            model = MODEL_NAME,
            request = buildRequest(prompt),
        )
        val text = response.firstText() ?: throw IllegalStateException("AIレスポンスが空です")
        parseFeedbackResponse(text)
    }

    override suspend fun generateSummary(articleBody: String): Result<String> = runCatching {
        val truncatedBody = articleBody.take(MAX_BODY_LENGTH)
        val prompt = """
            以下の技術記事を読んで、重要なポイントを3行で要約してください。
            各行は「- 」で始め、記事の核心的な概念・手法・結論を含めてください。
            日本語で回答してください。

            記事本文:
            $truncatedBody
        """.trimIndent()

        val response = geminiApiService.generateContent(
            model = MODEL_NAME,
            request = buildRequest(prompt),
        )
        response.firstText() ?: throw IllegalStateException("AIレスポンスが空です")
    }

    companion object {
        private const val MODEL_NAME = "gemini-2.5-flash"
        private const val MAX_BODY_LENGTH = 10_000  // トークン節約のため先頭10,000文字に制限
    }
}
```

---

## 3. `parseFeedbackResponse` — 構造化レスポンスのパース

Gemini は以下の形式で回答することを期待します:

```
JUDGMENT: OK
FEEDBACK: Kotlinのコルーチンについて正しく理解できています！suspend関数の仕組みをさらに深掘りすると、より実践的なコードが書けるようになります。
```

これを `FeedbackResult` に変換するパース関数:

```kotlin
private fun parseFeedbackResponse(text: String): FeedbackResult {
    val lines = text.lines()
    val judgmentLine = lines.firstOrNull { it.startsWith("JUDGMENT:") }
    val feedbackLine = lines.firstOrNull { it.startsWith("FEEDBACK:") }

    val isOk = judgmentLine?.substringAfter("JUDGMENT:")?.trim() == "OK"
    val feedback = feedbackLine?.substringAfter("FEEDBACK:")?.trim()
        ?: text  // パース失敗時はテキスト全体をフィードバックとして使用

    return FeedbackResult(
        feedback = feedback,
        isUnderstandingSufficient = isOk,
    )
}
```

### パース失敗時のフォールバック

Gemini が指定フォーマット通りに回答しない場合（稀）のフォールバック:

```
JUDGMENT 行が見つからない → isUnderstandingSufficient = false（保守的判定）
FEEDBACK 行が見つからない → テキスト全体をフィードバックとして使用
```

---

## 4. `runCatching` — 例外を Result に変換する

```kotlin
// runCatching の仕組み
runCatching {
    // このブロック内で例外が発生したら...
    val response = geminiApiService.generateContent(...)
    response.firstText() ?: throw IllegalStateException("空です")
}
// → 成功: Result.success(text)
// → 失敗: Result.failure(exception)
```

`runCatching` を使うことで `try-catch` のネストが不要になり、コードがすっきりします。

### タイムアウト・レートリミットも自動でキャッチ

| 例外の種類 | 原因 |
|-----------|------|
| `SocketTimeoutException` | 60秒タイムアウト（NetworkModule で設定） |
| `HttpException` | HTTP 429（レートリミット）、HTTP 500 等 |
| `IOException` | ネットワーク接続エラー |
| `IllegalStateException` | `firstText()` が null（レスポンスが空） |

---

## 5. プロンプトの設計

### `trimIndent()` で複数行文字列を整形

```kotlin
val prompt = """
    以下の技術記事を読んで...
    記事本文:
    $truncatedBody
""".trimIndent()
```

`"""..."""` は Kotlin のトリプルクォート文字列です。
`trimIndent()` を付けることで「先頭の共通インデント」を除去し、きれいな文字列になります。

### 3つのプロンプトの比較

| UseCase | プロンプトの入力 | 出力形式 |
|---------|---------------|---------|
| 問い生成 | 記事全文（最大10,000文字） | 質問文のみ |
| フィードバック生成 | 記事全文 + 問い + ユーザー回答 | `JUDGMENT: OK/NG\nFEEDBACK: <text>` |
| 要約生成 | 記事全文（最大10,000文字） | `- 行1\n- 行2\n- 行3` |

### `MAX_BODY_LENGTH = 10_000` の意図

Gemini 2.5 Flash は大きなコンテキストウィンドウを持ちますが、
長い本文をそのまま渡すとトークン消費・レイテンシが増加します。
Qiita の記事本文はほとんどが10,000文字以下であるため、この制限は実用上ほぼ影響しません。

---

## 6. モデル名の選択

```kotlin
companion object {
    // gemini-2.5-flash: 軽量・高速・コスパ良好
    private const val MODEL_NAME = "gemini-2.5-flash"
}
```

- `gemini-2.5-flash` は軽量・高速モデルで、要約・問い生成・フィードバックのような短いタスクに適しています
- `v1` エンドポイントは安定版。`v1beta` は新機能先行のため `404` になる場合があります
