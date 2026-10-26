# Apple design reference

The [Apple iOS and iPadOS 27 design library](https://www.figma.com/design/AmwPTDqf3t85rARQXQhfX9/) supplies the component terminology, geometry, typography, material layers and Sample wallpaper. The Sample renders interactive Compose controls. Existing control optics, menu springs and drag physics remain preserved.

| Apple component | Glasmio API | Reference node | Geometry |
| --- | --- | --- | --- |
| Button — Liquid Glass — Text | `GlasmioButton` | `10456:14504`, `10456:14456`, `5473:21932` | Heights 28/34/50 dp, horizontal padding 10/12/20 dp, text 15/17/17 sp with 18 sp line height and weight 510 |
| Toggle — Switch | `GlasmioSwitch` | `27:68905` | 64 × 28 dp track, 38 × 24 dp knob |
| Segmented Control | `GlasmioSegmentedControl` | `10460:170` | 32 dp compact container, 28 dp selected surface |
| Stepper | `GlasmioStepper` | `5415:17699` | 92 × 32 dp, 46 dp action halves |
| Text Field | `GlasmioTextField` | `553:23061` | 52 dp row, 16 dp horizontal inset |

Buttons retain the host's theme height when `size` is omitted, so the initial migration preserves MeiloX's existing control geometry. Explicit `GlasmioButtonSize` values use the official size terminology. `GlassEmphasis.Regular` and `Prominent` express Glass and Glass Prominent. Destructive and disabled states are caller-controlled.

All rounded component surfaces and Material theme shape roles use continuous curvature through `ContinuousRoundedRectangle` or Capsule. Circles remain circles. The host can override shape tokens and provide typography and symbol typefaces.

## Navigation glass

All standalone glass surfaces share the navigation renderer. `NavigationGlassMaterial` selects the scene parameters from the Materials page (`215:105157`): Regular Small and Clear use a 6 dp frost radius; Regular Medium and Large use 16 dp. The presets retain the source layer ordering, blend modes, light/dark tint and shadow geometry. Slider, Switch and Segmented Control internals keep their own optical behavior.

Figma's optical parameters are recorded in [design/ios27-materials.json](design/ios27-materials.json). Its `depth` and `refraction` fields do not use the same units as the Android lens shader. The existing calibrated navigation lens remains the shared optical implementation; those values are not presented as a direct numerical conversion.

## Sample wallpaper

Ordinary catalog, preference and About pages use a solid theme background. Material and glass-control demonstrations use these original Apple iPhone assets from the System page, cropped to the viewport. The source images are unchanged PNG exports at 1206 × 2622 pixels.

| Asset | Figma node | SHA-256 |
| --- | --- | --- |
| `sample/src/main/res/drawable-nodpi/ios27_wallpaper_light.png` | `0:5245` | `a05c7053f66c8f97d7a7dc57056ee1fa18527120b5307c0b8b6658130d39798d` |
| `sample/src/main/res/drawable-nodpi/ios27_wallpaper_dark.png` | `5707:9421` | `51be07df4d5969ae035066af28eb3a995b78f22ce04df49c590d24b11b46b946` |

Apple retains rights to those assets. They are Sample resources, excluded from the six library artifacts. Other reference screenshots remain local in `build/reports/design`.
