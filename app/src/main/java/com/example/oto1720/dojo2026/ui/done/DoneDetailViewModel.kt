package com.example.oto1720.dojo2026.ui.done

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.oto1720.dojo2026.data.repository.ArticleRepository
import com.example.oto1720.dojo2026.data.repository.DigestRepository
import com.example.oto1720.dojo2026.domain.model.DoneItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DoneDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val articleRepository: ArticleRepository,
    private val digestRepository: DigestRepository,
) : ViewModel() {

    private val articleId: String = checkNotNull(savedStateHandle["articleId"])

    private val _uiState = MutableStateFlow<DoneDetailUiState>(DoneDetailUiState.Loading)
    val uiState: StateFlow<DoneDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            val article = articleRepository.getArticleById(articleId)
            val digest = digestRepository.getByArticleId(articleId)
            _uiState.value = if (article != null && digest != null) {
                DoneDetailUiState.Success(
                    DoneItem(
                        articleId = article.id,
                        title = article.title,
                        url = article.url,
                        tags = article.tags,
                        savedAt = digest.savedAt,
                        userMemo = digest.userMemo.orEmpty(),
                        aiFeedback = digest.aiFeedback.orEmpty(),
                    )
                )
            } else {
                DoneDetailUiState.Error("記事が見つかりません")
            }
        }
    }
}
