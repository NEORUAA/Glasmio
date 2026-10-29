# Apple design reference

The [Apple iOS and iPadOS 27 design library](https://www.figma.com/design/AmwPTDqf3t85rARQXQhfX9/) supplies the component terminology, geometry, typography, material-layer references and Sample wallpaper. Native UIKit analysis and iPhone 13 mini captures take precedence for runtime material behavior. The Sample renders interactive Compose controls.

| Apple component | Glasmio API | Reference node | Geometry |
| --- | --- | --- | --- |
| Button — Liquid Glass — Text | `GlasmioButton` | `10456:14504`, `10456:14456`, `5473:21932` | Heights 28/34/50 dp, horizontal padding 10/12/20 dp, text 15/17/17 sp with 18 sp line height and weight 510 |
| Toggle — Switch | `GlasmioSwitch` | `27:68905` | 64 × 28 dp track, 38 × 24 dp knob |
| Segmented Control | `GlasmioSegmentedControl` | `10460:170` | 32 dp compact container, 28 dp selected surface |
| Stepper | `GlasmioStepper` | `5415:17699` | 92 × 32 dp, 46 dp action halves |
| Text Field | `GlasmioTextField` | `553:23061` | 52 dp row, 16 dp horizontal inset |
| Pop-up Button | `GlasmioPopUpButton` | `410:50568`, `410:50570`, inline List variant | 44 dp touch height; inline label inherits host body size, weight and tracking; 3 dp gap and a 14 × 18 dp regular symbol |
| Menu container | `GlasmioContextMenu` | `I5707:51032;10411:18581` | 238 dp width, 34 dp corners with 0.6 smoothing at reference heights 437/532/578 dp |
| Edit Menu | `GlasmioTextSelectionMenu` | `5713:47505`, `12740:24185` | 44 dp compact toolbar, 36 dp More button, 56 dp expanded shortcut row; 24 dp from the 17 sp/20 dp text frame to the toolbar |
| Text Selection | `GlasmioEditableText` | `I12740:24161;5711:35407`, `I12740:24161;5711:35410` | 16 dp lollipop, 2 dp stem, 8 dp center offset outside the selected line |
| Alert | `GlasmioAlertDialog` | `65:57149`, `12616:942` | 300 dp panel, 34 dp continuous corners, 14 dp outer inset, 19 dp field-to-action gap |
| List row | `GlasmioListRow` | `550:50627`, `550:50515` | 52 dp single-line or 68 dp subtitle row, inset separators, 17 sp detail |
| List trailing symbols | `SfIcon` | `212:119974`, `212:122278`, `212:124674`, `212:127130` | Star/info: 17 sp Regular, SS16, 22 dp slot; drill-in: 17 sp weight 590, SS15, 8 × 22 dp slot; checkmark: 17 sp weight 590, 22 dp slot |
| Section Title | `GlasmioSectionTitle` | `50:56535` | 39 dp minimum height, 16 dp horizontal inset, 9 dp bottom inset |
| Grouped Table Footer | `GlasmioGroupedTableFooter` | `35:55757` | 13 sp text, 18 sp line height, 16 dp horizontal inset |

Buttons retain the host's theme height when `size` is omitted, so the initial migration preserves MeiloX's existing control geometry. Explicit `GlasmioButtonSize` values use the official size terminology. `GlassEmphasis.Regular` and `Prominent` express Glass and Glass Prominent. Destructive and disabled states are caller-controlled.

List accessories set `SfIcon.fontSize` separately from the layout slot and opt out of glyph fitting with `fitGlyphToBounds = false`, preserving the design's native symbol metrics. Other icon callers retain their existing fitting behavior. Grouped action rows share the enclosing card background; explanatory `GlasmioGroupedTableFooter` text stays outside that card.

`GlasmioSwitch` defaults to iOS system green (`#34C759` light / `#30D158` dark), independently of the host accent; use `onTintColor` for an explicit override. Disabled switches apply 0.5 opacity to the whole control. Disabled text and icon buttons retain regular glass with tertiary vibrant content, including when their enabled style is Prominent or destructive.

The design's switch artwork uses a 64 × 28 track and a 38 × 24 knob. The runtime implementation follows the measured UIKit control: a 63 × 28 track and a 37 × 24 resting thumb. Native control-lens geometry and state behavior are recorded in [native materials](native-materials.md#control-lenses).

All rounded component surfaces and Material theme shape roles use continuous curvature through `ContinuousRoundedRectangle` or Capsule. Circles remain circles. The host can override shape tokens and provide typography and symbol typefaces.

## Navigation glass

All standalone glass surfaces share Glasmio's owned navigation renderer. Regular materials use a measured 5-point native blur profile; Clear uses a distinct 0.75-point profile. Native CPU/AIR recovery supplies radius packing, mip sampling, face/tint transforms and SDF edge transfers. Physical captures supply material profiles and confirm the finger-driven glow. Size interpolation and Android render-target adaptation remain documented reconstruction boundaries.

`NavigationGlassMaterial.RegularSmall`, `RegularMedium`, `RegularLarge` and `Clear` retain the design's scene terminology. The regular presets use native face and edge processing. Switch, Slider, Segmented Control and Tab selected indicators use the separate native control-lens composition, with distinct resting bodies and identity transitions.

The design's original parameters remain in [design/ios27-materials.json](design/ios27-materials.json). Their GLASS frost, depth and refraction fields are design data rather than Android kernel units. [Native material rendering](native-materials.md) separates measured UIKit values, recovered tint/hysteresis logic and Glasmio's portable shader reconstruction.

## Sample wallpaper

Only the Materials tab and Materials catalog page use these original Apple iPhone assets from the System page, cropped to the viewport. All other Sample pages use a solid theme background. The source images are unchanged PNG exports at 1206 × 2622 pixels.

| Asset | Figma node | SHA-256 |
| --- | --- | --- |
| `sample/src/main/res/drawable-nodpi/ios27_wallpaper_light.png` | `0:5245` | `a05c7053f66c8f97d7a7dc57056ee1fa18527120b5307c0b8b6658130d39798d` |
| `sample/src/main/res/drawable-nodpi/ios27_wallpaper_dark.png` | `5707:9421` | `51be07df4d5969ae035066af28eb3a995b78f22ce04df49c590d24b11b46b946` |

Apple retains rights to those assets. They are Sample resources, excluded from the six library artifacts. Other reference screenshots and inspection receipts remain local in the ignored `.local-research/design/` directory.
