package com.example.oto1720.dojo2026.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** ISO 8601 形式の日時フォーマット文字列（UTC）。保存・パース共通で使用する。 */
const val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"

/** ISO 8601 形式の日時文字列を "yyyy/MM/dd" 形式に変換する。パース失敗時は元の文字列を返す。 */
fun formatSavedAt(savedAt: String): String = runCatching {
    val input = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    val output = SimpleDateFormat("yyyy/MM/dd", Locale.JAPAN)
    output.format(input.parse(savedAt)!!)
}.getOrDefault(savedAt)
