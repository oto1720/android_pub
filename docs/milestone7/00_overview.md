# Milestone 7 全体像

## 何を作ったか

Milestone 6 までに「動く」アプリが完成しました。
Milestone 7 では「壊れにくく・使いやすい」アプリに仕上げるポリッシュ作業を実施しました。

| テーマ | 実装内容 |
|--------|--------|
| エラーハンドリング統一 | `AppError` sealed class で全エラーを分類・共通 UI で表示 |
| AI ローディング表示 | AI 処理中のスピナー表示・ボタン無効化 |
| AI 要約ダイアログ | 4状態（Hidden/Loading/Loaded/Error）を UiState で管理 |

---

## アーキテクチャ全体図

```
┌──────────────────────────────────────────────────────────┐
│  Repository 層                                           │
│   Throwable.toAppError()                                 │
│      HttpException(401) → AppError.Unauthorized          │
│      HttpException(429) → AppError.RateLimitExceeded     │
│      IOException        → AppError.NetworkError          │
│      その他             → AppError.UnknownError          │
└──────────────────────────┬───────────────────────────────┘
                           │ Result<T> に AppError を格納
┌──────────────────────────▼───────────────────────────────┐
│  ViewModel 層                                            │
│   PortalViewModel                                        │
│      pagingItems が LoadState.Error → AppError を取り出す │
│   DigestViewModel                                        │
│      isLoading フラグ → ReadMode / QuestionMode に反映    │
│      summaryDialogState → SummaryDialogState に反映      │
└──────────────────────────┬───────────────────────────────┘
                           │ UiState / LoadState
┌──────────────────────────▼───────────────────────────────┐
│  UI 層 (Compose)                                         │
│   PortalScreen → ErrorContent（AppError を表示）         │
│   DigestScreen → Button 内 CircularProgressIndicator     │
│                → SummaryDialog（4状態の AlertDialog）     │
└──────────────────────────────────────────────────────────┘
```

---

## エラーハンドリングの改善ポイント

```
改善前（Milestone 6 まで）:
  Throwable をそのまま catch
  → エラーメッセージがバラバラ
  → リトライ可否の判断ができない

改善後（Milestone 7）:
  Throwable.toAppError() で統一
  → AppError の種別でメッセージを一元管理
  → Unauthorized / Forbidden はリトライボタンを非表示
  → NetworkError / UnknownError はリトライボタンを表示
```

---

## AI ローディング表示の改善ポイント

```
改善前（Milestone 5 まで）:
  AI 生成中にボタンを連打できる
  → 複数リクエストが走る可能性

改善後（Milestone 7）:
  isLoading = true の間はボタンを disabled
  ボタン内にスピナーを表示して「処理中」を明示
```

---

## ファイル構成

```
app/src/main/java/com/example/oto1720/dojo2026/

（新規追加）
├── domain/
│   └── model/
│       └── AppError.kt          # sealed class + toAppError() 拡張関数

（既存ファイルへの追記）
├── ui/portal/PortalScreen.kt    → ErrorContent 共通 Composable
├── ui/digest/DigestUiState.kt   → isLoading フラグ、SummaryDialogState
└── ui/digest/DigestScreen.kt    → SummaryDialog、ボタン内スピナー
```

---

## Milestone 6 との違い

| 観点 | Milestone 6 | Milestone 7 |
|------|------------|------------|
| エラー表示 | Throwable.message を直接表示 | AppError で分類・統一メッセージ |
| リトライ | なし | NetworkError / UnknownError 時のみ表示 |
| AI 処理中 | ボタンが連打可能 | `isLoading` で無効化 + スピナー表示 |
| AI 要約ダイアログ | なし | 4状態（Hidden/Loading/Loaded/Error）で管理 |
