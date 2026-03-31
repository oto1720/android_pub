# Milestone 2 学習ガイド

このディレクトリは **Milestone 2（データ層・ドメイン層・ポータル画面の実装）** を細かく学習するためのドキュメント集です。

## 読む順番

| # | ファイル | 内容 | 対応 Issue |
|---|---------|------|-----------|
| 1 | [00_overview.md](00_overview.md) | Milestone 2 全体像・何を作ったか | - |
| 2 | [01_qiita_api.md](01_qiita_api.md) | Qiita API 通信と DTO 設計 | issue #8 |
| 3 | [02_repository_pattern.md](02_repository_pattern.md) | Repository パターンとデータフロー | issue #9 |
| 4 | [03_usecase.md](03_usecase.md) | UseCase 層のビジネスロジック | issue #10 |
| 5 | [04_viewmodel.md](04_viewmodel.md) | ViewModel・StateFlow・イベント設計 | issue #11 |
| 6 | [05_compose_ui.md](05_compose_ui.md) | Jetpack Compose による UI 実装 | issue #12 |

## Milestone 2 で実装したこと

- [x] Qiita API Service 定義（Retrofit インターフェース）
- [x] DTO → Entity マッピング（null 安全な変換）
- [x] ArticleRepository インターフェース + 実装（Qiita API × Room）
- [x] GetTrendArticlesUseCase（API 取得 → フィルタリング）
- [x] ObserveTsundokuCountUseCase（積読件数の監視）
- [x] AddToTsundokuUseCase（スロット上限付き積読追加）
- [x] PortalViewModel（StateFlow / SharedFlow / イベント設計）
- [x] PortalScreen（Jetpack Compose UI 実装）
- [x] PortalViewModelTest（コルーチンテスト実践）

生成日: 2026-03-03
