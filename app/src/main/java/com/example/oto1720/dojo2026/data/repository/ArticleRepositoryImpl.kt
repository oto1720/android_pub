package com.example.oto1720.dojo2026.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.example.oto1720.dojo2026.data.local.dao.ArticleDao
import com.example.oto1720.dojo2026.data.local.dao.RemoteKeyDao
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.paging.QiitaRemoteMediator
import com.example.oto1720.dojo2026.data.remote.api.QiitaApiService
import com.example.oto1720.dojo2026.data.remote.dto.toEntityOrNull
import com.example.oto1720.dojo2026.domain.model.SortOrder
import com.example.oto1720.dojo2026.domain.model.toAppError
import kotlinx.coroutines.flow.Flow
import com.example.oto1720.dojo2026.util.ISO_8601_FORMAT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleRepositoryImpl @Inject constructor(
    private val apiService: QiitaApiService,
    private val articleDao: ArticleDao,
    private val remoteKeyDao: RemoteKeyDao,
) : ArticleRepository {

    override fun observePortalArticles(): Flow<List<ArticleEntity>> =
        articleDao.observeByStatus(ArticleStatus.PORTAL)

    @OptIn(ExperimentalPagingApi::class)
    override fun getPortalPager(tag: String?, sortOrder: SortOrder): Flow<PagingData<ArticleEntity>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = QiitaRemoteMediator(
                tag = tag,
                apiService = apiService,
                articleDao = articleDao,
                remoteKeyDao = remoteKeyDao,
            ),
            pagingSourceFactory = {
                when (sortOrder) {
                    SortOrder.BY_DATE_DESC -> articleDao.pagingSourceByDateDesc(ArticleStatus.PORTAL)
                    SortOrder.BY_LIKES_DESC -> articleDao.pagingSourceByLikesDesc(ArticleStatus.PORTAL)
                }
            },
        ).flow

    override fun observePortalTagStrings(): Flow<List<String>> =
        articleDao.observeTagStrings(ArticleStatus.PORTAL)

    override fun observeTsundokuArticles(): Flow<List<ArticleEntity>> =
        articleDao.observeByStatus(ArticleStatus.TSUNDOKU)

    override fun observeDoneArticles(): Flow<List<ArticleEntity>> =
        articleDao.observeByStatus(ArticleStatus.DONE)

    override suspend fun refreshPortalArticles(query: String?): Result<Unit> = runCatching {
        // SimpleDateFormat はスレッドセーフでないため毎回インスタンス化する
        val cachedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        val entities = apiService.getItems(
            page = DEFAULT_PAGE,
            perPage = DEFAULT_PER_PAGE,
            query = query,
        ).mapNotNull { dto ->
            dto.toEntityOrNull(status = ArticleStatus.PORTAL, cachedAt = cachedAt)
        }

        // 積読・消化済みの記事は上書きしない（ユーザーが追加したデータを保護）
        val protectedIds = articleDao.getIdsByStatuses(
            listOf(ArticleStatus.TSUNDOKU, ArticleStatus.DONE),
        ).toSet()
        val entitiesToInsert = entities.filter { it.id !in protectedIds }

        // 古いPORTAL記事を削除してから挿入（データ蓄積を防ぐ）
        // TODO: deleteByStatus + insertAll をトランザクションで包む（将来対応）
        articleDao.deleteByStatus(ArticleStatus.PORTAL)
        articleDao.insertAll(entitiesToInsert)
    }.recoverCatching { throwable ->
        // HTTP ステータスコード・ネットワーク種別に応じた AppError に変換して再スロー
        throw throwable.toAppError()
    }

    override suspend fun getArticleById(id: String): ArticleEntity? =
        articleDao.getById(id)

    override suspend fun updateStatus(id: String, status: String) {
        articleDao.updateStatus(id, status)
    }

    override suspend fun updateStatusIfSlotAvailable(
        id: String,
        newStatus: String,
        slotCountStatus: String,
        maxSlots: Int,
    ): Boolean = articleDao.updateStatusIfSlotAvailable(
        articleId = id,
        newStatus = newStatus,
        slotCountStatus = slotCountStatus,
        maxSlots = maxSlots,
    ) > 0

    override suspend fun saveSummary(id: String, summary: String) {
        articleDao.updateSummary(id, summary)
    }

    companion object {
        private const val DEFAULT_PAGE = 1
        private const val DEFAULT_PER_PAGE = 20
        private const val PAGE_SIZE = 20
    }
}
