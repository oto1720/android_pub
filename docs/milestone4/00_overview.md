# Milestone 4 全体像

## 何を作ったか

Milestone 3 で積読スロットへの追加ができるようになりました。
Milestone 4 では「積読した記事を読んで、理解し、消化する」フローを実装しました。

| レイヤー | 実装内容 |
|---------|---------|
| **Data** | DigestRepository / DigestRepositoryImpl（消化記録の保存） |
| **Domain** | DigestArticleUseCase（消化実行・スロット解放）、GetArticleUseCase |
| **UI** | DigestViewModel（4段階 UiState 遷移）・DigestScreen（WebView・メモ入力・フィードバック） |

## UiState の遷移フロー

Milestone 4 の核心は「1つの画面で4段階の状態を管理する」設計です。

```
WebLoading
    │ loadArticle() 成功
    ▼
ReadMode（記事を WebView で読む）
    │ 「読了宣言」ボタンを押す
    ▼
QuestionMode（AI の問いに答える）
    │ 「考察を送信」ボタンを押す
    ▼
FeedbackMode（フィードバックを確認）
    │ 「習得済みとしてマーク」ボタンを押す
    ▼
（NavigateToDone イベント → 血肉リスト画面へ遷移）
```

## アーキテクチャ全体図

```
┌─────────────────────────────────────────────────────┐
│  UI 層 (Jetpack Compose)                            │
│                                                     │
│   DigestScreen                                      │
│      └── DigestViewModel  ← @HiltViewModel          │
│             ├── uiState: StateFlow<DigestUiState>   │
│             │    (WebLoading/ReadMode/Question/Feedback/Error)
│             └── event: SharedFlow<DigestEvent>      │
│                  (AddedToTsundoku/SlotFull/NavigateToDone)
└──────────────────┬──────────────────────────────────┘
                   │ invoke()
┌──────────────────▼──────────────────────────────────┐
│  Domain 層 (UseCase)                                │
│                                                     │
│   GetArticleUseCase    ← 記事をDBから取得             │
│   AddToTsundokuUseCase ← ポータルからの積読追加       │
│   DigestArticleUseCase ← 消化実行（メモ・FB保存）     │
└──────────────────┬──────────────────────────────────┘
                   │ interface
┌──────────────────▼──────────────────────────────────┐
│  Data 層 (Repository)                               │
│                                                     │
│   ArticleRepository  → status を DONE に更新 ──→ 💾  │
│   DigestRepository   → DigestEntity を保存   ──→ 💾  │
└─────────────────────────────────────────────────────┘
```

## 画面の構造

```
DigestScreen
  ├── TopAppBar（タイトル・タグ・戻るボタン）
  ├── Content（uiState によって切り替わる）
  │    ├── WebLoading → CircularProgressIndicator
  │    ├── ReadMode   → ArticleWebView（WebView 埋め込み）
  │    ├── QuestionMode → QuestionModeContent（問い + メモ入力）
  │    ├── FeedbackMode → FeedbackModeContent（考察 + フィードバック + 消化ボタン）
  │    └── Error → エラーメッセージ
  └── BottomBar（ReadMode のときだけ表示）
       ├── status == PORTAL → 「積読に追加」ボタン
       └── status == TSUNDOKU → 「AI要約」「読了宣言」ボタン
```

## ファイル構成

```
app/src/main/java/com/example/oto1720/dojo2026/
│
├── data/
│   ├── local/
│   │   └── entity/
│   │       └── DigestEntity.kt              # 消化記録テーブル
│   └── repository/
│       ├── DigestRepository.kt             # 消化記録インターフェース
│       └── DigestRepositoryImpl.kt         # 消化記録実装
│
├── domain/
│   └── usecase/
│       ├── GetArticleUseCase.kt            # 単一記事取得
│       └── DigestArticleUseCase.kt         # 消化実行
│
└── ui/
    └── digest/
        ├── DigestUiState.kt    # UiState / Event 定義
        ├── DigestViewModel.kt  # 状態遷移管理
        └── DigestScreen.kt     # Compose UI
```

## Milestone 3 との違い

| 観点 | Milestone 3 | Milestone 4 |
|------|------------|------------|
| 対象画面 | 積読スロット一覧 | 記事詳細・消化フロー |
| UiState の複雑さ | Loading / Success の2状態 | 5状態（段階的な遷移） |
| データ書き込み | status を TSUNDOKU に更新 | DigestEntity 保存 + status を DONE に更新 |
| DB テーブル | articles のみ | articles + digests の2テーブル |
