# Components

Glasmio uses state-hoisted Compose APIs: pass a value and handle its change callback. Menus, dialogs, and modal sheets remain present only while the host includes them in composition. Components use the current `GlasmioTheme` and require the backdrop host described in [Backdrop hosting](backdrop.md).

## API catalog

| Area | Public entry points |
| --- | --- |
| Theme | `GlasmioTheme`, `glasmioColorScheme`, `glasmioTypography`, `GlassAppearance`, `GlassColors`, `GlassDimensions`, `GlasmioShapes`, `GlasmioSpacing`, `GlasmioMotion` |
| Text styles | `GlasmioTextStyles`, `glasmioTextStyles`, `rememberGlasmioTextStyles`, `IosTextStyle`, `IosTextVariant`, `IosDynamicTypeSize` |
| Glass | `GlasmioBackdropHost`, `GlasmioBackdropProvider`, `GlassSurface`, `GlassSurfaceStyle`, `GlassEmphasis`, `rememberCrossWindowBackdrop`, `rememberSharedGlassBackdrop`, `rememberRasterizedBackdrop` |
| Buttons | `GlasmioButton`, `GlasmioIconButton`, `GlasmioButtonSize`, `GlasmioButtonDefaults` |
| Controls | `GlasmioSwitch`, `GlasmioSlider`, `GlasmioVolumeSlider`, `GlasmioAdaptiveSlider`, `GlasmioStepper`, `GlasmioSegmentedControl`, `GlasmioScrollableTabRow`, `GlasmioColorPicker` |
| Input | `GlasmioTextField`, `GlasmioSearchBar`, `GlasmioSearchToolbar`, `GlasmioEditableText` |
| Text editing | `GlasmioTextSelectionHost`, `GlasmioTextSelectionMenu`, `GlasmioTextSelectionLabels`, `GlasmioMagnifier`, `GlasmioLoupeMotion`, `GlasmioLoupeFlex`, `rememberGlasmioLoupeFollow`, `GlasmioLoupeFollowState` |
| Lists | `GlasmioCard`, `GlasmioGroupedList`, `GlasmioListRow`, `GlasmioSectionTitle`, `GlasmioGroupedTableFooter`, `GlasmioListValuePill`, `GlasmioListSwipeActions`, `GlasmioListSwipeAction`, `GlasmioSwipeActionStyle`, `GlasmioReorderableGroup` |
| Toolbars | `GlasmioTopBar`, `GlasmioTopBarStyle`, `GlasmioToolbarActionGroup`, `GlasmioToolbarActionStyle`, `GlasmioBottomToolbar`, `GlasmioSheetTopToolbar`, `GlasmioSheetToolbarButton`, `GlasmioSheetButton` |
| Navigation | `GlasmioNavigationBar`, `GlasmioTabItem` |
| Menus | `GlasmioMenu`, `GlasmioMenuItem`, `GlasmioContextMenu`, `GlasmioCascadingMenu`, `GlasmioCascadingMenuItem`, `GlasmioPopUpButton`, `rememberGlasmioMenuAnchorState`, `Modifier.glasmioMenuAnchor` |
| Alerts | `GlasmioAlertDialog`, `GlasmioAlertButtonSpec`, `GlasmioAlertButtonRole`, `GlasmioAlertButtonLayout`, `GlasmioAlertSurface`, `GlasmioAlertButton`, `GlasmioAlertFieldGroup`, `GlasmioAlertTextField` |
| Sheets and overlays | `GlasmioBottomSheet`, `GlasmioSheetSurface`, `GlasmioActionSheet`, `GlasmioActionSheetContent`, `GlasmioModalOverlay` |
| Pages | `GlasmioScaffold`, `GlasmioListPage`, `GlasmioPinnedListPage`, `GlasmioPinnedPage`, `rememberGlasmioGridCollapseProgress`, `GlasmioOverscrollHost`, `Modifier.pullToRefreshRebound`, `rememberGlasmioAppBarScrollBehavior` |
| Floating search | `GlasmioFloatingSearch`, `GlasmioFloatingSearchButton` |
| Sheet motion | `GlasmioSheetState`, `rememberGlasmioSheetState`, `GlasmioSheetValue`; custom player containers retain `GlasmioBottomSheetState` and `GlasmioSheetDefaults` |
| Preferences | `GlasmioPreferenceEntry`, `GlasmioSwitchPreference`, `GlasmioEditTextPreference`, `GlasmioListPreference`, `GlasmioEnumListPreference`, `GlasmioPreferenceGroupTitle` |
| Icons | `SfIcon`, `SfSymbol`, `SfSymbolCatalog`, `GlasmioIcon`, `GlasmioIconProvider`, `GlasmioIconResolver` |

