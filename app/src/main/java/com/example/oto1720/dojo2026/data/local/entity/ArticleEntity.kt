package com.example.oto1720.dojo2026.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val body: String? = null,
    val tags: String,
    val createdAt: String,
    val cachedAt: String,
    val status: String,
    val aiSummary: String? = null,
    val likesCount: Int = 0,
)
