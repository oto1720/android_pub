# Qiita API 通信と DTO 設計（issue #8）

## このドキュメントで学ぶこと

- Retrofit でどうやって API を呼び出すか
- API のレスポンスを安全に扱う「DTO パターン」
- null 安全な型変換の書き方

---

## 1. QiitaApiService — API の入り口

### コード全体

```kotlin
// data/remote/api/QiitaApiService.kt
interface QiitaApiService {
    @GET("api/v2/items")
    suspend fun getItems(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
        @Query("query") query: String?,
    ): List<QiitaArticleDto>
}
```

### ポイント解説

#### `interface` を使う理由
Retrofit は「インターフェースを実装したクラスを自動生成」します。
実際の HTTP 通信コードは自分で書かなくていい。

```
interface QiitaApiService
    ↓ Retrofit.create(QiitaApiService::class.java)
    ↓ Retrofit が自動で実装クラスを作る
    ↓ HTTP GET リクエストを自動送信
```

#### `suspend fun` の意味
コルーチン対応。`suspend` を付けることで、非同期処理を同期的な見た目で書けます。

```kotlin
// 悪い例（コールバック地獄）
getItems(..., callback = { response ->
    callback2(..., callback = { result ->
        // ネストが深くなる...
    })
})

// 良い例（suspend を使った書き方）
val articles = getItems(...)   // ← ここで一時停止 → データが来たら再開
val filtered = filter(articles)
```

#### `@Query` アノテーション
URL のクエリパラメータに自動変換される。

```
getItems(page=1, perPage=20, query="tag:android")
→ GET https://qiita.com/api/v2/items?page=1&per_page=20&query=tag%3Aandroid
```

---

## 2. QiitaArticleDto — API レスポンスの型

### コード全体

```kotlin
// data/remote/dto/QiitaArticleDto.kt
data class QiitaArticleDto(
    val id: String?,
    val title: String?,
    val url: String?,
    val tags: List<TagDto> = emptyList(),
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("likes_count") val likesCount: Int?,
    val user: UserDto?,
)

data class TagDto(
    val name: String?,
    val versions: List<String> = emptyList(),
)

data class UserDto(
    val id: String?,
    @SerializedName("profile_image_url") val profileImageUrl: String?,
)
```

### なぜ全フィールドが nullable（`?` 付き）なのか

**外部 API のレスポンスは信用できない**から。

```json
// 正常なレスポンス
{ "id": "abc123", "title": "Kotlin入門", "url": "https://..." }

// 異常なレスポンス（フィールドが欠如している場合がある）
{ "id": "abc123", "title": null }
```

フィールドが null になる可能性があるため、すべて `String?` などの nullable 型で受け取ります。

### `@SerializedName` とは

JSON のキー名と Kotlin のプロパティ名が異なる場合に使います。

```json
// JSON のキー
{ "created_at": "2026-03-01T12:00:00Z", "likes_count": 42 }
```

```kotlin
// Kotlin の命名規則はキャメルケース（created_at → createdAt）
@SerializedName("created_at") val createdAt: String?
```

---

## 3. QiitaArticleDtoMapper — DTO から Entity への変換

### コード全体

```kotlin
// data/remote/dto/QiitaArticleDtoMapper.kt
fun QiitaArticleDto.toEntityOrNull(status: String, cachedAt: String): ArticleEntity? {
    val safeId = id ?: return null
    val safeTitle = title ?: return null
    val safeUrl = url ?: return null
    val safeCreatedAt = createdAt ?: return null
    return ArticleEntity(
        id = safeId,
        title = safeTitle,
        url = safeUrl,
        tags = tags.mapNotNull { it.name }.joinToString(","),
        createdAt = safeCreatedAt,
        cachedAt = cachedAt,
        status = status,
    )
}
```

### なぜ `toEntityOrNull` という名前なのか

null を返す可能性があるから。
Kotlin の命名規則として「null を返しうる関数には OrNull を付ける」という慣習があります（`Int.toIntOrNull()` と同じ発想）。

### `?: return null` パターン

エルビス演算子を使った早期リターン。

```kotlin
val safeId = id ?: return null
// ↑ id が null なら、この関数を null で即終了
// id が non-null なら、safeId に代入して続行
```

これを `if` で書くと：
```kotlin
if (id == null) return null
val safeId = id
```

### タグのリスト → カンマ区切り文字列への変換

```kotlin
tags = tags.mapNotNull { it.name }.joinToString(",")
```

| 変換前（API レスポンス） | 変換後（DB 保存用） |
|---|---|
| `[{name:"Android"}, {name:"Kotlin"}, {name:null}]` | `"Android,Kotlin"` |

- `mapNotNull { it.name }` : name が null のタグを除外しながら文字列リストに変換
- `joinToString(",")` : カンマ区切りで結合

### なぜ拡張関数として定義するのか

```kotlin
// 拡張関数として定義
fun QiitaArticleDto.toEntityOrNull(...): ArticleEntity?

// 呼び出し側（自然な読み方）
dto.toEntityOrNull(status = ArticleStatus.PORTAL, cachedAt = cachedAt)
```

- DTO クラス自体を変更せずに変換ロジックを追加できる
- 「このオブジェクトをこの型に変換する」という意図が読みやすい

---

## 4. DTO パターンとは

```
外部 API (Qiita)
    ↓ JSON
QiitaArticleDto  ← API の形に合わせた「一時的な入れ物」
    ↓ toEntityOrNull() で変換
ArticleEntity    ← アプリの都合に合わせた「永続的なデータ」
    ↓ Room
SQLite DB
```

### DTO パターンを使う理由

1. **外部 API の変更に強い**: API が変わっても DTO だけ修正すれば他は影響を受けない
2. **関心の分離**: 「API の型」と「アプリ内の型」を分けることでコードが整理される
3. **null 安全**: 外部からの不完全データを DTO で受け止め、Entity 変換時に弾く

---

## よくある疑問

**Q: `data class` じゃなくて `class` にしたらダメ？**

`data class` は `equals()`, `hashCode()`, `copy()`, `toString()` を自動生成します。
特にテストで2つのオブジェクトを比較するときに `equals()` が役立ちます。

```kotlin
// data class なら ✅
dto1 == dto2  // フィールドの値で比較

// 普通の class だと ❌
dto1 == dto2  // 参照（メモリアドレス）で比較
```
