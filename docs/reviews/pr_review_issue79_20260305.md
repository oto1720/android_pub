# PR レビューレポート

**PR/ブランチ**: issue79
**レビュー日時**: 2026-03-05
**変更規模**: +18134 / -85 / 138ファイル

> **注意**: このブランチは過去のマイルストーン実装コミットを多数含む大規模ブランチである。
> issue79 固有の変更（ポータル記事ソート機能）は以下の2コミットに集約されている:
> - `4377c32 feat: ポータル記事のソート機能追加（issue #80）`
> - `b9f1daf fix: ソート変更時にリストを先頭へスクロール（issue #80）`
>
> レビューは上記2コミットで追加・変更された実装部分に焦点を当てる。

---

## 🎯 変更の概要

ポータル画面（記事一覧）にソート機能を追加するPR。ユーザーが「新着順（createdAt降順）」と「いいね順（likesCount降順）」の2種類のソートを切り替えられるようにする。

ソート状態は `PortalViewModel` の `StateFlow<SortOrder>` で管理され、UI側は `FilterChip` による切り替えボタンを提供する。ソート変更時はリストが先頭にスクロールされる。

**変更種別**:
- [x] 新機能 (Feature)
- [ ] バグ修正 (Bug Fix)
- [ ] リファクタリング (Refactoring)
- [ ] テスト追加
- [ ] ドキュメント
- [ ] CI/CD

---

## ✅ マージ判定

> **[APPROVE]**

ソート機能の実装は設計・実装・テストの観点でいずれも高い品質を満たしている。`SortOrder` をドメイン層のモデルとして独立させ、ViewModel でのソート適用、UI でのチップ切り替え、スクロール先頭復帰が整合的に実装されている。

テストケースも充実しており、「同一ソートの再選択時に状態不変」「Loading状態でのソート変更でuiStateが変わらない（sortOrderは更新される）」といったエッジケースまでカバーされている。

1点だけ軽微な懸念がある（後述）が、マージをブロックするレベルではない。

---

## 📁 変更ファイル一覧

issue79 固有のソート機能に関する変更ファイルに絞る。

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|-------|
| `app/src/main/java/.../domain/model/SortOrder.kt` | 追加 | 9 | 0 | 低 |
| `app/src/main/java/.../ui/portal/PortalViewModel.kt` | 追加 | 165 | 0 | 低 |
| `app/src/main/java/.../ui/portal/PortalUiState.kt` | 追加 | 24 | 0 | 低 |
| `app/src/main/java/.../ui/portal/PortalScreen.kt` | 変更 | 483 | 28 | 低 |
| `app/src/main/java/.../data/local/dao/ArticleDao.kt` | 変更 | 32 | 0 | 中 |
| `app/src/main/java/.../data/local/entity/ArticleEntity.kt` | 変更 | 2 | 0 | 低 |
| `app/src/test/java/.../ui/portal/PortalViewModelTest.kt` | 追加 | 529 | 0 | 低 |

---

## 🔍 詳細レビュー

### 1. SortOrder ドメインモデル（`SortOrder.kt`）

```kotlin
enum class SortOrder {
    /** 新着順（createdAt 降順） */
    BY_DATE_DESC,

    /** いいね順（likesCount 降順） */
    BY_LIKES_DESC,
}
```

**評価**: 良い設計。ソート種別をドメイン層の `enum` として独立させており、UI層と切り離されている。現状2種類だが、将来的な拡張（「コメント順」など）にも対応しやすい。

---

### 2. PortalViewModel のソートロジック

```kotlin
private val _sortOrder = MutableStateFlow(SortOrder.BY_DATE_DESC)
val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

fun onSortOrderChange(order: SortOrder) {
    if (_sortOrder.value == order) return
    _sortOrder.value = order
    val current = _uiState.value as? PortalUiState.Success ?: return
    _uiState.value = current.copy(articles = current.articles.sortedWith(order))
}

private fun List<ArticleEntity>.sortedWith(order: SortOrder): List<ArticleEntity> = when (order) {
    SortOrder.BY_DATE_DESC -> sortedByDescending { it.createdAt }
    SortOrder.BY_LIKES_DESC -> sortedByDescending { it.likesCount }
}
```

**評価**: 以下の点が優れている。
- 同一ソートの再選択時は早期リターンで不要な再描画を防いでいる。
- ロード結果にも即座にソートを適用している（`loadArticlesInternal` 内）:

```kotlin
_uiState.value = PortalUiState.Success(articles.sortedWith(_sortOrder.value), availableTags)
```

- キャッシュフォールバック時もソートが適用されており、一貫した体験を提供している。

**懸念点 (軽微)**: `createdAt` が ISO 8601 文字列（例: `"2026-03-05T00:00:00Z"`）であるため、アルファベット順ソートが日時順と一致する条件（フォーマット統一）に依存している。Qiita API のレスポンスが `Z` (UTC) サフィックスを常に含む想定であれば問題ないが、将来的には `Instant.parse()` 等を使った型安全なソートへの移行が望ましい。

