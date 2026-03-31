# DigestArticleUseCase・消化実行とスロット解放（issue #18）

## このドキュメントで学ぶこと

- 複数の Repository にまたがる UseCase の実装
- `DigestEntity` への消化記録の保存
- 「DONE に更新する = スロットが自動解放される」設計の理解

---

## 1. DigestArticleUseCase 全体

```kotlin
// domain/usecase/DigestArticleUseCase.kt
class DigestArticleUseCase @Inject constructor(
    private val articleRepository: ArticleRepository,
    private val digestRepository: DigestRepository,
) {
    /**
     * @param articleId 消化する記事のID
     * @param memo ユーザーが入力したメモ
     * @param feedback AIが生成したフィードバック
     */
    suspend operator fun invoke(
        articleId: String,
        memo: String,
        feedback: String,
    ) {
        val savedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

        // 1. 消化記録を DigestEntity として保存
        digestRepository.saveDigest(
            DigestEntity(
                articleId = articleId,
                userMemo = memo,
                aiFeedback = feedback,
                savedAt = savedAt,
            )
        )

        // 2. 記事の status を DONE に更新（スロット解放）
        articleRepository.updateStatus(id = articleId, status = ArticleStatus.DONE)
    }

    companion object {
        private const val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    }
}
```

### 2つの Repository を使う UseCase

この UseCase は `ArticleRepository` と `DigestRepository` の両方を使います。

```
DigestArticleUseCase
  ├── digestRepository.saveDigest()   → digests テーブルに記録を保存
  └── articleRepository.updateStatus() → articles テーブルの status を更新
```

UseCase 層が「複数のデータソースへの操作を1つのビジネスアクションとして束ねる」役割を担っています。

---

## 2. スロット解放の仕組み

「消化する」と積読スロットが解放される理由を理解します。

```
積読スロットの「使用数」 = articles テーブルで status = 'TSUNDOKU' な記事の数

消化前:
  articles: [id=A, status=TSUNDOKU], [id=B, status=TSUNDOKU], ... → 4件
  ObserveTsundokuCountUseCase → tsundokuCount = 4

消化後（DigestArticleUseCase を実行）:
  articles: [id=A, status=DONE], [id=B, status=TSUNDOKU], ... → 3件 TSUNDOKU
  ObserveTsundokuCountUseCase → tsundokuCount = 3  ← 自動的に減少
```

`status` を `TSUNDOKU` から `DONE` に変えるだけで、カウントが自動的に減ります。
`ObserveTsundokuCountUseCase` が `Flow` で監視しているため、UI も自動更新されます。

---

## 3. DigestEntity と DigestRepository

```kotlin
// data/local/entity/DigestEntity.kt（Milestone 1 で定義済み）
@Entity(tableName = "digests")
data class DigestEntity(
    @PrimaryKey val articleId: String,
    val userMemo: String,
    val aiFeedback: String,
    val savedAt: String,
)
```

```kotlin
// data/repository/DigestRepository.kt
interface DigestRepository {
    suspend fun saveDigest(digest: DigestEntity)
}

// data/repository/DigestRepositoryImpl.kt
class DigestRepositoryImpl @Inject constructor(
    private val digestDao: DigestDao,
) : DigestRepository {
    override suspend fun saveDigest(digest: DigestEntity) {
        digestDao.insert(digest)
    }
}
```

`@PrimaryKey` が `articleId` なので、同じ記事を再度消化しようとすると上書きされます
（`@Insert(onConflict = OnConflictStrategy.REPLACE)` の場合）。

---

## 4. ISO 8601 日時フォーマット

```kotlin
val savedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
    .format(Date())

// ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
// 出力例: "2026-03-04T12:30:00Z"
```

### なぜ `Locale.US` を指定するのか

`SimpleDateFormat` をロケールなしで使うと、端末のロケール設定によって
数字が異なる文字（アラビア数字など）に変換されることがあります。
`Locale.US` を指定することで常に ASCII 数字を使います。

### なぜ UTC を指定するのか

DB に保存する日時は「タイムゾーンに依存しない形式」で統一します。
タイムゾーンが混在すると比較・ソートが複雑になるためです。

### `.apply { timeZone = ... }` パターン

`SimpleDateFormat` はタイムゾーンを `timeZone` プロパティで設定します。
`.apply {}` を使うことで、生成と設定をまとめて書けます。

```kotlin
// 冗長な書き方
val sdf = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
sdf.timeZone = TimeZone.getTimeZone("UTC")
val savedAt = sdf.format(Date())

// apply を使った書き方（同等）
val savedAt = SimpleDateFormat(ISO_8601_FORMAT, Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
    .format(Date())
```

---

## まとめ：UseCase が複数 Repository をまたぐ意義

```
ViewModel から直接呼ぶ場合（悪い例）:
  viewModelScope.launch {
      digestRepository.saveDigest(...)   // ← Repository A
      articleRepository.updateStatus(...)  // ← Repository B
  }

UseCase を使う場合（良い例）:
  viewModelScope.launch {
      digestArticleUseCase(articleId, memo, feedback)
      // ← 「消化する」という1つのビジネスアクションとして表現
  }
```

UseCase を使うことで:
1. ViewModel が「何をするか」だけを知り、「どうやるか」を知らなくて済む
2. 「消化する」処理の詳細（どの Repository に何を保存するか）が UseCase にカプセル化される
3. テスト時に UseCase だけをモックすれば ViewModel のテストができる
