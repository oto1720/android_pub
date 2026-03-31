package com.example.oto1720.dojo2026.data.remote.api

import com.example.oto1720.dojo2026.data.remote.dto.QiitaArticleDto
import retrofit2.http.GET
import retrofit2.http.Query

interface QiitaApiService {

    /**
     * 記事一覧を取得する。
     *
     * Qiita API v2: GET /api/v2/items
     * - page: 1〜100（デフォルト: 1）
     * - perPage: 1〜100（デフォルト: 20）
     * - query: 検索クエリ（例: "tag:android"）
     *
     * Note: Retrofitのプロキシ実装のため、デフォルト引数はインターフェースに定義せず
     * 呼び出し側（Repository）で管理してください。
     */
    @GET("api/v2/items")
    suspend fun getItems(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("query") query: String?,
    ): List<QiitaArticleDto>
}
