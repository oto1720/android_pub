package com.example.oto1720.dojo2026.data.remote.dto

import com.google.gson.annotations.SerializedName

// ---- Request ----

data class GeminiRequestDto(
    val contents: List<GeminiContentDto>,
)

data class GeminiContentDto(
    val parts: List<GeminiPartDto>,
    val role: String? = null,
)

data class GeminiPartDto(
    val text: String,
)

// ---- Response ----

data class GeminiResponseDto(
    val candidates: List<GeminiCandidateDto>?,
    @SerializedName("usageMetadata") val usageMetadata: GeminiUsageMetadataDto?,
)

data class GeminiCandidateDto(
    val content: GeminiContentDto?,
    @SerializedName("finishReason") val finishReason: String?,
    val index: Int?,
)

data class GeminiUsageMetadataDto(
    @SerializedName("promptTokenCount") val promptTokenCount: Int?,
    @SerializedName("candidatesTokenCount") val candidatesTokenCount: Int?,
    @SerializedName("totalTokenCount") val totalTokenCount: Int?,
)

/**
 * レスポンスから最初の候補のテキストを取り出すヘルパー。
 * 候補が存在しない場合は null を返す。
 */
fun GeminiResponseDto.firstText(): String? =
    candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