Button naming follows Apple's Liquid Glass vocabulary through regular/prominent emphasis and Small/Medium/Large sizes. Other component names retain the `Glasmio` prefix while using familiar terms such as Toolbar, Pop-up Button, Alert, Sheet, Stepper, and Segmented Control.

## Example host

The examples below share this host and imports. Copy them into an Android Compose source file and call the desired screen from `setContent`. Install `GlasmioIconProvider` above these screens to use the application's SF symbol font globally.

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*
import io.github.neoruaa.glasmio.preference.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.ui.*

@Composable
fun ExampleHost(content: @Composable () -> Unit) {
    GlasmioTheme {
        val colors = GlasmioTheme.colors
        GlasmioBackdropHost(
            modifier = Modifier.fillMaxSize(),
            sampledContent = {
                Box(Modifier.fillMaxSize().background(colors.groupedBackground))
            },
            overlayContent = { content() },
        )
    }
}
```

## Controls and input

Values belong to the caller. `GlasmioSlider` also accepts `onValueChangeFinished` for committing a drag or accessibility adjustment. `GlasmioStepper` disables actions at the ends of its range. Search takes `TextFieldValue` so selection and composition are preserved.

`GlasmioVolumeSlider` uses a normalized `0f..1f` value in a 52 dp filled capsule. It provides
local drag feedback, a changing speaker symbol, RTL input, keyboard adjustments, and progress
accessibility. Supply its localized accessibility name through `modifier.semantics` and use
`onValueChangeFinished` to commit an adjustment when needed.

```kotlin
@Composable
fun ControlsScreen() = ExampleHost {
    var enabled by remember { mutableStateOf(true) }
    var level by remember { mutableFloatStateOf(0.5f) }
    var count by remember { mutableIntStateOf(1) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf(TextFieldValue()) }
    var searching by remember { mutableStateOf(false) }

    Column(
        Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GlasmioSwitch(checked = enabled, onCheckedChange = { enabled = it })
        GlasmioSlider(value = level, onValueChange = { level = it }, enabled = enabled)
        GlasmioStepper(value = count, onValueChange = { count = it }, range = 0..10)
        GlasmioTextField(value = name, onValueChange = { name = it }, placeholder = "Name")
        GlasmioSearchBar(
            query = query,
            onQueryChange = { query = it },
            active = searching,
            onActiveChange = { searching = it },
            onClose = { searching = false },
            placeholder = "Search",
            closeContentDescription = "Close search",
        )
        GlasmioButton(onClick = { count = 0 }, size = GlasmioButtonSize.Medium) {
            Text("Reset")
        }
    }
}
```

Supply localized descriptions for icon-only actions and Stepper controls. Use `enabled = false` for disabled controls. Slider values and range endpoints must be finite, with an increasing range.

`GlasmioSegmentedControl` keeps its 32 dp text layout by default. Set `height` and `itemContent`
for taller icon segments; each pair's label remains its accessibility name. The content slot is
rendered in both the optical source and the resting surface, so icons refract with the lens.
`selected = null` (or a value absent from `items`) shows no selected surface. Use
`deferSelection = true` when a device must confirm a request: releasing a drag calls `onSelected`
and returns the lens to the last external selection until `selected` changes.

```kotlin
GlasmioSegmentedControl(
    items = listOf(0 to "Quiet", 1 to "Balanced", 2 to "Open"),
    selected = confirmedMode,
    onSelected = ::requestMode,
    modifier = Modifier.fillMaxWidth(),
    height = 50.dp,
    enabled = !requestPending,
    deferSelection = true,
    itemContent = { mode, selected ->
        SfIcon(modeSymbols[mode], contentDescription = null, modifier = Modifier.size(25.dp))
    },
)
```

`GlasmioAdaptiveSlider` is a 56 dp continuous slider with nullable confirmed selection. Taps and
dragging report normalized preview values through `onValueChange`; `onValueChangeFinished` marks a
completed adjustment. A cancelled drag restores the confirmed preview without finishing. With
`deferSelection = true`, the renderer owns the local draft during a gesture and returns to the
external value after release. Values such as `0.4f` remain continuous in both geometry and progress
accessibility. `labels`, `showLabels`, and `captionOverride` provide captions.

For a device capability that supports only three positions, opt in with `discrete = true`, or
`adaptiveDiscrete = true` on the grouped overload. Interaction selects `0f` below `.31f`, `1f`
above `.7f`, and `.5f` between those bounds; repeated movement in one position emits no duplicate
preview. The thumb uses the native value spring. Incoming reports remain unchanged, so a reported
`0.4f` still displays 40% until the user interacts. The default remains continuous.

The grouped `GlasmioSegmentedControl` overload expands its confirmed `adaptiveItem` to three
ordinary cell widths within the same capsule. Main-item and adaptive-value callbacks remain
separate. A drag must start in the expanded adaptive group; entering a 20 dp outer edge zone
requests the neighboring main mode once and ends strength updates for that gesture. Collapsed
ordinary cells are tap targets. `hiddenItems`
controls capabilities independently of expansion. Its `showLabels` captions follow the animated
cell widths; a nonblank `captionOverride` replaces the caption row at the same 5 dp gap.

```kotlin
GlasmioSegmentedControl(
    items = modes,
    selected = confirmedMode,
    onSelected = ::requestMode,
    adaptiveItem = adaptiveMode,
    adaptiveValue = confirmedStrength,
    onAdaptiveValueChange = ::requestStrength,
    modifier = Modifier.fillMaxWidth(),
    deferSelection = true,
    showLabels = true,
)
```

Expansion, hidden-item layout, value motion, and drag scale use the recovered native spring
response/damping pairs. The adaptive thumb uses the existing Clear material, a separate white
resting fill, and one sharp foreground glyph. Its recording source contains the track and
stationary content. Ordinary modes retain a separate semantic-background matched pill. Interactive motion
preserves velocity with the native response and damping; SwiftUI's nonzero transaction blend
duration is not emulated.

## Text editing

Wrap each window's content in `GlasmioTextSelectionHost` to use the compact Edit Menu and its expanded actions panel. The host forwards Foundation's actual clipboard and selection callbacks. `additionalItems` accepts application-owned editing actions and cascading submenus. `GlasmioAlertDialog` installs its own window-local host when the activity uses one.

```kotlin
@Composable
fun EditingScreen() = ExampleHost {
    var value by remember { mutableStateOf(TextFieldValue("Hello World")) }
    GlasmioTextSelectionHost {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp)) {
            GlasmioEditableText(
                value, { value = it }, Modifier.fillMaxSize(),
                singleLine = false,
            )
        }
    }
}
```

`GlasmioEditableText`, text fields, search fields and Alert fields use the Clear magnifier and 16 dp selection lollipops. The lens follows UIKit's glass caret loupe: `115 × 85 dp` at `1.25×`, centred `75 dp` above the finger, a `0.3` low-pass feeding a `damping 0.7 / 0.2 s` spring, the two opening passes started together, a `0.23 s` dismissal that collapses the sampled content towards `0.125×`, and acceleration-driven body deformation while tracking, as recovered in [the native loupe motion reference](ios-loupe-motion.zh-CN.md). The opening shape is the caret rect, a caret-to-selection swap replays the opening passes, and a selection widget taller than the lens body suppresses the lens. The lens and its sample stay inside status-bar, navigation-bar and IME insets, a body larger than the viewport is scaled down, and a body that would leave the container on its lift side is hidden instead of flipped. UIKit's `borderWidth 42.5 → 2` and corner-radius morph, its status-bar hiding, and its vertical-writing lens are not wired to `GlassSurface`: the border is part of the shared material. Expanded editing menus stay within the same viewport and scroll when space is limited. Foundation continues to own text input, selection recognition, transformations and IME sessions. Set `singleLine = false` to wrap text and scroll vertically while retaining the same editing chrome. Constrain the editor with a height, `fillMaxSize()`, or `maxLines`; `maxLines` limits the visible lines without truncating the document. Selection handles support cross-line dragging and vertical edge scrolling, and editing menus anchor to the visible selection. The editor owns its scroll container, so avoid wrapping it in another vertical scroller. Apply IME insets at the screen level so the viewport can shrink above the keyboard. Without a host, fields use their normal Foundation behavior.

`GlasmioMagnifier` can also be used independently in a bounded overlay. Pass its source position, the shape it grows out of, and optional viewport bounds in physical pixels relative to that overlay. Its backdrop must exclude the lens itself. `lensSize`, `verticalOffset`, `orientation`, `magnification`, `collapsedMagnification` and `horizontalInset` override the recovered geometry; `motion` replaces every timing constant at once, `tracking` selects the tracking deformation spring, `identity` replays the opening passes when the attached widget changes, and `onDismissed` reports the end of the dismissal spring. `dismissalProgress` closes the lens without ending the caller's gesture; UIKit only writes that progress when `YukonMagnifiersDisabled` is set, so hosts keep it at its `0f` default. `backgroundColor` fills sampling beyond the source window near its edges. The lens uses Clear tint, edge and shadow layers with a local zero-frost override and preserved source colors, retaining AndroidLiquidGlass's 8/24 dp lens without navigation vibrancy or shadow lifting. `GlassSurface.frostRadius` and `preserveBackdropColors` affect only the specified surface. A lens with its own placement can reuse only the follower through `rememberGlasmioLoupeFollow`, which needs the same `GlasmioLoupeMotion`.

## Lists, insets, and preferences

`GlasmioPinnedListPage` owns a collapsing large title and its fixed toolbar. Its content uses `LazyListScope`, so item keys and lazy content remain under application control. The page owns its top inset; `bottomPadding` can include the space occupied by an application bottom bar.

The large title collapses on a breakpoint rather than tracking the finger: it expands below `collapseExitBreakpoint` (24 dp by default) and collapses once the list passes `collapseEnterBreakpoint` (56 dp by default), playing one spring between the two states. The gap between the thresholds is hysteresis, so a scroll resting near the boundary cannot retrigger the motion. The top bar material stays continuous and still fades with the scroll offset.

```kotlin
@Composable
fun PreferencesScreen() = ExampleHost {
    var notifications by remember { mutableStateOf(true) }
    var displayName by remember { mutableStateOf("Guest") }

    GlasmioPinnedListPage(title = "Preferences") {
        item {
            GlasmioPreferenceGroupTitle("General")
            GlasmioGroupedList {
                GlasmioSwitchPreference(
                    title = { Text("Notifications") },
                    checked = notifications,
                    onCheckedChange = { notifications = it },
                )
                GlasmioEditTextPreference(
                    title = { Text("Display name") },
                    value = displayName,
                    onValueChange = { displayName = it },
                    isInputValid = { it.isNotBlank() },
                )
            }
        }
    }
}
```

For independently measured bars, use `GlasmioScaffold` and pass its `PaddingValues` to `GlasmioListPage(contentPadding = padding)`. The list applies and consumes that padding once. Use `GlasmioPinnedPage` with `rememberGlasmioHeaderCollapse(gridState)` when the body is a lazy grid rather than a list; `rememberGlasmioGridCollapseProgress` is the legacy name for the same breakpoint-driven value. `rememberGlasmioHeaderScrollEdge` returns the continuous progress for a top bar material that must not pulse with the collapse.

The sample's Lists page switches between a lazy list and an adaptive grid while preserving each scroll state. Its toolbar refresh and pull gesture share one caller-owned callback, rotating the visible items after a short refresh. `Modifier.pullToRefreshRebound` animates only the body; the refresh indicator sits below the fixed toolbar, and both layouts apply the pinned page's content padding once.

Preferences present values and report edits. The host chooses DataStore, database, network, or in-memory storage. Disable a row's content without dimming its separator. Set `showTopSeparator = false` on the first preference row in a group.

Labeled swipe actions default to Apple's `SymbolAndLabelTall` layout: a 60 × 38 point
symbol capsule with its caption below, within a 68 point action slot. `SymbolOnly`
and `SymbolAndLabel` expose the other source variants. Reorderable groups keep
separators attached to fixed list slots while the grabbed row moves.

## Menus

The menu owns its popup lifecycle while the application owns `expanded`. Render actions through `GlasmioMenuItem` and invoke the supplied `close` callback after choosing an action. Nested actions can use `GlasmioCascadingMenuItem.children`.

`GlasmioCascadingMenu.itemHeight` and `separatorHeight` select row and section geometry without changing the default 44/12 dp menu. The Edit Menu uses Apple's 40/21 dp variant, with 60 dp for the explicit two-line Paste and Match Style row. `viewportBounds` constrains expanded menus in window pixels; their contents scroll when that viewport is shorter than the menu.

Pop-up Buttons and Cascading Menus use the native source-preview morph by default.
Inline Pop-up labels inherit the host's body typography, including font family,
size, weight and letter spacing, while keeping a 44 dp interaction height.
Standalone sources transfer their own foreground; toolbar sources retain the complete
button group. For an external anchor, pass a `GlasmioMenuAnchorState` attached through
`Modifier.glasmioMenuAnchor` to preserve its foreground throughout dismissal.
`morphFromAnchor = false` selects the separate compact-menu transition. See
[the native motion reference](dropdown-motion.md) for the branch and spring sources.

```kotlin
@Composable
fun MenuScreen() = ExampleHost {
    var expanded by remember { mutableStateOf(false) }
    var favorite by remember { mutableStateOf(false) }

    Box(Modifier.safeDrawingPadding().padding(24.dp)) {
        GlasmioMenu(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            itemCount = 2,
            anchor = { open ->
                GlasmioIconButton(onClick = open) { SfIcon("ellipsis", "Actions") }
            },
        ) { backdrop, close ->
            GlasmioMenuItem("Favorite", { favorite = !favorite; close() },
                systemName = "heart", backdrop = backdrop)
            GlasmioMenuItem("Delete", { close() },
                systemName = "trash", destructive = true, backdrop = backdrop)
        }
    }
}
```

## Alerts and sheets

`GlasmioAlertButtonSpec` carries the action, role, and enabled state. Keep alerts and sheets in composition and drive their `visible` state so the window and its backdrop remain mounted through dismissal.

`GlasmioBottomSheet` owns its modal window, native-style detents, drag resistance, projected release and scroll handoff. Its `GlasmioSheetState` is independent of Material3. Enable medium and large with `skipPartiallyExpanded = false`; the default has only large. Medium is resolved from available height and safe areas, then the entire platter and its content are uniformly inset. Upward scrolling expands the sheet before scrolling its content. At large, content scrolling and its fling take over; dragging down at the content's top returns control to the sheet. Set `prefersScrollingExpandsWhenScrolledToEdge = false` to reserve expansion for direct dragging or state commands.

The medium background is glass; the large background uses the system background color. Crossing the native full-height threshold retargets a separate background spring. `fitContent = true` presents a short action panel with floating insets. Insets include the IME by default and can be overridden through `contentWindowInsets` and `contentNavigationBarInsets`. See the [native sheet reference](ios-sheet-motion.md) for the recovered implementation and device measurements.

Inside a sheet, grouped rows inherit UIKit's material context. Medium rows transform the sheet's exported backplate with the native vibrancy matrix; Large rows use opaque semantic colors. Labels and separators follow their own native color roles. Apply `GlasmioTheme.colors.groupedBackground` to the table's scroll container: it is transparent in the glass context and resolves to the elevated grouped-table canvas in Large. The sheet's own backing, the table canvas and its cells are separate layers.

Put `GlasmioSheetTopToolbar` in the modal's `topBar` slot for a pinned progressive-blur header.
Only the body is recorded into the header's backdrop, excluding the toolbar and grabber.
The header uses the same 10dp progressive blur and tint as the page navigation header;
Reduce Transparency replaces it with a solid canvas color. With this slot, content draws
behind the handle, header and system navigation bar. Read `LocalGlasmioSheetContentPadding`:
apply its top value to the list's **content padding**, and its bottom value inside the footer's
background. Padding the whole list would prevent rows from scrolling beneath the header.
Without `topBar`, the existing automatic handle/navigation insets remain unchanged.
Header material starts clear by default. Supply the actual scroll edge through
`topBarBlurProgress = rememberGlasmioHeaderScrollEdge(listState)`; compact titles
do not imply an already visible blur. The same scroll-edge default applies to page
and shared navigation headers.

When migrating an explicit `sheetState`, replace `rememberModalBottomSheetState` with `rememberGlasmioSheetState` and Material3 `SheetValue` with `GlasmioSheetValue`. The new state's `show`, `hide`, `expand` and `partialExpand` are suspending commands. The separate `GlasmioBottomSheetState` in Scaffold retains its custom player/miniplayer contract.

Alerts enter from 1.2× scale using UIKit's 404 ms critical spring and dismiss with a fade.
The panel and dimming view share the timing; reopening during dismissal retains the current
presentation. See the [native motion reference](alert-motion.md) for the recovered parameters.

```kotlin
@Composable
fun ModalScreen() = ExampleHost {
    var alert by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    val detailsList = rememberLazyListState()

    Column(Modifier.safeDrawingPadding().padding(24.dp)) {
        GlasmioButton(onClick = { alert = true }) { Text("Show alert") }
        Spacer(Modifier.height(16.dp))
        GlasmioButton(onClick = { sheet = true }) { Text("Show sheet") }
    }
    GlasmioAlertDialog(
        visible = alert,
        title = "Save changes?",
        onDismissRequest = { alert = false },
        buttons = listOf(
            GlasmioAlertButtonSpec("Cancel", { alert = false }, role = GlasmioAlertButtonRole.Cancel),
            GlasmioAlertButtonSpec("Save", { alert = false }),
        ),
    )
    GlasmioBottomSheet(
        visible = sheet,
        skipPartiallyExpanded = false,
        onDismissRequest = { sheet = false },
        topBar = { GlasmioSheetTopToolbar(title = "Details") },
        topBarBlurProgress = rememberGlasmioHeaderScrollEdge(detailsList),
    ) {
        val safePadding = LocalGlasmioSheetContentPadding.current
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).background(GlasmioTheme.colors.groupedBackground),
            state = detailsList,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
                top = safePadding.calculateTopPadding() + 8.dp,
                bottom = safePadding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                GlasmioGroupedList {
                    GlasmioListRow("Notifications", showTopSeparator = false)
                    GlasmioListRow("Privacy", subtitle = "Details")
                }
            }
        }
    }
}
```

`GlasmioActionSheet` is a surface/content primitive. Place it in a modal container when dismissal and window behavior are needed. `GlasmioColorPicker` already manages its sheet presentation and accepts localized `GlasmioColorPickerLabels`.

## Navigation and reorderable content

`GlasmioFloatingSearch` combines the floating input toolbar, results overlay, keyboard
insets and focus cleanup. The host owns its route, query and results; its navigation
handles Back by default so the first Back can dismiss the IME. Set `handleBack = true`
when no navigation host owns dismissal. Use
`GlasmioFloatingSearchButton` beside the navigation bar with the same compact progress.

`GlasmioNavigationBar` takes caller-owned `GlasmioTabItem<T>` keys, selection, compact progress, and an expand callback. It does not define destinations or install a navigation runtime. The compact progress is `State<Float>` in `0f..1f`; use a stable state object for live transitions.

`GlasmioReorderableGroup` reports the complete reordered list through `onReorder`. Its grabber owns the drag target, leaving the rest of the row available for vertical scrolling. Supply localized drag, move-up, and move-down labels for accessibility actions.

The [sample source](../sample/src/main/java/io/github/neoruaa/glasmio/sample/MainActivity.kt) contains complete navigation, color-picker, reorder, toolbar, and menu examples.

## Text Styles and Dynamic Type

`GlasmioTheme.textStyles` exposes all eleven Apple semantic roles. Indexed access
adds the exact emphasis and italic variants available for each role:

```kotlin
Text("Body", style = GlasmioTheme.textStyles.body)
Text("Emphasis", style = GlasmioTheme.textStyles[IosTextStyle.Body, IosTextVariant.Emphasized])
```

The default Large presets follow Android font scaling. Pass
`dynamicTypeSize = IosDynamicTypeSize.AX1` to `GlasmioTheme` to select an explicit
Apple category, or use `rememberGlasmioTextStyles` for a local preview. Explicit
categories account for the host density once, without scaling the selected category
a second time. See [the source metrics](design/typography.md) for all twelve sizes.