---

### 3. PortalScreen UI

```kotlin
// ソート切り替え
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChipPill(
        label = "新着順",
        selected = sortOrder == SortOrder.BY_DATE_DESC,
        onClick = { onSortOrderChange(SortOrder.BY_DATE_DESC) },
    )
    FilterChipPill(
        label = "いいね順",
        selected = sortOrder == SortOrder.BY_LIKES_DESC,
        onClick = { onSortOrderChange(SortOrder.BY_LIKES_DESC) },
    )
}
```

**評価**: タグフィルタと同一の `FilterChipPill` コンポーネントを再利用しており、視覚的一貫性が高い。`FilterChipPill` は `RoundedCornerShape(9999.dp)` でピル形状を実現しており、素直な実装である。

**ソート変更時のスクロール復帰**:

```kotlin
val listState = rememberLazyListState()

// ソート順が変わったら先頭にスクロール
LaunchedEffect(sortOrder) {
    listState.scrollToItem(0)
}
```

`LaunchedEffect(sortOrder)` でソート変更をキーにしてスクロール先頭復帰している。`scrollToItem(0)` のため、`Success` 状態でない場合（記事がない場合）は自動的に何もしない点も安全である。

---

### 4. ArticleDao の新規クエリ

```kotlin
@Query("SELECT * FROM articles WHERE status = :status ORDER BY createdAt DESC")
fun observeByStatusSortedByDate(status: String): Flow<List<ArticleEntity>>

@Query("SELECT * FROM articles WHERE status = :status ORDER BY likesCount DESC")
fun observeByStatusSortedByLikes(status: String): Flow<List<ArticleEntity>>
```

**評価**: DB側でのソートも用意されているが、現在の `PortalViewModel` の実装ではこれらのクエリは使用されず、全件取得後にアプリ側でソートしている。

**懸念点 (軽微)**: `observeByStatusSortedByDate` と `observeByStatusSortedByLikes` が定義されているが、現在未使用である。将来的に `ObservePortalArticlesUseCase` をこれらに切り替えることを見越した先行実装とも取れるが、未使用のPublicメソッドが残ることへの懸念はある（コメントによる説明、または使用開始時まで非追加を検討してもよい）。

---

### 5. テスト品質

```kotlin
@Test
fun `onSortOrderChange - BY_LIKES_DESCに変更すると記事がいいね順に並び替えられる`() = runTest {
    // Arrange: いいね数が少ない順で返す
    val articles = listOf(
        createDummyArticle("1", "low likes", likesCount = 10),
        createDummyArticle("2", "high likes", likesCount = 100),
    )
    coEvery { getTrendArticlesUseCase(null) } returns Result.success(articles)
    createViewModel()
    runCurrent()
    // Act
    viewModel.onSortOrderChange(SortOrder.BY_LIKES_DESC)
    // Assert: high likes が先頭に来る
    val state = viewModel.uiState.value as PortalUiState.Success
    assertEquals("2", state.articles[0].id)
    assertEquals("1", state.articles[1].id)
    assertEquals(SortOrder.BY_LIKES_DESC, viewModel.sortOrder.value)
}

@Test
fun `onSortOrderChange - 同じsortOrderを渡しても状態は変わらない`() = runTest {
    // ...
    val stateBefore = viewModel.uiState.value
    viewModel.onSortOrderChange(SortOrder.BY_DATE_DESC)
    assertEquals(stateBefore, viewModel.uiState.value)
}

@Test
fun `onSortOrderChange - SuccessでないときはuiStateが変わらない`() = runTest {
    // Arrange: Loading 状態のまま
    createViewModel()
    viewModel.onSortOrderChange(SortOrder.BY_LIKES_DESC)
    assertTrue(viewModel.uiState.value is PortalUiState.Loading)
    // sortOrder は更新される
    assertEquals(SortOrder.BY_LIKES_DESC, viewModel.sortOrder.value)
}
```

**評価**: ソート機能に関するテストケースが充実している。特に「Loading状態でソート変更してもuiStateが変わらないが、sortOrderは更新される」という細かい仕様がテストで明示されている点は素晴らしい。

---

## ⚠️ 影響範囲

### データベーススキーマ変更
`ArticleEntity` に `body: String?` と `likesCount: Int` が追加され、DBバージョンが `1 → 3` に変更されている。ただし `AppDatabase` に **マイグレーションコードが存在しない**:

```kotlin
@Database(
    entities = [ArticleEntity::class, DigestEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun digestDao(): DigestDao
}
```

マイグレーション未定義のため、既存のDBを持つデバイスでは **Room がクラッシュするか、`fallbackToDestructiveMigration()` が必要** となる。現在 `RoomDatabase.Builder` での設定が確認できないため、開発中フェーズとして `fallbackToDestructiveMigration()` が使われている可能性があるが、明示的な対処が推奨される。

