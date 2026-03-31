package com.example.oto1720.dojo2026.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ページネーションのキーを保存するエンティティ。
 *
 * [label] は "tag|sortOrder" の形式（例: "null|BY_DATE_DESC", "kotlin|BY_LIKES_DESC"）。
 * [nextPage] が null のとき、そのクエリはページ末尾に達している。
 */
@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val label: String,
    val nextPage: Int?,
)
