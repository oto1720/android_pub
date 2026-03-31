package com.example.oto1720.dojo2026.ui.tsundoku

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity

sealed interface TsundokuUiState {
    data object Loading : TsundokuUiState
    data class Success(val articles: List<ArticleEntity>) : TsundokuUiState
}
