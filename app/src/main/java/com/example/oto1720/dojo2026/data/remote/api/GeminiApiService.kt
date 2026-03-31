package com.example.oto1720.dojo2026.data.remote.api

import com.example.oto1720.dojo2026.data.remote.dto.GeminiRequestDto
import com.example.oto1720.dojo2026.data.remote.dto.GeminiResponseDto
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApiService {

    /**
     * Gemini API でテキストを生成する。
     *
     * POST https://generativelanguage.googleapis.com/v1/models/{model}:generateContent
     *
     * - APIキーは OkHttp インターセプターで自動付与される（クエリパラメータ ?key=...）
     * - v1 は安定版。v1beta は新機能先行のため 404 になりやすい
     */
    @POST("v1/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiRequestDto,
    ): GeminiResponseDto
}
