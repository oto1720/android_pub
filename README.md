# Tech-Digest

> 「読んだつもり」をなくす、技術記事の **理解完了アプリ**

**Android Dojo 2026** インターンプロダクトとして開発した、技術記事の積読管理＆AI消化アプリです。

---

## 📱 プロダクト概要

若手開発者・プログラミング学生が抱える「読んだつもり」「積読の心理的負債」「チュートリアル地獄」を解決するアプリです。

| 課題 | 解決策 |
|------|--------|
| 知識未定着 | **強制1行メモ（AI壁打ち）** — AIが理解度チェックの問いを出し、1行メモを書いてはじめて「消化」扱い |
| 積読膨張 | **積読スロット制限（最大5件）** — 上限に達すると新規追加ができず「まず消化してから次へ」を強制 |
| 迷子防止 | **オフライン集中モード** — 記事をローカル保存し、目の前の1記事だけに集中 |

### 主な画面

- **ポータル** — Qiita トレンド記事一覧・タグ絞り込み・積読スロットへ追加
- **積読リスト** — 最大5枠、AI要約ボタン、記事本文へ進む
- **消化フロー** — AI問い生成 → 1行メモ入力 → AIフィードバック → 消化完了
- **完了リスト** — 消化済み記事・メモ・AIフィードバックの確認

---

## 🛠 技術スタック

| カテゴリ | 技術 |
|---------|------|
| プラットフォーム | Android (Kotlin 2.0) |
| UI | Jetpack Compose |
| アーキテクチャ | Clean Architecture + MVVM |
| 状態管理 | StateFlow / UiState |
| DI | Hilt |
| ローカルDB | Room + Paging |
| 外部API | Qiita API、Gemini API |
| ネットワーク | Retrofit + OkHttp |

---

## 📚 ドキュメント

### プロダクト・設計ドキュメント（`documents/`）

| ドキュメント | 内容 |
|-------------|------|
| [overview.md](documents/overview.md) | プロダクト概要・課題・ソリューション・ターゲット |
| [design.md](documents/design.md) | 画面フロー・機能設計・DBスキーマ・AIフロー |
| [technical-design.md](documents/technical-design.md) | パッケージ構成・クラス図・技術選定・トレードオフ |
| [database.md](documents/database.md) | データベース設計 |
| [persona.md](documents/persona.md) | ターゲットペルソナ・課題の深掘り |
| [issues.md](documents/issues.md) | 実装タスク・Issue管理 |

### 実装学習ドキュメント（`docs/`）

**Milestone 1 — アプリ基盤**

| ファイル | 内容 |
|---------|------|
| [00_overview](docs/milestone1/00_overview.md) | アプリ全体像・技術スタック |
| [01_hilt_di](docs/milestone1/01_hilt_di.md) | Hilt による依存性注入 |
| [02_room_database](docs/milestone1/02_room_database.md) | Room によるローカルDB |
| [03_navigation](docs/milestone1/03_navigation.md) | Navigation Compose |
| [04_material3_theme](docs/milestone1/04_material3_theme.md) | Material3 テーマ |
| [05_network](docs/milestone1/05_network.md) | Retrofit / OkHttp |

**Milestone 2 — Qiita API・Repository・ViewModel**

| ファイル | 内容 |
|---------|------|
| [00_overview](docs/milestone2/00_overview.md) | Milestone 2 概要 |
| [01_qiita_api](docs/milestone2/01_qiita_api.md) | Qiita API 連携 |
| [02_repository_pattern](docs/milestone2/02_repository_pattern.md) | Repository パターン |
| [03_usecase](docs/milestone2/03_usecase.md) | UseCase 層 |
| [04_viewmodel](docs/milestone2/04_viewmodel.md) | ViewModel 設計 |
| [05_compose_ui](docs/milestone2/05_compose_ui.md) | Compose UI |

**Milestone 3 — 積読機能**

| ファイル | 内容 |
|---------|------|
| [00_overview](docs/milestone3/00_overview.md) | Milestone 3 概要 |
| [01_add_to_tsundoku_usecase](docs/milestone3/01_add_to_tsundoku_usecase.md) | 積読追加 UseCase |
| [02_tsundoku_screen](docs/milestone3/02_tsundoku_screen.md) | 積読画面 |
| [03_portal_lock_ui](docs/milestone3/03_portal_lock_ui.md) | スロット満杯時のロックUI |

**Milestone 4 — 消化フロー**

| ファイル | 内容 |
|---------|------|
| [00_overview](docs/milestone4/00_overview.md) | Milestone 4 概要 |
| [01_digest_screen](docs/milestone4/01_digest_screen.md) | 消化画面 |
| [02_question_memo_ui](docs/milestone4/02_question_memo_ui.md) | 問い・メモUI |
| [03_digest_usecase](docs/milestone4/03_digest_usecase.md) | 消化 UseCase |
| [04_digest_viewmodel](docs/milestone4/04_digest_viewmodel.md) | Digest ViewModel |

**Milestone 5 — Gemini API 統合**

| ファイル | 内容 |
|---------|------|
| [00_overview](docs/milestone5/00_overview.md) | Milestone 5 概要 |
| [01_gemini_api_service](docs/milestone5/01_gemini_api_service.md) | Gemini API サービス |
| [02_ai_repository](docs/milestone5/02_ai_repository.md) | AI Repository |
| [03_generate_usecases](docs/milestone5/03_generate_usecases.md) | 要約・問い・フィードバック UseCase |

