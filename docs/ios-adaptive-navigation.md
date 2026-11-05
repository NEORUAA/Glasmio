# Adaptive navigation and primary sidebar

`GlasmioTabBar` shares MeiloX's content-sized top tabs, draggable liquid indicator,
label portal, press highlight and navigation glass. `GlasmioAdaptiveNavigationScaffold`
adds a primary sidebar without replacing navigation entries or their scroll state.
The default policy keeps the bottom bar in portrait and uses top tabs in landscape.
Applications can supply an explicit `GlasmioNavigationLayout` for other window policies.

Place the existing navigation scaffold, floating search and application overlays in
the adaptive scaffold's content. Share one `contentBackdrop` between the adaptive
and navigation scaffolds when enabling content extension. The navigation scaffold
owns its single recording, including an opaque base; shared controls remain outside
that source. Do not record that layer again around the caller's content. Pass the
saved adaptive state to the sidebar toggle. Use
`GlasmioNavigationScaffold.topBar` for the tabs; the slot reserves the actual measured
width of both shared button groups, including transitions between pages. It retains
the tab subtree while new button endpoints are measured. `state.tabBarBounds` and
`LocalGlasmioTabBarBounds` supply window coordinates for adjacent search controls.

When top tabs are registered, Automatic presentation replaces them with the sidebar
using UIKit's TabMorph path. `SplitView` explicitly retains the independent-primary
column transition. The sidebar search, rows, section headers and toolbar are separate composables.
Routes, selection, search results and app-specific actions remain caller-owned.
`GlasmioToolbarDefaults` gives both primary and content toolbars the same 62 dp
allocation, 44 dp controls and 9 dp vertical inset. Both columns keep the same
status-bar inset. Page backgrounds retain the complete viewport. Only foreground
content, shared buttons, page titles and floating controls consume
`LocalGlasmioSidebarInsets`; external controls can use `glasmioAdaptiveContent()`.
The sidebar retains its physical leading safe area.

Full-screen presentations use the adaptive scaffold's `overlay` slot. It draws
above both columns while preserving sidebar state and page width underneath.
The slot inherits the foreground insets for collapsed accessories and remains
outside the page backdrop capture. Overlay Back handlers take precedence over
the sidebar fallback.

Animation values are read during layout and drawing. Endpoint foregrounds use
Compose-owned graphics layers; preview padding wrappers retain those layers
without recording their subtrees on each spring frame. Expanded-state local
changes invalidate only their readers. Transition material nodes exist only
during the morph, so their adaptive luminance probes do not sample the page
while the transition is idle.

## Native reference

The reference is the local iOS 27.0 ARM64 runtime, build `24A434`, re-extracted
from its Cryptex shared cache without starting an iOS Simulator. Supporting files
are in `.local-research/ios/adaptive-sidebar/`; Figma responses are in
`.local-research/design/adaptive-sidebar/`.

`UITabBarController` uses `_UITabMorphTransition` (`0x1c5412018`) to replace top
tabs with the sidebar. The independent `UISplitViewController` primary-column
animation is retained for sidebar-only presentations.

## Tabs and sidebar morph

The independent rope clock runs from 0 to 2 with response .3 s and damping 1.
If both center displacements exceed 100 pt, stage 0 creates an empty intermediate
capsule with response .2 s and damping 1. Stage 1 starts when the rope's presentation
value reaches 1, not after a fixed delay. Wide sources first change width/centerX;
tall sources first change height/centerY. Other geometries use the direct target stage.

| Target-stage channel | Response | Damping |
| --- | ---: | ---: |
| Sidebar geometry and destination | .4 s | .9 |
| Top tabs geometry and destination | .45 s | 1 |
| Corner completion | .2 s | .8 |
| Page foreground safe area | .4 s | .9 |

Corner completion starts alongside stage 1. The source material is retained in
stage 0; target background/effects/border begin transitioning in stage 1. True
endpoint backgrounds remain hidden until the retained shared backplate completes.
The Android material mix combines weighted complete optical outputs in a local
offscreen layer before compositing them once over the page.

AnimationKit's `crossBlurWhenMorphing` is enabled: each source/destination preview
uses its own progress `p`, opacity `p`, and blur `(1-p)*4 pt`. Stage 0 removes old
content without inserting sidebar rows into the empty capsule. Stage 1 introduces
the real destination. Foreground portals retain endpoint layout, scale uniformly
by `currentWidth/endpointWidth`, and use the shared continuous-corner clip. They
are not stretched vertically with the changing background.

Tabs-to-sidebar page avoidance starts in stage 0; the reverse restores foreground
width only from stage 1. Retargeting preserves presentation values and velocities,
reuses captured content, and replaces the old rope queue. Native source addresses
and AnimationKit analysis are recorded in
`.local-research/ios/adaptive-sidebar/TAB-MORPH.md` and `native-receipt.json`.

## Independent primary column

