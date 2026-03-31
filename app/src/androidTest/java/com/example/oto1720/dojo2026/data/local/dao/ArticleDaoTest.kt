package com.example.oto1720.dojo2026.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.oto1720.dojo2026.data.local.AppDatabase
import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity
import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
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
 * ArticleDao の実動テスト。
 *
 * ここで守るもの:
 * - updateStatusIfSlotAvailable の SQL ロジック（スロット制限・アトミック更新）
 * - deleteByStatus / getIdsByStatuses のフィルタリング
 * - Flow が DB 変更を正しく流すこと
 *
 * Mock を使わずインメモリ Room DB で動かす。
 * SQL や @Query の変更を確実に検知するための「実動テスト」。
 */
@RunWith(AndroidJUnit4::class)
class ArticleDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ArticleDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.articleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun buildArticle(
        id: String,
        status: String = ArticleStatus.PORTAL,
        createdAt: String = "2026-03-05T00:00:00Z",
        likesCount: Int = 0,
    ) = ArticleEntity(
        id = id,
        title = "title_$id",
        url = "https://example.com/$id",
        tags = "android,kotlin",
        createdAt = createdAt,
        cachedAt = "2026-03-05T00:00:00Z",
        status = status,
        likesCount = likesCount,
    )

    // --- insert / getById ---

    @Test
    fun insert後にgetByIdで取得できる() = runTest {
        // Arrange
        val article = buildArticle("a1")

        // Act
        dao.insert(article)

        // Assert
        val result = dao.getById("a1")
        assertNotNull(result)
        assertEquals("a1", result?.id)
    }

    @Test
    fun 存在しないidはgetByIdでnullを返す() = runTest {
        val result = dao.getById("unknown")
        assertNull(result)
    }

    // --- updateStatus ---

    @Test
    fun updateStatus後にgetByIdで新しいstatusが取得できる() = runTest {
        // Arrange
        dao.insert(buildArticle("a1", status = ArticleStatus.PORTAL))

        // Act
        dao.updateStatus("a1", ArticleStatus.TSUNDOKU)

        // Assert
        val result = dao.getById("a1")
        assertEquals(ArticleStatus.TSUNDOKU, result?.status)
    }

    // --- updateStatusIfSlotAvailable（最重要） ---

    @Test
    fun updateStatusIfSlotAvailable_スロットが空いているとき_1を返し更新される() = runTest {
        // Arrange: TSUNDOKU が 0 件、maxSlots = 5
        dao.insert(buildArticle("a1", status = ArticleStatus.PORTAL))

        // Act
        val updated = dao.updateStatusIfSlotAvailable(
            articleId = "a1",
            newStatus = ArticleStatus.TSUNDOKU,
            slotCountStatus = ArticleStatus.TSUNDOKU,
            maxSlots = 5,
        )

        // Assert
        assertEquals(1, updated)
        assertEquals(ArticleStatus.TSUNDOKU, dao.getById("a1")?.status)
    }

    @Test
    fun updateStatusIfSlotAvailable_スロットが満杯のとき_0を返し更新されない() = runTest {
        // Arrange: TSUNDOKU を maxSlots 件まで埋める
        repeat(5) { i -> dao.insert(buildArticle("existing_$i", status = ArticleStatus.TSUNDOKU)) }
        dao.insert(buildArticle("newcomer", status = ArticleStatus.PORTAL))

        // Act
        val updated = dao.updateStatusIfSlotAvailable(
            articleId = "newcomer",
            newStatus = ArticleStatus.TSUNDOKU,
            slotCountStatus = ArticleStatus.TSUNDOKU,
            maxSlots = 5,
        )

        // Assert: 更新されない
        assertEquals(0, updated)
        assertEquals(ArticleStatus.PORTAL, dao.getById("newcomer")?.status)
    }

    @Test
    fun updateStatusIfSlotAvailable_スロットが1件空いているとき_追加できる() = runTest {
        // Arrange: TSUNDOKU が 4 件（上限 5 の 1 件手前）
        repeat(4) { i -> dao.insert(buildArticle("existing_$i", status = ArticleStatus.TSUNDOKU)) }
        dao.insert(buildArticle("newcomer", status = ArticleStatus.PORTAL))

        // Act
        val updated = dao.updateStatusIfSlotAvailable(
            articleId = "newcomer",
            newStatus = ArticleStatus.TSUNDOKU,
            slotCountStatus = ArticleStatus.TSUNDOKU,
            maxSlots = 5,
        )

        // Assert
        assertEquals(1, updated)
        assertEquals(ArticleStatus.TSUNDOKU, dao.getById("newcomer")?.status)
    }

    @Test
    fun updateStatusIfSlotAvailable_対象articleIdが存在しないとき_0を返す() = runTest {
        // Act
        val updated = dao.updateStatusIfSlotAvailable(
            articleId = "nonexistent",
            newStatus = ArticleStatus.TSUNDOKU,
            slotCountStatus = ArticleStatus.TSUNDOKU,
            maxSlots = 5,
        )

        // Assert
        assertEquals(0, updated)
    }

    // --- deleteByStatus ---

    @Test
    fun deleteByStatus_指定ステータスの記事のみ削除される() = runTest {
        // Arrange
        dao.insertAll(listOf(
            buildArticle("portal1", status = ArticleStatus.PORTAL),
            buildArticle("portal2", status = ArticleStatus.PORTAL),
            buildArticle("tsundoku1", status = ArticleStatus.TSUNDOKU),
        ))

        // Act
        dao.deleteByStatus(ArticleStatus.PORTAL)

        // Assert: PORTAL は消え TSUNDOKU は残る
        assertNull(dao.getById("portal1"))
        assertNull(dao.getById("portal2"))
        assertNotNull(dao.getById("tsundoku1"))
    }

    // --- getIdsByStatuses ---

    @Test
    fun getIdsByStatuses_指定したステータスのIDのみ返す() = runTest {
        // Arrange
        dao.insertAll(listOf(
            buildArticle("portal1", status = ArticleStatus.PORTAL),
            buildArticle("tsundoku1", status = ArticleStatus.TSUNDOKU),
            buildArticle("done1", status = ArticleStatus.DONE),
        ))

        // Act
        val ids = dao.getIdsByStatuses(listOf(ArticleStatus.TSUNDOKU, ArticleStatus.DONE))

        // Assert
        assertEquals(2, ids.size)
        assertTrue(ids.contains("tsundoku1"))
        assertTrue(ids.contains("done1"))
        assertTrue(!ids.contains("portal1"))
    }

    // --- Flow ---

    @Test
    fun observeByStatus_insertするとFlowに流れる() = runTest {
        // Arrange
        dao.insert(buildArticle("a1", status = ArticleStatus.TSUNDOKU))

        // Act
        val result = dao.observeByStatus(ArticleStatus.TSUNDOKU).first()

        // Assert
        assertEquals(1, result.size)
        assertEquals("a1", result[0].id)
    }

    @Test
    fun observeByStatus_別ステータスの記事はFlowに流れない() = runTest {
        // Arrange
        dao.insert(buildArticle("portal1", status = ArticleStatus.PORTAL))

        // Act
        val result = dao.observeByStatus(ArticleStatus.TSUNDOKU).first()

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun updateSummary_aiSummaryが更新される() = runTest {
        // Arrange
        dao.insert(buildArticle("a1"))

        // Act
        dao.updateSummary("a1", "テスト要約")

        // Assert
        assertEquals("テスト要約", dao.getById("a1")?.aiSummary)
    }
}
