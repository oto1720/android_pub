package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * トレンド記事を取得するUseCase。
 *
 * - tag = null のとき: Qiita トレンド記事を取得
 * - tag 指定のとき: Qiita API に "tag:{tagName}" クエリを送信し、
 *   さらに取得結果をクライアント側でも完全一致フィルタリングする
 *
 * NOTE: クライアント側フィルタリングを行う理由:
 * Qiita API の "tag:xxx" クエリは部分一致を含む場合があるため、
 * 厳密な完全一致フィルタリングをクライアント側で再度実施している。
 */
class GetTrendArticlesUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {

    /**
     * @param tag フィルタリングするタグ名（例: "Android"）。nullの場合はトレンド全体を取得。
     * @return 記事リスト。APIエラー時は [Result.failure]。
     */
    suspend operator fun invoke(tag: String? = null): Result<List<ArticleEntity>> {
        val refreshResult = repository.refreshPortalArticles(query = tag?.let { "tag:$it" })
        if (refreshResult.isFailure) {
            @Suppress("UNCHECKED_CAST")
            return refreshResult as Result<List<ArticleEntity>>
        }

        return try {
            val articles = repository.observePortalArticles().first()
            val filtered = if (tag != null) {
                articles.filter { article ->
                    article.tags.split(",").any { t ->
                        t.trim().equals(tag, ignoreCase = true)
                    }
                }
            } else {
                articles
            }
            Result.success(filtered)
        } catch (e: CancellationException) {
            throw e  // コルーチンキャンセルは伝播させる
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
