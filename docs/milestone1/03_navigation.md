# 03. Navigation Compose による画面遷移

## Navigation Compose とは？

Jetpack Navigation の Compose 版。
「画面遷移のグラフ」を宣言的に定義し、画面間を移動できる。

> **このプロジェクトの注意点**: Navigation 2.8+ では `@Serializable` を使った**型安全ナビゲーション（Type-Safe Navigation）** が追加された。
> しかし現在のコードは **旧来の文字列ベースのルート**（`"digest/{articleId}"` のような URL 形式）を使っている。
> 文字列ルートはタイポが実行時エラーになるため、厳密には「型安全」ではない。
> 今後のリファクタリング候補として覚えておこう。

---

## Screen.kt — ルートの定義

```kotlin
sealed class Screen(val route: String) {

    // 引数なし画面（ルートは固定文字列）
    data object Portal   : Screen("portal")
    data object Tsundoku : Screen("tsundoku")
    data object Done     : Screen("done")

    // 引数あり画面（ルートに値を埋め込む）
    data class Digest(val articleId: String) : Screen("digest/$articleId") {
        companion object {
            const val ROUTE = "digest/{articleId}"  // NavHost に登録するパターン
            const val ARG   = "articleId"           // 引数名
            fun createRoute(articleId: String) = "digest/$articleId"  // 遷移時に使う
        }
    }

    data class DoneDetail(val articleId: String) : Screen("done_detail/$articleId") {
        companion object {
            const val ROUTE = "done_detail/{articleId}"
            const val ARG   = "articleId"
            fun createRoute(articleId: String) = "done_detail/$articleId"
        }
    }
}
```

### sealed class を使う理由

- **網羅性チェック**: `when(screen)` で全画面を列挙強制できる
- **タイプセーフ**: 文字列のタイポを防げる（`"potal"` のようなミスが検出される）
- **一元管理**: ルート文字列が分散しない

### ROUTE と createRoute の使い分け

```
ROUTE       = "digest/{articleId}"  ← NavHost への登録（パターン）
createRoute = "digest/abc-123"      ← 実際の遷移（実値を埋め込む）
```

---

## AppNavHost.kt — 遷移グラフの定義

```kotlin
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Portal.route,  // 最初に表示する画面
        modifier = modifier,
    ) {

        // ─── 引数なし画面 ─────────────────────────────────
        composable(Screen.Portal.route) {
            PortalScreen(
                onNavigateToDigest = { articleId ->
                    navController.navigate(Screen.Digest.createRoute(articleId))
                },
                onNavigateToTsundoku = {
                    navController.navigate(Screen.Tsundoku.route)
                },
                onNavigateToDone = {
                    navController.navigate(Screen.Done.route)
                },
            )
        }

        // ─── 引数あり画面 ─────────────────────────────────
        composable(
            route = Screen.Digest.ROUTE,                       // パターン登録
            arguments = listOf(
                navArgument(Screen.Digest.ARG) {
                    type = NavType.StringType                   // 型を指定
                }
            ),
        ) { backStackEntry ->
            // バックスタックエントリから引数を取り出す
            val articleId = backStackEntry.arguments
                ?.getString(Screen.Digest.ARG)
                .orEmpty()
            DigestScreen(
                articleId = articleId,
                ...
            )
        }
    }
}
```

---

## 画面遷移グラフ（全体像）

```
            ┌─────────────┐
            │PortalScreen │  startDestination
            └──────┬──────┘
                   │ 3つのボタン
        ┌──────────┼──────────────┐
        ▼          ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│TsundokuScreen│ │ DigestScreen │ │  DoneScreen  │
│(積読リスト)  │ │(articleId)   │ │(血肉リスト)  │
└──────┬───────┘ └──────────────┘ └──────┬───────┘
       │                                  │
       │  onNavigateToDigest              │ onNavigateToDoneDetail
       ▼                                  ▼
┌──────────────┐                  ┌───────────────────┐
│ DigestScreen │                  │ DoneDetailScreen  │
│(articleId)   │                  │ (articleId)       │
└──────────────┘                  └───────────────────┘
```

---

## コールバックパターン（重要な設計）

Navigation Compose では、**画面 Composable が直接 navController を持たない** 設計を採用しています。

```kotlin
// ✅ このプロジェクトの設計（コールバックを親から渡す）
@Composable
fun TsundokuScreen(
    onNavigateToDigest: (articleId: String) -> Unit,  // 遷移先を知らない
    onNavigateToDone: () -> Unit,
    modifier: Modifier = Modifier,
) { ... }

// AppNavHost が橋渡しをする
composable(Screen.Tsundoku.route) {
    TsundokuScreen(
        onNavigateToDigest = { articleId ->
            navController.navigate(...)  // navController はここだけが持つ
        },
        ...
    )
}
```

**この設計のメリット**:
| 観点 | メリット |
|------|---------|
| テスト | Screen 単体をテストするとき navController が不要 |
| 再利用 | 同じ Screen を別の遷移先と組み合わせられる |
| 関心の分離 | 画面は「表示」だけ、遷移は NavHost が管理 |

---

## MainActivity.kt での NavController 生成

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()                   // ステータスバーを透過させて全画面描画
        setContent {
            TechDigestTheme {
                val navController = rememberNavController()  // ← ここで1つだけ作る
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
```

**`rememberNavController()`**:
- Compose の `remember` でラップされているので、再コンポーズで再作成されない
- バックスタックの状態も保持される

**`Scaffold` の役割**:
- Material Design の基本レイアウト（TopBar、BottomBar、FAB などの枠組み）
- `innerPadding` = システムバー（ステータスバー・ナビゲーションバー）の余白
- `Modifier.padding(innerPadding)` を NavHost に渡すことで、コンテンツがバーに隠れない

---

## バックスタックの動作

```
操作                         バックスタック
────────────────────────────────────────────
アプリ起動                   [Portal]
Portal → Tsundoku            [Portal, Tsundoku]
Tsundoku → Digest(abc)       [Portal, Tsundoku, Digest(abc)]
←（戻る）                   [Portal, Tsundoku]
←（戻る）                   [Portal]
←（戻る）                   [] → アプリ終了
```

`navController.popBackStack()` = 1つ前に戻る（`DigestScreen` などで使用）

---

## つまずきポイント

### Q: `Screen.Digest.ROUTE` と `Screen.Digest.createRoute(id)` を逆に使ったら？
→ NavHost の登録に実値 (`"digest/abc-123"`) を使うと、その ID しか遷移できなくなる。
　 必ずパターン (`"digest/{articleId}"`) で登録し、遷移時に実値を使う。

### Q: `backStackEntry.arguments?.getString(...)` が null になる？
→ `navArgument` で型を指定していない、または `ARG` の名前がルートの `{...}` と不一致。

### Q: `orEmpty()` はなぜ付いているか？
→ `getString()` は nullable を返すので、null の場合に空文字にフォールバックするため。

### Q: Navigation 2.8+ の型安全ナビゲーションとは？
→ `@Serializable` アノテーションを付けたデータクラスをルートとして使う新しい API。
　文字列タイポが **コンパイル時エラー** になるため、より安全。

```kotlin
// 新しい型安全な書き方（参考）
@Serializable
data object Portal

@Serializable
data class Digest(val articleId: String)

// NavHost への登録
composable<Digest> { backStackEntry ->
    val digest: Digest = backStackEntry.toRoute()  // 型付きで取得
    DigestScreen(articleId = digest.articleId, ...)
}

// 遷移
navController.navigate(Digest(articleId = "abc-123"))  // 文字列じゃなくオブジェクト
```
