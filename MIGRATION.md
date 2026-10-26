# Migrating MeiloX UI to Glasmio

Glasmio contains the reusable rendering and presentation code. MeiloX keeps music models, playback, navigation destinations, preference storage, cover-color extraction, and its own font resources. The dependency direction is `MeiloX → Glasmio`.

## Dependencies

The development version is pinned to `0.1.0`:

```kotlin
dependencies {
    implementation("io.github.neoruaa.glasmio:glasmio-scaffold:0.1.0")
    implementation("io.github.neoruaa.glasmio:glasmio-preference:0.1.0")
}
```

These modules expose UI, Glass, Core, and Icons transitively. Maven Central publication is pending. Use a local publication or the explicit source-composite option while developing this version.

### Source composite

Keep the checkouts alongside one another:

```text
workspace/
  Mei/
  Glasmio/
```

MeiloX enables `includeBuild("../Glasmio")` only when `glasmio.source=true` is supplied. This prevents normal builds and CI from silently using a neighboring working directory.

```sh
./gradlew :app:assembleDebug -Pglasmio.source=true
```

The configured Glasmio checkout must exist when the option is enabled. Coordinate and version declarations remain the same, so Gradle substitutes the matching source projects.

### Local Maven artifacts

Run `./gradlew publishToMavenLocal` in Glasmio, then build MeiloX without `glasmio.source`. Its `mavenLocal()` repository resolves the pinned artifacts. Once `0.1.0` is published to Maven Central, ordinary builds can resolve the same coordinates there without the local checkout.

## Package moves

| Previous source | New package/module |
| --- | --- |
| Theme/display tokens in `ui/glass/GlassTokens.kt` | `io.github.neoruaa.glasmio.core` |
| Backdrop, surface drawing, refraction, shadows, morph engine, generic liquid interactions | `io.github.neoruaa.glasmio.glass` |
| Controls, menus, toolbars, lists, inputs, dialogs, sheets | `io.github.neoruaa.glasmio.ui` |
| Pinned pages, overscroll, pull refresh, app-bar behavior, generic sheet state | `io.github.neoruaa.glasmio.scaffold` |
| Generic preference rows/editors | `io.github.neoruaa.glasmio.preference` |
| Symbol names, catalog, icon renderer/provider | `io.github.neoruaa.glasmio.icons` |

`Ios27Components.kt` is split by responsibility into toolbar, input, list, menu, alert, and overlay files. The library contains no `com.ljyh.mei` package or application resource import.

## Common API renames

| Old | New |
| --- | --- |
| `GlassBackdropHost` / `GlassBackdropProvider` | `GlasmioBackdropHost` / `GlasmioBackdropProvider` |
| `GlassButton` / `GlassIconButton` | `GlasmioButton` / `GlasmioIconButton` |
| `GlassToggle` / `GlassSlider` / `GlassCard` | `GlasmioSwitch` / `GlasmioSlider` / `GlasmioCard` |
| `GlassBottomBar` / `GlassTabItem` | `GlasmioNavigationBar` / `GlasmioTabItem` |
| `GlassSearchBar` | `GlasmioSearchBar` |
| `IosTopToolbar` / `IosToolbarActionGroup` | `GlasmioTopBar` / `GlasmioToolbarActionGroup` |
| `IosGroupedList` / `IosListRow` | `GlasmioGroupedList` / `GlasmioListRow` |
| `IosPopupMenu` / `IosPopupButton` | `GlasmioMenu` / `GlasmioPopUpButton` |
| `IosAlertDialog` / `IosAlertButtonSpec` | `GlasmioAlertDialog` / `GlasmioAlertButtonSpec` |
| `IosModalSheet` | `GlasmioBottomSheet` |
| `IosPinnedListPage` / `IosPinnedPage` | `GlasmioPinnedListPage` / `GlasmioPinnedPage` |
| `PageOverscrollHost` | `GlasmioOverscrollHost` |
| `BottomSheetState` / `rememberBottomSheetState` | `GlasmioBottomSheetState` / `rememberGlasmioBottomSheetState` |
| `PreferenceEntry` / `SwitchPreference` | `GlasmioPreferenceEntry` / `GlasmioSwitchPreference` |
| `EditTextPreference` / `ListPreference` | `GlasmioEditTextPreference` / `GlasmioListPreference` |

Complete machine-readable maps are in [UI/rendering renames](docs/api-renames.json) and [Scaffold/Preference renames](docs/scaffold-api-renames.json). Rendering terms such as `GlassSurface` and `GlassAppearance`, and semantic icon APIs `SfIcon`/`SfSymbol`, retain their names.

Application call sites use direct imports from the new packages. Migrated generic implementations are removed from MeiloX rather than maintained as parallel copies.

## Theme and icons

`MusicTheme` remains the application's adapter. It reads preferences and computes the color scheme, typography, and shape values, then supplies them to `GlasmioTheme`.

Install `GlasmioIconProvider` once around the application UI with the application's existing `R.font.sf_pro` Typeface. This preserves global SF symbol rendering without moving the resource into Glasmio. The standalone sample accepts a local debug font; see [README](README.md). Third-party applications can provide their own symbol font or icon resolver.

## Backdrop and page contracts

`GlasmioBackdropHost` has explicit sampled and overlay slots. Keep controls outside their own sampled layer. Use `LocalBlurBackdrop` or the `backdrop` parameter to select the rendered page source for a separate modal window; see [Backdrop hosting](docs/backdrop.md).

`GlasmioPinnedListPage` owns its fixed toolbar and status-bar inset. Pass application bottom-bar space through `bottomPadding`/`bottomPaddingValues`. A `GlasmioScaffold` body instead receives one measured `PaddingValues`; `GlasmioListPage` applies and consumes it.

## Sheet and native rendering boundaries

The generic anchor state retains the source spring and drag behavior. Player artwork geometry, mini-player transitions, playback visibility, and shared-cover rendering remain in MeiloX. They consume the public sheet state rather than a second copy of it.

The native liquid-menu field belongs to the Glass AAR. MeiloX removes its old copy from the application's native build. Both projects use the same established rendering dependencies and continuous-corner implementation.
