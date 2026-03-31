# テスト評価レポート

生成日: 2026-03-05

---

## 対象ファイル一覧

| ファイル | 種別 | テスト数 |
|---------|------|---------|
| `GeminiApiServiceTest` | API Service (MockWebServer) | 6 |
| `QiitaApiServiceTest` | API Service (MockWebServer) | 2 |
| `AiRepositoryImplTest` | Repository | 10 |
| `AiRepositoryIntegrationTest` | 統合テスト (実 API) | 3 |
| `ArticleRepositoryImplTest` | Repository | 9 |
| `AddToTsundokuUseCaseTest` | UseCase | 4 |
| `DigestArticleUseCaseTest` | UseCase | 6 |
| `GenerateFeedbackUseCaseTest` | UseCase | 8 |
| `GenerateQuestionUseCaseTest` | UseCase | 9 |
| `GenerateSummaryUseCaseTest` | UseCase | 8 |
| `GetDoneArticlesUseCaseTest` | UseCase | 8 |
| `GetTrendArticlesUseCaseTest` | UseCase | 9 |
| `DigestViewModelTest` | ViewModel | 20 |
| `DoneDetailViewModelTest` | ViewModel | 8 |
| `DoneViewModelTest` | ViewModel | 5 |
| `PortalViewModelTest` | ViewModel | 11 |
| `TsundokuViewModelTest` | ViewModel | 5 |
| **合計** | | **131** |

---

## 総合評価

**評価: A- (優秀)**

命名規則・MockK の使い方・状態遷移テスト・WhileSubscribed の挙動テストなど、
テストの基本習慣が全体を通じて一貫して実践されている。
品質番人として機能するテストが多い。
以下に細部の改善点を記す。

---

## 良い点

### 1. 命名規則の統一
バッククォート日本語の `` `操作 - 条件 - 期待結果` `` 形式が全体を通じて統一されている。
テストが落ちたとき何が壊れているかが即座にわかる。

### 2. MainDispatcherRule の適切な適用
全 ViewModel テストに `@get:Rule val mainDispatcherRule = MainDispatcherRule()` が適用されており、
Coroutines の時間制御が正確に行われている。

### 3. WhileSubscribed の挙動テスト
`TsundokuViewModelTest`, `DoneViewModelTest`, `PortalViewModelTest` で
`advanceTimeBy(5_001L)` を使った購読ライフサイクルのテストが書かれており、
Flow のメモリリークを検知できる構造になっている。

### 4. エラーマッピングテスト
`ArticleRepositoryImplTest` で 401 → `Unauthorized`、429 → `RateLimitExceeded`、
IOException → `NetworkError` と HTTP エラーコードごとに型チェックが明示されており、
エラーハンドリングの回帰を確実に検知できる。

### 5. slot<> によるデータフローの検証
`ArticleRepositoryImplTest` で `slot<List<ArticleEntity>>()` によるキャプチャを使い、
API レスポンスが正しく DAO に渡されることを検証している。

### 6. キャッシュロジックのテスト
`GenerateQuestionUseCaseTest`, `GenerateSummaryUseCaseTest` で DB キャッシュがある場合は
API を呼ばないことと、ない場合は呼んで保存することの両方がテストされている。

### 7. 状態遷移テスト（DigestViewModelTest）
WebLoading → ReadMode → QuestionMode → FeedbackMode の全フローをカバー。
isLoading フラグの中間状態確認（`runCurrent()` 呼び出し前の確認）も書かれている。

---

## 問題点・改善点

### [P1] AiRepositoryImplTest: HTTP エラー時の型チェック漏れ

**該当テスト:**
- `` `generateQuestion - レートリミットエラー時にResult_failureが返る` ``
- `` `generateFeedback - レートリミットエラー時にResult_failureが返る` ``

**問題:**
```kotlin
// 現状: isFailure の確認のみ
assertTrue(result.isFailure)

// あるべき姿: AppError 型まで確認
assertTrue(result.isFailure)
assertTrue(result.exceptionOrNull() is AppError.RateLimitExceeded)
```

`ArticleRepositoryImplTest` では 429 → `AppError.RateLimitExceeded` まで型確認しているが、
`AiRepositoryImplTest` では型確認がない。同型原理に違反している。

---

### [P2] DigestArticleUseCaseTest: 同一 Arrange の繰り返しによる冗長性

**該当テスト (4件):**
- `` `invoke - DigestEntityにメモが保存される` ``
- `` `invoke - DigestEntityにフィードバックが保存される` ``
- `` `invoke - DigestEntityのarticleIdが正しい` ``
- `` `invoke - DigestEntityのsavedAtが設定される` ``

**問題:**
全く同じ Arrange で、Assert のフィールドだけが異なる。
テストを落とさない限り検知できない「どのフィールドを検証しているか」が分散している。

