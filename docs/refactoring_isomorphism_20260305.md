# 同型原理リファクタリング記録

**実施日**: 2026-03-05
**ベース**: `docs/reviews/isomorphism_20260305_000000.md` の指摘事項

---

## 概要

コードベース全体を「プリンシプル オブ プログラミング」の7設計原則でレビューし、
発見された10件の逸脱をすべて修正した。

---

## 変更一覧

### [I-1] UseCase 命名の統一（同型原理）

**問題**: `GetDoneArticlesUseCase` は `Flow` を返すにもかかわらず `Get` prefix を使っていた。
`Observe` prefix を使う他の Flow 返却 UseCase（`ObserveTsundokuArticlesUseCase` 等）と不統一。

**対応**:
- `ObserveDoneArticlesUseCase.kt` を新規作成（正式な実装）
- `GetDoneArticlesUseCase.kt` を `typealias` に置き換え（後方互換）
- `DoneViewModel` と `DoneViewModelTest` を新クラス名に更新

```
Before: class GetDoneArticlesUseCase       // Flow を返すのに Get
After:  class ObserveDoneArticlesUseCase   // Observe prefix で意味が一致
```

---

### [I-2] GenerateFeedbackUseCase のエラーハンドリング統一（対称原理・安全原理）

**問題**: 記事が見つからない場合、`GenerateQuestionUseCase` / `GenerateSummaryUseCase` は
`Result.failure` を返すのに、`GenerateFeedbackUseCase` だけ空文字 `""` で API を呼んでいた。

**対応**:
```kotlin
// Before
val article = articleRepository.getArticleById(articleId)
val content = article?.body ?: article?.title ?: ""   // 記事がなくても続行

// After
val article = articleRepository.getArticleById(articleId)
    ?: return Result.failure(IllegalArgumentException("記事が見つかりません: $articleId"))
val content = article.body ?: article.title           // 他の UseCase と同パターン
```

---

### [I-3] DoneItem の層移動（階層原理）

**問題**: `DoneItem` が `ui/done/DoneUiState.kt` に定義されており、
domain 層の `GetDoneArticlesUseCase` が `ui.done` パッケージを import していた（依存方向の逆転）。

**対応**:
- `domain/model/DoneItem.kt` を新規作成
- `ui/done/DoneUiState.kt` から `DoneItem` の定義を削除し、domain から import
- `DoneDetailViewModel`・`DoneScreen`・`DoneDetailScreen`・`DoneViewModelTest` の import を更新

```
Before: ui/done/DoneUiState.kt          ← domain UseCase が ui を import
After:  domain/model/DoneItem.kt        ← 正しい方向（domain → ui は OK）
```

---

### [I-4] ISO_8601_FORMAT 定数の一元化（同型原理）

**問題**: 同一フォーマット文字列 `"yyyy-MM-dd'T'HH:mm:ss'Z'"` が6箇所以上に重複定義されていた。

**対応**:
- `util/DateUtils.kt` を新規作成し `ISO_8601_FORMAT` 定数を一元管理
- `formatSavedAt()` ユーティリティ関数も同ファイルに集約

**変更ファイル**:
| ファイル | 変更内容 |
|---------|---------|
| `data/repository/ArticleRepositoryImpl.kt` | private const 削除 → import |
| `data/repository/DigestRepositoryImpl.kt` | private const 削除 → import |
| `domain/usecase/DigestArticleUseCase.kt` | private const 削除 → import |
| `ui/done/DoneScreen.kt` | リテラル → 定数参照 |
| `ui/done/DoneDetailScreen.kt` | リテラル → 定数参照 |
| `ui/tsundoku/TsundokuScreen.kt` | リテラル → 定数参照 |

---

### [I-5] MAX_TSUNDOKU_SLOTS 参照の統一（同型原理）

**問題**: `ObserveTsundokuCountUseCase` が `MAX_TSUNDOKU_SLOTS = 5` を独自に `private` 定義していた。
`PortalScreen` / `TsundokuScreen` はすでに `AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS` を参照していた。

**対応**:
```kotlin
// Before
companion object {
    private const val MAX_TSUNDOKU_SLOTS = 5  // 独自定義
}

// After（companion object ごと削除）
.map { it.size.coerceAtMost(AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS) }
```

---

### [I-6] DigestViewModel.isOnline の SharingStarted 統一（同型原理）

**問題**: `DigestViewModel.isOnline` だけ `SharingStarted.Eagerly` を使っていた。
他の全 StateFlow（PortalViewModel 含む）は `WhileSubscribed(5_000)` で統一されていた。
`Eagerly` は ViewModel が生きている間ずっと収集し続けるためメモリ効率が低い。

