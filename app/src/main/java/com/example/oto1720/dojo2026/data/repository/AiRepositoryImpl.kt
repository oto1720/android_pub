package com.example.oto1720.dojo2026.data.repository

import com.example.oto1720.dojo2026.data.remote.api.GeminiApiService
import com.example.oto1720.dojo2026.data.remote.dto.GeminiContentDto
import com.example.oto1720.dojo2026.domain.model.FeedbackResult
import com.example.oto1720.dojo2026.data.remote.dto.GeminiPartDto
import com.example.oto1720.dojo2026.data.remote.dto.GeminiRequestDto
import com.example.oto1720.dojo2026.data.remote.dto.firstText
import com.example.oto1720.dojo2026.domain.model.AppError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepositoryImpl @Inject constructor(
    private val geminiApiService: GeminiApiService,
) : AiRepository {

    override suspend fun generateQuestion(articleContent: String): Result<String> = runCatching {
        val prompt = """
            以下の技術記事を読んだ読者に対して、理解度を確認する質問を日本語で1つ作成してください。
            質問は記事の核心的な内容を問う具体的なものにしてください。
            質問文のみを回答してください。

            記事本文:
            $articleContent
        """.trimIndent()

        val response = geminiApiService.generateContent(
            model = MODEL_NAME,
            request = buildRequest(prompt),
        )
        response.firstText() ?: throw IllegalStateException("AIレスポンスが空です")
    }.recoverCatching { throwable ->
        throw if (throwable is AppError) throwable else AppError.AiError(throwable)
    }

    override suspend fun generateFeedback(
        articleContent: String,
        question: String,
        userMemo: String,
    ): Result<FeedbackResult> = runCatching {
        val prompt = """
            以下の技術記事と質問に対するユーザーの回答を評価してください。

            記事本文:
            $articleContent

            質問: $question
            ユーザーの回答: $userMemo

            評価結果を以下の形式で厳密に出力してください:
            JUDGMENT: OK
            FEEDBACK: <フィードバック本文（日本語・200文字以内・励ましの言葉を含む）>

            理解が不十分な場合は:
            JUDGMENT: NG
            FEEDBACK: <不足点の指摘と再挑戦を促すメッセージ（日本語・200文字以内）>

            JUDGMENTの値は必ず OK か NG のみ出力してください。説明や補足を付けないでください。
        """.trimIndent()

        val response = geminiApiService.generateContent(
            model = MODEL_NAME,
            request = buildRequest(prompt),
        )
        val text = response.firstText() ?: throw IllegalStateException("AIレスポンスが空です")
        parseFeedbackResponse(text)
    }.recoverCatching { throwable ->
        throw if (throwable is AppError) throwable else AppError.AiError(throwable)
    }

    override suspend fun generateSummary(articleContent: String): Result<String> = runCatching {
        val prompt = """
            以下の記事を3行で要約してください。
            各行は簡潔にまとめ、日本語で回答してください。

            記事本文:
            $articleContent
        """.trimIndent()

        val response = geminiApiService.generateContent(
            model = MODEL_NAME,
            request = buildRequest(prompt),
        )
        response.firstText() ?: throw IllegalStateException("AIレスポンスが空です")
    }.recoverCatching { throwable ->
        throw if (throwable is AppError) throwable else AppError.AiError(throwable)
    }

    /**
     * Gemini のレスポンスから JUDGMENT と FEEDBACK をパースする。
     *
     * 期待フォーマット:
     * ```
     * JUDGMENT: OK
     * FEEDBACK: <フィードバック本文>
     * ```
     * パースに失敗した場合は全文をフィードバックとして扱い、理解十分と見なす。
     */
    private fun parseFeedbackResponse(response: String): FeedbackResult {
        val lines = response.lines()
        val judgmentLine = lines.find { it.trimStart().startsWith("JUDGMENT:") }
        val feedbackIndex = lines.indexOfFirst { it.trimStart().startsWith("FEEDBACK:") }

        val isOk = judgmentLine
            ?.substringAfter("JUDGMENT:")
            ?.trim()
            ?.startsWith("OK", ignoreCase = true)
            ?: true

        val feedback = if (feedbackIndex >= 0) {
            val firstLine = lines[feedbackIndex].substringAfter("FEEDBACK:").trim()
            val rest = lines.drop(feedbackIndex + 1).joinToString("\n").trim()
            if (rest.isBlank()) firstLine else "$firstLine\n$rest"
        } else {
            response
        }

        return FeedbackResult(
            feedback = feedback.ifBlank { response },
            isUnderstandingSufficient = isOk,
        )
    }

    private fun buildRequest(prompt: String) = GeminiRequestDto(
        contents = listOf(
            GeminiContentDto(parts = listOf(GeminiPartDto(text = prompt)))
        )
    )

    companion object {
        // gemini-2.0-flash 廃止。v1 API で gemini-2.5-flash を使用（価格・性能バランス良好）
        private const val MODEL_NAME = "gemini-2.5-flash"
    }
}
