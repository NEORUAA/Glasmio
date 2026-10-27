# Components

Glasmio uses state-hoisted Compose APIs: pass a value and handle its change callback. Menus, dialogs, and modal sheets remain present only while the host includes them in composition. Components use the current `GlasmioTheme` and require the backdrop host described in [Backdrop hosting](backdrop.md).

## API catalog

| Area | Public entry points |
| --- | --- |
| Theme | `GlasmioTheme`, `glasmioColorScheme`, `glasmioTypography`, `GlassAppearance`, `GlassColors`, `GlassDimensions`, `GlasmioShapes`, `GlasmioSpacing`, `GlasmioMotion` |
| Text styles | `GlasmioTextStyles`, `glasmioTextStyles`, `rememberGlasmioTextStyles`, `IosTextStyle`, `IosTextVariant`, `IosDynamicTypeSize` |
| Glass | `GlasmioBackdropHost`, `GlasmioBackdropProvider`, `GlassSurface`, `GlassSurfaceStyle`, `GlassEmphasis`, `rememberCrossWindowBackdrop`, `rememberSharedGlassBackdrop`, `rememberRasterizedBackdrop` |
| Buttons | `GlasmioButton`, `GlasmioIconButton`, `GlasmioButtonSize`, `GlasmioButtonDefaults` |
| Controls | `GlasmioSwitch`, `GlasmioSlider`, `GlasmioStepper`, `GlasmioSegmentedControl`, `GlasmioScrollableTabRow`, `GlasmioColorPicker` |
| Input | `GlasmioTextField`, `GlasmioSearchBar`, `GlasmioSearchToolbar`, `GlasmioEditableText` |
| Text editing | `GlasmioTextSelectionHost`, `GlasmioTextSelectionMenu`, `GlasmioTextSelectionLabels`, `GlasmioMagnifier` |
| Lists | `GlasmioCard`, `GlasmioGroupedList`, `GlasmioListRow`, `GlasmioSectionTitle`, `GlasmioGroupedTableFooter`, `GlasmioListValuePill`, `GlasmioListSwipeActions`, `GlasmioListSwipeAction`, `GlasmioSwipeActionStyle`, `GlasmioReorderableGroup` |
| Toolbars | `GlasmioTopBar`, `GlasmioTopBarStyle`, `GlasmioToolbarActionGroup`, `GlasmioToolbarActionStyle`, `GlasmioBottomToolbar`, `GlasmioSheetTopToolbar`, `GlasmioSheetToolbarButton`, `GlasmioSheetButton` |
| Navigation | `GlasmioNavigationBar`, `GlasmioTabItem` |
| Menus | `GlasmioMenu`, `GlasmioMenuItem`, `GlasmioContextMenu`, `GlasmioCascadingMenu`, `GlasmioCascadingMenuItem`, `GlasmioPopUpButton`, `rememberGlasmioMenuAnchorState`, `Modifier.glasmioMenuAnchor` |
| Alerts | `GlasmioAlertDialog`, `GlasmioAlertButtonSpec`, `GlasmioAlertButtonRole`, `GlasmioAlertButtonLayout`, `GlasmioAlertSurface`, `GlasmioAlertButton`, `GlasmioAlertFieldGroup`, `GlasmioAlertTextField` |
| Sheets and overlays | `GlasmioBottomSheet`, `GlasmioSheetSurface`, `GlasmioActionSheet`, `GlasmioActionSheetContent`, `GlasmioModalOverlay` |
| Pages | `GlasmioScaffold`, `GlasmioListPage`, `GlasmioPinnedListPage`, `GlasmioPinnedPage`, `rememberGlasmioGridCollapseProgress`, `GlasmioOverscrollHost`, `Modifier.pullToRefreshRebound`, `rememberGlasmioAppBarScrollBehavior` |
| Floating search | `GlasmioFloatingSearch`, `GlasmioFloatingSearchButton` |
| Sheet motion | `GlasmioBottomSheetState`, `rememberGlasmioBottomSheetState`, `GlasmioSheetDefaults` |
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

## Text editing

Wrap each window's content in `GlasmioTextSelectionHost` to use the compact Edit Menu and its expanded actions panel. The host forwards Foundation's actual clipboard and selection callbacks. `additionalItems` accepts application-owned editing actions and cascading submenus. `GlasmioAlertDialog` installs its own window-local host when the activity uses one.

```kotlin
@Composable
fun EditingScreen() = ExampleHost {
    var value by remember { mutableStateOf(TextFieldValue("Hello World")) }
    GlasmioTextSelectionHost {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            GlasmioEditableText(value, { value = it }, Modifier.fillMaxWidth())
        }
    }
}
```

Single-line `GlasmioEditableText`, text fields, search fields and Alert fields use the Clear magnifier and 16 dp selection lollipops. Both the lens and editing menu choose an available side within status-bar, navigation-bar and IME insets, and follow position changes with damped springs. Expanded editing menus stay within the same viewport and scroll when space is limited. Foundation continues to own text input, selection recognition, transformations and IME sessions. Multiline fields retain native selection chrome and scrolling; the host still supplies their editing menu. Without a host, fields use their normal Foundation behavior.

`GlasmioMagnifier` can also be used independently in a bounded overlay. Pass its source position and viewport bounds in physical pixels relative to that overlay. Its backdrop must exclude the lens itself. `backgroundColor` fills sampling beyond the source window near its edges. The lens uses Clear tint, edge and shadow layers with a local zero-frost override and preserved source colors, retaining AndroidLiquidGlass's 8/24 dp lens without navigation vibrancy or shadow lifting. `GlassSurface.frostRadius` and `preserveBackdropColors` affect only the specified surface.

## Lists, insets, and preferences

`GlasmioPinnedListPage` owns a collapsing large title and its fixed toolbar. Its content uses `LazyListScope`, so item keys and lazy content remain under application control. The page owns its top inset; `bottomPadding` can include the space occupied by an application bottom bar.

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

For independently measured bars, use `GlasmioScaffold` and pass its `PaddingValues` to `GlasmioListPage(contentPadding = padding)`. The list applies and consumes that padding once. Use `GlasmioPinnedPage` with `rememberGlasmioGridCollapseProgress` when the body is a lazy grid rather than a list.

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

`GlasmioAlertButtonSpec` carries the action, role, and enabled state. Keep `GlasmioAlertDialog` in composition and drive its `visible` state so the dialog remains mounted through its closing animation. `GlasmioBottomSheet` uses Material3's `SheetState`; the separate `GlasmioBottomSheetState` in Scaffold provides anchor-based motion for custom containers.

Alerts enter from 1.2× scale using UIKit's 404 ms critical spring and dismiss with a fade.
The panel and dimming view share the timing; reopening during dismissal retains the current
presentation. See the [native motion reference](alert-motion.md) for the recovered parameters.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalScreen() = ExampleHost {
    var alert by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }

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
    if (sheet) {
        GlasmioBottomSheet(onDismissRequest = { sheet = false }) {
            GlasmioSheetTopToolbar(title = "Details")
            Text("Sheet content", Modifier.padding(24.dp))
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
