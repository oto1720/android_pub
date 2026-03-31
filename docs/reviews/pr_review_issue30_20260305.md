# PR レビューレポート

**PR/ブランチ**: issue30
**レビュー日時**: 2026-03-05
**変更規模**: +2329 / -287 / 24ファイル

---

## 変更の概要

テスト品質向上を目的としたリファクタリングPR。「1テスト1観点」で書かれていた冗長なユニットテストを統合・整理し、インメモリ Room を使った DAO 実動テスト（`ArticleDaoTest` / `DigestDaoTest`）を新規追加した。あわせて、Gemini API への直接通信に依存する統合テスト（`AiRepositoryIntegrationTest`）と、フレームワークの挙動確認にすぎないテストを削除。テスト哲学ドキュメント（`testing-philosophy.md`、`test-evaluation.md`）と Milestone 6・7 の設計ドキュメントも追加されている。

**変更種別**:
- [ ] 新機能 (Feature)
- [ ] バグ修正 (Bug Fix)
- [x] リファクタリング (Refactoring)
- [x] テスト (Test)
- [x] ドキュメント (Docs)
- [ ] その他

---

## マージ判定

> **[APPROVE]**

テスト削除の方向性は `docs/testing-philosophy.md` に明文化された哲学と一致しており、整合性がとれている。DAO 実動テストの追加は SQL ロジック（スロット制限・外部キー CASCADE）の回帰を検知できる実質的な価値がある。ただし、`.kotlin/errors/` ログファイルのコミットと、`TsundokuScreen.kt` のアニメーション変更が未コミットのままローカルに残っている点は確認を要する。

---

## 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `.claude/skills/android-test-generator/SKILL.md` | 追加 | 281 | 0 | 🟡 |
| `.kotlin/errors/errors-1772705462573.log` | 追加 | 4 | 0 | 🔴 |
| `app/src/androidTest/.../ArticleDaoTest.kt` | 追加 | 255 | 0 | 🟢 |
| `app/src/androidTest/.../DigestDaoTest.kt` | 追加 | 184 | 0 | 🟢 |
| `app/src/test/.../GeminiApiServiceTest.kt` | 変更 | 0 | 23 | 🟡 |
| `app/src/test/.../AiRepositoryImplTest.kt` | 変更 | 0 | 19 | 🟡 |
| `app/src/test/.../AiRepositoryIntegrationTest.kt` | 削除 | 0 | 88 | 🟢 |
| `app/src/test/.../AddToTsundokuUseCaseTest.kt` | 変更 | 0 | 16 | 🟡 |
| `app/src/test/.../DigestArticleUseCaseTest.kt` | 変更 | 2 | 44 | 🟢 |
| `app/src/test/.../GenerateFeedbackUseCaseTest.kt` | 変更 | 0 | 13 | 🟡 |
| `app/src/test/.../GenerateQuestionUseCaseTest.kt` | 変更 | 0 | 12 | 🟡 |
| `app/src/test/.../GenerateSummaryUseCaseTest.kt` | 変更 | 0 | 11 | 🟡 |
| `app/src/test/.../GetDoneArticlesUseCaseTest.kt` | 変更 | 1 | 38 | 🟢 |
| `app/src/test/.../DoneDetailViewModelTest.kt` | 変更 | 1 | 23 | 🟢 |
| `docs/milestone6/00_overview.md` | 追加 | 95 | 0 | 🟢 |
| `docs/milestone6/01_network_monitor.md` | 追加 | 219 | 0 | 🟢 |
| `docs/milestone6/02_offline_snackbar.md` | 追加 | 225 | 0 | 🟢 |
| `docs/milestone6/README.md` | 追加 | 23 | 0 | 🟢 |
| `docs/milestone7/00_overview.md` | 追加 | 103 | 0 | 🟢 |
| `docs/milestone7/01_app_error.md` | 追加 | 221 | 0 | 🟢 |
| `docs/milestone7/02_ai_loading.md` | 追加 | 268 | 0 | 🟢 |
| `docs/milestone7/README.md` | 追加 | 32 | 0 | 🟢 |
| `docs/test-evaluation.md` | 追加 | 227 | 0 | 🟢 |
| `docs/testing-philosophy.md` | 追加 | 188 | 0 | 🟢 |

---

## 詳細レビュー

### 1. `.kotlin/errors/errors-1772705462573.log` — 懸念度: 🔴

**問題**: Kotlin コンパイラのエラーログがコミットに含まれている。

```
kotlin version: 2.0.21
error message: The daemon has terminated unexpectedly on startup attempt #1 with error code: 0.
```

