package com.example.oto1720.dojo2026.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.example.oto1720.dojo2026.data.local.dao.ArticleDao
import com.example.oto1720.dojo2026.data.local.dao.RemoteKeyDao
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.local.entity.RemoteKeyEntity
import com.example.oto1720.dojo2026.data.remote.api.QiitaApiService
import com.example.oto1720.dojo2026.data.remote.dto.toEntityOrNull
import com.example.oto1720.dojo2026.domain.model.toAppError
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalPagingApi::class)
class QiitaRemoteMediator(
    private val tag: String?,
    private val apiService: QiitaApiService,
    private val articleDao: ArticleDao,
    private val remoteKeyDao: RemoteKeyDao,
) : RemoteMediator<Int, ArticleEntity>() {

    /** このクエリを一意に識別するラベル（RemoteKeyEntity の主キーとして使用） */
    private val label = "${tag}|PORTAL"

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ArticleEntity>,
    ): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> FIRST_PAGE
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                val remoteKey = remoteKeyDao.remoteKeyByLabel(label)
                remoteKey?.nextPage ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
        }

        return try {
            val cachedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())

            val dtos = apiService.getItems(
                page = page,
                perPage = state.config.pageSize,
                query = tag?.let { "tag:$it" },
            )

            val entities = dtos.mapNotNull { dto ->
                dto.toEntityOrNull(status = ArticleStatus.PORTAL, cachedAt = cachedAt)
            }

            if (loadType == LoadType.REFRESH) {
                // タグが切り替わった場合も含め、既存の PORTAL 記事を削除
                articleDao.deleteByStatus(ArticleStatus.PORTAL)
                remoteKeyDao.deleteByLabel(label)
            }

            val protectedIds = articleDao.getIdsByStatuses(
                listOf(ArticleStatus.TSUNDOKU, ArticleStatus.DONE),
            ).toSet()
            articleDao.insertAll(entities.filter { it.id !in protectedIds })

            val nextPage = if (entities.isEmpty()) null else page + 1
            remoteKeyDao.insertOrReplace(RemoteKeyEntity(label = label, nextPage = nextPage))

            MediatorResult.Success(endOfPaginationReached = entities.isEmpty())
        } catch (e: Exception) {
            MediatorResult.Error(e.toAppError())
        }
    }

    companion object {
        private const val FIRST_PAGE = 1
        private const val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }
}