| Native entry | Recovered behavior |
| --- | --- |
| `_UISidebarPlatformMetrics.init`, `0x1c498e624` | Wide minimum/preferred/maximum widths: 280/320/400 pt; narrow: 280/280/320 pt. Zero fallback corner radius and platter insets. |
| Split default animation, `0x1c5219798` | 500 ms, zero delay, damping ratio 1, caller-supplied initial velocity; allow interaction and begin from the current presentation. |
| Spring conversion, `0x1c4ebbd48` / `0x1c4ebb518` | Unit mass, residual tolerance .001, velocity-dependent Newton solve using `expf`. |
| Ordinary zero-velocity toggle | Angular frequency 18.46682745, stiffness 341.023716, damping 36.933655; equivalent response .34024173 s. |
| Column interpolation | Frames: `0x1c51de2b4` / `0x1c51de0d4`; transforms: `0x1c51deb00` / `0x1c51de944`. |

Glasmio uses the recovered critical spring for sidebar-only primary-column travel
and foreground safe-area changes, retains velocity on reversal, and keeps sidebar
opacity at one. The 320 dp column overlaps a full-screen page background.
Windows that cannot retain 320 dp of
content use an overlay with Android Back/outside dismissal. That fallback threshold
and scrim are Glasmio window policy, not recovered UIKit constants.

## Material and design metrics

UIKit creates distinct sidebar materials: variant 9 is flexible `.sidebar`
(`0x1cad09d6c`); variant 10 is `.abuttedSidebar` (`0x1cad09d9c`). The attached
column uses the planar `SidebarRecipe` endpoint. Top tabs retain their regular
navigation recipe during the first morph stage.

The current iOS 27 V2 recipe and its Spec-to-CA conversion supply these ordinary
active-window values. Background content, increased contrast, diffusion and window
activity can select additional native branches.

| Parameter | Light | Dark |
| --- | ---: | ---: |
| Backdrop scale | .25 | .25 |
| Primary blur at a 320 pt column | 8 | 8 |
| Blur-fill radius | 16 | 16 |
| Fill darken / lighten / normal | 0 / .9 / .5 | .9 / 0 / .5 |
| Face black / white / saturation | .4 / 1.03 / 1.2 | .1 / .5 / .6 |
| White fill alpha | .2 | 0 |
| Maximum luminance, ordinary content | .85 | 1 |

Primary blur evaluates `minimumDistanceOfLargestArea ?? minimumDistance ?? 48`
through a clamped 64–160 → 0–4 recipe map, then doubles the result in the native
Spec converter. Blur-fill 16 passes through unchanged. The Android planar filter
uses the existing native mip sampler, blur-fill min/max composition and gamma-space
face transform, with working-space storage between GPU passes. Reduce Transparency
uses an opaque surface. Source and conversion receipts are in
`.local-research/ios/adaptive-sidebar/appearance/material/sidebar-base/ENDPOINT.md`.

`extendContentUnderSidebar` opts the host into the separately recovered background
extension. Its leading/trailing portal reflects adjacent content beneath the pane,
then applies a 10 pt variable blur over a .1-source-width mask. The sampling source is flattened once into a shared live quarter-resolution plane,
then mapped back to window coordinates for both endpoints. Visible page content
keeps its original resolution. Reflection fades into the original background within
15% of the content width, capped at a 130 dp adjoining edge band. The mask uses the normalized shifted-reciprocal curve
`alpha = (1 - t) / (1 + 3 * t)`, where `t` is outward distance divided by the band
width. It retains a long low-opacity tail and reaches zero at the outer bound. The private scene
records once instead of repeating nested control filters. This avoids
expanding capture padding by the entire sidebar width. The Android extension uses two materialized, normalized Gaussian passes before
the material mip pyramid. Hosts with an already full-window wallpaper can retain that
source directly. UIKit's public `UIBackgroundExtensionView` is an explicit content
facility, independent from the selected glass recipe. Source receipts are in
`.local-research/ios/adaptive-sidebar/appearance/extension/FINDINGS.md`.

`GlasmioSidebarToggleIcon` uses the recovered private CoreGlyphs vector
`rectangle.line.horizontal.inset.top` (canonical
`inset.line.horizontal.top.rectangle`). UIKit chooses preferred Body, Medium weight
and Large symbol scale. Its default 17 pt ink is 25.257129 × 19.782044 pt; the
component retains that aspect ratio and follows the host Body text size. The
private asset does not require a glyph in the host SF Pro font. Source receipts
are in `.local-research/ios/adaptive-sidebar/appearance/icon/README.md`.

The design's 320 pt primary column has 10 pt side insets, 44 pt rows, a 34 pt
symbol allocation, body text, capsule selection and collapsible section headers.
Top tabs use a 44 pt surface, 4 pt inset and 36 pt indicator; leading sidebar and
trailing search actions prefer 54 pt. Both action symbols use Semibold weight. The sidebar symbol has 16 dp measured
ink height and search has 17 dp, preserving each glyph's aspect ratio. Labels use
Semibold and the selected lens source uses Bold; both weights participate in cell
width measurement. Constrained labels truncate with their own
spacing before the independent actions lose their preferred width.
