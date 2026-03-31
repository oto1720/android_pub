package com.example.oto1720.dojo2026.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "digests",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,
            parentColumns = ["id"],
            childColumns = ["articleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["articleId"])],
)
data class DigestEntity(
    @PrimaryKey val articleId: String,
    val aiQuestion: String? = null,
    val userMemo: String? = null,
    val aiFeedback: String? = null,
    val isUnderstandingSufficient: Boolean? = null,
    val savedAt: String,
)
