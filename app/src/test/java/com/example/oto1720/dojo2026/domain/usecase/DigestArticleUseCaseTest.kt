package com.example.oto1720.dojo2026.domain.usecase

import com.example.oto1720.dojo2026.data.local.entity.ArticleStatus
import com.example.oto1720.dojo2026.data.local.entity.DigestEntity
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DigestArticleUseCaseTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var articleRepository: ArticleRepository

    @MockK
    private lateinit var digestRepository: DigestRepository

    private val useCase by lazy { DigestArticleUseCase(articleRepository, digestRepository) }

    // --- DigestEntity の保存 ---

    @Test
    fun `invoke - DigestEntityが正しい内容で保存される`() = runTest {
        val slot = slot<DigestEntity>()
        coEvery { digestRepository.saveDigest(capture(slot)) } returns Unit
        coEvery { articleRepository.updateStatus(any(), any()) } returns Unit

        useCase("article1", memo = "テストメモ", feedback = "テストフィードバック")

        assertEquals("article1", slot.captured.articleId)
        assertEquals("テストメモ", slot.captured.userMemo)
        assertEquals("テストフィードバック", slot.captured.aiFeedback)
        assertTrue(slot.captured.savedAt.isNotBlank())
    }

    // --- ArticleEntity のステータス更新 ---

    @Test
    fun `invoke - articleRepositoryにDONEステータスで更新される`() = runTest {
        coEvery { digestRepository.saveDigest(any()) } returns Unit
        coEvery { articleRepository.updateStatus(any(), any()) } returns Unit

        useCase("article1", memo = "memo", feedback = "feedback")

        coVerify(exactly = 1) { articleRepository.updateStatus("article1", ArticleStatus.DONE) }
    }

}
