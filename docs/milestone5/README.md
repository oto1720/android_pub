# Milestone 5 学習ガイド

このディレクトリは **Milestone 5（Gemini AI 統合）** を細かく学習するためのドキュメント集です。

## 読む順番

| # | ファイル | 内容 | 対応 Issue |
|---|---------|------|-----------|
| 1 | [00_overview.md](00_overview.md) | Milestone 5 全体像・AI 統合のアーキテクチャ | - |
| 2 | [01_gemini_api_service.md](01_gemini_api_service.md) | Gemini API の DTO 設計・APIキー管理・Named 修飾子 | issue #21 |
| 3 | [02_ai_repository.md](02_ai_repository.md) | AiRepository 実装・プロンプト設計・runCatching | issue #22 |
| 4 | [03_generate_usecases.md](03_generate_usecases.md) | 3つの Generate UseCase・キャッシュ戦略・Result 連鎖 | issue #23・#24・#25 |

## Milestone 5 で実装したこと

- [x] GeminiApiService（Retrofit インターフェース・DTO 定義）
- [x] API キーを `local.properties` + `BuildConfig` で安全に管理
- [x] Gemini 専用 OkHttpClient（インターセプターでキーを自動付与）
- [x] AiRepository interface + AiRepositoryImpl（3種類の生成メソッド）
- [x] GenerateSummaryUseCase（DB キャッシュ優先・未保存時のみ API 呼び出し）
- [x] GenerateQuestionUseCase（DigestEntity.aiQuestion に保存）
- [x] GenerateFeedbackUseCase（問い + メモ → フィードバック生成・保存）

生成日: 2026-03-05