```kotlin
// 4テストを1つに統合できる
@Test
fun `invoke - DigestEntityが正しい内容で保存される`() = runTest {
    val slot = slot<DigestEntity>()
    coEvery { digestRepository.saveDigest(capture(slot)) } returns Unit
    coEvery { articleRepository.updateStatus(any(), any()) } returns Unit

    useCase("article1", memo = "テストメモ", feedback = "テストフィードバック")

    assertEquals("article1", slot.captured.articleId)
    assertEquals("テストメモ", slot.captured.userMemo)
    assertEquals("テストフィードバック", slot.captured.aiFeedback)
    assertTrue(slot.captured.savedAt.isNotBlank())
}
```

---

### [P3] GetDoneArticlesUseCaseTest: フィールド検証の分散

**該当テスト (4件):**
- `` `invoke - DoneItemにarticleのタイトルが設定される` ``
- `` `invoke - DoneItemにuserMemoが設定される` ``
- `` `invoke - DoneItemにaiFeedbackが設定される` ``
- `` `invoke - DoneItemにsavedAtが設定される` ``

P2 と同じ問題。結合ロジックの検証であれば1テストにまとめ、
残りは `` `invoke - 消化済み記事と消化記録が結合されてDoneItemのリストが返る` `` に吸収できる。

---

### [P4] DoneDetailViewModelTest: フィールド検証の分散

**該当テスト (3件):**
- `` `init - Successのタイトルが正しい` ``
- `` `init - SuccessのuserMemoが正しい` ``
- `` `init - SuccessのaiFeedbackが正しい` ``

同じ Arrange で Assert だけ異なる。1テストに統合可能。

---

### [P5] AiRepositoryIntegrationTest: runBlocking の使用

**問題:**
```kotlin
// 現状
fun generateQuestion_実際のAPIを呼んで問いが返る() = runBlocking {

// あるべき姿
fun generateQuestion_実際のAPIを呼んで問いが返る() = runTest {
```

`runBlocking` は Coroutines のテストツール（時間制御、仮想クロック）を使えない。
統合テストでも `runTest` に統一することでテスト基盤が揃う。

---

### [P6] GeminiApiServiceTest / QiitaApiServiceTest: Retrofit 動作テストの混入

**該当テスト:**
- `` `generateContent - 正しいパスにリクエストが送信される` ``
- `` `generateContent - POSTメソッドでリクエストが送信される` ``

これらは Retrofit の `@POST` アノテーションが正しく動くかを確認しているだけで、
アノテーションを変更しない限り壊れないテスト。
「壊れたら UX が崩壊する」ロジックではないため、削除候補。

`firstText()` のパース確認や `candidates` の空チェックは意味がある。

---

### [P7] PortalViewModelTest: tsundokuCount の WhileSubscribed テストが不完全

**該当テスト:**
`` `tsundokuCount - 購読者がいる間だけUseCaseのFlowを購読する` ``

```kotlin
job2.cancel()
runCurrent()
// ここで advanceTimeBy(5_001L) と subscriptionCount の確認がない
```

`TsundokuViewModelTest` や `DoneViewModelTest` では 5秒後の停止まで確認しているが、
このテストは停止確認が抜けている。

---

## カバレッジギャップ（未テスト領域）

| 領域 | 状況 | 推奨アクション |
|-----|------|--------------|
| DAO テスト | 全て未作成 | InMemory Room テストを追加（`@RunWith(AndroidJUnit4::class)`）|
| `SearchViewModel` (feat/search ブランチ) | 未確認 | ブランチ確認後に追加 |
| `ObserveTsundokuArticlesUseCase` | 未作成 | 単純な Repository 委譲のみなら不要 |
| `ObserveDoneArticlesUseCase` | 未作成 | 同上 |
| `GetArticleUseCase` | 未作成 | 同上 |
| `ObserveTsundokuCountUseCase` | 未作成 | 同上 |

### DAO テストの優先度が高い理由
- `ArticleRepositoryImplTest` では `updateStatusIfSlotAvailable` のスロット制限ロジックを Mock で検証しているが、
  実際の SQL クエリが正しく動くかは未検証
- `insertAll` で既存ステータスが保護されるかは DAO レベルで保証されていない

---

## サマリー

| カテゴリ | 件数 | 評価 |
|---------|------|-----|
| 削除を推奨するテスト | 2 | GeminiApiServiceTest の Retrofit 動作確認テスト |
| 統合を推奨するテスト | 7 | DigestArticleUseCaseTest + GetDoneArticlesUseCaseTest + DoneDetailViewModelTest のフィールド分散 |
| 型チェック漏れ | 2 | AiRepositoryImplTest の 429 エラーテスト |
| 修正推奨 | 2 | AiRepositoryIntegrationTest の `runBlocking`、PortalViewModelTest の WhileSubscribed |
| 追加を推奨するテスト | DAO テスト | 実動検証が存在しない |
