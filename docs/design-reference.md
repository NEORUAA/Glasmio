# Apple design reference

The [Apple iOS and iPadOS 27 design library](https://www.figma.com/design/AmwPTDqf3t85rARQXQhfX9/) supplies the component terminology, geometry, typography, material layers and Sample wallpaper. The Sample renders interactive Compose controls. Existing control optics, menu springs and drag physics remain preserved.

| Apple component | Glasmio API | Reference node | Geometry |
| --- | --- | --- | --- |
| Button — Liquid Glass — Text | `GlasmioButton` | `10456:14504`, `10456:14456`, `5473:21932` | Heights 28/34/50 dp, horizontal padding 10/12/20 dp, text 15/17/17 sp with 18 sp line height and weight 510 |
| Toggle — Switch | `GlasmioSwitch` | `27:68905` | 64 × 28 dp track, 38 × 24 dp knob |
| Segmented Control | `GlasmioSegmentedControl` | `10460:170` | 32 dp compact container, 28 dp selected surface |
| Stepper | `GlasmioStepper` | `5415:17699` | 92 × 32 dp, 46 dp action halves |
| Text Field | `GlasmioTextField` | `553:23061` | 52 dp row, 16 dp horizontal inset |
| Pop-up Button | `GlasmioPopUpButton` | `410:50568`, `410:50570` | 44 dp height, 18 sp Semibold 590 with 18 sp line height, 3 dp gap, 14 × 18 dp Medium symbol |
| Alert | `GlasmioAlertDialog` | `65:57149`, `12616:942` | 300 dp panel, 34 dp continuous corners, 14 dp outer inset, 19 dp field-to-action gap |
| List row | `GlasmioListRow` | `550:50627`, `550:50515` | 52 dp single-line or 68 dp subtitle row, inset separators, 17 sp detail |
| Section Title | `GlasmioSectionTitle` | `50:56535` | 39 dp minimum height, 16 dp horizontal inset, 9 dp bottom inset |
| Grouped Table Footer | `GlasmioGroupedTableFooter` | `35:55757` | 13 sp text, 18 sp line height, 16 dp horizontal inset |

Buttons retain the host's theme height when `size` is omitted, so the initial migration preserves MeiloX's existing control geometry. Explicit `GlasmioButtonSize` values use the official size terminology. `GlassEmphasis.Regular` and `Prominent` express Glass and Glass Prominent. Destructive and disabled states are caller-controlled.

All rounded component surfaces and Material theme shape roles use continuous curvature through `ContinuousRoundedRectangle` or Capsule. Circles remain circles. The host can override shape tokens and provide typography and symbol typefaces.

## Navigation glass

All standalone glass surfaces share the navigation renderer. Its default retains MeiloX's calibrated navigation appearance: the original neutral tint, 2 dp backdrop blur, exterior side shadows, crisp outline, inner shadows and light-mode shadow lift. Selecting a scene never removes these edge layers. Existing navigation bars and floating controls retain this default.

Explicit `NavigationGlassMaterial` presets adapt backing parameters from the Materials page (`215:105157`): Regular Small and Clear declare a frost radius of 6; Regular Medium and Large declare 16. Their tint layers and exterior shadow parameters are applied within the same renderer. Slider, Switch and Segmented Control internals keep their own optical behavior.

Figma's native parameters are recorded in [design/ios27-materials.json](design/ios27-materials.json). Its GLASS frost, depth and refraction fields do not establish equivalent Android shader kernels or lens units. The existing calibrated navigation lens remains the shared optical implementation; those values are not presented as a direct numerical conversion. The preserved default remains the visual reference for navigation controls.

## Sample wallpaper

Only the Materials tab and Materials catalog page use these original Apple iPhone assets from the System page, cropped to the viewport. All other Sample pages use a solid theme background. The source images are unchanged PNG exports at 1206 × 2622 pixels.

| Asset | Figma node | SHA-256 |
| --- | --- | --- |
| `sample/src/main/res/drawable-nodpi/ios27_wallpaper_light.png` | `0:5245` | `a05c7053f66c8f97d7a7dc57056ee1fa18527120b5307c0b8b6658130d39798d` |
| `sample/src/main/res/drawable-nodpi/ios27_wallpaper_dark.png` | `5707:9421` | `51be07df4d5969ae035066af28eb3a995b78f22ce04df49c590d24b11b46b946` |

Apple retains rights to those assets. They are Sample resources, excluded from the six library artifacts. Other reference screenshots remain local in `build/reports/design`.
