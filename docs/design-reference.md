# Apple design reference

The local Apple iOS 27 Figma library was used to check the public component terminology and selected control geometry. Existing migrated rendering and spring behavior are preserved. The Sample uses composable controls rather than screenshots from the design file.

| Apple component | Glasmio API | Reference node | Geometry |
| --- | --- | --- | --- |
| Button — Liquid Glass — Text | `GlasmioButton` | `5473:21932` | Small 28, Medium 34, Large 50 dp; capsule outline |
| Toggle — Switch | `GlasmioSwitch` | `27:68905` | 64 × 28 dp track, 38 × 24 dp knob |
| Segmented Control | `GlasmioSegmentedControl` | `10460:170` | 32 dp compact container, 28 dp selected surface |
| Stepper | `GlasmioStepper` | `5415:17699` | 92 × 32 dp, 46 dp action halves |
| Text Field | `GlasmioTextField` | `553:23061` | 52 dp row, 16 dp horizontal inset |

Buttons retain the host's theme height when `size` is omitted, so the initial migration preserves MeiloX's existing control geometry. Explicit `GlasmioButtonSize` values use the official size terminology. `GlassEmphasis.Regular` and `Prominent` express Glass and Glass Prominent. Destructive and disabled states are caller-controlled.

All rounded component surfaces and Material theme shape roles use continuous curvature through `ContinuousRoundedRectangle` or Capsule. Circles remain circles. The host can override shape tokens and provide typography and symbol typefaces.

Rendered reference images used during development remain in the local `build/reports/design` directory. No Figma image is used as an implementation asset.
