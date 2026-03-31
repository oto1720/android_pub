# Milestone 6 学習ガイド

このディレクトリは **Milestone 6（オフライン対応）** を細かく学習するためのドキュメント集です。

## 読む順番

| # | ファイル | 内容 | 対応 Issue |
|---|---------|------|-----------|
| 1 | [00_overview.md](00_overview.md) | Milestone 6 全体像・オフライン対応のアーキテクチャ | - |
| 2 | [01_network_monitor.md](01_network_monitor.md) | ConnectivityManager + callbackFlow で接続状態を監視 | issue #26 |
| 3 | [02_offline_snackbar.md](02_offline_snackbar.md) | Snackbar 通知・キャッシュ表示・Markdown フォールバック | issue #27 |

## Milestone 6 で実装したこと

- [x] `NetworkMonitor` interface + `ConnectivityNetworkMonitor`（`callbackFlow` + `distinctUntilChanged`）
- [x] `NetworkMonitorModule`（Hilt で `@Binds`）
- [x] `PortalViewModel` に `isOnline` StateFlow を追加
- [x] オフライン遷移で `ShowOfflineMessage` イベントを発行（`.drop(1)` で初期値スキップ）
- [x] `PortalScreen` の Snackbar で「オフライン」メッセージを表示
- [x] `DigestViewModel` にオフライン監視を追加（Markdown 表示へ自動フォールバック）
- [x] `DigestScreen` に WebView/Markdown 切り替えボタン（`onToggleViewMode`）を追加

生成日: 2026-03-05
