package com.example.oto1720.dojo2026.data.repository

import com.example.oto1720.dojo2026.data.local.dao.DigestDao
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import kotlinx.coroutines.flow.Flow
import com.example.oto1720.dojo2026.util.ISO_8601_FORMAT
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DigestRepositoryImpl @Inject constructor(
    private val digestDao: DigestDao,
) : DigestRepository {

    override suspend fun saveDigest(digest: DigestEntity) = digestDao.insert(digest)

    override fun observeAll(): Flow<List<DigestEntity>> = digestDao.observeAll()

    override suspend fun getByArticleId(articleId: String): DigestEntity? =
        digestDao.getByArticleId(articleId)

    override suspend fun saveQuestion(articleId: String, question: String) {
        val existing = digestDao.getByArticleId(articleId)
        if (existing != null) {
            digestDao.update(existing.copy(aiQuestion = question))
        } else {
            val savedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
            digestDao.insert(DigestEntity(articleId = articleId, aiQuestion = question, savedAt = savedAt))
        }
    }

    override suspend fun saveFeedback(
        articleId: String,
        feedback: String,
        isUnderstandingSufficient: Boolean?,
    ) {
        val existing = digestDao.getByArticleId(articleId)
        if (existing != null) {
            digestDao.update(
                existing.copy(
                    aiFeedback = feedback,
                    isUnderstandingSufficient = isUnderstandingSufficient,
                )
            )
        } else {
            val savedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
            digestDao.insert(
                DigestEntity(
                    articleId = articleId,
                    aiFeedback = feedback,
                    isUnderstandingSufficient = isUnderstandingSufficient,
                    savedAt = savedAt,
                )
            )
        }
    }

}
