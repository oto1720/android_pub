# AddToTsundokuUseCase・アトミックなスロット管理（issue #13）

## このドキュメントで学ぶこと

- UseCase の責務と実装パターン
- 「スロット満杯チェック → 書き込み」を**アトミック**に行う理由
- Room の `@Query` を使ったカスタム SQL

---

## 1. AddToTsundokuUseCase 全体

```kotlin
// domain/usecase/AddToTsundokuUseCase.kt
class AddToTsundokuUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    companion object {
        const val MAX_TSUNDOKU_SLOTS = 5
    }

    /**
     * @param articleId 追加する記事のID
     * @return 追加成功 true、スロット満杯で追加不可 false
     */
    suspend operator fun invoke(articleId: String): Boolean {
        return repository.updateStatusIfSlotAvailable(
            id = articleId,
            newStatus = ArticleStatus.TSUNDOKU,
            slotCountStatus = ArticleStatus.TSUNDOKU,
            maxSlots = MAX_TSUNDOKU_SLOTS,
        )
    }
}
```

### ポイント解説

#### `companion object` に定数を置く理由

`MAX_TSUNDOKU_SLOTS = 5` は UseCase が「ビジネスルールとして定義している定数」です。
UI 側からもこの定数を参照することで、ハードコーディングによる不整合を防ぎます。

```kotlin
// TsundokuScreen.kt — UseCase の定数を参照している
private val MAX_TSUNDOKU_SLOTS = AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS
```

「5」という数字の変更が必要になったとき、UseCase の1箇所だけ直せば UI にも反映されます。

#### `operator fun invoke` とは

`invoke` を `operator` で定義すると、インスタンスを関数のように呼び出せます。

```kotlin
// UseCase を関数のように使える
val added = addToTsundokuUseCase(articleId)

// 内部的には invoke() が呼ばれている
val added = addToTsundokuUseCase.invoke(articleId)
```

UseCase クラスは「このクラスは1つの操作を行う」という設計思想なので、`invoke` が自然にフィットします。

---

## 2. アトミックなスロットチェックとは

### なぜアトミックでなければいけないのか

「2ステップに分けると競合が起きる」という問題があります。

```
❌ 非アトミックな実装（問題あり）
Step 1: SELECT COUNT(*) FROM articles WHERE status = 'TSUNDOKU'  → 4件
Step 2: UPDATE articles SET status = 'TSUNDOKU' WHERE id = ...

↑ Step 1 と Step 2 の間に別の操作が割り込んだら？

ユーザー A の Step 1: 4件 → OK
ユーザー B の Step 1: 4件 → OK  ← 同時に確認してしまう
ユーザー A の Step 2: TSUNDOKU に更新 → 5件に
ユーザー B の Step 2: TSUNDOKU に更新 → 6件に！← 上限オーバー
```

### アトミックな SQL で解決

```kotlin
// data/local/dao/ArticleDao.kt
@Query("""
    UPDATE articles SET status = :newStatus
    WHERE id = :articleId
    AND (SELECT COUNT(*) FROM articles WHERE status = :slotCountStatus) < :maxSlots
""")
suspend fun updateStatusIfSlotAvailable(
    articleId: String,
    newStatus: String,
    slotCountStatus: String,
    maxSlots: Int,
): Int
```

「チェックと更新」を1つの SQL 文で実行することで、競合を防ぎます。

```
SQL の動き:
  UPDATE ... WHERE id = :articleId          ← 対象の記事
  AND (SELECT COUNT(*) ...) < :maxSlots    ← AND 条件で同時にカウント確認

→ この SQL は DB が1つのトランザクションで実行する
→ 「チェックと更新」の間に割り込む余地がない = アトミック
```

### 戻り値で成否を判断する

```kotlin
// ArticleRepositoryImpl.kt
override suspend fun updateStatusIfSlotAvailable(...): Boolean =
    articleDao.updateStatusIfSlotAvailable(...) > 0

// DAOの戻り値（Int）: 更新した行数
//   1 → WHERE 条件に一致して更新できた（成功）
//   0 → WHERE 条件に一致しなかった（スロット満杯 or 記事が存在しない）
```

---

## 3. ObserveTsundokuArticlesUseCase

```kotlin
// domain/usecase/ObserveTsundokuArticlesUseCase.kt
class ObserveTsundokuArticlesUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    operator fun invoke(): Flow<List<ArticleEntity>> =
        repository.observeTsundokuArticles()
}
```

### `suspend` がついていない理由

`Flow` を返す関数は `suspend` にしません。

```
suspend fun → コルーチン内で一時停止しながら結果を1回返す
fun → Flow を返す（冷たい Stream。collect するたびに購読が始まる）
```

`observeTsundokuArticles()` は「リアルタイムに変化を監視する Flow」を返すため、
`suspend` は不要です。Room が DB の変化を検知するたびに新しいリストを流してくれます。

---

## よくある疑問

**Q: UseCase がこんなに薄いなら、ViewModel から直接 Repository を呼べばいいのでは？**

UseCase 層を挟む理由:
1. **ビジネスルールの集約**: `MAX_TSUNDOKU_SLOTS = 5` という上限のルールが UseCase に明示される
2. **テストしやすさ**: ViewModel のテストで UseCase をモックできる
3. **将来の変更に強い**: 「積読追加時にログも取る」等の要件追加時、UseCase だけ変えればよい
