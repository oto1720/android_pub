# Issue #6 テストコードの書き方と動作確認ガイド

Issue #6「Qiita API Service 定義」の完了条件を満たすための、**テストコードの書き方**と**AI PR で求められた動作確認**のやり方をまとめます。

---

## 1. 動作確認のやり方（AI PR フィードバック対応）

PR で以下の2点の動作確認が求められています：

| 確認項目 | やり方 |
|---------|--------|
| Hilt による QiitaApiService の依存注入が正常に動作する | 方法A or 方法B |
| GET /api/v2/items のレスポンスがパースできる | 方法C or 方法D |

---

### 方法A: アプリ起動で Hilt 注入を確認する

1. **QiitaApiService を注入しているクラスを確認**
   - 例: `ArticleRepositoryImpl` が `QiitaApiService` をコンストラクタで受け取っている
   - Hilt が正しく設定されていれば、起動時に注入され、クラッシュしない

2. **アプリをビルド・起動**
   ```bash
   ./gradlew assembleDebug
   ```
   - ビルドが成功し、アプリが起動すれば Hilt の依存解決は成功している
   - 起動時に `NoSuchMethodError` や `UnsatisfiedDependencyException` が出なければ OK

3. **ログで確認（任意）**
   - `BuildConfig.DEBUG` 時は `HttpLoggingInterceptor` が有効
   - ポータル画面で記事取得時に、Logcat に HTTP リクエスト/レスポンスが出ていれば API が呼ばれている

---

### 方法B: Hilt の Instrumented テストで注入を確認する

Android 実機/エミュレータ上で Hilt の注入をテストする方法です。

1. **テストクラスに `@HiltAndroidTest` を付与**
2. **`@Inject` で QiitaApiService を注入**
3. **null でなければ注入成功**

```kotlin
// app/src/androidTest/.../QiitaApiServiceHiltTest.kt
@HiltAndroidTest
class QiitaApiServiceHiltTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var qiitaApiService: QiitaApiService

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun qiitaApiService_isInjected() {
        assertNotNull(qiitaApiService)
    }
}
```

**必要な依存**: `androidTestImplementation(libs.hilt.android.testing)` など（Hilt のテスト用ライブラリ）

---

### 方法C: 実際の API を呼んでパースを確認する（手動）

1. **アプリを起動**
2. **ポータル画面を表示**
3. **記事一覧が表示される** → パース成功
4. **クラッシュや空表示** → パース失敗の可能性

ネットワークが必要で、Qiita API のレート制限（60 req/hour）に注意。

---

### 方法D: MockWebServer で Unit テストする（推奨）

**本番の API に依存せず、ローカルで再現可能**なテストです。後述の「2. テストコードの書き方」で詳しく説明します。

---

## 2. テストコードの書き方

### 2.1 テストの種類

| 種類 | 配置 | 用途 |
|------|------|------|
| Unit Test | `app/src/test/` | ローカル JVM で実行。API パース、ロジックなど |
| Instrumented Test | `app/src/androidTest/` | 実機/エミュレータで実行。Hilt 注入、UI など |

Issue #6 では **Unit Test** で `QiitaApiService` と `QiitaArticleDto` のパースを検証するのが適切です。

---

### 2.2 必要な依存関係

`app/build.gradle.kts` に以下を追加します。

```kotlin
// libs.versions.toml に追加
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version = "1.8.1" }

// app/build.gradle.kts の dependencies に追加
testImplementation(libs.okhttp.mockwebserver)
testImplementation(libs.kotlinx.coroutines.test)
```

`kotlinx-coroutines-test` は `runTest` で suspend 関数をテストするために必要です。

---

### 2.3 QiitaApiService のテスト（MockWebServer 使用）

**流れ**:
1. `MockWebServer` を起動
2. 期待する JSON レスポンスを `enqueue` で登録
3. そのサーバーを `baseUrl` にした Retrofit で `QiitaApiService` を生成
4. `getItems` を呼び、返り値が正しくパースされているか検証

---

#### 各ステップの詳しいやり方

**ステップ1: MockWebServer を起動**

```kotlin
mockWebServer = MockWebServer()
mockWebServer.start()
```

- `MockWebServer` は OkHttp に含まれる**仮の HTTP サーバー**
- `start()` でローカルの空きポートにバインドし、リクエストを受け付ける
- 実際の Qiita API には接続せず、テスト用のレスポンスを返せる

---

**ステップ2: 期待する JSON レスポンスを enqueue で登録**

```kotlin
val json = """[{"id": "abc123", "title": "テスト記事", ...}]""".trimIndent()
mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))
```

- `enqueue` = レスポンスを**キューに追加**する
- リクエストが来るたびに、キューから順番にレスポンスを返す
- `MockResponse()` で以下を設定できる:
  - `setBody(json)` … 返す JSON 文字列
  - `setResponseCode(200)` … HTTP ステータスコード
  - `addHeader("Content-Type", "application/json")` … ヘッダー（Gson は通常不要）
