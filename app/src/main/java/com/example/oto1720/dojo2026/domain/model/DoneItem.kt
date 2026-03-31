package com.example.oto1720.dojo2026.domain.model

/** 消化済み記事と消化記録を結合した UI モデル */
data class DoneItem(
    val articleId: String,
    val title: String,
    val url: String,
    val tags: String,
    val savedAt: String,
    val userMemo: String,
    val aiFeedback: String,
)
