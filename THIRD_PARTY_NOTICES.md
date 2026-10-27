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

Glasmio does not distribute rendered design screenshots as component artwork. SF Symbols names are accepted by the symbol resolver. Library artifacts contain no Apple fonts or SF Symbols artwork. The Demo APK bundles host-supplied SF Pro, its SF Symbols glyphs, and real italic faces; these Apple assets remain subject to Apple's terms and are not covered by Glasmio's Apache-2.0 license. The font source files are excluded from Git and library artifacts. Debug builds accept optional local fonts, while Demo builds require both font inputs.
