package com.example.oto1720.dojo2026.ui.done

import com.example.oto1720.dojo2026.domain.model.DoneItem

sealed interface DoneUiState {
    data object Loading : DoneUiState
    data class Success(val items: List<DoneItem>) : DoneUiState
}

sealed interface DoneDetailUiState {
    data object Loading : DoneDetailUiState
    data class Success(val item: DoneItem) : DoneDetailUiState
    data class Error(val message: String) : DoneDetailUiState
}