**対応**:
```kotlin
// Before
started = SharingStarted.Eagerly,

// After
started = SharingStarted.WhileSubscribed(5_000),
```

---

### [I-7] FeedbackResult の層移動（階層原理）

**問題**: `FeedbackResult` が `data/repository/FeedbackResult.kt` に定義されていた。
「理解度が十分か」というドメイン概念を表す型は `domain/model/` に置くべき。

**対応**:
- `domain/model/FeedbackResult.kt` を新規作成
- `data/repository/FeedbackResult.kt` を `typealias` に置き換え（後方互換）
- `AiRepository`・`AiRepositoryImpl`・`GenerateFeedbackUseCase`・`DigestViewModel` の import を更新

```
Before: data/repository/FeedbackResult.kt
After:  domain/model/FeedbackResult.kt
```

---

### [I-8] TsundokuScreen の未使用引数削除（対称原理）

**問題**: `TsundokuScreen(onNavigateToDone: () -> Unit)` が引数を受け取るだけで
内部のどこにも渡されておらず、実際には機能していなかった。

**対応**:
- `TsundokuScreen` から `onNavigateToDone` 引数を削除
- `AppNavHost.kt` の呼び出し箇所から対応する lambda を削除

---

### [I-9] PortalScreen の未接続検索バー削除（明証原理）

**問題**: `OutlinedTextField` による検索バーが UI に表示されていたが、
`searchQuery` はローカル state に保存されるだけで ViewModel に渡されておらず、
フィルタリングに一切使われていなかった。

**対応**:
- `PortalHeader` から `searchQuery` / `onSearchQueryChange` 引数と `OutlinedTextField` を削除
- `PortalScreenContent` から `searchQuery` の local state を削除
- 不要になった import（`OutlinedTextField`, `Search` アイコン, `rememberSaveable` 等）を削除

---

### [I-10] formatSavedAt 関数の重複削除（同型原理）

**問題**: 全く同じ実装の `formatSavedAt()` が `DoneScreen.kt` と `DoneDetailScreen.kt` の
両方に `private fun` として定義されていた。

**対応**:
- `util/DateUtils.kt` に `formatSavedAt()` を一元定義（[I-4] と同時対応）
- 両ファイルの private 関数を削除し、共通関数を import して使用

---

### [bonus] DigestViewModelTest の未追加モック修正

**問題**: `ai要約機能作成` コミットで `GenerateSummaryUseCase` が `DigestViewModel` に追加されたが、
`DigestViewModelTest` のコンストラクタ引数が更新されておらず、コンパイルエラーが潜在していた。

**対応**:
- `@MockK lateinit var generateSummaryUseCase: GenerateSummaryUseCase` を追加
- `createViewModel()` の引数リストに追加

---

## 新規作成ファイル一覧

| ファイル | 内容 |
|---------|------|
| `domain/model/DoneItem.kt` | DoneItem ドメインモデル |
| `domain/model/FeedbackResult.kt` | FeedbackResult ドメインモデル |
| `domain/usecase/ObserveDoneArticlesUseCase.kt` | 消化済み記事 Flow 監視 UseCase |
| `util/DateUtils.kt` | ISO_8601_FORMAT 定数 + formatSavedAt 関数 |

## 後方互換 typealias

| ファイル | 内容 |
|---------|------|
| `data/repository/FeedbackResult.kt` | `typealias FeedbackResult = domain.model.FeedbackResult` |
| `domain/usecase/GetDoneArticlesUseCase.kt` | `typealias GetDoneArticlesUseCase = ObserveDoneArticlesUseCase` |

---

## 設計ルール（今後の開発指針）

今回のリファクタリングで確認・確立したコードベースのルール。

### UseCase 命名規則

| 戻り値 | prefix | 例 |
|--------|--------|----|
| `Flow<T>` | `Observe` | `ObserveTsundokuArticlesUseCase` |
| `suspend` で単一値 | `Get` または動詞 | `GetArticleUseCase`, `AddToTsundokuUseCase` |

### レイヤー依存方向

```
ui → domain → data
```

- domain が ui を import してはならない
- ドメイン概念（DoneItem, FeedbackResult 等）は `domain/model/` に置く

### StateFlow の SharingStarted

全 ViewModel の StateFlow は `SharingStarted.WhileSubscribed(5_000)` で統一する。

### 共有定数・ユーティリティ

- 日時フォーマットは `util/DateUtils.kt` の `ISO_8601_FORMAT` を使う
- 積読スロット上限は `AddToTsundokuUseCase.MAX_TSUNDOKU_SLOTS` を参照する
