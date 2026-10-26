package io.github.neoruaa.glasmio.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*
import io.github.neoruaa.glasmio.preference.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(systemDark) }
            var reduceTransparency by rememberSaveable { mutableStateOf(false) }
            var rtl by rememberSaveable { mutableStateOf(false) }
            var page by rememberSaveable { mutableStateOf("Glasmio") }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            GlasmioTheme(isDark = dark, appearance = GlassAppearance(reduceTransparency = reduceTransparency)) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    BackHandler(page != "Glasmio") { page = "Glasmio" }
                    GlasmioBackdropHost(
                        modifier = Modifier.fillMaxSize(),
                        sampledContent = { SampleBackdrop(colorful = page == "Materials") },
                        overlayContent = {
                            if (page == "Lists") {
                                ListDemo(onBack = { page = "Glasmio" })
                            } else {
                                GlasmioScaffold(
                                    containerColor = Color.Transparent,
                                    topBar = {
                                        GlasmioTopBar(
                                            title = page,
                                            modifier = Modifier.statusBarsPadding(),
                                            style = GlasmioTopBarStyle.LargeTitle,
                                            navigation = if (page != "Glasmio") ({
                                                GlasmioIconButton(onClick = { page = "Glasmio" }) {
                                                    SfIcon("chevron.backward", "Back")
                                                }
                                            }) else null,
                                            actions = {
                                                GlasmioIconButton(onClick = { dark = !dark }) {
                                                    SfIcon(if (dark) "sun.max" else "moon", if (dark) "Light appearance" else "Dark appearance")
                                                }
                                            },
                                        )
                                    },
                                ) { padding ->
                                    when (page) {
                                        "Glasmio" -> Catalog(padding) { page = it }
                                        "Materials" -> MaterialsDemo(padding)
                                        "Controls" -> ControlsDemo(padding)
                                        "Navigation" -> NavigationDemo(padding)
                                        "Preferences" -> PreferencesDemo(padding, dark, { dark = it }, reduceTransparency, { reduceTransparency = it }, rtl, { rtl = it })
                                        "Search" -> SearchDemo(padding)
                                        "Menus" -> MenusDemo(padding)
                                        "Sheets" -> SheetsDemo(padding)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleBackdrop(colorful: Boolean) {
    val colors = LocalGlassColors.current
    Canvas(Modifier.fillMaxSize().background(colors.groupedBackground)) {
        val alpha = if (colorful) 0.9f else if (colors.isDark) 0.08f else 0.07f
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFF0088FF).copy(alpha = alpha), Color.Transparent)),
            radius = size.width * 0.9f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.24f),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFB65BF4).copy(alpha = alpha), Color.Transparent)),
            radius = size.width * 0.9f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.55f),
        )
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFF9F0A).copy(alpha = alpha), Color.Transparent)),
            radius = size.width * 0.8f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.9f),
        )
    }
}