**Milestone 6 — オフライン対応**

| ファイル | 内容 |
|---------|------|
| [00_overview](docs/milestone6/00_overview.md) | Milestone 6 概要 |
| [01_network_monitor](docs/milestone6/01_network_monitor.md) | ネットワーク監視 |
| [02_offline_snackbar](docs/milestone6/02_offline_snackbar.md) | オフライン時 Snackbar |

**Milestone 7 — エラーハンドリング・ポリッシュ**

| ファイル | 内容 |
|---------|------|
| [00_overview](docs/milestone7/00_overview.md) | Milestone 7 概要 |
| [01_app_error](docs/milestone7/01_app_error.md) | AppError 統一 |
| [02_ai_loading](docs/milestone7/02_ai_loading.md) | AI ローディング表示 |

### その他ドキュメント

| ファイル | 内容 |
|---------|------|
| [testing-philosophy.md](docs/testing-philosophy.md) | テスト方針 |
| [test-evaluation.md](docs/test-evaluation.md) | テスト評価 |
| [同型原理分析レポート.md](docs/同型原理分析レポート.md) | 同型原理に基づくコード分析 |

### コードレビュー履歴（`docs/reviews/`）

PR レビューやコードレビューのレポートが保存されています。

---

## 🤖 AI を活用した開発の工夫

このプロジェクトでは、**Cursor** 上で **Claude Skills** と **Agents** を活用し、コード品質の維持と学習効率の向上を図っています。

### Skills（スキル）

プロジェクト内の `.claude/skills/` に定義されたスキルで、特定のタスクを自動化・標準化しています。

| スキル | 用途 | トリガー例 |
|--------|------|-----------|
| [code-review](.claude/skills/code-review/SKILL.md) | コードレビュー・指摘事項の Markdown 出力 | 「コードレビューして」「改善点を教えて」 |
| [pr-review](.claude/skills/pr-review/SKILL.md) | PR・コミット差分のレビュー・マージ可否判断 | 「PRレビューして」「マージしても大丈夫？」 |
| [code-learner](.claude/skills/code-learner/SKILL.md) | 既存コードの学習用 Markdown ドキュメント生成 | 「このコードを教えて」「プロジェクトを理解したい」 |
| [feature-learner](.claude/skills/feature-learner/SKILL.md) | 新機能の学習ドキュメント生成 | 「この機能の学習ドキュメントを作って」 |
| [android-kotlin-mentor-skill](.claude/skills/android-kotlin-mentor-skill/SKILL.md) | Android / Kotlin のベストプラクティス・アーキテクチャ指導 | Android 開発の相談 |
| [android-test-generator](.claude/skills/android-test-generator/SKILL.md) | テストコード生成（ViewModel, Repository, DAO 等） | 「テストを書いて」「このViewModelのテストを作って」 |
| [isomorphism-check](.claude/skills/isomorphism-check/SKILL.md) | 同型原理に基づくコード一貫性チェック | 「同型チェック」「形が揃ってるか確認」 |
| [isomorphism-review](.claude/skills/isomorphism-review/SKILL.md) | 7設計原則に基づく実装パターン一貫性レビュー | 「同型原理チェックして」「パターン一貫性を確認して」 |
| [security-review](.claude/skills/security-review/SKILL.md) | セキュリティ観点でのコード精査 | 「セキュリティレビューして」 |

### Agents（エージェント）

`.claude/agents/` に定義されたエージェントで、より専門的な分析を行います。

| エージェント | 役割 |
|-------------|------|
| [review-analyzer](.claude/agents/review-analyzer.md) | コード品質・設計・パフォーマンスの多角的分析 |
| [android-senior-reviewer](.claude/agents/android-senior-reviewer.md) | Android シニアレビュアーとしての深い分析 |
| [code-explorer](.claude/agents/code-explorer.md) | コードベースの探索・理解 |
| [security-scanner](.claude/agents/security-scanner.md) | セキュリティスキャン |

---

## 🚀 セットアップ・ビルド

### 前提条件

- JDK 17
- Android Studio（推奨）または Android SDK
- Android SDK: compileSdk 36, minSdk 24

### 手順

1. リポジトリをクローン
2. `local.properties` に Gemini API キーを設定:

   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```

3. ビルド・実行:

   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

### テスト

| コマンド | 内容 | 備考 |
|---------|------|------|
| `./gradlew testDebugUnitTest` | ユニットテスト（ViewModel, Repository 等） | JVM 上で実行、高速 |
| `./gradlew connectedAndroidTest` | インストルメンテーションテスト（DAO, Compose UI） | エミュレータまたは実機が必要 |

### CI

GitHub Actions で以下を実行しています。

- `assembleDebug` ビルド
- `testDebugUnitTest` ユニットテスト
- `lint` 静的解析
- テスト失敗時: テスト結果をアーティファクトとしてアップロード

---

## 📁 プロジェクト構成

```
app/src/main/java/com/example/oto1720/dojo2026/
├── di/           # Hilt モジュール
├── data/         # Data層（Repository, API, Room）
├── domain/       # Domain層（UseCase, Model）
├── ui/           # UI層（Compose, ViewModel）
│   ├── portal/   # ポータル画面
│   ├── tsundoku/ # 積読リスト画面
│   ├── digest/   # 消化フロー画面
│   └── done/     # 完了リスト画面
└── util/         # ユーティリティ
```

---

## 📄 ライセンス

このプロジェクトは Android Dojo 2026 のインターンプロダクトとして開発されています。
