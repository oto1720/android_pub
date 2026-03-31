# 00. アプリ全体像

## このアプリは何か

**Tech Digest（仮称）** — 技術記事を「読む → 消化する → 血肉にする」という
3ステップで管理する Android アプリ。

| 画面名 | ルート | 役割 |
|--------|--------|------|
| PortalScreen | `portal` | トップ画面。各画面への入口 |
| TsundokuScreen | `tsundoku` | 積読リスト（未読記事一覧） |
| DigestScreen | `digest/{articleId}` | 記事を消化する画面 |
| DoneScreen | `done` | 消化済み（血肉）リスト |
| DoneDetailScreen | `done_detail/{articleId}` | 消化済み記事の詳細 |

---

## 技術スタック

| 技術 | バージョン | 用途 |
|------|----------|------|
| Kotlin | 2.0.21 | 言語 |
| Jetpack Compose | BOM 2025.05.00 | UI |
| Material3 | Compose BOM に含む | デザインシステム |
| Navigation Compose | 2.8.9 | 画面遷移 |
| Hilt | 2.51.1 | 依存性注入（DI） |
| KSP | 2.0.21-1.0.27 | コード生成（Room/Hilt） |
| Room | 2.6.1 | ローカルデータベース |
| Retrofit | 2.11.0 | HTTP通信 |
| OkHttp | 4.12.0 | HTTPクライアント |
| Gson | 2.11.0 | JSON変換 |

---

## ディレクトリ構造

```
app/src/main/java/com/example/oto1720/dojo2026/
│
├── DojoApplication.kt         # Application クラス（Hilt 起動点）
├── MainActivity.kt            # Activity（Compose エントリポイント）
│
├── navigation/
│   ├── Screen.kt              # 画面ルートの定義（sealed class）
│   └── AppNavHost.kt          # NavHost（遷移グラフの定義）
│
├── di/                        # 依存性注入モジュール
│   ├── AppModule.kt           # Room DB / DAO の提供
│   └── NetworkModule.kt       # OkHttp / Retrofit の提供
│
├── data/
│   └── local/
│       ├── AppDatabase.kt     # Room データベース定義
│       ├── entity/
│       │   ├── ArticleEntity.kt   # 記事テーブル
│       │   └── DigestEntity.kt    # 消化メモテーブル
│       └── dao/
│           ├── ArticleDao.kt      # 記事の CRUD + Flow
│           └── DigestDao.kt       # 消化メモの CRUD + Flow
│
└── ui/
    ├── theme/
    │   ├── Color.kt           # カラー定数（ライト/ダーク）
    │   ├── Type.kt            # タイポグラフィ定義
    │   └── Theme.kt           # MaterialTheme のセットアップ
    ├── portal/
    │   └── PortalScreen.kt    # トップ画面
    ├── tsundoku/
    │   └── TsundokuScreen.kt  # 積読リスト画面
    ├── digest/
    │   └── DigestScreen.kt    # 記事消化画面
    └── done/
        ├── DoneScreen.kt      # 血肉リスト画面
        └── DoneDetailScreen.kt # 詳細画面
```

---

## アプリの起動フロー

```
Android OS
  └─► DojoApplication.onCreate()
        └─► @HiltAndroidApp が Hilt コンテナを初期化

  └─► MainActivity.onCreate()
        ├─► enableEdgeToEdge()          // 画面端まで描画
        └─► setContent {
              TechDigestTheme {           // Material3 テーマを適用
                Scaffold {
                  AppNavHost(navController)  // 画面遷移グラフを構築
                    └─► startDestination = "portal"
                          └─► PortalScreen が最初に表示される
                }
              }
            }
```

---

## データの全体像

```
[ネットワーク]                [ローカルDB]
  API (Retrofit)   ─────►   Room (tech_digest.db)
                              │
                        ┌─────┴─────┐
                        │           │
                   articles     digests
                   テーブル      テーブル
                        │           │
                   ArticleDao   DigestDao
                        │           │
                        └─────┬─────┘
                              │ Flow<List<...>>
                         ViewModel (次milestone)
                              │
                            UI (Compose)
```

> **Note**: Milestone 1 は「骨格」なので、ViewModel やリポジトリは次以降のmilestoneで実装します。
