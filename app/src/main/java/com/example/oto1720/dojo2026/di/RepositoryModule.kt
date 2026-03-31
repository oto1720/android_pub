package com.example.oto1720.dojo2026.di

import com.example.oto1720.dojo2026.data.repository.AiRepository
import com.example.oto1720.dojo2026.data.repository.AiRepositoryImpl
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.ArticleRepositoryImpl
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindArticleRepository(impl: ArticleRepositoryImpl): ArticleRepository

    @Binds
    @Singleton
    abstract fun bindDigestRepository(impl: DigestRepositoryImpl): DigestRepository

    @Binds
    @Singleton
    abstract fun bindAiRepository(impl: AiRepositoryImpl): AiRepository
}
