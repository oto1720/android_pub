# UseCase 層のビジネスロジック（issue #10）

## このドキュメントで学ぶこと

- UseCase とは何か・なぜ分離するか
- `operator fun invoke()` パターン
- `CancellationException` の正しい扱い方
- Flow の変換（`map`, `coerceAtMost`）

---

## 1. UseCase とは

UseCase は「アプリが何をするか」を表すクラスです。

```
Repository → 「データをどう保存・取得するか」（方法）
UseCase    → 「ユーザーが何をするか」（目的）
```

### この実装で登場する UseCase

| UseCase | 目的 |
|---------|------|
| `GetTrendArticlesUseCase` | トレンド記事を取得してフィルタリングする |
| `ObserveTsundokuCountUseCase` | 積読中の記事数をリアルタイムで監視する |
| `AddToTsundokuUseCase` | 記事を積読スロットに追加する |

---

## 2. GetTrendArticlesUseCase — 記事取得

```kotlin
// domain/usecase/GetTrendArticlesUseCase.kt
class GetTrendArticlesUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    suspend operator fun invoke(tag: String? = null): Result<List<ArticleEntity>> {
        // Step 1: API からデータを取得して DB に保存
        val refreshResult = repository.refreshPortalArticles(query = tag?.let { "tag:$it" })
        if (refreshResult.isFailure) {
            @Suppress("UNCHECKED_CAST")
            return refreshResult as Result<List<ArticleEntity>>
        }

        // Step 2: DB からデータを読み取って返す
        return try {
            val articles = repository.observePortalArticles().first()
            val filtered = if (tag != null) {
                articles.filter { article ->
                    article.tags.split(",").any { t ->
                        t.trim().equals(tag, ignoreCase = true)
                    }
                }
            } else {
                articles
            }
            Result.success(filtered)
        } catch (e: CancellationException) {
            throw e  // ← コルーチンのキャンセルは必ず再スロー
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### `operator fun invoke()` とは

Kotlin の演算子オーバーロード機能。これを使うと**クラスを関数のように呼び出せます**。

```kotlin
// invoke を定義
operator fun invoke(tag: String? = null): Result<List<ArticleEntity>>

// 呼び出し方（2つの書き方は等価）
val result = getTrendArticlesUseCase.invoke(tag = "Android")
val result = getTrendArticlesUseCase(tag = "Android")  // ← 関数のように呼べる
```

UseCase に `invoke` を使う理由：
- UseCase には通常「1つの仕事」しかないため、メソッド名は不要
- `useCase()` という自然な呼び出し方ができる

### タグフィルタリングをクライアント側でやる理由

Qiita API のタグ検索は**部分一致**を含む場合があります。

```
API クエリ: tag:Android
API が返す可能性:
  - "Android"      ← 正しい
  - "AndroidTV"    ← 不要！
  - "Android Tips" ← 不要！
```

そのため、API でタグ絞り込みをした上で、**アプリ側でも完全一致フィルタリング**を行います。

```kotlin
// 完全一致フィルタリング
articles.filter { article ->
    article.tags.split(",")           // "Android,Kotlin" → ["Android", "Kotlin"]
        .any { t ->
            t.trim().equals(tag, ignoreCase = true)  // 大文字小文字無視で比較
        }
}
```

### `CancellationException` は必ず再スロー

```kotlin
} catch (e: CancellationException) {
    throw e  // ← これは絶対に catch したままにしてはダメ
} catch (e: Exception) {
    Result.failure(e)
}
```

コルーチンがキャンセルされると `CancellationException` が発生します。
これを捕まえて `Result.failure` にしてしまうと、**コルーチンがキャンセルされたことが上位に伝わらなくなります**。

```
タグを素早く切り替える
    → 古いコルーチンがキャンセルされる
    → CancellationException が発生
    → 再スローしないと: 古い処理が「エラーで終了」として扱われる ❌
    → 再スローすると: 古い処理が「キャンセル」として正しく終了される ✅
