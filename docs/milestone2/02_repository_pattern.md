# Repository パターンとデータフロー（issue #9）

## このドキュメントで学ぶこと

- Repository パターンとは何か・なぜ使うか
- インターフェースと実装を分ける設計
- `Flow` を使ったリアルタイムデータ監視
- `Result<T>` を使ったエラーハンドリング

---

## 1. Repository パターンとは

```
                ┌──────────┐
  ViewModel ──→ │   API    │  → ??? どうやってデータを取る？
                └──────────┘
                ┌──────────┐
  ViewModel ──→ │   DB     │  → ??? どっちを見ればいい？
                └──────────┘
```

ViewModel が「どこからデータを取るか」を知っていると、ViewModel が複雑になります。
Repository は「データの統一窓口」として機能します。

```
                ┌────────────────────────────┐
  ViewModel ──→ │  ArticleRepository         │
                │  (唯一の窓口)               │
                │   ├── QiitaApiService ──→ 🌐│
                │   └── ArticleDao      ──→ 💾│
                └────────────────────────────┘
```

ViewModel は「どこからデータが来るか」を気にしなくていい。Repository に任せる。

---

## 2. ArticleRepository — インターフェース

```kotlin
// data/repository/ArticleRepository.kt
interface ArticleRepository {

    /** ポータル記事をFlowで監視する */
    fun observePortalArticles(): Flow<List<ArticleEntity>>

    /** 積読記事をFlowで監視する */
    fun observeTsundokuArticles(): Flow<List<ArticleEntity>>

    /**
     * Qiita APIから記事を取得してRoomに保存する。
     * @return 成功時は Result.success、APIエラー等の場合は Result.failure
     */
    suspend fun refreshPortalArticles(query: String? = null): Result<Unit>

    /** 記事のステータスを更新する */
    suspend fun updateStatus(id: String, status: String)

    /** スロット上限を守りながら記事のステータスを更新する */
    suspend fun updateStatusIfSlotAvailable(
        id: String,
        newStatus: String,
        currentStatus: String,
        maxSlots: Int,
    ): Boolean
}
```

### なぜ interface にするのか

**テストのため**が最大の理由です。

```kotlin
// テスト時は FakeRepository を使える
class FakeArticleRepository : ArticleRepository {
    override fun observePortalArticles() = flowOf(listOf(dummyArticle))
    override suspend fun refreshPortalArticles(...) = Result.success(Unit)
    // ...
}

// 本番時は ArticleRepositoryImpl を使う（Hilt が注入）
```

interface があれば、実際の API や DB を使わずに高速なテストが書けます。

---

## 3. ArticleRepositoryImpl — 実装

```kotlin
// data/repository/ArticleRepositoryImpl.kt
@Singleton
class ArticleRepositoryImpl @Inject constructor(
    private val apiService: QiitaApiService,
    private val articleDao: ArticleDao,
) : ArticleRepository {

    override fun observePortalArticles(): Flow<List<ArticleEntity>> =
        articleDao.observeByStatus(ArticleStatus.PORTAL)

    override fun observeTsundokuArticles(): Flow<List<ArticleEntity>> =
        articleDao.observeByStatus(ArticleStatus.TSUNDOKU)

    override suspend fun refreshPortalArticles(query: String?): Result<Unit> = runCatching {
        val cachedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        val entities = apiService.getItems(
            page = DEFAULT_PAGE,
            perPage = DEFAULT_PER_PAGE,
            query = query,
        ).mapNotNull { dto ->
            dto.toEntityOrNull(status = ArticleStatus.PORTAL, cachedAt = cachedAt)
        }

        articleDao.deleteByStatus(ArticleStatus.PORTAL)
        articleDao.insertAll(entities)
    }

    // ... 省略
}
```

### `@Singleton` とは

アプリ全体で **インスタンスが1つだけ** になることを保証するアノテーション。

