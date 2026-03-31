package com.example.oto1720.dojo2026.domain.model

import retrofit2.HttpException
import java.io.IOException

/**
 * アプリ全体で統一されたエラー型。
 *
 * Repository 層で [Throwable] をこの型にマッピングし、
 * ViewModel / UI 層でエラー種別に応じたメッセージを表示する。
 */
sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 401 Unauthorized — APIキーが無効 */
    data object Unauthorized : AppError("APIキーが無効です")

    /** 403 Forbidden — アクセスが拒否された */
    data object Forbidden : AppError("アクセスが拒否されました")

    /** 429 Too Many Requests — レート制限超過 */
    data object RateLimitExceeded : AppError("リクエスト制限に達しました。しばらく待ってから再試行してください")

    /** ネットワーク接続エラー（タイムアウト・DNS解決失敗など） */
    class NetworkError(cause: Throwable? = null) : AppError("ネットワーク接続エラーが発生しました", cause)

    /** Gemini AI API エラー */
    class AiError(cause: Throwable? = null) : AppError("AI機能でエラーが発生しました", cause)

    /** 上記に分類できないその他のエラー */
    class UnknownError(cause: Throwable? = null) : AppError(cause?.message ?: "不明なエラーが発生しました", cause)
}

/**
 * [Throwable] を [AppError] にマッピングする。
 * すでに [AppError] であればそのまま返す。
 * HTTP ステータスコードに応じて種別を分類する。
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is HttpException -> when (code()) {
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        429 -> AppError.RateLimitExceeded
        else -> AppError.NetworkError(this)
    }
    is IOException -> AppError.NetworkError(this)
    else -> AppError.UnknownError(this)
}
