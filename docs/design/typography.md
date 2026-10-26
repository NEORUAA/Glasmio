# Text styles and Dynamic Type

Source: [Apple iOS 27, Text Styles and Dynamic Type](https://www.figma.com/design/AmwPTDqf3t85rARQXQhfX9?node-id=0-2194).

The primary text column is `224:56274` in Text styles `5418:17464`.
The regular Dynamic Type columns are in `5418:17465`; all five accessibility
columns are in `5418:27121`. Raw node IDs, text-style IDs, variable-font axes,
font sizes, line heights, and tracking are retained in
[ios27-typography.json](ios27-typography.json).

## Semantic presets

`GlasmioTheme.textStyles` supplies all eleven regular semantic styles.
`IosTextStyle` and `IosTextVariant` expose the 34 variants actually present
in the source. Unsupported combinations, such as an italic Large Title, are
rejected rather than synthesized.

| Role | Regular weight | Emphasized weight | Italic weight | Emphasized italic weight | Tracking |
| --- | ---: | ---: | ---: | ---: | ---: |
| Large Title | 400 | 700 | — | — | 0.40 |
| Title 1 | 400 | 700 | — | — | 0.38 |
| Title 2 | 400 | 700 | — | — | -0.26 |
| Title 3 | 400 | 590 | — | — | -0.45 |
| Headline | 590 | — | 590.7999878 | — | -0.43 |
| Body | 400 | 590 | 400 | 590.7999878 | -0.43 |
| Callout | 400 | 590.7999878 italic | 400 | 590.7999878 | -0.31 |
| Subheadline | 400 | 590 | 400 | 590.7999878 | -0.23 |
| Footnote | 400 | 590 | 400 | 590.7999878 | -0.08 |
| Caption 1 | 400 | 590 | 400 | 590.7999878 | 0 |
| Caption 2 | 400 | 590 | 400 | 590.7999878 | 0.06 |

The supplied file names Callout/Emphasized but assigns Semibold Italic, matching
its Emphasized Italic style. The implementation preserves that source value.
Compose uses integer font weights, so 590.7999878 is represented by 591.
The supplied Subheadline/Emphaized Italic style spelling is retained in the
raw evidence; the API uses `EmphasizedItalic`.

## Dynamic Type values

Each cell is **font size / line height**, in the design's logical points.
Tracking stays the role-specific value above at every size.

| Style | xSmall | Small | Medium | Large (Default) | xLarge | xxLarge | xxxLarge | AX1 | AX2 | AX3 | AX4 | AX5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Large Title | 31/38 | 32/38 | 33/40 | 34/41 | 36/43 | 38/46 | 40/48 | 44/52 | 48/57 | 52/61 | 56/66 | 60/70 |
| Title 1 | 25/31 | 26/31 | 27/33 | 28/34 | 30/36 | 32/39 | 34/41 | 38/46 | 43/51 | 48/57 | 53/62 | 58/68 |
| Title 2 | 19/23 | 20/23 | 21/28 | 22/28 | 24/30 | 26/32 | 28/34 | 34/41 | 43/51 | 44/52 | 50/59 | 56/66 |
| Title 3 | 17/22 | 18/22 | 19/24 | 20/25 | 22/28 | 24/30 | 26/32 | 31/38 | 37/44 | 43/51 | 43/51 | 55/65 |
| Headline | 14/19 | 15/19 | 16/21 | 17/22 | 19/24 | 21/26 | 23/29 | 28/34 | 33/40 | 40/48 | 47/56 | 53/62 |
| Body | 14/19 | 15/19 | 16/21 | 17/22 | 19/24 | 21/26 | 23/29 | 28/34 | 33/40 | 40/48 | 47/56 | 53/62 |
| Callout | 13/18 | 14/18 | 15/20 | 16/21 | 18/23 | 20/24 | 22/28 | 26/32 | 32/39 | 38/46 | 44/52 | 51/60 |
| Subhead | 12/16 | 13/16 | 14/19 | 15/20 | 17/22 | 19/24 | 21/28 | 25/31 | 30/37 | 36/43 | 42/50 | 49/58 |
| Footnote | 12/16 | 12/16 | 12/16 | 13/18 | 15/20 | 17/22 | 19/24 | 23/29 | 27/33 | 33/40 | 38/46 | 44/52 |
| Caption 1 | 11/13 | 11/13 | 11/13 | 12/16 | 14/19 | 16/21 | 18/23 | 22/28 | 26/32 | 32/39 | 37/44 | 43/51 |
| Caption 2 | 11/13 | 11/13 | 11/13 | 11/13 | 13/18 | 15/20 | 17/22 | 20/25 | 24/30 | 29/35 | 34/41 | 40/48 |

These are separate tables, not one scale factor. For example, the supplied
Title 3 AX3 and AX4 cells both contain 43/51.

## Android scaling and host fonts

The default `GlasmioTheme()` and `glasmioTypography()` use exact Large
presets in sp and let Android apply the user's system font size. Existing
`IosTypography.largeTitle` and `title2` keep their emphasized role selection;
the missing semantic aliases are also available. Material display roles map
to the Apple Large Title, Title 1, and Title 2 emphasized presets.

An explicit `dynamicTypeSize` selects one exact Apple column. Each font size,
line height, and tracking value is converted from dp to sp with the current
`Density`; rendering applies the inverse conversion, so Android font scaling
is not applied a second time. The surrounding UI density and other unrelated
text are unchanged. This also uses Compose's nonlinear font-scale conversion
rather than dividing by a guessed linear factor.

```kotlin
GlasmioTheme(dynamicTypeSize = IosDynamicTypeSize.AX1) {
    Text("Body", style = GlasmioTheme.textStyles.body)
    Text(
        "Body",
        style = GlasmioTheme.textStyles[IosTextStyle.Body, IosTextVariant.Emphasized],
    )
}
```

`rememberGlasmioTextStyles(dynamicTypeSize = size)` supports a local preview
without changing the surrounding theme. It inherits the font family and
platform text settings from each matching Material role. The non-composable
explicit-category factory requires the host `Density`:

```kotlin
val styles = glasmioTextStyles(
    fontFamily = licensedHostFamily,
    dynamicTypeSize = IosDynamicTypeSize.AX1,
    density = density,
)
```

A host-provided `Typography` remains an override when passed to
`GlasmioTheme`; its own sizes and weights are retained unless the host also
selects an explicit Dynamic Type category. Preset text styles inherit its
role-specific font families. Glasmio does not bundle SF Pro. Presets retain their requested leading without
trimming the first or last line. Android can reserve a slightly taller outer
line box when the loaded font's rounded ascent and descent exceed that leading;
the preset values remain unchanged and glyphs are not clipped.

The Sample's `TypographyDemo` renders the actual presets, all source variants,
and a category picker. Its large title, progressive blur, background, and
scroll state use the common page shell.
