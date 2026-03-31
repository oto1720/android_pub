# 04. Material3 テーマ・カラー・タイポグラフィ

## Material3 とは？

Google の最新デザインシステム。色・形・タイポグラフィを統一した UI コンポーネント群。
Compose では `MaterialTheme` でアプリ全体にテーマを適用する。

---

## テーマ適用の仕組み — Theme.kt

```kotlin
@Composable
fun TechDigestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),  // システム設定を自動検出
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,  // カラーパレット
        typography = Typography,    // テキストスタイル
        content = content,          // このテーマが適用される子Composable
    )
}
```

**`isSystemInDarkTheme()`**:
- Android の「ダークモード設定」を読み取る
- ユーザーが設定を変えると自動で再コンポーズされる

**`MaterialTheme` の役割**:
- `MaterialTheme.colorScheme` / `MaterialTheme.typography` として子Composableからアクセス可能
- `Button`, `Text`, `Card` などのコンポーネントが自動でテーマ色を使う

---

## カラーシステム — Color.kt

Material3 のカラーシステムは「役割ベース」。色を直接指定せず、**役割（primary, secondary, ...）** で指定します。

### ライトテーマのカラー

```kotlin
private val LightColorScheme = lightColorScheme(
    primary            = Color(0xFF1A6B5A),  // ← 深緑（ブランドカラー）
    onPrimary          = Color(0xFFFFFFFF),  // primary の上に置くテキスト（白）
    primaryContainer   = Color(0xFFA8F0DE),  // primary の背景コンテナ（薄緑）
    onPrimaryContainer = Color(0xFF00201A),  // primaryContainer 上のテキスト（濃緑）

    secondary            = Color(0xFFD0663A),  // オレンジ（アクセントカラー）
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFFFDDD0),
    onSecondaryContainer = Color(0xFF3A0E00),

    tertiary            = Color(0xFF4D609B),  // 青紫（補助カラー）
    onTertiary          = Color(0xFFFFFFFF),
    tertiaryContainer   = Color(0xFFDDE1FF),
    onTertiaryContainer = Color(0xFF061754),

    background   = Color(0xFFF5FAF8),  // 画面の背景
    onBackground = Color(0xFF171D1B),  // 背景上のテキスト
    surface      = Color(0xFFF5FAF8),  // カードなどの表面
    onSurface    = Color(0xFF171D1B),  // surface 上のテキスト
    ...
)
```

### カラーロールの命名規則

```
primary           ← メインカラー（ボタン背景など）
on + Primary      ← primary の上に置くもの（テキスト、アイコン）
primary + Container ← primary を薄くしたコンテナ
on + PrimaryContainer ← container の上に置くもの
```

**Composable での使い方**:
```kotlin
// テーマカラーを使う（ハードコードしない）
Text(
    text = "Hello",
    color = MaterialTheme.colorScheme.onBackground,  // 背景上のテキスト色
)
Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
    )
) { ... }
```

### ライト/ダーク対応の仕組み

```
Color.kt で全色を定義
          │
          ├── LightColorScheme（PrimaryLight = 0xFF1A6B5A など）
          └── DarkColorScheme（PrimaryDark = 0xFF8CD4BF など）
                              ↑
                    ダークでは明るめの色にする
                    （背景が暗いので視認性を確保）

Theme.kt で切り替え
  isSystemInDarkTheme() → true  → DarkColorScheme
                        → false → LightColorScheme
```

---

## タイポグラフィ — Type.kt

テキストサイズ・ウェイト・行間を役割ごとに定義します。

```kotlin
val Typography = Typography(

    // 大見出し（記事タイトルなど）
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),

    // セクションタイトル・カード見出し
    titleLarge  = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, ...),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 16.sp, ...),
    titleSmall  = TextStyle(fontWeight = FontWeight.Medium,   fontSize = 14.sp, ...),

    // 本文
    bodyLarge  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall  = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),

    // ラベル・タグ・補足テキスト
    labelLarge  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, ...),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, ...),
    labelSmall  = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, ...),
)
```

**Composable での使い方**:
```kotlin
Text(
    text = "記事タイトル",
    style = MaterialTheme.typography.headlineSmall,  // スタイルを指定
)
Text(
    text = "本文テキスト",
    style = MaterialTheme.typography.bodyMedium,
)
```

### タイポグラフィスケール（サイズ一覧）

| 役割 | サイズ | ウェイト | 用途例 |
|------|--------|---------|-------|
| headlineLarge | 28sp | Bold | 画面タイトル |
| headlineMedium | 24sp | SemiBold | ダイアログタイトル |
| headlineSmall | 20sp | SemiBold | セクション大見出し |
| titleLarge | 18sp | SemiBold | カード見出し |
| titleMedium | 16sp | Medium | リストアイテムタイトル |
| titleSmall | 14sp | Medium | サブタイトル |
| bodyLarge | 16sp | Normal | メイン本文 |
| bodyMedium | 14sp | Normal | 補足説明 |
| bodySmall | 12sp | Normal | 注釈 |
| labelLarge | 14sp | Medium | ボタンラベル |
| labelMedium | 12sp | Medium | タグ |
| labelSmall | 11sp | Medium | 最小ラベル |

---

## MainActivity での適用

```kotlin
setContent {
    TechDigestTheme {                    // ← ここでテーマを適用
        val navController = rememberNavController()
        Scaffold(...) {
            AppNavHost(...)              // ← 子Composable 全体にテーマが伝播
        }
    }
}
```

`TechDigestTheme` の中にある全 Composable は `MaterialTheme.colorScheme` と
`MaterialTheme.typography` を参照できます。

---

## なぜハードコードしないのか

```kotlin
// ❌ ハードコード（ダークモードで読めなくなる可能性）
Text(text = "Hello", color = Color(0xFF171D1B))

// ✅ テーマカラー参照（ダーク/ライト自動対応）
Text(text = "Hello", color = MaterialTheme.colorScheme.onBackground)
```

テーマ経由にすることで：
- ダークモード対応が自動
- ブランドカラー変更時に1箇所の修正で済む
- コンポーネントの見た目が統一される

---

## つまずきポイント

### Q: `sp` と `dp` の違いは？
→ `sp` はフォントサイズ用（ユーザーのフォントサイズ設定に追従）、`dp` はレイアウト用（追従しない）。テキストには必ず `sp` を使う。

### Q: `onPrimary` はいつ使う？
→ `primary` の背景の上にテキストやアイコンを置くとき。ボタンのラベルなど。

### Q: ダークテーマの色がライトより明るいのはなぜ？
→ 暗い背景の上でコントラスト比を確保するため。Material3 のカラーシステムのルール。
