# AppError sealed class・エラーハンドリング統一（issue #29）

## このドキュメントで学ぶこと

- `sealed class` による型安全なエラー分類
- `Throwable.toAppError()` 拡張関数でエラーを一か所で変換する方法
- HTTP ステータスコードに応じたリトライ可否の制御
- `ErrorContent` 共通 Composable でエラー UI を一元管理する方法

---

## 1. AppError sealed class の設計

```kotlin
// domain/model/AppError.kt
sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** 401 Unauthorized — APIキーが無効 */
    data object Unauthorized : AppError("APIキーが無効です")

    /** 403 Forbidden — アクセスが拒否された */
    data object Forbidden : AppError("アクセスが拒否されました")

    /** 429 Too Many Requests — レート制限超過 */
    data object RateLimitExceeded : AppError("リクエスト制限に達しました。しばらく待ってから再試行してください")

    /** ネットワーク接続エラー（タイムアウト・DNS解決失敗など） */
    class NetworkError(cause: Throwable? = null) : AppError("ネットワーク接続エラーが発生しました", cause)

    /** Gemini AI API エラー */
    class AiError(cause: Throwable? = null) : AppError("AI機能でエラーが発生しました", cause)

    /** 上記に分類できないその他のエラー */
    class UnknownError(cause: Throwable? = null) : AppError(cause?.message ?: "不明なエラーが発生しました", cause)
}
```

### `sealed class` vs `sealed interface`

```
sealed class AppError(...) : Exception(...)
    ↑ Exception を継承している

sealed interface PortalEvent
    ↑ インターフェースなので Exception 継承不要
```

`AppError` が `Exception` を継承しているのは、`runCatching` や `Result<T>` の
`Failure` に格納してそのまま `throw` できるようにするためです。

### `data object` vs `class`

```kotlin
data object Unauthorized : AppError(...)  // ← 状態を持たない・シングルトン
class NetworkError(cause: Throwable?)     // ← 原因（cause）を持つ・都度生成
```

| 種類 | 使い分け |
|------|---------|
| `data object` | 原因に関係なくメッセージが固定のエラー（401, 403） |
| `class` | 元の例外（cause）を保持したいエラー（ネットワーク, AI, 不明） |

---

## 2. toAppError() 拡張関数

```kotlin
// domain/model/AppError.kt
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this                    // ← すでに AppError ならそのまま返す
    is HttpException -> when (code()) {
        401 -> AppError.Unauthorized
        403 -> AppError.Forbidden
        429 -> AppError.RateLimitExceeded
        else -> AppError.NetworkError(this)
    }
    is IOException -> AppError.NetworkError(this)
    else -> AppError.UnknownError(this)
}
```

### 変換フロー

```
Retrofit が例外をスロー
    ↓
runCatching { ... }.onFailure { throwable ->
    throwable.toAppError()
}
    ↓
AppError に変換して Result.Failure に格納
```

### なぜ `is AppError -> this` が必要か

UseCase が `AiError` を作って投げた場合に、さらに外側で
`toAppError()` が呼ばれても二重変換を防ぎます。

```kotlin
// AiRepositoryImpl.kt
runCatching { geminiApiService.generateContent(...) }
    .onFailure { throw AppError.AiError(it) }

// ViewModel.kt
runCatching { generateSummaryUseCase(...) }
    .onFailure { error ->
        error.toAppError()  // AppError.AiError → そのまま返る（変換不要）
    }
```

---

## 3. PortalScreen での ErrorContent 使用

```kotlin
// ui/portal/PortalScreen.kt
when {
    refreshState is LoadState.Error && pagingItems.itemCount == 0 -> {
        val appError = refreshState.error as? AppError ?: refreshState.error.toAppError()
        ErrorContent(
            error = appError,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
    ...
}
```

`refreshState.error` は `Throwable` 型のため:
1. まず `as? AppError` でキャスト試行
2. `null`（AppError でない）なら `toAppError()` で変換

---

## 4. ErrorContent 共通 Composable

```kotlin
// ui/portal/PortalScreen.kt
@Composable
private fun ErrorContent(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = error.message ?: "エラーが発生しました",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            // 401/403 はリトライしても解決しないためボタンを非表示
            if (error !is AppError.Unauthorized && error !is AppError.Forbidden) {
                Button(onClick = onRetry) {
                    Text("リトライ")
                }
            }
        }
    }
}
```

### リトライボタンの表示制御

```
AppError.Unauthorized     → ボタン非表示（APIキーを直すしかない）
AppError.Forbidden        → ボタン非表示（権限の問題）
AppError.RateLimitExceeded→ ボタン表示（時間を置けば解決）
AppError.NetworkError     → ボタン表示（接続を直せば解決）
AppError.AiError          → ボタン表示（再試行で解決することも）
AppError.UnknownError     → ボタン表示（とりあえず試せる）
```

`error !is AppError.Unauthorized && error !is AppError.Forbidden` という条件式で
「2種類を除いた全て」を表現しています。

---

## 5. エラーの種別とメッセージ一覧

| AppError | 発生原因 | ユーザーへのメッセージ | リトライ |
|---------|---------|---------------------|--------|
| `Unauthorized` | APIキーが無効（401） | APIキーが無効です | ✗ |
| `Forbidden` | アクセス拒否（403） | アクセスが拒否されました | ✗ |
| `RateLimitExceeded` | レート制限（429） | リクエスト制限に達しました | ✓ |
| `NetworkError` | IOException・タイムアウト | ネットワーク接続エラーが発生しました | ✓ |
| `AiError` | Gemini API エラー | AI機能でエラーが発生しました | ✓ |
| `UnknownError` | その他 | 不明なエラーが発生しました | ✓ |

---

## 6. Domain 層にエラー型を置く理由

```
domain/model/AppError.kt  ← ここに配置

app/
└── domain/
    └── model/
        ├── AppError.kt     ← ここ
        ├── SortOrder.kt
        └── ...
```

`data` 層や `ui` 層ではなく `domain` 層に置くことで:
- Repository（data 層）も ViewModel（ui 層）も参照できる
- Android フレームワーク依存なし（`HttpException` は Retrofit だが汎用型）
- テスト時にモックしやすい