### `PortalScreen` の関数シグネチャ変更
```kotlin
// 変更前
fun PortalScreen(
    onNavigateToDigest: (articleId: String) -> Unit,
    onNavigateToTsundoku: () -> Unit,
    onNavigateToDone: () -> Unit,
    modifier: Modifier = Modifier,
) {

// 変更後
fun PortalScreen(
    onNavigateToTsundoku: () -> Unit,
    onNavigateToDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortalViewModel = hiltViewModel(),
) {
```

`onNavigateToDigest` パラメータが削除されている。`AppNavHost.kt` での呼び出し側も変更されているため整合性は取れているが、Digestへのナビゲーション起点が変更された点は注意が必要である。

---

## 🧪 テスト確認

| テスト対象 | テスト種別 | 実装状況 |
|-----------|-----------|---------|
| `onSortOrderChange - BY_LIKES_DESCへの変更` | ユニットテスト | 実装済み |
| `onSortOrderChange - BY_DATE_DESCへの復帰` | ユニットテスト | 実装済み |
| `onSortOrderChange - 同一ソートの再選択` | ユニットテスト | 実装済み |
| `onSortOrderChange - Loading状態での変更` | ユニットテスト | 実装済み |
| `init - デフォルトがBY_DATE_DESCである` | ユニットテスト | 実装済み |
| `init - 初期表示でcreatedAt降順になる` | ユニットテスト | 実装済み |
| UI上のFilterChipの表示/選択状態 | UIテスト | 未実装（手動確認推奨） |
| ソート変更時のスクロール先頭復帰 | UIテスト | 未実装（手動確認推奨） |

---

## 💬 レビューコメント（コピペ用）

### コメント 1: createdAt の文字列ソートへの依存

```
app/src/main/java/com/example/oto1720/dojo2026/ui/portal/PortalViewModel.kt
```

> `createdAt` が `String` 型で ISO 8601 形式の場合、文字列のアルファベット順ソートが日時降順と一致します。
> ただし、フォーマットの一貫性（例: タイムゾーン表記の統一、`Z` vs `+00:00`）に依存するため、将来的に `Instant.parse(it.createdAt)` を使用した型安全なソートへ移行することを推奨します。
>
> ```kotlin
> // 現在
> SortOrder.BY_DATE_DESC -> sortedByDescending { it.createdAt }
>
> // 推奨（移行後）
> SortOrder.BY_DATE_DESC -> sortedByDescending { runCatching { Instant.parse(it.createdAt) }.getOrElse { Instant.EPOCH } }
> ```

---

### コメント 2: DBマイグレーションの未定義

```
app/src/main/java/com/example/oto1720/dojo2026/data/local/AppDatabase.kt
```

> DBバージョンが `1 → 3` に変更されていますが、マイグレーション定義が見当たりません。
> 開発フェーズであれば `fallbackToDestructiveMigration()` で問題ありませんが、`RoomDatabase.Builder` に明示的に追加されているか確認をお願いします。
> リリースに向けては適切なマイグレーションクラスの追加が必要になります。

---

### コメント 3: ArticleDao の未使用クエリ

```
app/src/main/java/com/example/oto1720/dojo2026/data/local/dao/ArticleDao.kt
```

> `observeByStatusSortedByDate` と `observeByStatusSortedByLikes` が追加されていますが、現在の実装では使用されていません。
> 将来の最適化（DB側ソート移行）のための先行追加であれば、コメントにその旨を記載しておくと他の開発者への意図が伝わりやすくなります。
>
> ```kotlin
> // TODO: ViewModel でのアプリ側ソートをこのクエリに移行予定
> @Query("SELECT * FROM articles WHERE status = :status ORDER BY createdAt DESC")
> fun observeByStatusSortedByDate(status: String): Flow<List<ArticleEntity>>
> ```

---

## ✅ チェックリスト

- [x] ソート機能（新着順・いいね順）が実装されている
- [x] SortOrder がドメイン層で定義されている
- [x] ViewModel で sortOrder が StateFlow として公開されている
- [x] 初期ソートがデフォルト（新着順）として設定されている
- [x] ソート変更時にリストが先頭にスクロールされる
- [x] 同一ソートの再選択時に不要な更新が行われない
- [x] Loading/Error 状態でのソート変更が安全に処理される
- [x] ロード・キャッシュフォールバック時にもソートが適用される
- [x] ViewModel のユニットテストが充実している
- [ ] DBマイグレーション処理の確認（RoomDatabase.Builder を要確認）
- [ ] `createdAt` 文字列ソートのフォーマット依存の将来的な対処検討
- [ ] 未使用の ArticleDao クエリへのコメント追記（任意）

---

*Generated by Claude Code / pr-review skill*
