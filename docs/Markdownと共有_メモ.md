# Markdown と共有 — 軽いメモ

> Tech-Digest での実装と、Android での一般的なアプローチをまとめた軽いメモです。

---

## Markdown レンダリング

### このプロジェクトでの実装

- **ライブラリ**: [Markwon](https://github.com/noties/Markwon)（`markwon.core`）
- **用途**: 消化画面で記事本文を Markdown 表示。オフライン時は WebView の代わりに Markdown で表示
- **場所**: `DigestScreen.kt` の `MarkdownContent`（記事本文の Markdown 表示は消化画面のみ。完了詳細はメモ・フィードバックをプレーンテキスト、記事は WebView で表示）

```kotlin
// 使用例
Markwon.create(textView.context).setMarkdown(textView, markdown)
```

- **WebView との切り替え**: `isMarkdownMode` で WebView / Markdown を切り替え。オフライン時は自動で Markdown にフォールバック

---

### Markdown 実装の工夫

| 工夫 | 内容 |
|------|------|
| **オフライン自動フォールバック** | `NetworkMonitor.isOnline` が false になり、かつ `article.body != null` のとき、`isMarkdownMode = true` に自動切り替え。電波がなくても Room にキャッシュした body を Markdown で表示できる。 |
| **body が null のときは切り替えボタン非表示** | `onToggleViewMode = if (readMode?.article?.body != null) onToggleViewMode else null`。body がない記事（API で取得していない等）では MD/Web 切り替えを出さない。 |
| **Compose と View の責務分離** | `AndroidView` の `factory` で TextView を生成、`update` で Markwon に描画を依頼。スクロールは Compose の `verticalScroll` で扱い、TextView の独自スクロールとの競合を回避。 |
| **body の長さ制限** | `QiitaArticleDtoMapper` で `body?.take(BODY_MAX_LENGTH)`（10,000 文字）に切り詰め。長文で DB やレンダリングが重くならないように。 |
| **テキストスタイル** | `textSize = 15f`、`setLineSpacing(0f, 1.5f)` で行間を調整。読みやすさを確保。 |
| **パディング** | `padding(horizontal = 16.dp, vertical = 24.dp)` で余白を統一。 |

### パフォーマンス上の注意点（PR レビュー指摘）

- **現状**: `update` ラムダ内で `Markwon.create()` を毎回呼んでいる。`markdown` が変わるたびに `update` が走るため、Recomposition のたびに Markwon インスタンスが生成される。
- **改善案**: `factory` で `Markwon.create(context)` を1回だけ生成し、`textView.tag = markwon` で保持。`update` では `(textView.tag as Markwon).setMarkdown(textView, markdown)` で再利用。現状は静的コンテンツのため必須ではないが、動的コンテンツを想定するならキャッシュがベター。

### Compose 向け Markdown ライブラリ（参考）

| ライブラリ | 特徴 | 備考 |
|------------|------|------|
| **Markwon** | 従来の View 向け。TextView に描画 | このプロジェクトで使用。`AndroidView` で Compose に埋め込み |
| **MarkdownText** (ArnyminerZ) | Compose ネイティブ | CommonMark / GitHub フレーバー対応。テーブル等は未対応 |
| **MarkdownTwain** | 編集・表示両方 | Markwon ベース。シンタックスハイライト、プレビュー |
| **compose-markdown** (jisungbin) | 学習用 PoC | アーカイブ済み |

### WebView vs Markdown のトレードオフ

| 観点 | WebView | Markdown |
|------|---------|----------|
| 実装コスト | 低（URL を loadUrl） | 高（パース→描画） |
| オフライン | △ URL は開けない | ◎ 文字列をキャッシュ可能 |
| 表示 | 元サイトのデザイン | アプリ内で統一 |
| Qiita 記事 | ◎ そのまま表示 | API の body を取得して表示 |

---

## 共有機能

### このプロジェクトでの実装

- **方法**: `Intent.ACTION_SEND` + `Intent.createChooser`
- **共有内容**: 記事タイトル + URL（テキスト）
- **場所**: `DigestScreen.kt`、`DoneDetailScreen.kt` の共有ボタン

```kotlin
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.url}")
}
context.startActivity(Intent.createChooser(intent, "記事を共有"))
```

### Android 共有の基本

| 方法 | 用途 |
|------|------|
| **Android Sharesheet** | ユーザーが共有先を選ぶ。`Intent.createChooser()` で表示 |
| **Intent Resolver** | 特定アプリに直接渡す（例: PDF ビューア） |

### 共有の種類

| 種類 | MIME 型 | 実装 |
|------|---------|------|
| **テキスト** | `text/plain` | `putExtra(Intent.EXTRA_TEXT, "..."` |
| **画像** | `image/*` | `putExtra(Intent.EXTRA_STREAM, contentUri)` |
| **ファイル** | 各種 | FileProvider で Content URI を発行 |

### ファイル共有時の注意

- **FileProvider** を AndroidManifest に登録
- `file_path.xml` で共有するパスを定義
- `android:grantUriPermissions="true"` を指定
- ストレージ権限（READ_EXTERNAL_STORAGE 等）が必要な場合あり

### 公式ドキュメント

- [Send simple data to other apps](https://developer.android.com/training/sharing/send)
- Sharesheet の使用が推奨（UX の一貫性、ランキング）

---

## このプロジェクトでの今後の拡張案

- **Markdown**: 現状 Markwon で十分。Compose ネイティブにしたい場合は MarkdownText 等の検討
- **共有**: 現状はテキストのみ。画像（OGP 等）やメモ付き共有は FileProvider 等の追加が必要