これは開発環境の一時ファイルであり、リポジトリに含める意味はない。`.gitignore` に `.kotlin/` が含まれているか確認が必要。本ファイルはマージ前に除外すべき。

---

### 2. `ArticleDaoTest.kt` — 懸念度: 🟢

Room インメモリ DB を用いた実動テストとして正しく実装されている。特に `updateStatusIfSlotAvailable` に対して境界値（0件・上限ちょうど・上限-1件・対象ID不在）を網羅している点は高評価。

```kotlin
@Test
fun updateStatusIfSlotAvailable_スロットが満杯のとき_0を返し更新されない() = runTest {
    // Arrange: TSUNDOKU を maxSlots 件まで埋める
    repeat(5) { i -> dao.insert(buildArticle("existing_$i", status = ArticleStatus.TSUNDOKU)) }
    dao.insert(buildArticle("newcomer", status = ArticleStatus.PORTAL))

    // Act
    val updated = dao.updateStatusIfSlotAvailable(
        articleId = "newcomer",
        newStatus = ArticleStatus.TSUNDOKU,
        slotCountStatus = ArticleStatus.TSUNDOKU,
        maxSlots = 5,
    )

    // Assert: 更新されない
    assertEquals(0, updated)
    assertEquals(ArticleStatus.PORTAL, dao.getById("newcomer")?.status)
}
```

テスト名が一部バッククォート日本語ではなくスネークケース形式（例: `updateStatusIfSlotAvailable_スロットが空いているとき_1を返し更新される`）で書かれており、プロジェクトの命名規則（`` `操作 - 条件 - 期待結果` `` 形式）と若干ずれている。機能的には問題ないが統一するとよい。

---

### 3. `DigestDaoTest.kt` — 懸念度: 🟢

外部キー制約（CASCADE DELETE）を実際に検証しているテストは価値が高い。

```kotlin
@Test
fun `記事を削除するとDigestEntityも自動で削除される`() = runTest {
    val article = buildArticle("a1")
    articleDao.insert(article)
    digestDao.insert(buildDigest("a1"))
    assertNotNull(digestDao.getByArticleId("a1")) // 前提確認

    articleDao.delete(article)

    assertNull(digestDao.getByArticleId("a1"))
}
```

こちらはバッククォート日本語形式で統一されており、命名規則も良好。`setUp()` での `allowMainThreadQueries()` も適切。

---

### 4. `GeminiApiServiceTest.kt` — 懸念度: 🟡

削除された2件のテストは「フレームワークの HTTP メソッドとパスが正しく設定されているか」を検証するものだった。

```kotlin
// 削除されたテスト（抜粋）
@Test
fun `generateContent - 正しいパスにリクエストが送信される`() = runTest {
    ...
    assertEquals("/v1/models/gemini-2.0-flash:generateContent", request.path?.substringBefore("?"))
}

@Test
fun `generateContent - POSTメソッドでリクエストが送信される`() = runTest {
    ...
    assertEquals("POST", request.method)
}
```

哲学ドキュメントの「フレームワーク自体の動作テスト」に該当するため削除は妥当。ただし URL パスの変更を意図せず行った場合に検知する手段がなくなる点はトレードオフとして認識しておく必要がある。

---

### 5. `AiRepositoryIntegrationTest.kt` (削除) — 懸念度: 🟢

実際の Gemini API を呼ぶ統合テストであり、`Assume.assumeTrue(apiKey.isNotBlank())` で API キー未設定時はスキップする設計だった。CI での安定性・ネットワーク依存という問題を考えると削除は合理的。

```kotlin
// 削除されたクラス（概要）
class AiRepositoryIntegrationTest {
    // local.properties に GEMINI_API_KEY が必要
    // ネットワーク接続が必要
    // CI では常にスキップされる実質的に意味のないテスト
}
```

---

### 6. UseCase テスト群（複数ファイル）— 懸念度: 🟡

`AddToTsundokuUseCaseTest`・`GenerateFeedbackUseCaseTest`・`GenerateQuestionUseCaseTest`・`GenerateSummaryUseCaseTest` において、「API / Repository を N 回呼ぶ」という呼び出し回数の検証テストが削除された。

```kotlin
// 削除されたテスト例（AddToTsundokuUseCaseTest）
@Test
fun `invoke - updateStatusIfSlotAvailableを1回だけ呼ぶ`() = runTest {
    coEvery { repository.updateStatusIfSlotAvailable(any(), any(), any(), any()) } returns true
    useCase("article1")
    coVerify(exactly = 1) { repository.updateStatusIfSlotAvailable(any(), any(), any(), any()) }
}
```

