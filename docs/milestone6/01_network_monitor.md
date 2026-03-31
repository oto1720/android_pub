# NetworkMonitor・callbackFlow による接続状態監視（issue #26）

## このドキュメントで学ぶこと

- `callbackFlow` で Android コールバック API を Kotlin Flow に変換する方法
- `awaitClose` によるリソースのクリーンアップ
- `distinctUntilChanged` で重複通知を排除する理由
- `@ApplicationContext` で Application の Context を安全に注入する方法

---

## 1. NetworkMonitor インターフェース

```kotlin
// data/network/NetworkMonitor.kt
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
```

インターフェースを定義することで:
- テスト時に偽の実装（FakeNetworkMonitor）に差し替えられる
- Android 依存（ConnectivityManager）を UseCase / ViewModel から隠蔽できる

---

## 2. ConnectivityNetworkMonitor — callbackFlow の実装

```kotlin
// data/network/ConnectivityNetworkMonitor.kt
@Singleton
class ConnectivityNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

        // 1. 現在の状態を即座に emit（Flow 購読開始時の初期値）
        trySend(connectivityManager.isCurrentlyConnected())

        // 2. コールバックで変化を通知
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                // 複数ネットワーク（WiFi + モバイルデータ）がある場合を考慮
                trySend(connectivityManager.isCurrentlyConnected())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        // 3. ネットワーク変化の監視を登録
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        // 4. Flow がキャンセルされたらコールバックを解除
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
    ...
}
```

---

## 3. `callbackFlow` とは

通常のコールバック API を `Flow` に変換するためのビルダーです。

```
Android の世界:
  NetworkCallback.onAvailable() → コールバックで通知
  NetworkCallback.onLost()      → コールバックで通知

Kotlin Flow の世界:
  Flow<Boolean> → collect するたびに値が流れてくる
```

`callbackFlow` ブロック内では `trySend(value)` で Flow にデータを送り込みます。

```kotlin
override fun onAvailable(network: Network) {
    trySend(true)   // ← Flow に true を送る（= 接続された）
}
```

### `send` vs `trySend`

`callbackFlow` 内では `send` ではなく `trySend` を使います。

- `send(value)` → バッファが満杯なら suspend する（コールバック内で使えない）
- `trySend(value)` → バッファが満杯でも suspend せず失敗を返す（コールバック内で安全）

---

## 4. `awaitClose` — リソースのクリーンアップ

```kotlin
awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
```

`awaitClose` は「Flow がキャンセルされるまで待機し、キャンセル時にクリーンアップを実行する」ブロックです。

```
ViewModel が消える（onCleared）
    ↓
viewModelScope がキャンセルされる
    ↓
isOnline Flow のコレクタが停止
    ↓
callbackFlow がキャンセル
    ↓
awaitClose { ... } のブロックが実行される
    ↓
unregisterNetworkCallback(callback)  ← メモリリーク防止
```

`awaitClose` がないと、アプリがバックグラウンドに行ってもコールバックが登録されたまま残り続けます。

---

## 5. `distinctUntilChanged` — 重複通知の排除

```kotlin
}.distinctUntilChanged()
```

`ConnectivityManager` は同じ状態を複数回コールバックすることがあります。
例えば WiFi が再接続されると `onAvailable` が複数回呼ばれる場合があります。

```
distinctUntilChanged なし:
  true → true → false → false → true  ← 同じ値が連続して流れる

distinctUntilChanged あり:
  true → false → true  ← 値が変わったときだけ流れる
```

`PortalViewModel` で「オフライン遷移だけ」に反応したいため、重複が排除されていないと
Snackbar が二重に表示されます。

---

## 6. `onLost` で直接 false を送らない理由

```kotlin
override fun onLost(network: Network) {
    // 複数ネットワークがある場合に備えて現在状態を再確認
    trySend(connectivityManager.isCurrentlyConnected())
}
```

`onLost` は「このネットワーク接続が切れた」という通知ですが、
WiFi が切れてもモバイルデータがある場合はオンラインのままです。

```
WiFi 切断 → onLost 発火
    ↓
isCurrentlyConnected() で現在の状態を確認
    ├── モバイルデータ接続あり → true （実際にはオンライン）
    └── 接続なし → false （本当にオフライン）
```

`trySend(false)` と直接書くと、WiFi → モバイルデータの切り替えで
一瞬「オフライン」と誤検知してしまいます。

---

## 7. `isCurrentlyConnected` — 拡張関数でわかりやすく

```kotlin
private fun ConnectivityManager.isCurrentlyConnected(): Boolean =
    activeNetwork?.let { getNetworkCapabilities(it) }
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
```

`ConnectivityManager` の拡張関数として定義することで、
「このクラスの操作」として自然に読める形になっています。

```
activeNetwork              → Network?（接続中のネットワーク）
    ?.let { getNetworkCapabilities(it) }  → NetworkCapabilities?
    ?.hasCapability(NET_CAPABILITY_INTERNET)  → Boolean?
    == true  → null なら false、非null なら値で判定
```

`NET_CAPABILITY_INTERNET` は「インターネットへの経路がある」ことを表します
（ローカルネットワークのみ、キャプティブポータルなどは false になります）。

---

## 8. `@ApplicationContext` の使い方

```kotlin
class ConnectivityNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : NetworkMonitor {
```

Hilt では `Context` を注入するときに `@ApplicationContext` を付ける必要があります。

| アノテーション | 取得できる Context | ライフサイクル |
|--------------|-----------------|-------------|
| `@ApplicationContext` | Application の Context | アプリ起動中ずっと生存 |
| `@ActivityContext` | Activity の Context | Activity と同期 |

`NetworkMonitor` は `@Singleton`（アプリ全体で1つ）なので、
Activity より長生きする `@ApplicationContext` が適切です。
Activity の Context を保持すると、Activity が破棄されてもメモリが解放されない（メモリリーク）危険があります。
