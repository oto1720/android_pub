package com.example.oto1720.dojo2026.ui.digest

import com.example.oto1720.dojo2026.data.local.entity.ArticleEntity

sealed interface DigestUiState {
    /** 記事をDBから取得中 */
    data object WebLoading : DigestUiState

    /** 記事取得完了・WebView または Markdown 表示中 */
    data class ReadMode(
        val article: ArticleEntity,
        /** true: 問い生成中（読了宣言ボタンにスピナーを表示） */
        val isLoading: Boolean = false,
        /** true: Markdown 表示、false: WebView 表示 */
        val isMarkdownMode: Boolean = false,
        /** AI要約ダイアログの状態 */
        val summaryDialogState: SummaryDialogState = SummaryDialogState.Hidden,
    ) : DigestUiState

    /** 読了宣言後・AI問いとメモ入力を表示 */
    data class QuestionMode(
        val article: ArticleEntity,
        val question: String,
        /** true: フィードバック生成中（考察を送信ボタンにスピナーを表示） */
        val isLoading: Boolean = false,
    ) : DigestUiState

    /** メモ送信後・AIフィードバックと消化ボタンを表示 */
    data class FeedbackMode(
        val article: ArticleEntity,
        val question: String,
        val memo: String,
        val feedback: String,
        /** true: 消化ボタン有効、false: 理解不十分のため無効 */
        val canDigest: Boolean = true,
    ) : DigestUiState

    /** 記事取得失敗 */
    data class Error(val message: String) : DigestUiState
}

/** AI要約ダイアログの状態 */
sealed interface SummaryDialogState {
    data object Hidden : SummaryDialogState
    data object Loading : SummaryDialogState
    data class Loaded(val summary: String) : SummaryDialogState
    data class Error(val message: String) : SummaryDialogState
}

sealed interface DigestEvent {
    /** 積読スロットへの追加に成功した */
    data object AddedToTsundoku : DigestEvent

    /** 積読スロットが満杯で追加できなかった */
    data object SlotFull : DigestEvent

    /** 消化が完了した */
    data object NavigateToDone : DigestEvent
}
