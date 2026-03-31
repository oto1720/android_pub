# PR レビューレポート

**PR/ブランチ**: issue4 → main
**PR URL**: https://github.com/CyberAgentHack/dojo2026_android_otozu_kotaro/pull/34
**レビュー日時**: 2026-03-02
**変更規模**: +160 / -1 / 8ファイル（Room 関連のみ）

---

## 🎯 変更の概要

Room DB をローカルデータ永続化の基盤として新規導入する PR。
`ArticleEntity` / `DigestEntity` の定義、各 DAO の CRUD 実装、`AppDatabase` クラスの作成、Hilt の `AppModule` への DB バインディング登録まで一通りのセットアップが完了している。
`DigestEntity` には `ForeignKey(onDelete = CASCADE)` が設定されており、記事削除時の一貫性も確保されている。

**変更種別**:
- [x] 新機能 (Feature) — Room DB 基盤の導入
- [ ] バグ修正
- [ ] リファクタリング
- [ ] パフォーマンス改善
- [ ] テスト追加
- [ ] ドキュメント

---

## ✅ マージ判定

> **APPROVE（条件付き）**

Room のセットアップとして必要なコンポーネントが揃っており、実装に致命的な問題はない。
ForeignKey・Flow・suspend 関数の使い方はいずれも正しい。
Minor 指摘が複数あるが、DB 基盤のセットアップという目的の範囲では許容できる。
`tags` フィールドの型（String）は今後の絞り込みで扱いが難しくなる可能性があり、次フェーズで TypeConverter の検討を推奨する。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `gradle/libs.versions.toml` | Modified | +7 | 2 | 🟢 問題なし |
| `app/build.gradle.kts` | Modified | +7 | 1 | 🟢 問題なし |
| `entity/ArticleEntity.kt` | Added | +16 | - | 🟡 軽微 |
| `entity/DigestEntity.kt` | Added | +24 | - | 🟢 問題なし |
| `dao/ArticleDao.kt` | Added | +41 | - | 🟡 軽微 |
| `dao/DigestDao.kt` | Added | +29 | - | 🟢 問題なし |
| `data/local/AppDatabase.kt` | Added | +18 | - | 🟡 軽微 |
| `di/AppModule.kt` | Modified | +27 | 1 | 🟢 問題なし |

---

## 🔍 詳細レビュー

### entity/ArticleEntity.kt

#### 変更の意図
Qiita API から取得した記事をローカルキャッシュするための Room エンティティを定義。

#### 指摘事項

**[🟡 Mi-1] `tags` が `String` のみで保持されており、パース方法が未定義**

```kotlin
// ❌ 現状
val tags: String,
```

```kotlin
// ✅ 推奨案 A: JSON 文字列として保持 + TypeConverter
@TypeConverters(StringListConverter::class)
val tags: List<String>,

// ✅ 推奨案 B: 現状の String を維持しつつコメントで規約を明示
val tags: String,  // カンマ区切り例: "Android,Kotlin,Jetpack"
```

> Qiita API の `tags` はリスト形式で返ってくるため、Room への保存形式（JSON / カンマ区切り）を統一しないと、Repository 層でのパース処理が複数箇所で重複する恐れがある。今フェーズでは String のままでも許容できるが、保存規約をコメントで明示することを推奨。

**[🟡 Mi-2] `status` が String のため typo リスクがある**

```kotlin
// ❌ 現状
val status: String,
```

```kotlin
// ✅ 推奨: enum class + TypeConverter
enum class ArticleStatus { PORTAL, TSUNDOKU, DONE }

@TypeConverters(ArticleStatusConverter::class)
val status: ArticleStatus,
```

> 設計書に `PORTAL / TSUNDOKU / DONE` の 3 値が定義されているため、文字列リテラルの直打ちよりも enum で型安全にする方がベター。TypeConverter の追加は軽微な工数で対応可能。

---

### dao/ArticleDao.kt

#### 変更の意図
Article の CRUD と Flow ベースのリアルタイム監視クエリを提供。

#### 指摘事項

**[🟡 Mi-3] `observeByStatus` の引数が String のため typo リスク**

```kotlin
// ❌ 現状
fun observeByStatus(status: String): Flow<List<ArticleEntity>>
```

