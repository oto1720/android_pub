# 05. Retrofit / OkHttp によるネットワーク層

## ネットワーク層の全体像

```
UI / ViewModel
      │ 呼び出す
      ▼
Repository（次milestone以降）
      │
      ▼
ApiService（Retrofit インターフェース）
      │ HTTP リクエスト生成
      ▼
Retrofit
      │ HTTP 送受信を委譲
      ▼
OkHttpClient
      │ インターセプターを通す
      ▼
HttpLoggingInterceptor（ログ出力）
      │
      ▼
実際の HTTP リクエスト → サーバー
      │
      ▼
JSON レスポンス
      │ GsonConverterFactory が変換
      ▼
Kotlin データクラス
```

---

## OkHttp — HTTPクライアント

```kotlin
@Provides
@Singleton
fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)  // TCP接続確立の制限時間
        .readTimeout(30, TimeUnit.SECONDS)     // レスポンスデータ受信の制限時間
        .writeTimeout(30, TimeUnit.SECONDS)    // リクエスト送信の制限時間
        .addInterceptor(loggingInterceptor)    // 全リクエストにログを仕込む
        .build()
```

**タイムアウトの意味**:
```
connectTimeout: サーバーへの TCP 接続 ─── 30秒でタイムアウト
readTimeout:    レスポンスの最初のバイト ─ 30秒でタイムアウト
writeTimeout:   リクエストボディ送信 ────  30秒でタイムアウト
```

**インターセプターとは**:
全リクエスト/レスポンスの途中に割り込む仕組み。ロギング、認証ヘッダー付与、リトライなどに使う。

---

## HttpLoggingInterceptor — ログ出力

```kotlin
@Provides
@Singleton
fun provideLoggingInterceptor(): HttpLoggingInterceptor =
    HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY   // DEBUGビルド: 全て出力
        } else {
            HttpLoggingInterceptor.Level.NONE   // RELEASEビルド: 出力なし
        }
    }
```

**ログレベルの種類**:

| レベル | 出力内容 |
|--------|---------|
| `NONE` | 何も出力しない |
| `BASIC` | リクエスト行・レスポンスコードのみ |
| `HEADERS` | ヘッダーも出力 |
| `BODY` | ヘッダー + ボディ（JSON全体）を出力 |

**なぜ RELEASE で NONE にするか**:
- API キーやトークンがヘッダーに含まれる場合、ログから漏洩するリスク
- ユーザーの個人情報がボディに含まれる場合も同様
- セキュリティ上、本番では絶対に `NONE` にする

---

## Retrofit — HTTP 通信の抽象化

```kotlin
@Provides
@Singleton
fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
    Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)              // "https://api.example.com/"
        .client(okHttpClient)                       // OkHttp を使う
        .addConverterFactory(GsonConverterFactory.create())  // JSON変換
        .build()
```

**baseUrl の末尾スラッシュ**:
```kotlin
// ✅ 末尾スラッシュが必要
.baseUrl("https://api.example.com/")

// ❌ これだと IllegalArgumentException
.baseUrl("https://api.example.com")
```

**GsonConverterFactory の役割**:
```
HTTP レスポンス (JSON 文字列)
  {"id": "123", "title": "記事タイトル"}
          ↓ Gson が自動変換
Kotlin データクラス
  ArticleResponse(id = "123", title = "記事タイトル")
```

---

## BuildConfig.BASE_URL の設定

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "BASE_URL", "\"https://api.example.com/\"")
        //                型          フィールド名  値（文字列はエスケープ必要）
    }
}
```

**BuildConfig とは**:
- ビルド時に自動生成される Kotlin クラス
- `DEBUG` / `RELEASE` でビルドタイプごとに値を変えられる
- API の URL やフラグなど環境依存の設定を管理

```kotlin
// 複数環境の設定例（このプロジェクトには未実装）
buildTypes {
    debug {
        buildConfigField("String", "BASE_URL", "\"https://dev.api.example.com/\"")
    }
    release {
        buildConfigField("String", "BASE_URL", "\"https://api.example.com/\"")
    }
}
```

---

## 次の milestone での使い方イメージ

Milestone 1 では Retrofit インスタンスを作るところまで。
次以降で ApiService インターフェースを追加して実際に使います。

```kotlin
// 次 milestone 以降で追加予定のコード例

