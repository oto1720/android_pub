package com.example.oto1720.dojo2026.data.remote.dto

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity

/**
 * 必須フィールドにnullが含まれる場合はnullを返す。
 * 空文字列のid/title/urlをDBに混入させないためのガード。
 */
fun QiitaArticleDto.toEntityOrNull(status: String, cachedAt: String): ArticleEntity? {
    val safeId = id ?: return null
    val safeTitle = title ?: return null
    val safeUrl = url ?: return null
    val safeCreatedAt = createdAt ?: return null
    return ArticleEntity(
        id = safeId,
        title = safeTitle,
        url = safeUrl,
        body = body?.take(BODY_MAX_LENGTH),
        tags = tags.mapNotNull { it.name }.joinToString(","),
        createdAt = safeCreatedAt,
        cachedAt = cachedAt,
        status = status,
        likesCount = likesCount ?: 0,
    )
}

private const val BODY_MAX_LENGTH = 10_000
