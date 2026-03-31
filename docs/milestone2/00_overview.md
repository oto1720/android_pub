# Milestone 2 全体像

## 何を作ったか

Milestone 1 でアプリの「骨格（Hilt・Room・Navigation・テーマ・Retrofit）」を構築しました。
Milestone 2 では骨格に**肉付け**をして、実際に動くポータル画面を完成させました。

| レイヤー | 実装内容 |
|---------|---------|
| **Data** | Qiita API 通信・DTO 変換・Repository 実装 |
| **Domain** | 3つの UseCase（取得・カウント監視・積読追加） |
| **UI** | PortalViewModel（状態管理）・PortalScreen（Compose UI） |

## アーキテクチャ全体図

```
┌─────────────────────────────────────────────────────┐
│  UI 層 (Jetpack Compose)                            │
│                                                     │
│   PortalScreen                                      │
│      └── PortalViewModel  ← @HiltViewModel          │
│             ├── uiState: StateFlow<PortalUiState>   │
│             ├── selectedTag: StateFlow<String?>     │
│             ├── tsundokuCount: StateFlow<Int>       │
│             └── event: SharedFlow<PortalEvent>      │
└──────────────────┬──────────────────────────────────┘
                   │ invoke()
┌──────────────────▼──────────────────────────────────┐
│  Domain 層 (UseCase)                                │
│                                                     │
│   GetTrendArticlesUseCase                           │
│   ObserveTsundokuCountUseCase                       │
│   AddToTsundokuUseCase                              │
└──────────────────┬──────────────────────────────────┘
                   │ interface
┌──────────────────▼──────────────────────────────────┐
│  Data 層 (Repository)                               │
│                                                     │
│   ArticleRepository (interface)                     │
│      └── ArticleRepositoryImpl (@Singleton)         │
│             ├── QiitaApiService (Retrofit)  ──→ 🌐  │
│             └── ArticleDao (Room)           ──→ 💾  │
└─────────────────────────────────────────────────────┘
```

## データの流れ（ポータル画面を開いたとき）

```
1. PortalScreen が表示される
        ↓
2. PortalViewModel.init { loadArticles() } が実行される
        ↓
3. GetTrendArticlesUseCase.invoke(tag = null) が呼ばれる
        ↓
4. repository.refreshPortalArticles() → Qiita API に GET リクエスト
        ↓
5. レスポンス (List<QiitaArticleDto>) を ArticleEntity に変換
        ↓
6. 古い PORTAL 記事を削除 → 新しい記事を DB に保存
        ↓
7. repository.observePortalArticles() で DB を監視 → List<ArticleEntity> を取得
        ↓
8. ViewModel が PortalUiState.Success(articles, availableTags) に更新
        ↓
9. Compose が recompose して記事一覧が表示される
```

## ファイル構成

```
app/src/main/java/com/example/oto1720/dojo2026/
│
├── data/
│   ├── local/
│   │   ├── dao/
│   │   │   └── ArticleDao.kt          # Room DAO (SQL クエリ定義)
│   │   └── entity/
│   │       ├── ArticleEntity.kt       # DB テーブル定義
│   │       └── ArticleStatus.kt       # ステータス定数
│   ├── remote/
│   │   ├── api/
│   │   │   └── QiitaApiService.kt     # Retrofit インターフェース
│   │   └── dto/
│   │       ├── QiitaArticleDto.kt     # API レスポンスの型
│   │       └── QiitaArticleDtoMapper.kt # DTO → Entity 変換
│   └── repository/
│       ├── ArticleRepository.kt       # リポジトリ interface
│       └── ArticleRepositoryImpl.kt   # リポジトリ実装
│
├── domain/
│   └── usecase/
│       ├── GetTrendArticlesUseCase.kt        # 記事取得
│       ├── ObserveTsundokuCountUseCase.kt    # 積読数監視
│       └── AddToTsundokuUseCase.kt           # 積読追加
│
└── ui/
    └── portal/
        ├── PortalUiState.kt           # 状態・イベント定義
        ├── PortalViewModel.kt         # ViewModel
        └── PortalScreen.kt            # Compose UI
```

## Milestone 1 との違い

| 観点 | Milestone 1 | Milestone 2 |
|------|------------|------------|
| 画面 | ナビゲーション骨格のみ（空画面） | ポータル画面が実際に動く |
| データ | Room Entity の定義だけ | API 取得 → DB 保存 → UI 表示まで完結 |
| 状態管理 | なし | StateFlow / SharedFlow / UiState パターン |
| テスト | なし | ViewModel のコルーチンテスト |