// 1. API インターフェースを定義
interface ArticleApiService {
    @GET("articles")
    suspend fun getArticles(): List<ArticleResponse>

    @GET("articles/{id}")
    suspend fun getArticle(@Path("id") id: String): ArticleResponse
}

// 2. DI モジュールに追加
@Provides
@Singleton
fun provideArticleApiService(retrofit: Retrofit): ArticleApiService =
    retrofit.create(ArticleApiService::class.java)
//  ↑ Retrofit が動的プロキシで実装を自動生成

// 3. リポジトリで使う
class ArticleRepository(
    private val apiService: ArticleApiService,
    private val articleDao: ArticleDao,
) {
    suspend fun fetchAndSave() {
        val articles = apiService.getArticles()  // HTTP GET
        articleDao.insertAll(articles.map { it.toEntity() })  // DB に保存
    }
}
```

---

## Hilt との連携（依存の連鎖）

```
NetworkModule の提供する依存関係:

Retrofit            ← @Provides で提供
  └─ OkHttpClient   ← @Provides で提供（Retrofit に自動注入）
       └─ HttpLoggingInterceptor ← @Provides で提供（OkHttpClient に自動注入）

すべて @Singleton なので、アプリ起動中は1インスタンスのみ
```

---

## つまずきポイント

### Q: `suspend fun` にしないとどうなる？
→ `suspend` なしの場合、Retrofit は `Call<T>` 型を返す（旧来の非同期 API）。
　`.enqueue()` で非同期実行 / `.execute()` で同期実行のどちらも可能。
　ただしコルーチンと連携できないため、現代の Android 開発では `suspend fun` が推奨。

```kotlin
// ❌ 旧来スタイル（suspend なし）
interface OldApiService {
    @GET("articles")
    fun getArticles(): Call<List<ArticleResponse>>  // Call<T> を返す
}

// 使い方（コルーチンと連携できない）
service.getArticles().enqueue(object : Callback<List<ArticleResponse>> {
    override fun onResponse(...) { /* コールバック地獄 */ }
    override fun onFailure(...) { }
})

// ✅ 現代スタイル（suspend あり）
interface ModernApiService {
    @GET("articles")
    suspend fun getArticles(): List<ArticleResponse>  // コルーチンで直接呼べる
}
```

### Q: GsonConverterFactory を使わないと？
→ レスポンスボディを `ResponseBody` で受け取ることになり、自分で JSON をパースする必要がある。

### Q: Gson の代わりに kotlinx.serialization を使う選択肢は？
→ Kotlin プロジェクトでは **kotlinx.serialization** の方が推奨されることが多い。
　理由：GsonはKotlinのnon-null型（`String` など）でも `null` を無視してクラッシュしないケースがあり、実行時エラーを見逃す可能性がある。

```kotlin
// kotlinx.serialization を使う場合の比較

// Gson: Kotlinのnull安全を無視してしまうことがある
data class ArticleResponse(val id: String)  // nullが来てもクラッシュしない→バグが潜む

// kotlinx.serialization: Kotlinの型システムと連動
@Serializable
data class ArticleResponse(val id: String)  // nullが来るとデシリアライズ時にエラー
```
→ このプロジェクトでは Gson を採用しているが、将来的には kotlinx.serialization への移行も検討に値する。

### Q: `BuildConfig.BASE_URL` が見つからないエラーが出る？
→ `buildFeatures { buildConfig = true }` が `app/build.gradle.kts` に必要。このプロジェクトでは設定済み。

### Q: タイムアウトを短くしすぎると？
→ 遅いネットワーク環境でタイムアウトエラーが頻発する。長すぎると UX が悪い。30秒は一般的な設定値。
