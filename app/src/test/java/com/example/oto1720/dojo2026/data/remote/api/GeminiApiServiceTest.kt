package com.example.oto1720.dojo2026.data.remote.api

import com.example.oto1720.dojo2026.data.remote.dto.GeminiContentDto
import com.example.oto1720.dojo2026.data.remote.dto.GeminiPartDto
import com.example.oto1720.dojo2026.data.remote.dto.GeminiRequestDto
import com.example.oto1720.dojo2026.data.remote.dto.firstText
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class GeminiApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: GeminiApiService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(GeminiApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    private fun buildRequest(prompt: String) = GeminiRequestDto(
        contents = listOf(
            GeminiContentDto(parts = listOf(GeminiPartDto(text = prompt)))
        )
    )

    // --- レスポンスのパース ---

    @Test
    fun `generateContent - 正常レスポンスをパースできる`() = runTest {
        val json = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [{ "text": "AIの回答テキスト" }],
                    "role": "model"
                  },
                  "finishReason": "STOP",
                  "index": 0
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 10,
                "candidatesTokenCount": 5,
                "totalTokenCount": 15
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiService.generateContent("gemini-2.0-flash", buildRequest("テスト"))

        assertNotNull(result.candidates)
        assertEquals(1, result.candidates!!.size)
        assertEquals("AIの回答テキスト", result.candidates[0].content?.parts?.first()?.text)
    }

    @Test
    fun `generateContent - firstText()でテキストを取り出せる`() = runTest {
        val json = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [{ "text": "生成されたテキスト" }],
                    "role": "model"
                  },
                  "finishReason": "STOP",
                  "index": 0
                }
              ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiService.generateContent("gemini-2.0-flash", buildRequest("prompt"))

        assertEquals("生成されたテキスト", result.firstText())
    }

    @Test
    fun `generateContent - candidatesが空のときfirstTextはnullを返す`() = runTest {
        val json = """{ "candidates": [] }""".trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiService.generateContent("gemini-2.0-flash", buildRequest("prompt"))

        assertNull(result.firstText())
    }

    @Test
    fun `generateContent - usageMetadataのtotalTokenCountが正しい`() = runTest {
        val json = """
            {
              "candidates": [
                { "content": { "parts": [{ "text": "ok" }], "role": "model" }, "finishReason": "STOP", "index": 0 }
              ],
              "usageMetadata": { "promptTokenCount": 4, "candidatesTokenCount": 2, "totalTokenCount": 6 }
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiService.generateContent("gemini-2.0-flash", buildRequest("prompt"))

        assertEquals(6, result.usageMetadata?.totalTokenCount)
    }

}
