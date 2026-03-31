package com.example.oto1720.dojo2026.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Qiita Article DTO.
 *
 * フィールドは nullable にしています。GsonはKotlinのnull安全を無視するため、
 * APIレスポンスに予期しないnullが含まれる場合のNPEを防ぐためです。
 */
data class QiitaArticleDto(
    val id: String?,
    val title: String?,
    val url: String?,
    val body: String?,
    val tags: List<TagDto> = emptyList(),
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("likes_count") val likesCount: Int?,
    val user: UserDto?,
)

data class TagDto(
    val name: String?,
    val versions: List<String> = emptyList(),
)

data class UserDto(
    val id: String?,
    @SerializedName("profile_image_url") val profileImageUrl: String?,
)
