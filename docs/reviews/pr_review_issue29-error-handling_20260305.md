# PR レビューレポート

**PR/ブランチ**: issue29-error-handling
**レビュー日時**: 2026-03-05
**変更規模**: +145 / -27 / 9ファイル（新規1 + 修正8）

---

## 🎯 変更の概要

issue #29（エラーハンドリング統一）と issue #81（エラー種別ごとのハンドリング）を実装。
`AppError` sealed class を新設し、`ArticleRepositoryImpl` / `AiRepositoryImpl` で HTTP ステータスコード（401/403/429）・ネットワークエラー・AI エラーを統一的にマッピング。
`PortalUiState.Error` が `AppError` を保持するよう変更し、UI でエラー種別に応じたメッセージとアイコンを表示する。

**変更種別**:
- [x] 新機能 (Feature) — `AppError` sealed class + `ErrorContent` composable
- [x] リファクタリング — `PortalUiState.Error` の型変更・ViewModel の error handling 統一
- [x] テスト追加 — HTTP エラーマッピングテスト・AppError 種別テスト追加

---

## ✅ マージ判定

> **APPROVE**

変更が適切にスコープされており、Clean Architecture のレイヤーを意識した設計になっている。
`recoverCatching` による 2 行追加で既存ロジックを変えずにエラーマッピングを導入した点が特に良い。
後方互換性を保つ `message` プロパティで既存テストへの影響を最小化している。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `AppError.kt` | Added | +48 | - | 🟢 問題なし |
| `ArticleRepositoryImpl.kt` | Modified | +4 | 0 | 🟢 問題なし |
| `AiRepositoryImpl.kt` | Modified | +7 | 0 | 🟢 問題なし |
| `PortalUiState.kt` | Modified | +6 | -1 | 🟢 問題なし |
| `PortalViewModel.kt` | Modified | +7 | -1 | 🟢 問題なし |
| `PortalScreen.kt` | Modified | +41 | -26 | 🟢 問題なし |
| `ArticleRepositoryImplTest.kt` | Modified | +44 | -3 | 🟢 問題なし |
| `AiRepositoryImplTest.kt` | Modified | +5 | -4 | 🟢 問題なし |
| `PortalViewModelTest.kt` | Modified | +27 | 0 | 🟢 問題なし |

---

## 🔍 詳細レビュー

### AppError.kt（新規）

#### 変更の意図
アプリ全体で統一されたエラー型を提供し、HTTP ステータスコードに応じた種別分類を `toAppError()` 拡張関数で行う。

#### 指摘事項

**[🟢 G-1] `recoverCatching` + `throw` パターンで既存コードを変えずにマッピングを追加**

```kotlin
// ✅ 既存の runCatching ブロックに 2 行追加するだけでエラーマッピングが完成
runCatching {
    // 既存ロジックそのまま
}.recoverCatching { throwable ->
    throw throwable.toAppError()
}
```

> 既存ロジックに手を入れず、最小侵襲でエラーマッピングを追加できている。

**[🟢 G-2] `data object` で 401/403/429 を値として表現**

```kotlin
// ✅ オブジェクト同一性で比較できる
data object Unauthorized : AppError("APIキーが無効です")
data object Forbidden : AppError("アクセスが拒否されました")
data object RateLimitExceeded : AppError("リクエスト制限に達しました。しばらく待ってから再試行してください")
```

> `is AppError.Unauthorized` で型チェックでき、`when` での網羅性チェックも sealed class が保証する。

**[🟡 M-1] `AppError.kt` が `domain/model/` にあるが `retrofit2.HttpException` をインポートしている**

```kotlin
// 現状: domain 層が data 層の依存（Retrofit）に依存している
import retrofit2.HttpException  // ← domain がインフラ依存を持つ
```

> Clean Architecture 的には `toAppError()` を `data/repository/` に置くべき。ただし小規模プロジェクトでの実用上の許容範囲内であり、今後 multi-module 化するまでは OK。

---

### ArticleRepositoryImpl.kt

#### 変更の意図
Qiita API 呼び出し失敗時に HTTP ステータスコードに応じた `AppError` を返す。

#### 指摘事項

**[🟢 G-3] `refreshPortalArticles` のみに適用し、Read 系は変更しない適切なスコープ**

```kotlin
// ✅ 書き込み系（外部 API 呼び出しがある）のみ toAppError() を適用
override suspend fun refreshPortalArticles(query: String?): Result<Unit> = runCatching {
    // ...
}.recoverCatching { throwable ->
    throw throwable.toAppError()
}
```

> `getArticleById` など Room 操作のみの関数はそのまま。影響範囲が最小。

---

### AiRepositoryImpl.kt

#### 変更の意図
Gemini API の全メソッドで例外を `AppError.AiError` にラップして返す。

#### 指摘事項

**[🟢 G-4] AI 系エラーを `AppError.AiError` に統一**

```kotlin
// ✅ AiError でラップすることで「AIエラー」を他のエラーと区別できる
}.recoverCatching { throwable ->
    throw if (throwable is AppError) throwable else AppError.AiError(throwable)
}
```

> すでに `AppError` の場合（例: `IllegalStateException("AIレスポンスが空です")` → `AiError` にはならない）はそのまま伝播させる設計が正確。

**[🟡 M-2] `generateSummary` も同様にラップされているが `GenerateSummaryUseCase` のエラーは現状 UI に到達しない**

```kotlin
// 🟡 PortalViewModel / DigestViewModel は generateSummary のエラーを UI に表示しない
// 将来 AI要約機能（issue21）を実装する際は AiError を UI で拾う必要がある
```

