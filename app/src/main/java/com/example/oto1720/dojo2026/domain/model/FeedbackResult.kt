package com.example.oto1720.dojo2026.domain.model

/**
 * AI フィードバック生成の結果。
 *
 * @param feedback AI が生成したフィードバック本文
 * @param isUnderstandingSufficient 理解度が十分かどうか（true: 消化ボタン有効、false: 再挑戦を促す）
 */
data class FeedbackResult(
    val feedback: String,
    val isUnderstandingSufficient: Boolean,
)
