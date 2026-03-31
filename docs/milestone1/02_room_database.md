# 02. Room によるローカルデータベース

## Room とは？

SQLite の上に構築された Android 公式の ORM（Object-Relational Mapper）。
SQL を直接書きながら、Kotlin オブジェクトとの変換を自動化してくれる。

```
┌─────────────────────────────────────┐
│            Room 全体像              │
│                                     │
│  @Entity ──► テーブル定義           │
│  @Dao    ──► CRUD操作               │
│  @Database──► DB全体の管理          │
└─────────────────────────────────────┘
```

---

## エンティティ（テーブル定義）

### ArticleEntity.kt — 記事テーブル

```kotlin
@Entity(tableName = "articles")         // ← テーブル名を指定
data class ArticleEntity(
    @PrimaryKey val id: String,         // ← 主キー（記事ID）
    val title: String,                  // タイトル
    val url: String,                    // 記事URL
    val tags: String,                   // タグ（カンマ区切りの文字列）
    val createdAt: String,              // 記事の作成日時
    val cachedAt: String,               // ローカルにキャッシュした日時
    val status: String,                 // "tsundoku" / "done" など
    val aiSummary: String? = null,      // AI要約（nullable）
)
```

**対応する SQL テーブル**:
```sql
CREATE TABLE articles (
    id        TEXT PRIMARY KEY,
    title     TEXT NOT NULL,
    url       TEXT NOT NULL,
    tags      TEXT NOT NULL,
    createdAt TEXT NOT NULL,
    cachedAt  TEXT NOT NULL,
    status    TEXT NOT NULL,
    aiSummary TEXT           -- NULL OK
);
```

**設計ポイント**:
- `id` は `String` 型（API から UUID が来ることを想定）
- `tags` は正規化せずカンマ区切り文字列で保存（シンプルさ優先）
- `aiSummary` は `null` 許容（まだ生成していない状態を表現）
- `status` は文字列で管理（"tsundoku" / "done" など）

---

### DigestEntity.kt — 消化メモテーブル

```kotlin
@Entity(
    tableName = "digests",
    foreignKeys = [
        ForeignKey(
            entity = ArticleEntity::class,  // 参照先テーブル
            parentColumns = ["id"],          // 参照先の列
            childColumns = ["articleId"],    // このテーブルの列
            onDelete = ForeignKey.CASCADE,   // 記事が消えたらメモも消える
        ),
    ],
    indices = [Index(value = ["articleId"])],  // 検索を高速化
)
data class DigestEntity(
    @PrimaryKey val articleId: String,  // 記事IDが主キー（1記事1メモ）
    val aiQuestion: String? = null,     // AIが生成した問い
    val userMemo: String? = null,       // ユーザーの自由メモ
    val aiFeedback: String? = null,     // AI からのフィードバック
    val savedAt: String,                // 保存日時
)
```

**外部キー（ForeignKey）の説明**:

```
articles テーブル          digests テーブル
┌─────────────────┐        ┌──────────────────┐
│ id  (PK)        │◄───────│ articleId  (PK)  │
│ title           │        │ aiQuestion       │
│ ...             │        │ userMemo         │
└─────────────────┘        │ aiFeedback       │
                           │ savedAt          │
                           └──────────────────┘
    記事が削除されると CASCADE で digest も削除
```

**`indices` の役割**:
- `articleId` で検索する SQL（`WHERE articleId = ?`）を高速化
- インデックスがないと全行スキャンになり遅い

---

## DAO（データアクセスオブジェクト）

### ArticleDao.kt

