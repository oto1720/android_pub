# Milestone 6 全体像

## 何を作ったか

Milestone 5 までは「オンライン前提」で動作するアプリでした。
Milestone 6 では「ネットワークが切れても壊れない」オフライン対応を実装しました。

| 機能 | オフライン時の動作 |
|------|-----------------|
| ポータル画面 | Room キャッシュを表示し続ける・Snackbar で通知 |
| 記事詳細画面 | WebView → Markdown 表示へ自動フォールバック |
| AI機能（要約・問い・フィードバック）| オフライン時はボタンを無効化 |

## アーキテクチャ全体図

```
┌─────────────────────────────────────────────────────┐
│  System 層                                          │
│   Android ConnectivityManager                       │
│      └── NetworkCallback（接続状態の変化をコールバック）│
└──────────────────┬──────────────────────────────────┘
                   │ callbackFlow でラップ
┌──────────────────▼──────────────────────────────────┐
│  Data 層 (Network)                                  │
│   NetworkMonitor (interface)                        │
│      val isOnline: Flow<Boolean>                    │
│   ConnectivityNetworkMonitor (@Singleton)           │
│      ← Android 機能を Kotlin Flow に変換する橋渡し   │
└──────────────────┬──────────────────────────────────┘
                   │ DI 注入
┌──────────────────▼──────────────────────────────────┐
│  ViewModel 層                                       │
│   PortalViewModel                                   │
│      isOnline: StateFlow<Boolean>                   │
│      → オフライン遷移で ShowOfflineMessage イベント発行│
│   DigestViewModel                                   │
│      isOnline: StateFlow<Boolean>                   │
│      → オフライン時 isMarkdownMode = true に自動切替  │
└──────────────────┬──────────────────────────────────┘
                   │ UI
┌──────────────────▼──────────────────────────────────┐
│  UI 層 (Compose)                                    │
│   PortalScreen → Snackbar「オフラインです」          │
│   DigestScreen → WebView/Markdown 切り替えボタン     │
└─────────────────────────────────────────────────────┘
```

## オフライン時のデータフロー（ポータル画面）

```
ネットワーク切断
    ↓
ConnectivityNetworkMonitor が false を Flow に流す
    ↓
PortalViewModel.isOnline が false に更新
    ↓
.drop(1) で「初期値以外の変化」として検知
    ↓
PortalEvent.ShowOfflineMessage を SharedFlow に emit
    ↓
PortalScreen の LaunchedEffect が受け取る
    ↓
snackbarHostState.showSnackbar("オフラインです。キャッシュを表示しています")
    ↓
PagingData（Room キャッシュ）はそのまま表示を継続
```

## ファイル構成

```
app/src/main/java/com/example/oto1720/dojo2026/
│
├── data/
│   └── network/
│       ├── NetworkMonitor.kt               # interface（isOnline: Flow<Boolean>）
│       └── ConnectivityNetworkMonitor.kt   # callbackFlow 実装
│
└── di/
    └── NetworkMonitorModule.kt             # Hilt @Binds 設定

（既存ファイルへの追記）
├── ui/portal/PortalViewModel.kt   → isOnline StateFlow + ShowOfflineMessage イベント
├── ui/portal/PortalScreen.kt      → Snackbar 表示
├── ui/digest/DigestViewModel.kt   → isOnline StateFlow + Markdown フォールバック
└── ui/digest/DigestScreen.kt      → WebView/Markdown 切り替えボタン
```

## Milestone 5 との違い

| 観点 | Milestone 5 | Milestone 6 |
|------|------------|------------|
| ネットワーク前提 | オンライン必須 | オフライン時もアプリが動作 |
| エラー発生時 | エラー画面に遷移 | キャッシュ継続表示 + Snackbar 通知 |
| 記事表示 | WebView のみ | WebView / Markdown 切り替え可能 |
| 接続監視 | なし | `callbackFlow` でリアルタイム監視 |
