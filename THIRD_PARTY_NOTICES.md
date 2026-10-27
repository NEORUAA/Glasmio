# Third-party notices

Glasmio was extracted from the reusable UI implementation in [MeiloX](https://github.com/NEORUAA/MeiloX). Original Glasmio code is Copyright 2026 NEORUAA and licensed under Apache-2.0. Existing third-party notices in source files remain applicable to those files and portions.

| Project | Use | License |
| --- | --- | --- |
| [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), Kyant and contributors | Adapted refraction math, interaction utilities, and the `MagnifierContent` optics in `GlasmioMagnifier`, including notices retained in `GlassMorphRenderer`, `NavigationContactRefraction`, `DragGestureInspector`, and `IosMagnifier` | Apache-2.0 |
| [Backdrop / AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), Kyant and contributors | Backdrop recording and optical effects | Apache-2.0 |
| [Capsule](https://github.com/Kyant0/Capsule), Kyant and contributors | Continuous rounded geometry | Apache-2.0 |
| [Shapes](https://github.com/Kyant0/Shapes), Kyant and contributors | Capsule outlines and shape utilities | Apache-2.0 |
| [AndroidX](https://android.googlesource.com/platform/frameworks/support), Google and contributors | Compose, Material, lifecycle, activity, vector fallback icons | Apache-2.0 |
| [Reorderable](https://github.com/Calvin-LL/Reorderable), Calvin Liang and contributors | Drag reordering | Apache-2.0 |
| [Miuix](https://github.com/compose-miuix-ui/miuix), Miuix contributors | Overscroll and the Sample's Navigation3 transitions | Apache-2.0 |

The Apache-2.0 license text is included in [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt). Dependencies also retain the notices in their own distributions.

Apple's iOS and iPadOS 27 Figma library is a design reference. The Sample includes the original light and dark iPhone wallpapers exported from that library; those Apple assets remain subject to Apple's terms and are not covered by Glasmio's Apache-2.0 license. Their source nodes and checksums are recorded in [docs/design-reference.md](docs/design-reference.md). They are not included in the published library AARs.

Glasmio does not distribute rendered design screenshots as component artwork. SF Symbols names are accepted by the symbol resolver. Library artifacts contain no Apple fonts. The listening-mode artwork below is included in `glasmio-icons`. The Demo APK bundles host-supplied SF Pro, its SF Symbols glyphs, and real italic faces; these Apple assets remain subject to Apple's terms and are not covered by Glasmio's Apache-2.0 license. The font source files are excluded from Git and library artifacts. Debug builds accept optional local fonts, while Demo builds require both font inputs.

## Listening-mode symbols

`glasmio-icons/src/main/res/drawable-nodpi/glasmio_adaptive_audio.png` is the
original `person.and.sparkles.fill` Regular glyph from the user-authorized local
iOS 27.0 Simulator, build `24A434`, extracted on 2026-10-27 from
`CoreGlyphsPrivate.bundle/Assets.car`. The private symbol has no verified SF Pro
code-point mapping, so the component uses this native image instead of a font
alias. Apple Inc. retains copyright; this artwork is not covered by Glasmio's
Apache-2.0 license.

The CoreSVG glyph was rendered into a transparent 96 × 96 PNG with an 80-pixel
visible height. The copied resource is byte-identical to HyperPods-X's
`listening_adaptive.png`; the component applies its foreground tint.

- Image SHA-256: `8286ad51be85a13c1799ab7ac6ce08378a39ec964ee2185025b43682fab4e1ed`
- Source catalog SHA-256: `86675131b87b74b3a236883700161fa6719bf7453213fe883a583890cfe12ec7`

The Demo's ordinary listening-mode symbols are copied from the same authorized
iOS 27.0 Simulator extraction, without re-encoding. They are original 96 × 96
CoreUI-decoded renditions from `HeadphoneConfigs.framework/Assets.car`, 32 pt @3x.
The Off image retains its gray ring; dark appearance inverts its grayscale.
Apple Inc. retains copyright in these images, separately from Glasmio's code.

| Resource in `glasmio-icons/src/main/res/drawable-nodpi/` | Original rendition | SHA-256 |
| --- | --- | --- |
| `glasmio_listening_off.png` | `Off` | `9e0134932b97c94d2513559833a88dbc1dd56590cc63afa9910354b25c1aaf17` |
| `glasmio_listening_transparency.png` | `Transparency` | `bfd7056034d5a69fd7fa4ad71575c3f390b083d1d7fc474766e60b5b04dcd0c7` |
| `glasmio_listening_anc.png` | `Noise Cancellation` | `29e3ef950298e0599d217b57d164b1452aaa3cc271ee076f815ce384c2e3d7c0` |

Source catalog SHA-256: `cfa5d2d79c5e6c16c3f96c1b997a91a51c16f79d624f26436059f3a31cce8b10`.