> 今後対応予定なので現時点では問題なし。

---

### PortalUiState.kt

#### 変更の意図
`Error` が `AppError` を保持し、UI 層でエラー種別に応じた表示ができるようにする。

#### 指摘事項

**[🟢 G-5] 後方互換 `message` プロパティで既存テスト・コードへの影響ゼロ**

```kotlin
// ✅ 後方互換プロパティを追加することで既存テストの assertEquals("Network Error", state.message) がそのまま動く
data class Error(val error: AppError) : PortalUiState {
    val message: String get() = error.message ?: "不明なエラーが発生しました"
}
```

> テスト修正量を最小化しながら型安全な `error` プロパティを追加できている。

---

### PortalViewModel.kt

#### 変更の意図
`onFailure` で受け取った `Throwable` を `AppError` にキャストまたは変換して `PortalUiState.Error` に設定する。

#### 指摘事項

**[🟢 G-6] `is AppError` チェックで二重変換を回避**

```kotlin
// ✅ ArticleRepositoryImpl がすでに AppError に変換済みの場合そのまま使う
val appError = if (throwable is AppError) throwable else throwable.toAppError()
_uiState.value = PortalUiState.Error(appError)
```

> `toAppError()` 内でも同チェックをしているため現状は二重チェックになるが、防御的コードとして許容範囲。

---

### PortalScreen.kt

#### 変更の意図
エラー UI を `ErrorContent` composable に抽出し、エラー種別に応じてリトライボタンの表示/非表示を制御する。

#### 指摘事項

**[🟢 G-7] 401/403 でリトライボタンを非表示にする適切な UX 判断**

```kotlin
// ✅ 認証エラーはリトライしても解決しないためボタンを非表示
if (error !is AppError.Unauthorized && error !is AppError.Forbidden) {
    Button(onClick = onRetry) {
        Text("リトライ")
    }
}
```

> ユーザーがリトライしても意味のない操作を防ぎ、適切なメッセージのみ表示する。

**[🟢 G-8] Warning アイコンで視認性向上**

```kotlin
// ✅ 48dp の Warning アイコンで空白だったエラー画面が視覚的に改善
Icon(
    imageVector = Icons.Default.Warning,
    modifier = Modifier.size(48.dp),
    tint = MaterialTheme.colorScheme.error,
)
```

---

### テストファイル

#### 指摘事項

**[🟢 G-9] `HttpException(Response.error(401, ...))` で実際の HTTP エラーを模倣した適切なテスト**

```kotlin
// ✅ 実際の Retrofit エラーオブジェクトを使ってマッピングを検証
val response = Response.error<List<QiitaArticleDto>>(401, "Unauthorized".toResponseBody())
coEvery { apiService.getItems(any(), any(), any()) } throws HttpException(response)
val result = repository.refreshPortalArticles(null)
assertTrue(result.exceptionOrNull() is AppError.Unauthorized)
```

**[🟢 G-10] `AppError.Unauthorized` / `AppError.RateLimitExceeded` の ViewModel 伝播テスト**

```kotlin
// ✅ Repository → UseCase → ViewModel の経路でエラー型が保持されることを確認
coEvery { getTrendArticlesUseCase(any()) } returns Result.failure(AppError.Unauthorized)
val state = viewModel.uiState.value as PortalUiState.Error
assertTrue(state.error is AppError.Unauthorized)
assertEquals("APIキーが無効です", state.message)
```

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `PortalViewModel` — `onFailure` で `AppError` を扱うよう変更（既存テスト全パス確認済み）
- `PortalScreen` — `ErrorContent` composable に置き換え（表示ロジック改善）
- `ArticleRepositoryImpl` — `refreshPortalArticles` のエラー型が `AppError` に変更
- `AiRepositoryImpl` — 全 AI メソッドのエラー型が `AppError.AiError` に変更
- `DigestViewModel` — `generateQuestionUseCase` / `generateFeedbackUseCase` の failure が `AppError` になるが、現状 `getOrDefault` でフォールバックするため UI への影響なし

**破壊的変更 (Breaking Change)**: なし（`PortalUiState.Error.message` 後方互換プロパティで既存コードが継続動作）

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| 既存テストがパスするか | ✅ BUILD SUCCESSFUL (147テスト) |
| HTTP 401 → `AppError.Unauthorized` | ✅ テスト追加済み |
| HTTP 429 → `AppError.RateLimitExceeded` | ✅ テスト追加済み |
| `IOException` → `AppError.NetworkError` | ✅ テスト追加済み |
| Gemini エラー → `AppError.AiError` | ✅ テスト更新済み |
| ViewModel で `AppError` 種別が保持される | ✅ テスト追加済み |

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。APPROVE です。

指摘事項なし。
recoverCatching で既存ロジックを変えずにエラーマッピングを追加するアプローチ、
後方互換 message プロパティで既存テストへの影響ゼロにした設計、
401/403 でリトライボタンを非表示にする UX 判断、すべて適切です。

軽微な設計上の注意点として、AppError.kt が domain/model/ にあるにも関わらず
retrofit2.HttpException をインポートしている点は、
今後 multi-module 化する際に toAppError() を data 層に移動させてください。
```

---

## ✅ チェックリスト

- [x] Critical issueがすべて解決されている（Criticalなし）
- [x] テストが追加・更新されている
- [x] 既存テストがパスしている（BUILD SUCCESSFUL）
- [x] 破壊的変更なし（後方互換 `message` プロパティあり）
- [x] セルフレビュー済み

---
*Generated by Claude Code / pr-review skill*