```kotlin
// ✅ enum 採用後
fun observeByStatus(status: ArticleStatus): Flow<List<ArticleEntity>>
```

> Mi-2 と連動。enum を採用すれば自然に解消する。

**[🟢 Good] `insertAll` で `REPLACE` 戦略を使用**

```kotlin
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertAll(articles: List<ArticleEntity>)
```

> キャッシュの更新を単一操作でべき等に行えるため、Qiita API レスポンスの同期処理に適している。

---

### data/local/AppDatabase.kt

#### 変更の意図
Room DB の入口となるクラスを定義。

#### 指摘事項

**[🟡 Mi-4] `exportSchema = false` は本番運用には非推奨**

```kotlin
// ❌ 現状
@Database(
    entities = [ArticleEntity::class, DigestEntity::class],
    version = 1,
    exportSchema = false,
)
```

```kotlin
// ✅ スキーマエクスポートを有効化 + パス設定
// app/build.gradle.kts に追加:
// ksp { arg("room.schemaLocation", "$projectDir/schemas") }

@Database(
    entities = [ArticleEntity::class, DigestEntity::class],
    version = 1,
    exportSchema = true,
)
```

> `exportSchema = true` にすると `schemas/` フォルダにスキーマが出力され、マイグレーション時の検証に使える。開発初期から有効にしておくことを推奨（version 上げ時に必須になる）。

---

### di/AppModule.kt

#### 変更の意図
Hilt の `SingletonComponent` に `AppDatabase` と各 DAO を登録。

#### 指摘事項

**[🟢 Good] `AppDatabase` に `@Singleton`、DAO には付与しない設計**

```kotlin
@Provides
@Singleton
fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase = ...

@Provides
fun provideArticleDao(db: AppDatabase): ArticleDao = db.articleDao()
```

> DB インスタンスはシングルトンで持ち、DAO は DB から都度取得する正しいパターン。

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `AppModule` — DB・DAO が DI グラフに追加され、今後の Repository 実装が注入可能になる
- 現時点で `ArticleDao` / `DigestDao` を直接参照するファイルは `AppModule` のみ（影響範囲は限定的）

**破壊的変更 (Breaking Change)**: **なし**

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| 既存テストがパスするか | ❓ 未確認 |
| Room DAO のユニットテストが追加されているか | ❌ 不足 |
| ForeignKey 制約の動作確認 | ❌ 不足 |

**追加すべきテストケース（推奨）**:
```kotlin
// app/src/androidTest/.../dao/ArticleDaoTest.kt
@RunWith(AndroidJUnit4::class)
class ArticleDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ArticleDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = db.articleDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun insert_and_getById() = runTest {
        val article = ArticleEntity(id = "1", title = "Test", ...)
        dao.insert(article)
        assertThat(dao.getById("1")).isEqualTo(article)
    }
}
```

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。APPROVE です。

Room のセットアップとして必要なコンポーネントが揃っており実装に致命的な問題はありません。
Minor 4件の指摘があります（Critical なし）。

主な改善推奨点:
1. `tags` / `status` フィールドを TypeConverter + enum で型安全にする（Mi-1, Mi-2）
2. `exportSchema = true` に変更し schemas/ を git 管理する（Mi-4）

詳細: docs/reviews/pr_review_issue4_20260302.md
```

**インラインコメント候補**:
- `ArticleEntity.kt:11`: `tags: String` の保存形式（JSON / カンマ区切り）をコメントで明示してください。将来的に `List<String>` + TypeConverter に移行することを推奨します。
- `ArticleEntity.kt:13`: `status` を enum class `ArticleStatus` にして TypeConverter を使うと typo リスクを排除できます。
- `AppDatabase.kt:12`: `exportSchema = false` → `true` に変更し、KSP の `room.schemaLocation` 引数を設定することを推奨します。

---

## ✅ チェックリスト

- [x] Critical issue がすべて解決されている（Critical なし）
- [ ] DAO のテストが追加されている（推奨）
- [x] 破壊的変更がある場合、マイグレーション手順が記載されている（破壊的変更なし）
- [x] ForeignKey の CASCADE 設定が設計書と一致している

---
*Generated by Claude Code / pr-review skill*
