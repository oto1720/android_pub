# Milestone 5 全体像

## 何を作ったか

Milestone 4 で消化フロー（読了宣言 → 問い → フィードバック）の UI とロジックが完成しましたが、
問いとフィードバックはプレースホルダーでした。
Milestone 5 では **Gemini API** を統合して、実際の AI 生成コンテンツを届けます。

また仕様変更として、**AI は記事タイトルではなく記事全文を読んで** 要約・問い・フィードバックを生成します。
さらに AI がユーザーの理解度を評価し、**理解が不十分な場合は「消化する」ボタンを無効化する読了ゲート**を導入します。

| 機能 | Milestone 4 まで | Milestone 5 以降 |
|------|-----------------|-----------------|
| 問い | ハードコードされた固定文字列 | Gemini が**記事全文**から内容に即した問いを生成 |
| フィードバック | ハードコードされた固定文字列 | Gemini が問い+メモ+**記事全文**からフィードバックを生成 |
| 要約 | 未実装 | Gemini が**記事全文**から重要ポイントを3行で要約 |
| 読了ゲート | なし（フィードバック後は常に「消化する」有効） | AI が理解度を評価し、不十分なら「消化する」を無効化 |

## アーキテクチャ全体図

```
┌─────────────────────────────────────────────────────────┐
│  Domain 層 (UseCase)                                    │
│                                                         │
│   GenerateSummaryUseCase   → 3行要約（DBキャッシュ優先）  │
│   GenerateQuestionUseCase  → 理解度チェックの問い         │
│   GenerateFeedbackUseCase  → メモへのフィードバック+評価  │
└──────────────────┬──────────────────────────────────────┘
                   │ interface
┌──────────────────▼──────────────────────────────────────┐
│  Data 層 (Repository)                                   │
│                                                         │
│   AiRepository (interface)                              │
│      └── AiRepositoryImpl (@Singleton)                  │
│             └── GeminiApiService (Retrofit) ──→ 🤖 Gemini│
│                  POST /v1/models/{model}:generateContent │
└─────────────────────────────────────────────────────────┘
          │（生成結果を DB に保存）
          ▼
┌─────────────────────────────────────────────────────────┐
│  Data 層 (ローカルDB)                                    │
│                                                         │
│   ArticleEntity.body       ← 記事全文（AI への入力）     │
│   ArticleEntity.aiSummary  ← 要約を保存                  │
│   DigestEntity.aiQuestion  ← 問いを保存                  │
│   DigestEntity.aiFeedback  ← フィードバックを保存        │
│   DigestEntity.isUnderstandingSufficient ← 理解度評価   │
└─────────────────────────────────────────────────────────┘
```

## データフロー（問いを生成するとき）

```
1. 「読了宣言」ボタンを押す
        ↓
2. DigestViewModel.showQuestion() が呼ばれる
        ↓
3. GenerateQuestionUseCase.invoke(articleId) が実行される
        ↓
4. ArticleRepository.getArticleById(articleId) で body を取得
        ↓
5. DigestRepository.getByArticleId(articleId) で DB を確認
        ↓
6a. aiQuestion が DB に保存済み
        → Result.success(aiQuestion) を即時返す（API 呼び出しなし）
6b. aiQuestion が未保存
        → AiRepository.generateQuestion(articleBody) を呼ぶ  ← タイトルでなく本文
        → Gemini API に POST リクエスト（本文全文を含む）
        → 生成された問いを DigestEntity.aiQuestion に保存
        → Result.success(question) を返す
        ↓
7. DigestViewModel が QuestionMode(question = ...) に状態遷移
        ↓
8. DigestScreen が問いを表示
```

## データフロー（フィードバック＋理解度評価）

```
1. ユーザーが1行メモを入力し「送信」を押す
        ↓
2. DigestViewModel.submitMemo(memo) が呼ばれる
        ↓
3. GenerateFeedbackUseCase.invoke(articleId, question, userMemo) が実行される
        ↓
4. ArticleRepository.getArticleById(articleId) で body を取得
        ↓
5. Gemini API にリクエスト（body + question + userMemo を含む）
        ↓
6. Gemini が以下の形式で回答:
        JUDGMENT: OK  ← または NG
        FEEDBACK: <フィードバック本文>
        ↓
7. AiRepositoryImpl がパース → FeedbackResult(feedback, isUnderstandingSufficient)
        ↓
8. DigestEntity に memo + feedback + isUnderstandingSufficient を保存
        ↓
9. DigestViewModel が FeedbackMode(result = ...) に状態遷移
        ↓
10. isUnderstandingSufficient == true  → 「消化する」ボタン有効
    isUnderstandingSufficient == false → ボタン無効・再回答を促すメッセージ表示
```

## ファイル構成

```
app/src/main/java/com/example/oto1720/dojo2026/
│
├── data/
│   ├── remote/
│   │   ├── api/
│   │   │   └── GeminiApiService.kt     # Gemini API の Retrofit インターフェース
│   │   └── dto/
│   │       └── GeminiDto.kt            # リクエスト / レスポンス DTO
│   └── repository/
│       ├── AiRepository.kt             # AI 操作の interface（FeedbackResult を含む）
│       └── AiRepositoryImpl.kt         # プロンプト組み立て・API 呼び出し・レスポンスパース
│
├── di/
│   ├── NetworkModule.kt                # Gemini 専用 OkHttpClient / Retrofit 追加
│   └── RepositoryModule.kt             # AiRepository の bind 追加
│
└── domain/
    └── usecase/
        ├── GenerateSummaryUseCase.kt   # 3行要約（キャッシュ付き・全文使用）
        ├── GenerateQuestionUseCase.kt  # 問い生成（キャッシュ付き・全文使用）
        └── GenerateFeedbackUseCase.kt  # フィードバック生成＋理解度評価
```

## Milestone 4 との違い

| 観点 | Milestone 4 | Milestone 5 |
|------|------------|------------|
| 問い・フィードバック | プレースホルダー文字列 | Gemini API で動的生成 |
| AI 入力 | （なし） | 記事全文（body）をプロンプトに渡す |
| 理解度評価 | なし | Gemini が OK/NG を判定・DB保存 |
| 読了ゲート | なし | isUnderstandingSufficient で「消化する」を制御 |
| ネットワーク | Qiita API（1種類）| Qiita API + Gemini API（2種類） |
| DI の複雑さ | 単一 OkHttpClient | `@Named` で2種類の OkHttpClient を使い分け |
| エラーハンドリング | API 失敗 = Error 状態 | `runCatching` で Result にラップして UseCase に返す |
| DB 活用 | 記事・消化記録の保存のみ | AI 生成結果・理解度評価をキャッシュとして DB に保存 |
