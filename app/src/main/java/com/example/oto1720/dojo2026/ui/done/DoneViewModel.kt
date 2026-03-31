package com.example.oto1720.dojo2026.ui.done

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oto1720.dojo2026.domain.usecase.ObserveDoneArticlesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DoneViewModel @Inject constructor(
    observeDoneArticlesUseCase: ObserveDoneArticlesUseCase,
) : ViewModel() {

    val uiState: StateFlow<DoneUiState> = observeDoneArticlesUseCase()
        .map { DoneUiState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DoneUiState.Loading,
        )
}
