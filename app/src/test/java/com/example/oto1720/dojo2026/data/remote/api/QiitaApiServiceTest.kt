package com.example.oto1720.dojo2026.data.remote.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class QiitaApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: QiitaApiService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(QiitaApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }
    
    @Test
    fun getItems_正常レスポンスをパースできる() = runTest {
        val json = """
            [{
                "id": "abc123",
                "title": "テスト記事",
                "url": "https://qiita.com/test",
                "tags": [{"name": "Kotlin", "versions": []}],
                "created_at": "2024-01-01T00:00:00+09:00",
                "likes_count": 10,
                "user": {"id": "testuser", "profile_image_url": "https://example.com/img.png"}
            }]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiService.getItems(page = 1, perPage = 20, query = null)

        assertEquals(1, result.size)
        assertEquals("abc123", result[0].id)
        assertEquals("テスト記事", result[0].title)
        assertEquals("Kotlin", result[0].tags[0].name)
    }

    @Test
    fun getItems_空のtagsをパースできる() = runTest {
        val json = """
            [{
                "id": "xyz",
                "title": "タグなし記事",
                "url": "https://qiita.com/xyz",
                "tags": [],
                "created_at": "2024-01-01T00:00:00+09:00",
                "likes_count": 0,
                "user": null
            }]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiService.getItems(page = 1, perPage = 20, query = null)

        assertEquals(1, result.size)
        assertEquals(0, result[0].tags.size)
    }
   
}
