# PR レビューレポート

**PR/ブランチ**: `issue80`
**レビュー日時**: 2026-03-05
**変更規模**: +154 / -7 / 7ファイル（新規1ファイル含む）

---

## 🎯 変更の概要

ポータル画面に「新着順 / いいね順」のソート切り替え機能を追加する。
`ArticleEntity` に `likesCount` フィールドを追加し DB version を 3 へ更新。
`PortalViewModel` で `sortOrder: StateFlow<SortOrder>` を管理し、
ヘッダーに `FilterChip` ベースのソートセレクタを追加。

**変更種別**:
- [x] 新機能 (Feature)
- [ ] バグ修正 (Bug Fix)
- [ ] リファクタリング
- [ ] パフォーマンス改善
- [x] テスト追加
- [ ] ドキュメント

---

## ✅ マージ判定

> **APPROVE**

Critical / Major な問題なし。
`fallbackToDestructiveMigration()` 採用済みのため DB version 更新は安全。
ソートをインメモリで行う設計は軽量かつ適切。
Minor 2 件（任意改善）を除けば品質十分でマージ可。

---

## 📁 変更ファイル一覧

| ファイル | 変更種別 | +行 | -行 | 懸念度 |
|---------|---------|-----|-----|--------|
| `SortOrder.kt` | Added | +9 | 0 | 🟢 問題なし |
| `ArticleEntity.kt` | Modified | +1 | 0 | 🟢 問題なし |
| `AppDatabase.kt` | Modified | +1 | -1 | 🟢 問題なし |
| `QiitaArticleDtoMapper.kt` | Modified | +1 | 0 | 🟢 問題なし |
| `ArticleDao.kt` | Modified | +6 | 0 | 🟢 問題なし |
| `PortalViewModel.kt` | Modified | +23 | -2 | 🟢 問題なし |
| `PortalScreen.kt` | Modified | +27 | -3 | 🟡 軽微（後述） |
| `PortalViewModelTest.kt` | Modified | +95 | -6 | 🟢 問題なし |

---

## 🔍 詳細レビュー

### SortOrder.kt（新規）

#### 変更の意図
新規 enum で `BY_DATE_DESC`（新着順）と `BY_LIKES_DESC`（いいね順）を定義。

#### 指摘事項
特になし。`domain/model` パッケージに配置しており適切。

---

### ArticleEntity.kt

#### 変更の意図
いいね数でソートするために `likesCount: Int = 0` を追加。

#### 良い点
```kotlin
val likesCount: Int = 0,
```
デフォルト値を `0` にしているため、既存テストコードが named parameters を使っている限りコンパイルエラーなし。古いキャッシュレコード（DB マイグレーション後）も `0` として扱われ安全。

#### 指摘事項
特になし。

---

### AppDatabase.kt

#### 変更の意図
`likesCount` カラム追加に伴い DB version を 2 → 3 に更新。

```kotlin
-    version = 2,
+    version = 3,
```

`AppModule.kt` に `fallbackToDestructiveMigration()` が設定済みであるため、明示的な `Migration` クラスは不要。開発フェーズでの運用として適切。

#### 指摘事項
特になし。本番運用になった際は `Migration(2, 3)` への切り替えを検討。

---

### QiitaArticleDtoMapper.kt

#### 変更の意図
API レスポンスの `likes_count` を `likesCount` にマッピング。

```kotlin
+        likesCount = likesCount ?: 0,
```

DTO の `likesCount` フィールドはすでに `@SerializedName("likes_count") val likesCount: Int?` で定義済み（nullable）。`?: 0` で安全に処理している。

#### 指摘事項
特になし。

---

### ArticleDao.kt

#### 変更の意図
`ORDER BY createdAt DESC` / `ORDER BY likesCount DESC` の 2 クエリを追加。

```kotlin
@Query("SELECT * FROM articles WHERE status = :status ORDER BY createdAt DESC")
fun observeByStatusSortedByDate(status: String): Flow<List<ArticleEntity>>

@Query("SELECT * FROM articles WHERE status = :status ORDER BY likesCount DESC")
fun observeByStatusSortedByLikes(status: String): Flow<List<ArticleEntity>>
```

#### 指摘事項（Minor）

**[🟡 M-1] 追加したクエリが現時点で未使用** (`ArticleDao.kt:36-42`)

```kotlin
// 現在は未参照
fun observeByStatusSortedByDate(status: String): Flow<List<ArticleEntity>>
fun observeByStatusSortedByLikes(status: String): Flow<List<ArticleEntity>>
```

> 実際のソートは `PortalViewModel` 内でインメモリソートとして実装されており、
> これらの DAO クエリは呼ばれていない。
> issue 仕様書に「ArticleDao に ORDER BY クエリを追加」とあるため仕様通りではあるが、
> 将来 `ObservePortalArticlesUseCase` と接続する際に使用される意図と思われる。
> 今のところ使われていないことをコメントで明示するか、将来のリファクタリングとして追跡しておくと良い。

---

### PortalViewModel.kt

#### 変更の意図
- `_sortOrder: MutableStateFlow<SortOrder>` と公開 `sortOrder: StateFlow<SortOrder>` を追加
- `onSortOrderChange()` 関数で現在の Success 状態の記事をその場でインメモリソート
- `loadArticlesInternal()` で記事ロード時も現在の `_sortOrder` を適用
- `List<ArticleEntity>.sortedWith(SortOrder)` をプライベートメソッドとして定義

#### 良い点

```kotlin
fun onSortOrderChange(order: SortOrder) {
    if (_sortOrder.value == order) return  // 同値ガード
    _sortOrder.value = order
    val current = _uiState.value as? PortalUiState.Success ?: return  // 型安全ガード
    _uiState.value = current.copy(articles = current.articles.sortedWith(order))
}
```
同値チェック、非 Success 状態のガードが正確で、不要なリコンポーズを防いでいる。