```
PortalViewModel    ──→  ArticleRepositoryImpl (同じインスタンス)
TsundokuViewModel  ──→  ArticleRepositoryImpl (同じインスタンス)
DigestViewModel    ──→  ArticleRepositoryImpl (同じインスタンス)
```

DB への接続を使い回せるためメモリ効率が良い。

### `runCatching` — 例外を Result に変換

```kotlin
override suspend fun refreshPortalArticles(query: String?): Result<Unit> = runCatching {
    // ブロック内で例外が発生すると...
    apiService.getItems(...)  // ← ここで IOException が発生したら
    // ...
}
// → Result.failure(IOException) が返る（クラッシュしない）
```

```kotlin
// runCatching を使わない場合（例外が上に伝播する）
override suspend fun refreshPortalArticles(query: String?): Result<Unit> {
    try {
        apiService.getItems(...)
        return Result.success(Unit)
    } catch (e: Exception) {
        return Result.failure(e)
    }
}
```

`runCatching { }` は `try-catch` を簡潔に書ける Kotlin 標準ライブラリの関数。

### `SimpleDateFormat` を毎回インスタンス化する理由

```kotlin
val cachedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
    .format(Date())
```

`SimpleDateFormat` はスレッドセーフではありません。
複数のコルーチンが同時に使い回すと壊れるため、毎回 `new` しています。

---

## 4. Flow — リアルタイムデータ監視

### `observePortalArticles()` の仕組み

```kotlin
override fun observePortalArticles(): Flow<List<ArticleEntity>> =
    articleDao.observeByStatus(ArticleStatus.PORTAL)
```

Room の `@Query` が `Flow` を返すと、**DB が更新されるたびに自動で新しい値が流れてきます**。

```
DB に新しい記事が insertAll される
        ↓
observeByStatus() が変化を検知
        ↓
Flow が新しい List<ArticleEntity> を emit
        ↓
ViewModel の StateFlow が更新
        ↓
Compose が recompose して UI が更新される
```

### Flow と LiveData の違い

| 観点 | Flow | LiveData |
|------|------|----------|
| 用途 | データ層〜ViewModel | ViewModel〜UI |
| スレッド安全性 | コルーチン上で動く | メインスレッドで動く |
| 現在の推奨 | ✅ Kotlin/Compose 推奨 | △ XML View 時代の技術 |

---

## 5. アトミックなスロット制限

```kotlin
// ArticleDao.kt
@Query("""
    UPDATE articles SET status = :newStatus
    WHERE id = :articleId
    AND (SELECT COUNT(*) FROM articles WHERE status = :currentStatus) < :maxSlots
""")
suspend fun updateStatusIfSlotAvailable(
    articleId: String,
    newStatus: String,
    currentStatus: String,
    maxSlots: Int,
): Int
```

### なぜ SQL で一発でやる必要があるのか

**TOCTOU（Time Of Check, Time Of Use）競合**を防ぐためです。

```kotlin
// ❌ 危険な実装
val count = dao.count()          // (1) チェック
if (count < 5) {
    // ここで別のコルーチンが同時に実行されたら？
    dao.updateStatus(id, status)  // (2) 使用
}
// → 2つのコルーチンが同時に (1) を実行 → 両方 count < 5 と判断 → 6件目が入る
```

```kotlin
// ✅ 安全な実装（SQL で一発処理）
dao.updateStatusIfSlotAvailable(...)
// → SQLite のロック機構によりアトミックに実行される
// → 同時に2つのコルーチンが呼んでも、片方しか成功しない
```

---

## まとめ

| パターン | 用途 | この実装 |
|---------|------|---------|
| Repository | データの統一窓口 | ArticleRepository |
| interface + impl | テスト容易性 | ArticleRepository + ArticleRepositoryImpl |
| Result<T> | エラーを型で表現 | refreshPortalArticles の戻り値 |
| Flow | リアルタイム監視 | observePortalArticles |
| Atomic SQL | 競合防止 | updateStatusIfSlotAvailable |
