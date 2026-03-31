# Tech-Digest 基本設計ドキュメント

## 1. 画面フロー

```mermaid
flowchart TD
    Start([アプリ起動]) --> Portal

    Portal[ポータル画面\nタイトル・プレビューのみ表示]
    Portal -->|積読リストアイコン| Tsundoku
    Portal -->|血肉リストアイコン| Done

    Tsundoku[積読リスト画面\n最大5枠]
    Tsundoku -->|記事カードタップ| DetailFromTsundoku
    Tsundoku -->|血肉リストアイコン| Done

    DetailFromTsundoku[記事詳細・消化画面\n積読モード]
    DetailFromTsundoku -->|「AI要約」ボタン| DetailFromTsundoku
    DetailFromTsundoku -->|本文読了宣言| QuestionFlow
    DetailFromTsundoku -->|バック| Tsundoku

    QuestionFlow[理解度チェック\nAI問い表示]
    QuestionFlow -->|一行メモ入力 → 送信| Feedback
    Feedback[AIフィードバック表示]
    Feedback -->|「消化する」ボタン| Done
    Feedback -->|バック| Tsundoku

    Done[血肉リスト画面\n消化済み一覧]
    Done -->|記事カードタップ| DoneDetail
    DoneDetail[消化済み詳細\nメモ・フィードバック確認]
    DoneDetail -->|バック| Done
```

---

## 2. 機能設計（ユースケース図）

```mermaid
graph LR
    User((ユーザー))

    subgraph Portal[ポータル機能]
        P1[トレンド記事一覧閲覧]
        P2[タグ別記事絞り込み]
        P3[積読リストへ追加]
        P4[オフライン時キャッシュ表示]
    end

    subgraph Tsundoku[積読リスト機能]
        T1[積読スロット管理\n最大5件]
        T2[AI要約生成]
        T3[積読上限ロック\nポータルをグレーアウト]
    end

    subgraph Digest[消化フロー機能]
        D1[本文表示\nWebView/Markdown]
        D2[AI理解度チェック問い生成]
        D3[一行メモ入力]
        D4[AIフィードバック取得]
        D5[消化実行\nステータス更新]
    end

    subgraph Done[血肉リスト機能]
        N1[消化済み記事一覧]
        N2[メモ・AIフィードバック確認]
    end

    User --> P1
    User --> P2
    User --> P3
    User --> T1
    User --> T2
    User --> D1
    User --> D2
    User --> D3
    User --> D4
    User --> D5
    User --> N1
    User --> N2

    P3 --> T1
    T1 --> T3
    T3 --> P4
    D5 --> N1
```

---

## 3. アーキテクチャ構成

```mermaid
graph TD
    subgraph UI層[UI層 - Jetpack Compose]
        PortalScreen
        TsundokuScreen
        DigestScreen
        DoneScreen
    end

    subgraph ViewModel層[ViewModel層 - StateFlow]
        PortalVM[PortalViewModel]
        TsundokuVM[TsundokuViewModel]
        DigestVM[DigestViewModel]
        DoneVM[DoneViewModel]
    end

    subgraph UseCase層[UseCase層 - Clean Architecture]
        UC1[GetTrendArticlesUseCase]
        UC2[AddToTsundokuUseCase]
        UC3[GenerateSummaryUseCase]
        UC4[GenerateQuestionUseCase]
        UC5[GenerateFeedbackUseCase]
        UC6[DigestArticleUseCase]
        UC7[GetDoneArticlesUseCase]
    end

    subgraph Repository層[Repository層]
        ArticleRepo[ArticleRepository]
        DigestRepo[DigestRepository]
        AiRepo[AiRepository]
    end

    subgraph DataSource層[DataSource層]
        QiitaAPI[QiitaAPI\nRetrofit]
        GeminiAPI[GeminiAPI\nRetrofit]
        RoomDB[(Room DB)]
    end

    PortalScreen --> PortalVM
    TsundokuScreen --> TsundokuVM
    DigestScreen --> DigestVM
    DoneScreen --> DoneVM

    PortalVM --> UC1
    PortalVM --> UC2
    TsundokuVM --> UC3
    DigestVM --> UC4
    DigestVM --> UC5
    DigestVM --> UC6
    DoneVM --> UC7

    UC1 --> ArticleRepo
    UC2 --> ArticleRepo
    UC3 --> AiRepo
    UC3 --> DigestRepo
    UC4 --> AiRepo
    UC5 --> AiRepo
    UC5 --> DigestRepo
    UC6 --> ArticleRepo
    UC6 --> DigestRepo
    UC7 --> DigestRepo

    ArticleRepo --> QiitaAPI
    ArticleRepo --> RoomDB
    AiRepo --> GeminiAPI
    DigestRepo --> RoomDB
```

---

## 4. Roomデータベース設計

```mermaid
erDiagram
    ArticleEntity {
        String id PK
        String title
        String url
        String tags
        String createdAt
        String cachedAt
        String status "PORTAL / TSUNDOKU / DONE"
        String aiSummary "nullable"
    }

    DigestEntity {
        String articleId PK "FK -> ArticleEntity.id"
        String aiQuestion "nullable"
        String userMemo "nullable"
        String aiFeedback "nullable"
        String savedAt
    }

    ArticleEntity ||--o| DigestEntity : "消化記録"
```

---

## 5. AIフロー設計