@Composable
private fun DemoList(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

private val catalog = listOf(
    "Materials" to "square.stack.3d.up",
    "Controls" to "slider.horizontal.3",
    "Navigation" to "rectangle.3.group",
    "Lists" to "list.bullet",
    "Preferences" to "gear",
    "Search" to "magnifyingglass",
    "Menus" to "ellipsis",
    "Sheets" to "rectangle.bottomthird.inset.filled",
)

@Composable
private fun Catalog(padding: PaddingValues, onOpen: (String) -> Unit) {
    DemoList(padding) {
        items(catalog.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (title, icon) ->
                    GlasmioCard(Modifier.weight(1f).height(132.dp), onClick = { onOpen(title) }) {
                        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            SfIcon(icon, null, size = 30.dp, tint = LocalGlassColors.current.accent)
                            Text(title, style = IosTypography.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = IosTypography.headline, modifier = Modifier.padding(horizontal = 4.dp))
        content()
    }
}

@Composable
private fun MaterialsDemo(padding: PaddingValues) {
    var separated by rememberSaveable { mutableStateOf(false) }
    var count by rememberSaveable { mutableIntStateOf(0) }
    DemoList(padding) {
        item {
            Section("Liquid Glass") {
                GlassSurface(
                    Modifier.fillMaxWidth().height(170.dp),
                    shape = ContinuousRoundedRectangle(32.dp),
                    onClick = { count++ },
                ) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        SfIcon("sparkles", null, size = 36.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(if (count == 0) "Touch the glass" else "$count", style = IosTypography.title2)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlasmioButton(onClick = { count++ }, modifier = Modifier.weight(1f)) { Text("Glass") }
                GlasmioButton(onClick = { count++ }, modifier = Modifier.weight(1f), emphasis = GlassEmphasis.Prominent) { Text("Glass Prominent") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GlasmioGroupedList {
                    GlasmioListRow("Separate Actions", showTopSeparator = false, trailing = { GlasmioSwitch(separated, { separated = it }) })
                }
                GlasmioToolbarActionGroup(style = if (separated) GlasmioToolbarActionStyle.Separated else GlasmioToolbarActionStyle.Merged) {
                GlasmioIconButton(onClick = { count++ }) { SfIcon("plus", "Add") }
                GlasmioIconButton(onClick = { count = 0 }) { SfIcon("arrow.counterclockwise", "Reset") }
                GlasmioIconButton(onClick = { count++ }) { SfIcon("heart", "Like") }
                }
            }
        }
    }
}

@Composable
private fun ControlsDemo(padding: PaddingValues) {
    var enabled by rememberSaveable { mutableStateOf(true) }
    var checked by rememberSaveable { mutableStateOf(true) }
    var value by rememberSaveable { mutableFloatStateOf(0.45f) }
    var step by rememberSaveable { mutableIntStateOf(3) }
    var text by rememberSaveable { mutableStateOf("") }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var color by remember { mutableStateOf(Color(0xFF0088FF)) }
    var colorPicker by rememberSaveable { mutableStateOf(false) }
    DemoList(padding) {
        item {
            GlasmioGroupedList {
                GlasmioListRow("Enabled", showTopSeparator = false, trailing = { GlasmioSwitch(enabled, { enabled = it }) })
                GlasmioListRow("Switch", trailing = { GlasmioSwitch(checked, { checked = it }, enabled = enabled) })
                GlasmioListRow("Stepper", detail = "$step", trailing = { GlasmioStepper(step, { step = it }, range = 0..10, enabled = enabled) })
                GlasmioTextField(text, { text = it }, placeholder = "Text Field", enabled = enabled)
            }
        }
        item {
            Section("Slider") {
                GlasmioCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${(value * 100).toInt()}%", style = IosTypography.headline)
                        GlasmioSlider(value, { value = it }, Modifier.fillMaxWidth(), enabled = enabled)
                    }
                }
            }
        }
        item {
            Section("Segmented Control") {
                GlasmioSegmentedControl(listOf(0 to "Day", 1 to "Week", 2 to "Month"), segment, { segment = it }, Modifier.fillMaxWidth())
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlasmioButton(onClick = { colorPicker = true }, enabled = enabled) { Text("Color Picker") }
                Box(Modifier.size(34.dp).background(color, Capsule()))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlasmioButton(onClick = {}, enabled = enabled, emphasis = GlassEmphasis.Prominent) { Text("Continue") }
                GlasmioButton(onClick = {}, enabled = false) { Text("Disabled") }
            }
        }
    }
    GlasmioColorPicker(colorPicker, color, { color = it }, { colorPicker = false }, "Colors")
}

@Composable
private fun NavigationDemo(padding: PaddingValues) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var compact by rememberSaveable { mutableStateOf(false) }
    val compactProgress = animateFloatAsState(if (compact) 1f else 0f, label = "Navigation compact progress")
    var style by rememberSaveable { mutableIntStateOf(0) }
    DemoList(padding) {
        item {
            GlasmioGroupedList {
                GlasmioListRow("Compact Tab Bar", showTopSeparator = false, trailing = { GlasmioSwitch(compact, { compact = it }) })
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlasmioNavigationBar(
                    items = listOf(
                        GlasmioTabItem(0, "Home", SfSymbol.House),
                        GlasmioTabItem(1, "Favorites", SfSymbol.Heart),
                        GlasmioTabItem(2, "Search", SfSymbol.Search),
                    ),
                    selectedKey = selected,
                    onSelected = { selected = it },
                    onExpand = { compact = false },
                    compactProgress = compactProgress,
                    compactSize = 48.dp,
                )
            }
        }
        item {
            Section("Toolbars") {
                GlasmioPopUpButton(GlasmioTopBarStyle.entries[style], GlasmioTopBarStyle.entries, { style = it.ordinal }, { it.name })
                GlasmioTopBar("Title", subtitle = "Subtitle", style = GlasmioTopBarStyle.entries[style], actions = {
                    GlasmioIconButton(onClick = {}) { SfIcon("ellipsis", "More") }
                })
            }
        }
        item {
            GlasmioBottomToolbar(Modifier.fillMaxWidth()) {
                GlasmioIconButton(onClick = {}) { SfIcon("chevron.backward", "Previous") }
                Spacer(Modifier.weight(1f))
                Text("$selected", style = IosTypography.headline)
                Spacer(Modifier.weight(1f))
                GlasmioIconButton(onClick = {}) { SfIcon("square.and.arrow.up", "Share") }
            }
        }
    }
}

@Composable
private fun ListDemo(onBack: () -> Unit) {
    var reordered by remember { mutableStateOf(listOf("First", "Second", "Third")) }
    GlasmioPinnedListPage(title = "Lists", onNavigateBack = onBack) {
        item {
            GlasmioReorderableGroup(reordered, { reordered = it }, "Reorder", "Move up", "Move down") {
                Text(it, style = IosTypography.body, modifier = Modifier.weight(1f))
            }
        }
        item {
            GlasmioGroupedList {
                GlasmioListRow("Simple row", showTopSeparator = false)
                GlasmioListRow("Title", subtitle = "Subtitle", detail = "Detail", systemName = "star")
                GlasmioListRow("A long row title stays inside its layout and truncates cleanly", onClick = {})
            }
        }
        items((1..24).toList()) { index ->
            GlasmioCard(Modifier.fillMaxWidth()) {
                GlasmioListRow("Item $index", showTopSeparator = false, onClick = {})
            }
        }
    }
}

@Composable
private fun PreferencesDemo(
    padding: PaddingValues,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparency: (Boolean) -> Unit,
    rtl: Boolean,
    onRtl: (Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("Glasmio") }
    var choice by rememberSaveable { mutableStateOf("System") }
    DemoList(padding) {
        item {
            Section("Appearance") {
                GlasmioGroupedList {
                    GlasmioSwitchPreference(title = { Text("Dark Appearance") }, checked = dark, onCheckedChange = onDark)
                    GlasmioSwitchPreference(title = { Text("Reduce Transparency") }, checked = reduceTransparency, onCheckedChange = onReduceTransparency)
                    GlasmioSwitchPreference(title = { Text("Right to Left") }, checked = rtl, onCheckedChange = onRtl)
                }
            }
        }
        item {
            Section("Preferences") {
                GlasmioGroupedList {
                    GlasmioEditTextPreference(title = { Text("Name") }, value = name, onValueChange = { name = it })
                    GlasmioListPreference(title = { Text("Appearance") }, selectedValue = choice, values = listOf("System", "Light", "Dark"), valueText = { it }, onValueSelected = { choice = it })
                    GlasmioSwitchPreference(title = { Text("Disabled Preference") }, checked = true, onCheckedChange = {}, isEnabled = false)
                }
            }
        }
    }
}

@Composable
private fun SearchDemo(padding: PaddingValues) {
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var active by rememberSaveable { mutableStateOf(false) }
    val matches = catalog.filter { it.first.contains(query.text, ignoreCase = true) }
    DemoList(padding) {
        item {
            GlasmioSearchBar(query, { query = it }, active, { active = it }, { active = false }, "Search Components", "Cancel", Modifier.fillMaxWidth())
        }
        if (matches.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No Results", style = IosTypography.title2, color = LocalGlassColors.current.secondaryContent)
                }
            }
        } else {
            item {
                GlasmioGroupedList {
                    matches.forEachIndexed { index, (title, symbol) ->
                        GlasmioListRow(title, systemName = symbol, showTopSeparator = index > 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenusDemo(padding: PaddingValues) {
    var expanded by remember { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf("Default") }
    var alert by rememberSaveable { mutableStateOf(false) }
    var inputAlert by rememberSaveable { mutableStateOf(false) }
    var nestedMenu by remember { mutableStateOf(false) }
    val nestedAnchor = rememberGlasmioMenuAnchorState()
    var name by remember { mutableStateOf(TextFieldValue("")) }
    DemoList(padding) {
        item {
            GlasmioMenu(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                itemCount = 4,
                anchor = { open -> GlasmioButton(onClick = open) { Text("Menu") } },
            ) { backdrop, close ->
                GlasmioMenuItem("Favorite", { selection = "Favorite"; close() }, systemName = "heart", backdrop = backdrop)
                GlasmioMenuItem("Duplicate", { selection = "Duplicate"; close() }, systemName = "plus.square.on.square", backdrop = backdrop)
                GlasmioMenuItem("Unavailable", {}, enabled = false, backdrop = backdrop)
                GlasmioMenuItem("Delete", { selection = "Delete"; close() }, destructive = true, systemName = "trash", backdrop = backdrop)
            }
        }
        item {
            GlasmioGroupedList {
                GlasmioListRow("Pop-up Button", showTopSeparator = false, trailing = {
                    GlasmioPopUpButton(selection.takeIf { it in listOf("Default", "Recent", "Name") } ?: "Default", listOf("Default", "Recent", "Name"), { selection = it }, { it })
                })
                GlasmioListRow("Alert", onClick = { alert = true })
                GlasmioListRow("Input Alert", onClick = { inputAlert = true })
            }
        }
        item {
            GlasmioButton(onClick = { nestedMenu = true }, modifier = Modifier.glasmioMenuAnchor(nestedAnchor)) { Text("Cascading Menu") }
        }
    }
    if (nestedMenu) {
        GlasmioCascadingMenu(
            anchorBounds = nestedAnchor.bounds,
            menuAnchorState = nestedAnchor,
            items = listOf(
                GlasmioCascadingMenuItem("Sort By", systemName = "arrow.up.arrow.down", children = listOf(
                    GlasmioCascadingMenuItem("Name", onClick = { selection = "Name" }),
                    GlasmioCascadingMenuItem("Recent", onClick = { selection = "Recent" }),
                )),
                GlasmioCascadingMenuItem("Favorite", systemName = "heart", onClick = { selection = "Favorite" }),
            ),
            title = "Options",
            expandedDescription = "Expanded",
            collapsedDescription = "Collapsed",
            onDismiss = { nestedMenu = false },
        )
    }
    if (alert) {
        GlasmioAlertDialog(
            onDismissRequest = { alert = false },
            title = "Save Changes?",
            message = "Your changes will appear in this sample.",
            buttons = listOf(
                GlasmioAlertButtonSpec("Cancel", { alert = false }, GlasmioAlertButtonRole.Cancel),
                GlasmioAlertButtonSpec("Save", { alert = false }),
            ),
        )
    }
    if (inputAlert) {
        GlasmioAlertDialog(
            onDismissRequest = { inputAlert = false },
            title = "Name",
            buttons = listOf(
                GlasmioAlertButtonSpec("Cancel", { inputAlert = false }, GlasmioAlertButtonRole.Cancel),
                GlasmioAlertButtonSpec("Done", { inputAlert = false }, enabled = name.text.isNotBlank()),
            ),
        ) {
            GlasmioAlertFieldGroup { GlasmioAlertTextField(name, { name = it }, "Name") }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SheetsDemo(padding: PaddingValues) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    var actionSheet by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(true) }
    DemoList(padding) {
        item {
            GlasmioGroupedList {
                GlasmioListRow("Sheet", showTopSeparator = false, onClick = { sheet = true })
                GlasmioListRow("Action Sheet", onClick = { actionSheet = true })
            }
        }
    }
    if (sheet) {
        GlasmioBottomSheet(onDismissRequest = { sheet = false }) {
            GlasmioSheetTopToolbar("Sheet", actions = {
                GlasmioSheetToolbarButton(onClick = { sheet = false }) { SfIcon("xmark", "Close") }
            })
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GlasmioGroupedList(framed = false) {
                    GlasmioListRow("Notifications", showTopSeparator = false, trailing = { GlasmioSwitch(checked, { checked = it }) })
                    GlasmioListRow("Privacy", systemName = "lock", onClick = {})
                }
                GlasmioButton(onClick = { sheet = false }, modifier = Modifier.fillMaxWidth(), emphasis = GlassEmphasis.Prominent) { Text("Done") }
            }
        }
    }
    if (actionSheet) {
        GlasmioBottomSheet(onDismissRequest = { actionSheet = false }) {
            GlasmioActionSheetContent("Actions", message = "Choose an action") {
                GlasmioListRow("Share", systemName = "square.and.arrow.up", showTopSeparator = false, onClick = { actionSheet = false })
                GlasmioListRow("Duplicate", systemName = "plus.square.on.square", onClick = { actionSheet = false })
                GlasmioListRow("Cancel", onClick = { actionSheet = false })
            }
        }
    }
}
