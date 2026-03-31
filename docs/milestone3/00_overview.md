# Milestone 3 全体像

## 何を作ったか

Milestone 2 でポータル画面（記事一覧）が動くようになりました。
Milestone 3 では「記事を積読スロットに追加する」機能を完成させました。

| レイヤー | 実装内容 |
|---------|---------|
| **Domain** | AddToTsundokuUseCase（5件上限・アトミック更新）、ObserveTsundokuArticlesUseCase |
| **UI** | TsundokuViewModel（状態管理）・TsundokuScreen（スロット UI） |
| **UI（既存改修）** | PortalScreen に満杯ロック UI（グレーアウト・バナー）を追加 |

## アーキテクチャ全体図（Milestone 3 追加分）

```
┌─────────────────────────────────────────────────────┐
│  UI 層 (Jetpack Compose)                            │
│                                                     │
│   TsundokuScreen                                    │
│      └── TsundokuViewModel  ← @HiltViewModel        │
│             └── uiState: StateFlow<TsundokuUiState> │
│                                                     │
│   PortalScreen（既存 + 満杯ロック追加）               │
│      └── PortalViewModel                            │
│             ├── tsundokuCount: StateFlow<Int>       │
│             └── addToTsundoku()                     │
└──────────────────┬──────────────────────────────────┘
                   │ invoke()
┌──────────────────▼──────────────────────────────────┐
│  Domain 層 (UseCase)                                │
│                                                     │
│   AddToTsundokuUseCase  ← 5件上限チェック              │
│   ObserveTsundokuArticlesUseCase  ← Flow 監視        │
└──────────────────┬──────────────────────────────────┘
                   │ interface
┌──────────────────▼──────────────────────────────────┐
│  Data 層 (Repository / DAO)                         │
│                                                     │
│   ArticleRepositoryImpl                             │
│      └── ArticleDao                                 │
│             └── updateStatusIfSlotAvailable()  ──→ 💾│
│                  （SQL の WHERE サブクエリで上限確認）  │
└─────────────────────────────────────────────────────┘
```

## データの流れ（「スロットに追加」ボタンを押したとき）

```
1. PortalScreen の「スロットに追加」ボタンを押す
        ↓
2. PortalViewModel.addToTsundoku(articleId) が呼ばれる
        ↓
3. AddToTsundokuUseCase.invoke(articleId) が実行される
        ↓
4. repository.updateStatusIfSlotAvailable() → DB に SQL 発行
        ↓
   SQL: UPDATE articles SET status = 'TSUNDOKU'
        WHERE id = :articleId
        AND (SELECT COUNT(*) FROM articles WHERE status = 'TSUNDOKU') < 5
        ↓
5a. 成功（スロット空き）→ PortalViewModel が記事一覧からその記事を除外
5b. 失敗（スロット満杯）→ SharedFlow でイベント SlotFull を発行
        ↓
6a. TsundokuViewModel が observeTsundokuArticlesUseCase() を通じて変更を検知
        ↓
7a. TsundokuScreen が recompose してスロットを更新
```

## ファイル構成

```
app/src/main/java/com/example/oto1720/dojo2026/
│
├── domain/
│   └── usecase/
│       ├── AddToTsundokuUseCase.kt          # スロット追加（上限管理）
│       └── ObserveTsundokuArticlesUseCase.kt # 積読一覧を Flow 監視
│
└── ui/
    ├── tsundoku/
    │   ├── TsundokuUiState.kt     # Loading / Success
    │   ├── TsundokuViewModel.kt   # StateFlow 管理
    │   └── TsundokuScreen.kt      # 5スロット UI
    └── portal/
        ├── PortalScreen.kt        # 満杯ロック UI 追加（既存改修）
        └── PortalViewModel.kt     # addToTsundoku() 追加（既存改修）
```

## Milestone 2 との違い

| 観点 | Milestone 2 | Milestone 3 |
|------|------------|------------|
| 積読機能 | UseCase の骨格のみ | 実際に5件制限付きで動作 |
| 積読画面 | ナビゲーション骨格のみ（空画面） | 5スロット固定 UI が表示される |
| ポータル | 「スロットに追加」ボタンが動作 | 5件到達でロック・グレーアウト表示 |
| DB 操作 | 単純な status 更新 | SQL サブクエリによるアトミック上限チェック |
