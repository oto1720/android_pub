package com.example.oto1720.dojo2026.ui.tsundoku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oto1720.dojo2026.domain.usecase.ObserveTsundokuArticlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TsundokuViewModel @Inject constructor(
    observeTsundokuArticlesUseCase: ObserveTsundokuArticlesUseCase,
) : ViewModel() {

    val uiState: StateFlow<TsundokuUiState> = observeTsundokuArticlesUseCase()
        .map { TsundokuUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TsundokuUiState.Loading,
        )
}
