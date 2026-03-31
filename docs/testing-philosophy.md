# テスト実装の哲学・理由・トレードオフ

---

## なぜテストを書くのか

テストは「コードが動く証拠」ではなく、**「壊れたときに即座に気づける仕組み」** を作ることが目的。

### 具体的な効果

| 効果 | 説明 |
|-----|------|
| **回帰防止** | リファクタリングや機能追加で既存の挙動を壊したとき、CI が即座に検知する |
| **設計の圧力** | テストが書きにくいコードは「依存が多すぎる・責務が大きすぎる」設計のサイン |
| **ドキュメント代替** | テスト名が仕様書になる（例: `updateStatusIfSlotAvailable - スロットが満杯のとき - 0を返し更新されない`） |
| **安全なリファクタリング** | テストが通る限り、内部実装を自由に変えられる |

---

## このプロジェクトのテスト戦略

### テストピラミッド

```
         /\
        /  \
       / E2E\        ← 書かない（コストが高い・壊れやすい）
      /------\
     / Integr.\     ← DAO テスト（InMemory Room）
    /----------\
   / Unit Tests \   ← ViewModel / Repository / UseCase
  /--------------\
```

### 各レイヤーで「何を守るか」

| レイヤー | テスト種別 | 守るもの |
|---------|----------|---------|
| DAO | 統合テスト（InMemory Room） | SQL クエリのロジック・外部キー制約・Flow |
| Repository | 単体テスト（MockK） | エラーマッピング・ステータス保護・データフロー |
| UseCase | 単体テスト（MockK） | ビジネスルール（スロット制限・キャッシュ制御） |
| ViewModel | 単体テスト（MockK） | UiState 遷移・Event 発行・Flow 購読ライフサイクル |

---

## なぜ Mock を使うのか

Mock（MockK）を使う理由は「依存を制御して、テスト対象だけを検証するため」。

```kotlin
// Repository が「API が 429 を返したとき AppError.RateLimitExceeded に変換する」ロジックを検証
coEvery { apiService.getItems(any(), any(), any()) } throws HttpException(response429)

val result = repository.refreshPortalArticles(null)

assertTrue(result.exceptionOrNull() is AppError.RateLimitExceeded)
```

実際の API を呼んだら:
- ネットワークに依存する（CI で不安定）
- レート制限を実際に踏む
- テストが遅い（数秒〜十数秒）

---

## なぜ DAO だけ「実動テスト」なのか

DAO の SQL クエリ（特に `updateStatusIfSlotAvailable`）は Mock では検証できない。

```sql
-- この SQL が本当に「スロット満杯のとき何もしない」かは、
-- 実際に SQLite に流して確認しないとわからない
UPDATE articles SET status = :newStatus
WHERE id = :articleId
AND (SELECT COUNT(*) FROM articles WHERE status = :slotCountStatus) < :maxSlots
```

InMemory Room DB を使えば:
- 実際の SQLite で動く
- テストが終わればメモリ上から消える（副作用なし）
- ネットワーク不要でローカル実行できる

---

## トレードオフ

### 単体テスト（Mock）

| メリット | デメリット |
|---------|----------|
| 高速（ミリ秒） | Mock の設定が実際の挙動と乖離する可能性 |
| ネットワーク不要 | 「Mock が通った」だけで「実際に動く」保証にならない |
| 特定ロジックを孤立して検証できる | Mock が増えすぎると「何をテストしているかわからない」テストになる |

### 統合テスト（InMemory Room）

| メリット | デメリット |
|---------|----------|
| 実際の DB で動く | 単体テストより遅い（数百ミリ秒） |
| SQL のバグを確実に検知 | Android コンテキストが必要（`androidTest` で実行） |
| 外部キー制約・CASCADE も検証できる | エミュレータまたは実機が必要 |

### 書かないテストのコスト

| テストの種類 | 書かない理由 |
|------------|------------|
| 定数テスト（`assertEquals(5, MAX_SLOTS)`）| 定数が変わったらテストも直すだけ。壊れても UX に影響しない |
| 「1回だけ呼ぶ」テスト | 正常系テストが通れば「呼ばれた」は自明 |
| Retrofit アノテーション確認 | フレームワーク自体の動作保証。Retrofit が壊れたらアノテーション以外も全滅する |
| data class フィールドチェック | フィールドを変えたらコンパイルエラーで気づく |
| 実 API を叩く統合テスト | ネットワーク依存・レート消費・CI で不安定 |

**テストを書くコスト（作成 + メンテナンス）が、テストで得られる品質保証を下回るとき、そのテストは書かない。**

---

## このプロジェクトで「絶対守りたい」テスト

以下は壊れたら即座に UX / データ整合性が崩壊するため、絶対テストする。

### 1. スロット制限ロジック（`ArticleDaoTest`）

```kotlin
// 積読スロットが満杯のとき、追加できないことを SQL レベルで検証
fun `updateStatusIfSlotAvailable - スロットが満杯のとき - 0を返し更新されない`()
```

**なぜ重要か**: ここが壊れると「積読は5件まで」というコア仕様が崩壊する。

### 2. TSUNDOKU/DONE 記事の上書き保護（`ArticleRepositoryImplTest`）

```kotlin
// API からの新しいデータが既存の積読/消化記事を上書きしないことを検証
fun `refreshPortalArticles - 積読・消化済みの記事は上書きされない`()
```

**なぜ重要か**: ここが壊れるとユーザーの積読リストが API リフレッシュのたびに消える。

### 3. 外部キー CASCADE（`DigestDaoTest`）

```kotlin
// 記事を削除したとき、消化記録も自動で消えることを検証
fun `記事を削除するとDigestEntityも自動で削除される`()
```

**なぜ重要か**: ここが壊れるとゴースト消化記録が DB に残り続ける。

### 4. エラーマッピング（`ArticleRepositoryImplTest`）

```kotlin
// 429 → AppError.RateLimitExceeded に正しく変換されることを検証
fun `refreshPortalArticles - 429エラー時にAppError_RateLimitExceededを返す`()
```

**なぜ重要か**: ここが壊れると Qiita API のレート制限時にユーザーに適切なエラーを表示できない。

---

## テストを書くときの判断基準

```
このテストが落ちたとき、何が壊れているかが即座にわかるか？
         ↓
YES → 書く価値がある
NO  → テスト名・範囲を見直す or 書かない
```

```
このテストは「壊れたら UX / データ整合性が崩壊する」ロジックを守っているか？
         ↓
YES → 書く
NO  → コスト > ベネフィット。書かない
```

---

## テスト実行コマンド

```bash
# 単体テスト（JVM 上で実行）
./gradlew test

# DAO 統合テスト（エミュレータ or 実機が必要）
./gradlew connectedAndroidTest

# 特定クラスのみ実行
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.oto1720.dojo2026.data.local.dao.ArticleDaoTest
```
