package com.example.oto1720.dojo2026.data.repository

import com.example.oto1720.dojo2026.domain.model.FeedbackResult

interface AiRepository {

    /**
     * 記事本文をもとに理解度確認の問いを生成する。
     *
     * @param articleContent 記事本文（body）。null の場合はタイトルなど代替テキストを渡す。
     * @return 生成された問い文字列。エラー時は [Result.failure]（タイムアウト・レートリミット含む）
     */
    suspend fun generateQuestion(articleContent: String): Result<String>

    /**
     * 記事本文・問い・ユーザーメモをもとにフィードバックと理解度判定を生成する。
     *
     * @param articleContent 記事本文（body）。null の場合はタイトルなど代替テキストを渡す。
     * @param question AI が生成した問い
     * @param userMemo ユーザーが入力したメモ
     * @return [FeedbackResult]（フィードバック本文と理解度判定）。エラー時は [Result.failure]
     */
    suspend fun generateFeedback(
        articleContent: String,
        question: String,
        userMemo: String,
    ): Result<FeedbackResult>

    /**
     * 記事本文をもとに3行要約を生成する。
     *
     * @param articleContent 記事本文（body）。null の場合はタイトルなど代替テキストを渡す。
     * @return 生成された要約文字列。エラー時は [Result.failure]
     */
    suspend fun generateSummary(articleContent: String): Result<String>
}
