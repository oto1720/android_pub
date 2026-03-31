# Milestone 7 学習ガイド

このディレクトリは **Milestone 7（磨き・仕上げ）** を細かく学習するためのドキュメント集です。

## 読む順番

| # | ファイル | 内容 | 対応 Issue |
|---|---------|------|-----------|
| 1 | [00_overview.md](00_overview.md) | Milestone 7 全体像・ポリッシュのアーキテクチャ | - |
| 2 | [01_app_error.md](01_app_error.md) | AppError sealed class・toAppError 拡張関数・ErrorContent 共通 UI | issue #29 |
| 3 | [02_ai_loading.md](02_ai_loading.md) | AI生成中ローディング・isLoading UiState フラグ・SummaryDialogState | issue #31 |

## Milestone 7 で実装したこと

- [x] `AppError` sealed class（Unauthorized / Forbidden / RateLimitExceeded / NetworkError / AiError / UnknownError）
- [x] `Throwable.toAppError()` 拡張関数（HTTP ステータスコードで分類）
- [x] `ErrorContent` 共通 Composable（Warning アイコン + エラーメッセージ + リトライボタン）
- [x] `DigestUiState.ReadMode` に `isLoading` フラグを追加（読了宣言ボタンのスピナー）
- [x] `DigestUiState.QuestionMode` に `isLoading` フラグを追加（考察送信ボタンのスピナー）
- [x] `SummaryDialogState` sealed interface（Hidden / Loading / Loaded / Error）
- [x] `SummaryDialog` Composable（AlertDialog 内でローディング・要約・エラーを切り替え）

## 未実装（OPEN Issue）

| Issue | タイトル | 優先度 |
|-------|--------|--------|
| #28 | 画面遷移アニメーション | low |
| #30 | 積読スロット空き枠アニメーション | low |
| #32 | AI要約再生成ボタン | mid |
| #48 | 共通化 | - |

生成日: 2026-03-05
