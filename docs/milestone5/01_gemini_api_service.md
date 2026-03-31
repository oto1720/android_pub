# Gemini API Service 定義（issue #21）

## このドキュメントで学ぶこと

- Gemini API のリクエスト / レスポンス DTO 設計
- API キーを安全に管理する `BuildConfig` パターン
- 同一 DI グラフに2種類の Retrofit を共存させる `@Named` 修飾子

---

## 1. GeminiApiService — API の入り口

```kotlin
// data/remote/api/GeminiApiService.kt
interface GeminiApiService {

    /**
     * POST https://generativelanguage.googleapis.com/v1/models/{model}:generateContent
     *
     * APIキーは OkHttp インターセプターで自動付与（?key=...）
     */
    @POST("v1/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Body request: GeminiRequestDto,
    ): GeminiResponseDto
}
```

### `@Path` アノテーション

URL の `{model}` 部分を動的に埋め込みます。

```kotlin
// 呼び出し
geminiApiService.generateContent(model = "gemini-2.5-flash", request = ...)

// 実際の URL
POST https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent
```

`@Query` との違い:
- `@Path` → URL パスの一部（`/models/gemini-2.5-flash`）
- `@Query` → URL クエリパラメータ（`?key=...`）

### Qiita API（GET）と Gemini API（POST）の違い

| 観点 | QiitaApiService | GeminiApiService |
|------|----------------|-----------------|
| HTTP メソッド | `@GET` | `@POST` |
| 入力 | `@Query` パラメータ | `@Body` JSON ボディ |
| 出力 | リストのレスポンス | オブジェクトのレスポンス |

---

## 2. GeminiDto — リクエストとレスポンスの型

### リクエスト DTO（送信する構造）

```kotlin
// data/remote/dto/GeminiDto.kt
data class GeminiRequestDto(
    val contents: List<GeminiContentDto>,
)

data class GeminiContentDto(
    val parts: List<GeminiPartDto>,
    val role: String? = null,
)

data class GeminiPartDto(
    val text: String,
)
```

Gemini API に送信する JSON の構造:

```json
{
  "contents": [
    {
      "parts": [
        { "text": "以下の記事を3行で要約してください。\n記事タイトル: Kotlin入門" }
      ]
    }
  ]
}
```

この階層的な構造は Gemini API の仕様（マルチターン会話対応のため `contents` がリスト）によるものです。
1回の質問でも `contents: [{ parts: [{ text: "プロンプト" }] }]` という形式が必要です。

### レスポンス DTO（受信する構造）

```kotlin
data class GeminiResponseDto(
    val candidates: List<GeminiCandidateDto>?,
    val usageMetadata: GeminiUsageMetadataDto?,
)

data class GeminiCandidateDto(
    val content: GeminiContentDto?,
    val finishReason: String?,
    val index: Int?,
)

data class GeminiUsageMetadataDto(
    val promptTokenCount: Int?,
    val candidatesTokenCount: Int?,
    val totalTokenCount: Int?,
)
```

レスポンスの JSON 構造:

```json
{
  "candidates": [
    {
      "content": {
        "parts": [{ "text": "1. Kotlinは静的型付け言語です\n2. ..." }],
        "role": "model"
      },
      "finishReason": "STOP",
      "index": 0
    }
  ],
  "usageMetadata": {
    "promptTokenCount": 30,
    "candidatesTokenCount": 100,
    "totalTokenCount": 130
  }
}
```

### `firstText()` ヘルパー関数

```kotlin
fun GeminiResponseDto.firstText(): String? =
    candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
```

深いネストを安全に取り出す拡張関数です。

```
candidates                     // → List<GeminiCandidateDto>?
  ?.firstOrNull()              // → GeminiCandidateDto?
  ?.content                   // → GeminiContentDto?
  ?.parts                     // → List<GeminiPartDto>?
  ?.firstOrNull()              // → GeminiPartDto?
  ?.text                      // → String?
```

各ステップで `?.` を使うため、どこかが null でも NPE が発生せず `null` を返します。

---

## 3. API キーの安全な管理

### なぜハードコードしてはいけないのか

```kotlin
// ❌ NG: ソースコードに API キーを直接書く
private const val API_KEY = "AIzaSy..."
```

Git にコミットすると GitHub などで公開され、キーが漏洩します。
悪意ある第三者に使われ、課金が発生したり、レートリミットが超過したりします。

### `local.properties` + `BuildConfig` パターン

**Step 1**: `local.properties`（Git 管理外）にキーを書く

```properties
# local.properties（.gitignore で除外されている）
GEMINI_API_KEY=AIzaSy...
GEMINI_BASE_URL=https://generativelanguage.googleapis.com/
```

**Step 2**: `build.gradle` で `BuildConfig` フィールドとして読み込む

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        val properties = Properties().apply {
            load(rootProject.file("local.properties").inputStream())
        }
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${properties["GEMINI_API_KEY"]}\"")
        buildConfigField("String", "GEMINI_BASE_URL",
            "\"${properties["GEMINI_BASE_URL"]}\"")
    }
}
```

**Step 3**: コードから `BuildConfig` 経由でアクセスする

```kotlin
// di/NetworkModule.kt
BuildConfig.GEMINI_API_KEY   // ← ビルド時に値が埋め込まれる
BuildConfig.GEMINI_BASE_URL
```

```
local.properties（Git 管理外）
    ↓ ビルド時に読み込み
BuildConfig（自動生成クラス）
    ↓ ランタイムに参照
NetworkModule → OkHttpClient のインターセプター
```

---

## 4. Gemini 専用 OkHttpClient と `@Named` 修飾子

### なぜ2種類の OkHttpClient が必要か

| | Qiita 用 | Gemini 用 |
|--|---------|---------|
| ベース URL | `https://qiita.com/` | `https://generativelanguage.googleapis.com/` |
| 認証方式 | なし（公開 API） | クエリパラメータ `?key=...` |
| 追加処理 | なし | API キーを自動付与するインターセプター |

同じ `OkHttpClient` を共有すると、Qiita API にも Gemini の API キーが付いてしまいます。

### インターセプターで API キーを自動付与

```kotlin
// di/NetworkModule.kt
.addInterceptor { chain ->
    val original = chain.request()
    val url = original.url.newBuilder()
        .addQueryParameter("key", BuildConfig.GEMINI_API_KEY)  // ← キーを追加
        .build()
    chain.proceed(original.newBuilder().url(url).build())       // ← 変更した URL で続行
}
```

すべてのリクエストの URL に `?key=AIzaSy...` が自動的に追加されます。
`GeminiApiService` のメソッドに `@Query("key")` を書かなくて済みます。

### `@Named` 修飾子で2種類を区別

Hilt に「同じ型（`OkHttpClient`）が2つある」ことを伝えるために `@Named` を使います。

```kotlin
// NetworkModule.kt
@Named("gemini")   // ← 名前で区別
@Provides @Singleton
fun provideGeminiOkHttpClient(...): OkHttpClient = ...

@Named("gemini")
@Provides @Singleton
fun provideGeminiRetrofit(@Named("gemini") okHttpClient: OkHttpClient): Retrofit = ...

@Provides @Singleton
fun provideGeminiApiService(@Named("gemini") retrofit: Retrofit): GeminiApiService = ...
```

```
DI グラフ:

OkHttpClient（名前なし） → Retrofit（名前なし） → QiitaApiService
OkHttpClient("gemini")  → Retrofit("gemini")  → GeminiApiService
```

`@Named` なしの `OkHttpClient` と `@Named("gemini")` の `OkHttpClient` は別物として扱われます。