```kotlin
@Dao
interface ArticleDao {

    // 1件挿入（同じIDがあれば上書き）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(article: ArticleEntity)

    // 複数件まとめて挿入（APIから取得した記事一覧を保存する場合など）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(articles: List<ArticleEntity>)

    // 更新（idが一致する行を全フィールド上書き）
    @Update
    suspend fun update(article: ArticleEntity)

    // 削除
    @Delete
    suspend fun delete(article: ArticleEntity)

    // ID で1件取得（存在しない場合は null）
    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: String): ArticleEntity?

    // 全件をリアルタイム監視（Flow）
    @Query("SELECT * FROM articles ORDER BY cachedAt DESC")
    fun observeAll(): Flow<List<ArticleEntity>>
    //  ↑ suspend なし ＝ Flow はコルーチン内で collect する

    // ステータスでフィルタしてリアルタイム監視
    @Query("SELECT * FROM articles WHERE status = :status ORDER BY cachedAt DESC")
    fun observeByStatus(status: String): Flow<List<ArticleEntity>>

    // ステータスだけ更新（エンティティ全体を渡さなくていい）
    @Query("UPDATE articles SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    // AI要約だけ更新
    @Query("UPDATE articles SET aiSummary = :summary WHERE id = :id")
    suspend fun updateSummary(id: String, summary: String)
}
```

**`suspend` と `Flow` の違い**:

| | `suspend fun` | `fun ... : Flow<...>` |
|--|---------------|----------------------|
| 実行 | 1回だけ | DB変更のたびに自動で再発行 |
| 使い所 | 書き込み・1回の読み取り | UIに継続的に表示したいデータ |
| 呼び出し | コルーチン内で直接呼ぶ（`await` 不要） | `collect` / `collectAsState` |

**`OnConflictStrategy.REPLACE` の動作**:
```
同じ id の行が既にある場合 → 古い行を削除して新しい行を挿入
同じ id の行がない場合    → そのまま挿入
```

---

### DigestDao.kt

```kotlin
@Dao
interface DigestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(digest: DigestEntity)

    @Update
    suspend fun update(digest: DigestEntity)

    @Delete
    suspend fun delete(digest: DigestEntity)

    // articleId で1件取得
    @Query("SELECT * FROM digests WHERE articleId = :articleId")
    suspend fun getByArticleId(articleId: String): DigestEntity?

    // 全件をリアルタイム監視
    @Query("SELECT * FROM digests ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<DigestEntity>>
}
```

---

## AppDatabase.kt — DB全体の定義

```kotlin
@Database(
    entities = [ArticleEntity::class, DigestEntity::class],  // 管理するテーブル一覧
    version = 1,                // スキーマバージョン（変更時はインクリメント）
    exportSchema = false,       // スキーマJSONを出力しない（CI不要なら false でOK）
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao   // DAO を取得するメソッド
    abstract fun digestDao(): DigestDao
}
```

**バージョン管理の注意点**:
- テーブル構造を変えるときは `version` を上げる必要がある
- `fallbackToDestructiveMigration()` を使っているので、バージョンが上がると **DBが全消去される**
- 本番では `addMigrations(...)` でデータを保持するマイグレーションを書く

---

## Room の動作フロー

```
コード（DAO呼び出し）
    │
    ▼
ArticleDao.observeByStatus("tsundoku")
    │
    ▼
Room が SQL を実行
SELECT * FROM articles WHERE status = 'tsundoku' ORDER BY cachedAt DESC
    │
    ▼
SQLite (tech_digest.db ファイル)
    │
    ▼
Flow<List<ArticleEntity>> として結果を emit
    │
    ▼
ViewModel が collect → UI が更新
```

---

## テーブル間の関係

```
articles                digests
━━━━━━━━━━━━━           ━━━━━━━━━━━━━━━━━━
id (PK)  ◄──────────── articleId (PK, FK)
title                   aiQuestion
url                     userMemo
tags                    aiFeedback
status                  savedAt
aiSummary
cachedAt

1記事 : 0 or 1メモ（消化したらメモが生まれる）
```

---

## つまずきポイント

### Q: `@Query` の `:paramName` の書き方は？
→ 関数の引数名と一致させる。`fun getById(id: String)` なら `:id`。

### Q: `Flow` を使うとき `suspend` は不要？
→ そう。`Flow` 自体が非同期なので `suspend` は付けない。

### Q: `@Insert` と `@Update` の違いは？
→ `@Insert` は新規行追加、`@Update` は既存行の全フィールド更新（主キーで検索）。

### Q: `fallbackToDestructiveMigration()` は危険？
→ 開発中は便利だが、本番では使わない。ユーザーデータが全消えする。