呼び出し回数テストはロジックの重複呼び出しバグを検知できる面もあるため、削除による網羅性の低下は意識する必要がある。一方でプロジェクトの「Mock だらけで実際のロジックを何も検証しないテストは書かない」という哲学とは整合している。

`AddToTsundokuUseCaseTest` では定数確認テストも削除された。

```kotlin
// 削除されたテスト（AddToTsundokuUseCaseTest）
@Test
fun `MAX_TSUNDOKU_SLOTS - 上限は5件`() {
    assertEquals(5, AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS)
}
```

この定数は DAO テスト（`ArticleDaoTest`）の `maxSlots = 5` でハードコードされているため、定数値が変わっても DAO テストは通ってしまう。定数値変更の検知がやや難しくなった点は留意する。

---

### 7. `DigestArticleUseCaseTest.kt` / `GetDoneArticlesUseCaseTest.kt` / `DoneDetailViewModelTest.kt` — 懸念度: 🟢

「1テスト1アサーション」を複数のアサーションを持つ1テストに統合するリファクタリングで、コードの重複が大幅に削減された。

```kotlin
// Before（4つのテストが別々）
fun `invoke - DigestEntityにメモが保存される`() ...
fun `invoke - DigestEntityにフィードバックが保存される`() ...
fun `invoke - DigestEntityのarticleIdが正しい`() ...
fun `invoke - DigestEntityのsavedAtが設定される`() ...

// After（1つのテストに統合）
@Test
fun `invoke - DigestEntityが正しい内容で保存される`() = runTest {
    ...
    assertEquals("article1", slot.captured.articleId)
    assertEquals("テストメモ", slot.captured.userMemo)
    assertEquals("テストフィードバック", slot.captured.aiFeedback)
    assertTrue(slot.captured.savedAt.isNotBlank())
}
```

統合後のテストは1つ失敗すると後続の assertEquals が実行されない点（JUnit 4 の fail-fast）は意識しておく価値があるが、実用上は問題ない。

---

### 8. `.claude/skills/android-test-generator/SKILL.md` — 懸念度: 🟡

Claude Code のカスタムスキル定義ファイル。テスト生成の指示書として丁寧に書かれており、「書かないテスト」の明示や ViewModel / Repository / DAO / UseCase ごとのテンプレートが整備されている。

```markdown
### 絶対に書かないテスト（生成禁止）
- data class の getter テスト
- Room Entity の構造テスト
- Retrofit の疎通確認テスト
- フレームワーク自体の動作テスト
- Mock だらけで実際のロジックを何も検証しないテスト
```

このファイルをリポジトリに含めることの是非はチームポリシーによるが、AI ツールの設定ファイルをプロジェクトで共有することへの合意が必要。`.claude/` ディレクトリが `.gitignore` から除外されているか確認を推奨する。

---

### 9. ドキュメント群（docs/milestone6, milestone7, test-evaluation, testing-philosophy）— 懸念度: 🟢

Milestone 6（ネットワーク監視・オフライン Snackbar）と Milestone 7（アプリエラー・AI ローディング）の設計ドキュメントが整理されている。`testing-philosophy.md` によりテスト選択の判断基準が明文化されており、今後のテスト追加・削除の議論がしやすくなった。

---

### 10. 未コミット変更（`git diff HEAD` より）— 懸念度: 🔴

`TsundokuScreen.kt` にアニメーション追加の変更がローカルに残っており、このブランチには含まれていない。

```kotlin
// 未コミット変更（git diff HEAD より）
itemsIndexed(
    items = slots,
    key = { index, article -> article?.id ?: "empty-$index" },
) { index, article ->
    AnimatedContent(
        targetState = article,
        label = "slot-$slotNumber",
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(300)) + expandVertically(tween(300))) togetherWith fadeOut(tween(150))
            } else {
                fadeIn(tween(300, delayMillis = 200)) togetherWith
                    (fadeOut(tween(200)) + shrinkVertically(tween(300)))
            }
        },
    ) { ... }
}
```

このアニメーション変更が issue30 のスコープに含まれるなら別途コミットが必要。含まれないならブランチを切り替える際にスタッシュするか、別ブランチに移動すること。

---

## 影響範囲

