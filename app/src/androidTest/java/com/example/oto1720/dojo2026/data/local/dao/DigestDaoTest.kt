package com.example.oto1720.dojo2026.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oto1720.dojo2026.data.local.AppDatabase
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DigestDao の実動テスト。
 *
 * ここで守るもの:
 * - insert / getByArticleId の基本動作
 * - ArticleEntity の CASCADE DELETE（記事削除 → 消化記録も消える）
 * - observeAll の Flow が変更を検知すること
 *
 * DigestEntity は ArticleEntity への外部キーを持つため、
 * 記事を先に insert しないと制約違反になる点を考慮している。
 */
@RunWith(AndroidJUnit4::class)
class DigestDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var articleDao: ArticleDao
    private lateinit var digestDao: DigestDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        articleDao = database.articleDao()
        digestDao = database.digestDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun buildArticle(id: String) = ArticleEntity(
        id = id,
        title = "title_$id",
        url = "https://example.com/$id",
        tags = "android",
        createdAt = "2026-03-05T00:00:00Z",
        cachedAt = "2026-03-05T00:00:00Z",
        status = ArticleStatus.TSUNDOKU,
    )

    private fun buildDigest(
        articleId: String,
        memo: String = "メモ",
        feedback: String = "フィードバック",
    ) = DigestEntity(
        articleId = articleId,
        userMemo = memo,
        aiFeedback = feedback,
        savedAt = "2026-03-05T10:00:00Z",
    )

    // --- insert / getByArticleId ---

    @Test
    fun `insert後にgetByArticleIdで取得できる`() = runTest {
        // Arrange
        articleDao.insert(buildArticle("a1"))

        // Act
        digestDao.insert(buildDigest("a1"))

        // Assert
        val result = digestDao.getByArticleId("a1")
        assertNotNull(result)
        assertEquals("a1", result?.articleId)
        assertEquals("メモ", result?.userMemo)
        assertEquals("フィードバック", result?.aiFeedback)
    }

    @Test
    fun `存在しないarticleIdはgetByArticleIdでnullを返す`() = runTest {
        val result = digestDao.getByArticleId("unknown")
        assertNull(result)
    }

    // --- update ---

    @Test
    fun `update後にgetByArticleIdで更新された値が取得できる`() = runTest {
        // Arrange
        articleDao.insert(buildArticle("a1"))
        digestDao.insert(buildDigest("a1", memo = "古いメモ"))

        // Act
        val updated = buildDigest("a1", memo = "新しいメモ")
        digestDao.update(updated)

        // Assert
        assertEquals("新しいメモ", digestDao.getByArticleId("a1")?.userMemo)
    }

    // --- delete ---

    @Test
    fun `delete後にgetByArticleIdでnullを返す`() = runTest {
        // Arrange
        articleDao.insert(buildArticle("a1"))
        val digest = buildDigest("a1")
        digestDao.insert(digest)

        // Act
        digestDao.delete(digest)

        // Assert
        assertNull(digestDao.getByArticleId("a1"))
    }

    // --- 外部キー CASCADE ---

    @Test
    fun `記事を削除するとDigestEntityも自動で削除される`() = runTest {
        // Arrange: 記事と消化記録を両方 insert
        val article = buildArticle("a1")
        articleDao.insert(article)
        digestDao.insert(buildDigest("a1"))
        assertNotNull(digestDao.getByArticleId("a1")) // 前提確認

        // Act: 記事を削除
        articleDao.delete(article)

        // Assert: CASCADE により消化記録も消えること
        assertNull(digestDao.getByArticleId("a1"))
    }

    // --- observeAll (Flow) ---

    @Test
    fun `insert後にobserveAllのFlowに流れる`() = runTest {
        // Arrange
        articleDao.insert(buildArticle("a1"))

        // Act
        digestDao.insert(buildDigest("a1"))

        // Assert
        val result = digestDao.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("a1", result[0].articleId)
    }

    @Test
    fun `複数insertするとobserveAllが全件返す`() = runTest {
        // Arrange
        articleDao.insert(buildArticle("a1"))
        articleDao.insert(buildArticle("a2"))

        // Act
        digestDao.insert(buildDigest("a1"))
        digestDao.insert(buildDigest("a2"))

        // Assert
        val result = digestDao.observeAll().first()
        assertEquals(2, result.size)
    }

    @Test
    fun `insert前はobserveAllが空リストを返す`() = runTest {
        val result = digestDao.observeAll().first()
        assertTrue(result.isEmpty())
    }
}