```

---

## 3. ObserveTsundokuCountUseCase — 積読数監視

```kotlin
// domain/usecase/ObserveTsundokuCountUseCase.kt
class ObserveTsundokuCountUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    operator fun invoke(): Flow<Int> = repository
        .observeTsundokuArticles()
        .map { it.size.coerceAtMost(MAX_TSUNDOKU_SLOTS) }

    companion object {
        private const val MAX_TSUNDOKU_SLOTS = 5
    }
}
```

### `suspend` が付いていない理由

このメソッドは `Flow` を**返すだけ**です。実際のデータ取得は Flow を collect した時点で始まります。

```kotlin
// suspend なし: Flow オブジェクトを返すだけ（即座に返る）
operator fun invoke(): Flow<Int> = ...

// suspend あり: データを取得するまで待機する
suspend fun invoke(): Int = ...
```

### `map { it.size.coerceAtMost(MAX_TSUNDOKU_SLOTS) }` の動き

```
DB から TSUNDOKU 記事が流れてくる
  ↓
.map { articles -> articles.size.coerceAtMost(5) }
  ↓
記事リスト [A, B, C, D, E, F] → 6   → coerceAtMost(5) → 5
記事リスト [A, B, C]           → 3   → coerceAtMost(5) → 3
記事リスト []                  → 0   → coerceAtMost(5) → 0
```

`coerceAtMost(5)` は「5 を超えたら 5 にする」関数。
万が一 DB に 5 件超えが入っても UI で正しく表示できるようにする防御処理。

---

## 4. AddToTsundokuUseCase — 積読追加

```kotlin
// domain/usecase/AddToTsundokuUseCase.kt
class AddToTsundokuUseCase @Inject constructor(
    private val repository: ArticleRepository,
) {
    companion object {
        const val MAX_TSUNDOKU_SLOTS = 5  // public: PortalScreen も参照する
    }

    suspend operator fun invoke(articleId: String): Boolean {
        return repository.updateStatusIfSlotAvailable(
            id = articleId,
            newStatus = ArticleStatus.TSUNDOKU,
            currentStatus = ArticleStatus.TSUNDOKU,  // スロット数カウントに使うステータス
            maxSlots = MAX_TSUNDOKU_SLOTS,
        )
    }
}
```

### companion object の `const val`

```kotlin
companion object {
    const val MAX_TSUNDOKU_SLOTS = 5  // public にして他のクラスから参照可能
}

// 参照例
AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS  // 5
```

`MAX_TSUNDOKU_SLOTS` を1か所だけに定義することで、「5 件」という仕様変更があっても1箇所を直すだけで済みます。

---

## 5. UseCase の設計原則まとめ

```
✅ UseCase がやること
  - ビジネスロジック（フィルタリング・バリデーション・上限チェック）
  - Repository の呼び出し順序の制御
  - 複数の Repository をまたいだ処理

❌ UseCase がやってはいけないこと
  - UI の状態管理（ViewModel の仕事）
  - DB 接続・HTTP 通信（Repository/DAO の仕事）
  - Android フレームワークの知識（Context など）
```

### 依存方向

```
UI 層 (PortalViewModel)
    ↓ 依存
Domain 層 (GetTrendArticlesUseCase)
    ↓ 依存
Data 層 (ArticleRepository)
```

上位のレイヤーは下位のレイヤーの interface に依存する。
実装（Impl）には依存しない。これが Clean Architecture の核心。

---

## Flow 変換チートシート

このプロジェクトで使われている Flow の変換一覧：

| 演算子 | 用途 | 使用箇所 |
|--------|------|---------|
| `.map { }` | 各値を変換 | ObserveTsundokuCountUseCase |
| `.first()` | Flow から最初の値を1つ取得 | GetTrendArticlesUseCase |
| `.filter { }` | 条件に一致するものだけ残す | GetTrendArticlesUseCase (List) |
| `.stateIn(...)` | Flow → StateFlow に変換 | PortalViewModel |
