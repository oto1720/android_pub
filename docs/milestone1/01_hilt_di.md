# 01. Hilt による依存性注入（DI）

## 依存性注入とは？

「クラスが必要とするオブジェクトを、自分で作らずに外から受け取る」仕組み。

```kotlin
// ❌ 依存性注入なし（クラスが自分でDBを作る）
class ArticleRepository {
    private val db = Room.databaseBuilder(...).build()  // 密結合・テスト困難
}

// ✅ 依存性注入あり（外から渡してもらう）
class ArticleRepository(private val dao: ArticleDao) {  // テストで差し替え可能
    ...
}
```

Hilt はこの「外から渡す」部分を自動化してくれるライブラリです。

---

## Hilt の起動 — DojoApplication.kt

```kotlin
@HiltAndroidApp                          // ← これを付けるだけ
class DojoApplication : Application()
```

**`@HiltAndroidApp` が何をするか**
- アプリ起動時に Hilt の DI コンテナ（依存関係を管理する箱）を初期化
- コード生成（KSP）によって `DojoApplication` のスーパークラスを差し替える
- これが無いと `@Inject` や `@AndroidEntryPoint` が動かない

**AndroidManifest.xml への登録も必要**（自動では設定されない）:
```xml
<application
    android:name=".DojoApplication"
    ...>
```

---

## Activity への注入 — MainActivity.kt

```kotlin
@AndroidEntryPoint                       // ← Activity に DI を有効化
class MainActivity : ComponentActivity() {
    ...
}
```

**`@AndroidEntryPoint` が何をするか**
- Activity / Fragment / ViewModel などの Android コンポーネントに対して Hilt を有効にする
- この Activity の中で `@Inject` でフィールド注入ができるようになる
- NavController や ViewModel の注入が可能になる

---

## DI モジュール — AppModule.kt

```kotlin
@Module                                  // ← Hilt に「これはモジュールだよ」と伝える
@InstallIn(SingletonComponent::class)    // ← アプリ全体で1つのインスタンスを共有
object AppModule {

    @Provides                            // ← 「この関数が AppDatabase を提供するよ」
    @Singleton                           // ← アプリ中で1インスタンスだけ作る
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tech_digest.db",           // DBファイル名
        ).fallbackToDestructiveMigration()  // スキーマ変更時にDBを作り直す
         .build()

    @Provides
    @Singleton
    fun provideArticleDao(db: AppDatabase): ArticleDao = db.articleDao()
    //                    ↑ AppDatabase は上の @Provides から自動で渡される

    @Provides
    @Singleton
    fun provideDigestDao(db: AppDatabase): DigestDao = db.digestDao()
}
```

### 重要な概念

| アノテーション | 意味 |
|--------------|------|
| `@Module` | Hilt にモジュールとして認識させる |
| `@InstallIn(SingletonComponent::class)` | アプリのライフサイクルに紐づける |
| `@Provides` | この関数がオブジェクトを生成・提供する |
| `@Singleton` | アプリ全体で1インスタンスのみ（使い回す） |
| `@ApplicationContext` | Application の Context を Hilt が自動注入 |

### Hilt コンポーネントの種類

```
SingletonComponent              ← アプリ全体（AppModule, NetworkModule で使用）
  └─ ActivityRetainedComponent  ← 画面回転でも保持される（ViewModel のライフサイクル）
       ├─ ViewModelComponent    ← ViewModel（@HiltViewModel）のスコープ
       └─ ActivityComponent     ← Activity のライフサイクル
            └─ FragmentComponent ← Fragment のライフサイクル
                 └─ ViewComponent ← View のライフサイクル
  └─ ServiceComponent           ← Service のライフサイクル
```

> **重要**: `ViewModelComponent` は `ActivityComponent` の下ではなく `ActivityRetainedComponent` の下。
> ViewModel は Activity の再生成（画面回転）をまたいで生存するため、Activity より上位のコンポーネントに属する。

---

## DI モジュール — NetworkModule.kt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // DEBUGビルドなら詳細ログ、RELEASEなら無効
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY   // リクエスト/レスポンス全体
            } else {
                HttpLoggingInterceptor.Level.NONE   // ログ無し（本番環境）
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient =
        //                   ↑ 上の @Provides から自動で受け取る
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)  // 接続タイムアウト
            .readTimeout(30, TimeUnit.SECONDS)     // 読み込みタイムアウト
            .writeTimeout(30, TimeUnit.SECONDS)    // 書き込みタイムアウト
            .addInterceptor(loggingInterceptor)    // ロギングを追加
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        //               ↑ 上の @Provides から自動で受け取る
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)         // "https://api.example.com/"
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())  // JSON ↔ Kotlin変換
            .build()
}
```

### 依存の連鎖（Hilt が自動解決する）

```
Retrofit
  └─ OkHttpClient           （Hilt が自動で作って渡す）
       └─ HttpLoggingInterceptor  （Hilt が自動で作って渡す）
```

---

## Hilt の全体的な流れ

```
1. アプリ起動
   DojoApplication (@HiltAndroidApp)
         │
         ▼
2. Hilt コンテナ初期化
   SingletonComponent が生成される
         │
         ├─ AppModule.provideAppDatabase()    → AppDatabase インスタンス生成
         ├─ AppModule.provideArticleDao()     → ArticleDao インスタンス生成
         ├─ AppModule.provideDigestDao()      → DigestDao インスタンス生成
         ├─ NetworkModule.provideLogging...() → HttpLoggingInterceptor 生成
         ├─ NetworkModule.provideOkHttp...()  → OkHttpClient 生成
         └─ NetworkModule.provideRetrofit()   → Retrofit 生成
         │
         ▼
3. MainActivity (@AndroidEntryPoint) が起動
   必要なものが自動で注入される
```

---

## なぜ Hilt を使うのか

1. **テストしやすい** — ViewModel やリポジトリを単体テストするとき、DBをモックに差し替えられる
2. **コードが減る** — `new AppDatabase(...)` のような生成コードを書かなくていい
3. **ライフサイクル管理** — `@Singleton` で1つだけ作られるので、複数箇所で別々にDBを作るミスがない
4. **明示的な依存関係** — 何がどこに依存しているかが `@Module` を見れば一目瞭然

---

## つまずきポイント

### Q: `@HiltAndroidApp` を付け忘れたら？
→ ビルドエラー or 実行時クラッシュ。`DojoApplication` を `android:name` に設定し忘れても同様。

### Q: `@Singleton` を付けないとどうなる？
→ `@Provides` が呼ばれるたびに新しいインスタンスが作られる。DBが複数作られてしまうなど問題が起きる。

### Q: `@Module` の `object` と `class` の違いは？
→ `object`（シングルトン）にするのが一般的。`@Provides` メソッドが状態を持たないため `object` で十分。