### AI入力データについて

すべての AI 操作（要約・問い・フィードバック）は **記事タイトルではなく記事全文（body）** を使用する。
Qiita API の `/api/v2/items` が返す `body`（Markdown形式）を `ArticleEntity.body` に保存し、AI プロンプトに渡す。
本文が長い場合は先頭 10,000 文字に切り詰めて使用する。

```mermaid
sequenceDiagram
    actor User
    participant UI as Compose UI
    participant VM as ViewModel
    participant UC as UseCase
    participant Gemini as Gemini API
    participant Room as Room DB

    Note over User, Room: 【要約フロー】
    User ->> UI: 「AI要約」ボタンタップ
    UI ->> VM: generateSummary(articleId)
    VM ->> UC: GenerateSummaryUseCase
    UC ->> Room: ArticleEntity.body を取得
    UC ->> Gemini: 「以下の記事全文を読んで重要ポイントを3行で要約してください」+ body全文
    Gemini -->> UC: 要約テキスト（3行）
    UC ->> Room: ArticleEntity.aiSummary を更新
    UC -->> VM: 要約テキスト
    VM -->> UI: StateFlow更新 → UI再描画

    Note over User, Room: 【消化フロー - 問い生成】
    User ->> UI: 「本文読了・理解度チェック」タップ
    UI ->> VM: generateQuestion(articleId)
    VM ->> UC: GenerateQuestionUseCase
    UC ->> Room: ArticleEntity.body を取得
    UC ->> Gemini: 「記事全文を読んで内容に即した理解度確認問いを1つ生成してください」+ body全文
    Gemini -->> UC: 問いテキスト
    UC ->> Room: DigestEntity.aiQuestion を保存
    UC -->> VM: 問いテキスト（提案として表示）
    VM -->> UI: StateFlow更新 → 問いをUI表示

    Note over User, Room: 【消化フロー - フィードバック＋理解度評価】
    User ->> UI: 一行メモ入力 → 送信
    UI ->> VM: generateFeedback(articleId, memo)
    VM ->> UC: GenerateFeedbackUseCase
    UC ->> Room: ArticleEntity.body と DigestEntity.aiQuestion を取得
    UC ->> Gemini: 「記事全文・問い・回答を踏まえて理解度を評価しフィードバックしてください」
    Gemini -->> UC: フィードバック ＋ 理解度判定（OK/NG）
    UC ->> Room: DigestEntity 保存（memo + feedback + isUnderstandingSufficient）
    UC -->> VM: FeedbackResult(feedback, isUnderstandingSufficient)
    VM -->> UI: StateFlow更新 → フィードバック表示

    alt isUnderstandingSufficient == true
        UI ->> UI: 「消化する」ボタンを有効化
    else isUnderstandingSufficient == false
        UI ->> UI: 「消化する」ボタン無効、再回答を促すメッセージ表示
    end

    Note over User, Room: 【消化完了（理解十分の場合のみ）】
    User ->> UI: 「消化する」ボタンタップ
    UI ->> VM: digestArticle(articleId)
    VM ->> UC: DigestArticleUseCase
    UC ->> Room: ArticleEntity.status = DONE
    UC -->> VM: 完了
    VM -->> UI: 血肉リストへ遷移
```

### FeedbackResult — フィードバックと理解度評価のペア

`GenerateFeedbackUseCase` は文字列ではなく以下のデータクラスを返す:

```kotlin
data class FeedbackResult(
    val feedback: String,               // ユーザーへのフィードバック本文
    val isUnderstandingSufficient: Boolean,  // 理解度十分かどうか
)
```

`AiRepositoryImpl` は Gemini から以下の形式のレスポンスを期待し、パースする:

```
JUDGMENT: OK
FEEDBACK: <フィードバック本文>
```

または:

```
JUDGMENT: NG
FEEDBACK: <不足している点の指摘と再挑戦を促すメッセージ>
```

- `JUDGMENT: OK` → `isUnderstandingSufficient = true` → 「消化する」ボタン有効
- `JUDGMENT: NG` → `isUnderstandingSufficient = false` → 「消化する」ボタン無効

---

## 6. オフライン対応フロー

```mermaid
flowchart TD
    Launch([起動・一覧取得]) --> CheckNet{ネット接続確認\nConnectivityManager}

    CheckNet -->|オンライン| FetchQiita[Qiita API呼び出し]
    FetchQiita -->|成功| SaveRoom[Room に保存]
    SaveRoom --> ShowList[一覧表示]

    FetchQiita -->|失敗| ShowError[Snackbarでエラー通知]
    ShowError --> LoadCache[RoomキャッシュをFlow取得]
    LoadCache --> ShowList

    CheckNet -->|オフライン| ShowOffline[Snackbar\nオフライン通知]
    ShowOffline --> LoadCache
```

---

## 7. 積読スロット状態管理

```mermaid
stateDiagram-v2
    [*] --> PORTAL : 記事取得

    PORTAL --> TSUNDOKU : 積読に追加\n(スロット空き ≤ 4)
    PORTAL --> PORTAL_LOCKED : 積読スロット満杯\n(5件到達)

    PORTAL_LOCKED --> TSUNDOKU : 積読追加不可\nポータルはロック表示

    TSUNDOKU --> DONE : 消化フロー完了\n(「消化する」ボタン)

    DONE --> [*] : 血肉リストへ
```
