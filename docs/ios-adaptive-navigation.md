# Adaptive navigation and primary sidebar

`GlasmioTabBar` shares MeiloX's content-sized top tabs, draggable liquid indicator,
label portal, press highlight and navigation glass. `GlasmioAdaptiveNavigationScaffold`
adds a primary sidebar without replacing navigation entries or their scroll state.
The default policy keeps the bottom bar in portrait and uses top tabs in landscape.
Applications can supply an explicit `GlasmioNavigationLayout` for other window policies.

Place the existing navigation scaffold, floating search and application overlays in
the adaptive scaffold's content. Pass its saved state to the sidebar toggle. Use
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
(`0x1cad09d6c`); variant 10 is `.abuttedSidebar` (`0x1cad09d9c`). Their final
parameters resolve through the environment and luminance; static extraction did
not recover a fixed light/dark tuple.

The Android abutted background therefore uses the supplied Figma Sidebar BG
compositing reference: stacked 40/60 pt backdrop blur, 92% luminosity coating
(`#DAE6F2` in the supplied light mode), and 80% desaturation. Dark mode inherits
the host elevated background's luminosity. This is a design-resource material
adaptation, not a claim that UIKit variants 9/10 equal navigation glass. Sidebar
toolbar buttons use Glasmio's existing navigation material over that background.
Reduce Transparency supplies an opaque surface.

The design's 320 pt primary column has 10 pt side insets, 44 pt rows, a 34 pt
symbol allocation, body text, capsule selection and collapsible section headers.
Top tabs use a 44 pt surface, 4 pt inset and 36 pt indicator; leading sidebar and
trailing search actions prefer 54 pt. Constrained labels truncate with their own
spacing before the independent actions lose their preferred width.
