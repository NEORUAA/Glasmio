# Migration boundaries

Glasmio owns rendering, controls, page structure, and preference presentation. MeiloX retains playback, navigation destinations, settings storage, dynamic cover colors, and its optional font resources.

| Source area | Coupling in MeiloX | Glasmio boundary |
| --- | --- | --- |
| `GlassTokens` | `AppFontFamily` and application typography | Host-supplied typography and color tokens in `glasmio-core` |
| `GlassBackdrop` / `GlassSurface` | Android window/view capture and renderer effects | Android platform implementation in `glasmio-glass`; no business state |
| `Ios27Components` | Application strings, icon font, mixed widget categories | Separate toolbar, input, list, menu, dialog, overlay, and sheet files in `glasmio-ui` |
| `IosPinnedListPage` | Application back-label resource, local overscroll host | `GlasmioPinnedListPage` / `GlasmioPinnedPage`, caller-provided back description, dedicated recorded body layer |
| `PageOverscrollHost` | MIUIX overscroll physics | `GlasmioOverscrollHost` retains `MiuixOverscrollFactory` and native-child dispatch tracking |
| `PullToRefreshRebound` | Material3 pull state and refresh progress | Public modifier accepts caller-owned refresh state; no data loading |
| `AppBar` | Material3 scroll state | Public scroll behavior with caller-provided state, scroll permission, and animation specs |
| `BottomSheet` | Calls to player-named spring and drag helpers | `GlasmioBottomSheetState` uses generic sheet motion with the same 300-stiffness spring and release direction rules |
| `Preference` | App-local dialog helpers | `glasmio-preference` owns presentation, local edit drafts, validation, and callbacks; no persistent storage |
| `SfSymbol` | `R.font.sf_pro` and app theme locals | Independent semantic icon resolver and Material fallback; optional caller-owned Android `Typeface` |
| `MusicTheme` | Preference keys, cover extraction, font resources | Stays in MeiloX and supplies the calculated presentation values |

## Platform and third-party dependencies

- All first-release modules target Android Compose. Android `View`, window, bitmap, canvas, and runtime shader code remains in the rendering implementation rather than public business models.
- Backdrop, Capsule, and Shapes provide existing glass sampling and continuous corners. The extraction preserves the source rendering parameters.
- `glasmio-scaffold` uses AndroidX Foundation lazy/overscroll APIs, Material3 pull-to-refresh state, and MIUIX overscroll physics. Coroutine jobs remain bound to Compose lifecycles.
- `glasmio-icons` uses Apache-2.0 Material icons when a host does not install its own symbol renderer. No Apple font file or app resource ID is included.
- Preference dialogs compose Glasmio card and alert-button primitives. The host owns selected values, validation, and persistence.

## Insets and background sampling

`GlasmioScaffold` measures caller bars and returns one `PaddingValues`. `GlasmioListPage` applies this as lazy content padding and consumes it for descendants. A pinned page owns its status-bar inset and takes the occupied bottom space explicitly; use it directly when the pinned toolbar owns the page.

Pinned-page bodies export a separate Backdrop layer. Their fixed toolbars sample that body, preventing recursive sampling of the toolbar itself. Progressive blur is a reusable glass-layer effect and has the source uniform-blur fallback when runtime shaders are unavailable.

## Symbols

`GlasmioIconProvider` accepts a `GlasmioIconResolver` and an optional host-owned `Typeface`. Existing `SfIcon` overloads preserve semantic system-name APIs and exact font glyph centering when the host supplies a compatible font. Unknown names fall back to a visible generic icon; hosts can resolve them to their own vectors.

No music domain model, player renderer, network repository, application preference key, or application resource is required by the published modules.
