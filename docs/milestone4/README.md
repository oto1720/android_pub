# Milestone 4 学習ガイド

このディレクトリは **Milestone 4（消化（Digest）機能の実装）** を細かく学習するためのドキュメント集です。

## 読む順番

| # | ファイル | 内容 | 対応 Issue |
|---|---------|------|-----------|
| 1 | [00_overview.md](00_overview.md) | Milestone 4 全体像・UiState 遷移フロー | - |
| 2 | [01_digest_screen.md](01_digest_screen.md) | 記事詳細 UI・WebView 埋め込み・BottomBar の出し分け | issue #16 |
| 3 | [02_question_memo_ui.md](02_question_memo_ui.md) | 理解度チェック・メモ入力・フィードバック UI | issue #17 |
| 4 | [03_digest_usecase.md](03_digest_usecase.md) | DigestArticleUseCase・DigestEntity 保存・スロット解放 | issue #18 |
| 5 | [04_digest_viewmodel.md](04_digest_viewmodel.md) | DigestViewModel・UiState 遷移・SavedStateHandle | issue #19 |

## Milestone 4 で実装したこと

- [x] DigestScreen（WebView 埋め込み・TopAppBar・BottomBar の status 別出し分け）
- [x] QuestionMode UI（AI問い表示・メモ入力 TextField・送信ボタン）
- [x] FeedbackMode UI（考察表示・フィードバック表示・消化ボタン）
- [x] DigestArticleUseCase（DigestEntity 保存・status を DONE に更新）
- [x] DigestViewModel（WebLoading → ReadMode → QuestionMode → FeedbackMode 遷移）
- [x] SavedStateHandle による articleId の安全な取得

生成日: 2026-03-04