| 影響ファイル/領域 | 影響の種類 |
|----------------|-----------|
| `app/src/androidTest/` (新規) | 端末 or エミュレータが必要な androidTest として追加。CI の設定（Gradle ワークフロー）がこれを実行するか要確認 |
| `AiRepositoryIntegrationTest` (削除) | 実 API テストの削除。手動確認の代替手段があるか確認 |
| `.claude/skills/` (新規) | チーム全員の Claude Code 環境に影響。スキルが意図せず発火する可能性を確認 |
| `docs/milestone6/`, `docs/milestone7/` (新規) | 設計資料。既存の `milestone1`〜`milestone5` ドキュメント体系と整合 |
| `docs/test-evaluation.md`, `docs/testing-philosophy.md` (新規) | テストスタンダードの明文化。他の開発者への周知が必要 |

---

## テスト確認

| テスト種別 | 対象 | 状態 | 備考 |
|-----------|------|------|------|
| DAO 実動テスト (androidTest) | `ArticleDaoTest` | 新規追加 | CI でのエミュレータ実行設定を確認 |
| DAO 実動テスト (androidTest) | `DigestDaoTest` | 新規追加 | CASCADE DELETE の検証を含む |
| ユニットテスト削減 | `GeminiApiServiceTest` | 2件削除 | フレームワーク動作テスト削除（意図的） |
| ユニットテスト削減 | `AiRepositoryImplTest` | 2件削除 | 呼び出し回数テスト削除（意図的） |
| ファイル削除 | `AiRepositoryIntegrationTest` | 全削除 | 実 API テスト廃止（意図的） |
| ユニットテスト削減 | `AddToTsundokuUseCaseTest` | 2件削除 | 呼び出し回数・定数確認テスト削除 |
| ユニットテスト統合 | `DigestArticleUseCaseTest` | 4→1件に統合 | アサーション数は維持 |
| ユニットテスト削減 | `GenerateFeedbackUseCaseTest` | 1件削除 | API 呼び出し回数テスト削除 |
| ユニットテスト削減 | `GenerateQuestionUseCaseTest` | 1件削除 | API 呼び出し回数テスト削除 |
| ユニットテスト削減 | `GenerateSummaryUseCaseTest` | 1件削除 | API 呼び出し回数テスト削除 |
| ユニットテスト統合 | `GetDoneArticlesUseCaseTest` | 4→1件に統合 | アサーション数は維持 |
| ユニットテスト統合 | `DoneDetailViewModelTest` | 3→1件に統合 | アサーション数は維持 |

---

## レビューコメント（コピペ用）

```
レビューありがとうございます。全体的に「価値のあるテストだけを残す」という哲学が一貫しており、testing-philosophy.md による判断基準の明文化も含めて良い変更だと思います。

【必須対応】
1. `.kotlin/errors/errors-1772705462573.log` がコミットに含まれています。Kotlin コンパイラの一時ログファイルなので `.gitignore` に `.kotlin/` を追加してリポジトリから除外してください。

2. `TsundokuScreen.kt` のアニメーション変更（AnimatedContent の追加）がローカルに未コミットのまま残っています。この変更が issue30 のスコープに含まれるかどうか確認してください。含まれる場合はコミットを、含まれない場合はスタッシュや別ブランチへの移動をお願いします。

【確認事項】
3. `ArticleDaoTest` と `DigestDaoTest` は `androidTest`（端末/エミュレータが必要）として追加されています。CI（GitHub Actions 等）でエミュレータ実行が設定されているか確認してください。

4. `.claude/skills/android-test-generator/SKILL.md` をリポジトリに含める判断についてチームで合意できていますか？Claude Code を使っていないメンバーには関係のないファイルになります。

【Good Point】
- `ArticleDaoTest` の `updateStatusIfSlotAvailable` に対する境界値テスト（0件・満杯・満杯-1・ID不在）は実装上の最も重要なロジックを正確に守っています。
- `DigestDaoTest` の外部キー CASCADE DELETE テストは Room スキーマ変更時の回帰を確実に検知できます。
- 4つあった DigestArticleUseCase のテストを1つに統合したリファクタリングはコードの重複を適切に除去しています。
```

---

## チェックリスト

- [x] 変更の概要が理解できる
- [x] テスト哲学に基づいた削除・統合であることが確認できる
- [x] DAO 実動テストが適切に実装されている（InMemory Room、tearDown あり）
- [ ] `.kotlin/errors/` ログファイルの除外 (要対応)
- [ ] `TsundokuScreen.kt` の未コミット変更の扱いを決める (要確認)
- [ ] androidTest の CI 実行設定を確認 (要確認)
- [ ] `.claude/skills/` のリポジトリ収録についてチームで合意 (要確認)
- [x] ドキュメントが整理されている（milestone6/7、testing-philosophy、test-evaluation）
- [x] 命名規則が概ね守られている（一部 ArticleDaoTest でスネークケース混在）

*Generated by Claude Code / pr-review skill*