```kotlin
_uiState.value = PortalUiState.Success(articles.sortedWith(_sortOrder.value), availableTags)
```
API ロード後・キャッシュフォールバック後の両方で `_sortOrder` を適用しており、ソート状態の一貫性が保たれている。

```kotlin
private fun List<ArticleEntity>.sortedWith(order: SortOrder): List<ArticleEntity> = when (order) {
    SortOrder.BY_DATE_DESC -> sortedByDescending { it.createdAt }
    SortOrder.BY_LIKES_DESC -> sortedByDescending { it.likesCount }
}
```
`when` 式で全ケースを網羅しており、新しい `SortOrder` 追加時にコンパイルエラーが出る設計。

#### 指摘事項（Minor）

**[🟡 M-2] `createdAt` は ISO 8601 文字列の辞書順ソートに依存している** (`PortalViewModel.kt`)

```kotlin
SortOrder.BY_DATE_DESC -> sortedByDescending { it.createdAt }
```

> Qiita API の `created_at` は `"2026-03-05T12:00:00+09:00"` 形式（タイムゾーン付き）。
> 辞書順ソートはタイムゾーンが混在すると正確でない可能性がある。
> 現状 DB にはそのまま文字列として格納されており、
> 日本語圏のみで同タイムゾーンであれば実害はほぼないが、
> より厳密にするなら `Instant.parse(it.createdAt).epochSecond` でソートする方が安全。
> これは任意改善。

---

### PortalScreen.kt

#### 変更の意図
- `PortalScreenContent` と `PortalHeader` に `sortOrder` / `onSortOrderChange` パラメータを追加
- ヘッダー末尾に "新着順" / "いいね順" の `FilterChip` 2 個を追加

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

既存の `FilterChipPill` コンポーザブルを再利用しており、UI の一貫性が保たれている。

#### 指摘事項
特になし。

---

### PortalViewModelTest.kt

#### 変更の意図
- `createDummyArticle` に `createdAt` / `likesCount` パラメータを追加（デフォルト値付き）
- ソート機能の 5 件のテストを追加

#### 良い点

```kotlin
private fun createDummyArticle(
    id: String,
    title: String,
    createdAt: String = "2026-03-05T00:00:00Z",
    likesCount: Int = 0,
): ArticleEntity { ... }
```
デフォルト値付きでパラメータを追加したため、既存テストのコードは変更不要。後方互換を保った良いテストヘルパー設計。

```kotlin
@Test
fun `onSortOrderChange - BY_DATE_DESCに戻すと記事が新着順に並び替えられる`() = runTest {
    // 双方向の切り替えを検証
    viewModel.onSortOrderChange(SortOrder.BY_LIKES_DESC) // likes → 1が先頭
    viewModel.onSortOrderChange(SortOrder.BY_DATE_DESC) // date → 2が先頭
    ...
}
```
往復の切り替えが正しく動作することを1テストで検証している。

#### 指摘事項
特になし。5 件のテストがすべて境界値・エッジケースを適切にカバーしている。

---

## ⚠️ 影響範囲

**このPRの変更が影響する箇所**:
- `PortalScreen.kt` — `PortalScreenContent` のシグネチャ変更（`sortOrder` / `onSortOrderChange` 追加）
  - `internal` なので外部からの参照なし
- `PortalViewModel.kt` — constructor 変更なし。`sortOrder` StateFlow と `onSortOrderChange()` を追加
- `ArticleEntity.kt` — デフォルト値付きのフィールド追加。`DigestScreen`・`TsundokuScreen` など `ArticleEntity` を使う画面に影響なし
- `AppDatabase.kt` — version 3 に更新。`fallbackToDestructiveMigration()` で既存 DB は削除・再作成

**破壊的変更 (Breaking Change)**: なし（コード互換）
- DB はバージョン更新で再作成されるが、開発フェーズでは想定内

---

## 🧪 テスト確認

| テスト項目 | 状態 |
|-----------|------|
| 既存テストがパスするか | ✅ 全 159 件 PASS |
| 新機能のテストが追加されているか | ✅ 5 件追加 |
| エッジケース（同値 / 非 Success 状態） | ✅ カバー済み |
| 双方向ソート切り替え | ✅ カバー済み |

---

## 💬 レビューコメント（コピペ用）

**全体コメント**:
```
レビューしました。APPROVE します。

Minor な改善提案が 2 件ありますが、いずれも必須修正ではありません。
- M-1: ArticleDao の新規クエリが未使用（将来接続予定であればコメント追記推奨）
- M-2: createdAt の文字列辞書順ソートはタイムゾーン混在時に不正確な可能性あり

テストカバレッジが適切で、ソート後の記事順・双方向切り替え・
非Success状態でのガードをすべて検証できています。
```

**インラインコメント候補**:
- `ArticleDao.kt:36`: `observeByStatusSortedByDate` は現在未使用です。将来 UseCase から呼ぶ予定があれば `// TODO:` コメントを追加すると意図が明確になります（任意）
- `PortalViewModel.kt (BY_DATE_DESC)`: `createdAt` はタイムゾーン付き ISO 8601 の場合、辞書順ソートがずれる可能性があります。現状問題ないですが、厳密化するなら `Instant.parse` 経由でのソートを検討（任意）

---

## ✅ チェックリスト

- [x] Critical issue がすべて解決されている（Critical なし）
- [x] テストが追加・更新されている（5 件追加）
- [x] 破壊的変更がある場合、マイグレーション手順が記載されている（`fallbackToDestructiveMigration` 採用済み）
- [x] セルフレビュー済み

---
*Generated by Claude Code / pr-review skill*
