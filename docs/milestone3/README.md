# Milestone 3 学習ガイド

このディレクトリは **Milestone 3（積読スロット機能の実装）** を細かく学習するためのドキュメント集です。

## 読む順番

| # | ファイル | 内容 | 対応 Issue |
|---|---------|------|-----------|
| 1 | [00_overview.md](00_overview.md) | Milestone 3 全体像・何を作ったか | - |
| 2 | [01_add_to_tsundoku_usecase.md](01_add_to_tsundoku_usecase.md) | UseCase でのスロット上限管理・アトミック更新 | issue #13 |
| 3 | [02_tsundoku_screen.md](02_tsundoku_screen.md) | 積読スロット UI・TsundokuViewModel | issue #14 |
| 4 | [03_portal_lock_ui.md](03_portal_lock_ui.md) | 満杯時のロック UI・グレーアウト実装 | issue #15 |

## Milestone 3 で実装したこと

- [x] AddToTsundokuUseCase（5件上限チェック・アトミック更新）
- [x] ObserveTsundokuArticlesUseCase（積読記事リストの Flow 監視）
- [x] TsundokuViewModel（StateFlow によるシンプルな状態管理）
- [x] TsundokuScreen（5スロット固定グリッド・破線カード・空きカード）
- [x] ポータルの積読満杯ロック UI（グレーアウト・バナー・ボタン無効化）

生成日: 2026-03-04
