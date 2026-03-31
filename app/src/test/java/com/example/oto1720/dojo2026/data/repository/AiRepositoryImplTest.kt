package com.example.oto1720.dojo2026.data.repository

import com.example.oto1720.dojo2026.data.remote.api.GeminiApiService
import com.example.oto1720.dojo2026.data.remote.dto.GeminiCandidateDto
import com.example.oto1720.dojo2026.data.remote.dto.GeminiContentDto
import com.example.oto1720.dojo2026.data.remote.dto.GeminiPartDto
import com.example.oto1720.dojo2026.data.remote.dto.GeminiResponseDto
import com.example.oto1720.dojo2026.domain.model.AppError
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class AiRepositoryImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var geminiApiService: GeminiApiService

    private val repository by lazy { AiRepositoryImpl(geminiApiService) }

    private fun buildResponse(text: String) = GeminiResponseDto(
        candidates = listOf(
            GeminiCandidateDto(
                content = GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = text)),
                    role = "model",
                ),
                finishReason = "STOP",
                index = 0,
            )
        ),
        usageMetadata = null,
    )

    private val emptyResponse = GeminiResponseDto(candidates = emptyList(), usageMetadata = null)

    // --- generateQuestion ---

    @Test
    fun `generateQuestion - 成功時にResult_successで問いが返る`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } returns buildResponse("テスト問い")

        val result = repository.generateQuestion("テスト記事タイトル")

        assertTrue(result.isSuccess)
        assertEquals("テスト問い", result.getOrNull())
    }

    @Test
    fun `generateQuestion - candidatesが空のときResult_failureが返る`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } returns emptyResponse

        val result = repository.generateQuestion("タイトル")

        assertTrue(result.isFailure)
    }

    @Test
    fun `generateQuestion - ネットワークエラー時にResult_failureでAppError_AiErrorが返る`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } throws IOException("timeout")

        val result = repository.generateQuestion("タイトル")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.AiError)
    }

    @Test
    fun `generateQuestion - レートリミットエラー時にResult_failureが返る`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } throws
            retrofit2.HttpException(
                retrofit2.Response.error<Any>(429, "rate limit".toResponseBody(null))
            )

        val result = repository.generateQuestion("タイトル")

        assertTrue(result.isFailure)
    }

    // --- generateFeedback ---

    @Test
    fun `generateFeedback - 成功時にResult_successでFeedbackResultが返る`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } returns buildResponse("JUDGMENT: OK\nFEEDBACK: テストフィードバック")

        val result = repository.generateFeedback("記事本文", "問い", "メモ")

        assertTrue(result.isSuccess)
        assertEquals("テストフィードバック", result.getOrNull()?.feedback)
        assertEquals(true, result.getOrNull()?.isUnderstandingSufficient)
    }

    @Test
    fun `generateFeedback - candidatesが空のときResult_failureが返る`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } returns emptyResponse

        val result = repository.generateFeedback("記事本文", "問い", "メモ")

        assertTrue(result.isFailure)
    }

    @Test
    fun `generateFeedback - ネットワークエラー時にResult_failureでAppError_AiErrorが返る`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } throws IOException("connection refused")

        val result = repository.generateFeedback("記事本文", "問い", "メモ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AppError.AiError)
    }

    @Test
    fun `generateFeedback - レートリミットエラー時にResult_failureが返る`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } throws
            retrofit2.HttpException(
                retrofit2.Response.error<Any>(429, "rate limit".toResponseBody(null))
            )

        val result = repository.generateFeedback("記事本文", "問い", "メモ")

        assertTrue(result.isFailure)
    }

    @Test
    fun `generateFeedback - JUDGMENT_NGの場合isUnderstandingSufficientがfalseになる`() = runTest {
        coEvery { geminiApiService.generateContent(any(), any()) } returns
            buildResponse("JUDGMENT: NG\nFEEDBACK: 理解が不十分です。再挑戦しましょう。")

        val result = repository.generateFeedback("記事本文", "問い", "メモ")

        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull()?.isUnderstandingSufficient)
        assertEquals("理解が不十分です。再挑戦しましょう。", result.getOrNull()?.feedback)
    }
}