- **重要**: JSON は `QiitaArticleDto` のフィールド名・型に合わせる（`created_at` など `@SerializedName` 対応）

---

**ステップ3: baseUrl に MockWebServer の URL を指定して Retrofit を構築**

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl(mockWebServer.url("/"))  // ← ここがポイント
    .addConverterFactory(GsonConverterFactory.create())
    .build()
apiService = retrofit.create(QiitaApiService::class.java)
```

- `mockWebServer.url("/")` で `http://localhost:xxxxx/` のような URL を取得
- Retrofit はこの URL にリクエストを送る → 実際には MockWebServer が受け取る
- 本番の `BuildConfig.BASE_URL`（Qiita API）は使わない

---

**ステップ4: getItems を呼び、返り値を検証**

```kotlin
val result = apiService.getItems(page = 1, perPage = 20, query = null)
assertEquals(1, result.size)
assertEquals("abc123", result[0].id)
```

- `getItems` は suspend 関数なので `runTest { ... }` 内で呼ぶ
- Retrofit が MockWebServer に HTTP リクエストを送り、`enqueue` した JSON が返る
- Gson が JSON を `List<QiitaArticleDto>` にパースする
- `assertEquals` でパース結果が期待通りか確認する

---

**補足: 複数リクエスト・エラーレスポンスのテスト**

```kotlin
// 複数回 enqueue すると、呼び出し順に返る
mockWebServer.enqueue(MockResponse().setBody(json1).setResponseCode(200))
mockWebServer.enqueue(MockResponse().setBody(json2).setResponseCode(200))

// エラー（404 など）のテスト
mockWebServer.enqueue(MockResponse().setResponseCode(404))
// → Retrofit は HttpException をスローするので、runCatching で検証
```

---

```kotlin
// app/src/test/java/com/example/oto1720/dojo2026/data/remote/api/QiitaApiServiceTest.kt
package com.example.oto1720.dojo2026.data.remote.api

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class QiitaApiServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: QiitaApiService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(QiitaApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun getItems_正常レスポンスをパースできる() = runTest {
        val json = """
            [{
                "id": "abc123",
                "title": "テスト記事",
                "url": "https://qiita.com/test",
                "tags": [{"name": "Kotlin", "versions": []}],
                "created_at": "2024-01-01T00:00:00+09:00",
                "likes_count": 10,
                "user": {"id": "testuser", "profile_image_url": "https://example.com/img.png"}
            }]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiService.getItems(page = 1, perPage = 20, query = null)

        assertEquals(1, result.size)
        assertEquals("abc123", result[0].id)
        assertEquals("テスト記事", result[0].title)
        assertEquals("Kotlin", result[0].tags[0].name)
    }

    @Test
    fun getItems_空のtagsをパースできる() = runTest {
        val json = """
            [{
                "id": "xyz",
                "title": "タグなし記事",
                "url": "https://qiita.com/xyz",
                "tags": [],
                "created_at": "2024-01-01T00:00:00+09:00",
                "likes_count": 0,
                "user": null
            }]
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(json).setResponseCode(200))

        val result = apiService.getItems(page = 1, perPage = 20, query = null)

        assertEquals(1, result.size)
        assertEquals(0, result[0].tags.size)
    }
}
```

---

### 2.4 QiitaArticleDto の @SerializedName マッピング確認

`created_at` → `createdAt`、`likes_count` → `likesCount` など、Gson の `@SerializedName` が正しく動くかは、上記の `QiitaApiServiceTest` 内で `result[0].createdAt` や `result[0].likesCount` を検証すれば十分です。

別途 DTO 単体のテストを書く場合は、Gson で直接デシリアライズして検証します。

```kotlin
// 例: QiitaArticleDto のパース単体テスト
@Test
fun qiitaArticleDto_serializedNameが正しくマッピングされる() {
    val json = """
        {
            "id": "1",
            "title": "タイトル",
            "url": "https://qiita.com/1",
            "created_at": "2024-01-01T00:00:00+09:00",
            "likes_count": 5,
            "user": null
        }
    """.trimIndent()

    val dto = Gson().fromJson(json, QiitaArticleDto::class.java)

    assertEquals("2024-01-01T00:00:00+09:00", dto.createdAt)
    assertEquals(5, dto.likesCount)
}
```

---

### 2.5 テストの実行方法

```bash
# 全 Unit テストを実行（Android の場合）
./gradlew :app:testDebugUnitTest

# 特定のテストクラスのみ
./gradlew :app:testDebugUnitTest --tests "com.example.oto1720.dojo2026.data.remote.api.QiitaApiServiceTest"
```

---

## 3. まとめ

| 目的 | 推奨方法 |
|------|----------|
| Hilt 注入の確認 | アプリ起動でクラッシュしないこと確認（方法A） |
| GET /api/v2/items のパース確認 | MockWebServer を使った Unit テスト（方法D） |
| テストコードの追加 | `QiitaApiServiceTest` を `app/src/test/` に作成 |

テストを書いて `./gradlew test` が通れば、PR で求められた動作確認をコードで証明できます。
