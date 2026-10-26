package io.github.neoruaa.glasmio.sample

import android.os.Bundle
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*
import io.github.neoruaa.glasmio.preference.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

private data class SampleAssets(
    val symbolTypeface: Typeface?,
    val typography: Typography,
    val lightWallpaper: ImageBitmap,
    val darkWallpaper: ImageBitmap,
)

private val LocalSampleAssets = staticCompositionLocalOf<SampleAssets> { error("Sample assets are not loaded") }
private val topLevelRoutes = setOf("Home", "MaterialTab", "About")
private val wallpaperRoutes = setOf("MaterialTab", "Materials", "Controls", "Navigation", "Search", "Menus", "Sheets")

class MainActivity : ComponentActivity() {
    private var appDarkAppearance: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val systemBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { resources ->
            appDarkAppearance ?: ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        }
        enableEdgeToEdge(statusBarStyle = systemBarStyle, navigationBarStyle = systemBarStyle)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(systemDark) }
            var reduceTransparency by rememberSaveable { mutableStateOf(false) }
            var rtl by rememberSaveable { mutableStateOf(false) }
            val context = LocalContext.current.applicationContext
            val fontResolver = LocalFontFamilyResolver.current
            val assets by produceState<SampleAssets?>(null, context, fontResolver) {
                value = withContext(Dispatchers.IO) {
                    val symbolTypeface = runCatching { Typeface.createFromAsset(context.assets, "glasmio_symbols.ttf") }.getOrNull()
                    val typography = if (symbolTypeface != null) sampleTypography(context.assets) else glasmioTypography()
                    listOf(
                        typography.displayLarge, typography.displayMedium, typography.displaySmall,
                        typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
                        typography.titleLarge, typography.titleMedium, typography.titleSmall,
                        typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
                        typography.labelLarge, typography.labelMedium, typography.labelSmall,
                    ).mapNotNull { it.fontFamily }.distinct().forEach { fontResolver.preload(it) }
                    SampleAssets(
                        symbolTypeface = symbolTypeface,
                        typography = typography,
                        lightWallpaper = requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.ios27_wallpaper_light)).apply { prepareToDraw() }.asImageBitmap(),
                        darkWallpaper = requireNotNull(BitmapFactory.decodeResource(context.resources, R.drawable.ios27_wallpaper_dark)).apply { prepareToDraw() }.asImageBitmap(),
                    )
                }
            }
            val darkAppearance = dark
            SideEffect {
                appDarkAppearance = darkAppearance
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkAppearance
                    isAppearanceLightNavigationBars = !darkAppearance
                }
            }
            val loadedAssets = assets
            if (loadedAssets == null) {
                Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White))
            } else {
                GlasmioIconProvider(symbolTypeface = loadedAssets.symbolTypeface) {
                    GlasmioTheme(
                        isDark = dark,
                        typography = loadedAssets.typography,
                        appearance = GlassAppearance(reduceTransparency = reduceTransparency),
                    ) {
                        CompositionLocalProvider(
                            LocalSampleAssets provides loadedAssets,
                            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                        ) {
                            SampleNavigation(dark, { dark = it }, reduceTransparency, { reduceTransparency = it }, rtl, { rtl = it })
                        }
                    }
                }
            }
        }
    }
}

/** Match each Compose weight request to the SF Pro variable font instead of a fixed Typeface. */
private fun sampleTypography(assetManager: AssetManager): Typography {
    val base = glasmioTypography()
    val families = mutableMapOf<Float, FontFamily>()
    val weights = listOf(400 to 400, 500 to 510, 510 to 510, 590 to 590, 600 to 590, 700 to 700)
    fun resolve(style: TextStyle): TextStyle {
        val opticalSize = style.fontSize.value.coerceIn(17f, 28f)
        val family = families.getOrPut(opticalSize) {
            FontFamily(weights.map { (request, axis) ->
                Font(
                    path = "glasmio_symbols.ttf",
                    assetManager = assetManager,
                    weight = FontWeight(request),
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(axis),
                        FontVariation.Setting("opsz", opticalSize),
                    ),
                )
            })
        }
        return style.copy(fontFamily = family)
    }
    return base.copy(
        displayLarge = resolve(base.displayLarge),
        displayMedium = resolve(base.displayMedium),
        displaySmall = resolve(base.displaySmall),
        headlineLarge = resolve(base.headlineLarge),
        headlineMedium = resolve(base.headlineMedium),
        headlineSmall = resolve(base.headlineSmall),
        titleLarge = resolve(base.titleLarge),
        titleMedium = resolve(base.titleMedium),
        titleSmall = resolve(base.titleSmall),
        bodyLarge = resolve(base.bodyLarge),
        bodyMedium = resolve(base.bodyMedium),
        bodySmall = resolve(base.bodySmall),
        labelLarge = resolve(base.labelLarge),
        labelMedium = resolve(base.labelMedium),
        labelSmall = resolve(base.labelSmall),
    )
}

@Composable
private fun SampleNavigation(
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparency: (Boolean) -> Unit,
    rtl: Boolean,
    onRtl: (Boolean) -> Unit,
) {
    val backStack = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf("Home") }
    val homeListState = rememberLazyListState()
    val materialsListState = rememberLazyListState()
    val aboutListState = rememberLazyListState()
    val rootListStates = remember(homeListState, materialsListState, aboutListState) {
        mapOf("Home" to homeListState, "MaterialTab" to materialsListState, "About" to aboutListState)
    }
    val currentDark by rememberUpdatedState(dark)
    val currentOnDark by rememberUpdatedState(onDark)
    val currentReduceTransparency by rememberUpdatedState(reduceTransparency)
    val currentOnReduceTransparency by rememberUpdatedState(onReduceTransparency)
    val currentRtl by rememberUpdatedState(rtl)
    val currentOnRtl by rememberUpdatedState(onRtl)
    val navigate: (String) -> Unit = { route ->
        if (backStack.lastOrNull() != route) backStack.add(route)
    }
    val navigateBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    val selectTab: (String) -> Unit = { route ->
        if (backStack.lastOrNull() != route) {
            backStack.clear()
            backStack.add("Home")
            if (route != "Home") backStack.add(route)
        }
    }
    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = navigateBack,
        transitionEffects = remember { NavDisplayTransitionEffects(enableCornerClip = true, dimAmount = 0.5f, blockInputDuringTransition = false) },
        entryProvider = { route ->
            NavEntry(key = route, contentKey = route) {
                val wallpaper = route in wallpaperRoutes
                GlasmioBackdropHost(
                    modifier = Modifier.fillMaxSize(),
                    sampledContent = { SampleBackdrop(wallpaper) },
                    overlayContent = {
                        val isTopLevel = route in topLevelRoutes
                        val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        val bottomPadding = navigationInset + if (isTopLevel) NavigationBarSurfaceHeight + 8.dp else 0.dp
                        val pageBackdrop = rememberLayerBackdrop()
                        val bottomBackdrop = rememberSharedGlassBackdrop(LocalGlassBackdrop.current, pageBackdrop)
                        CompositionLocalProvider(LocalBlurBackdrop provides pageBackdrop) {
                            Box(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxSize().glassBackdropSource(pageBackdrop).trackBackdropPosition(pageBackdrop)) {
                                    if (route == "Lists") {
                                        ListDemo(onBack = navigateBack)
                                    } else {
                                        val title = when (route) {
                                            "Home" -> "Glasmio"
                                            "MaterialTab" -> "材质"
                                            "About" -> "关于"
                                            else -> route
                                        }
                                        SamplePinnedPage(
                                            title = title,
                                            bottomPadding = bottomPadding,
                                            wallpaper = wallpaper,
                                            onBack = if (isTopLevel) null else navigateBack,
                                            dark = currentDark,
                                            onDark = currentOnDark,
                                            listState = rootListStates[route],
                                        ) { layout ->
                                            when (route) {
                                                "Home" -> Catalog(layout, navigate)
                                                "MaterialTab", "Materials" -> MaterialsDemo(layout)
                                                "Controls" -> ControlsDemo(layout)
                                                "Navigation" -> NavigationDemo(layout)
                                                "Preferences" -> PreferencesDemo(layout, currentDark, currentOnDark, currentReduceTransparency, currentOnReduceTransparency, currentRtl, currentOnRtl)
                                                "Search" -> SearchDemo(layout, navigate)
                                                "Menus" -> MenusDemo(layout)
                                                "Sheets" -> SheetsDemo(layout)
                                                "About" -> AboutDemo(layout)
                                            }
                                        }
                                    }
                                }
                                if (isTopLevel) {
                                    SampleBottomBar(
                                        selected = route,
                                        onSelected = selectTab,
                                        onSearch = { navigate("Search") },
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                        backdrop = bottomBackdrop,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    )
}

@Composable
private fun SampleBackdrop(wallpaper: Boolean) {
    if (wallpaper) {
        val assets = LocalSampleAssets.current
        Image(
            bitmap = if (LocalGlassColors.current.isDark) assets.darkWallpaper else assets.lightWallpaper,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.fillMaxSize().background(LocalGlassColors.current.groupedBackground))
    }
}

private data class SamplePageLayout(
    val title: String,
    val padding: PaddingValues,
    val listState: LazyListState,
    val collapseProgress: Float,
)

@Composable
private fun SamplePinnedPage(
    title: String,
    bottomPadding: Dp,
    wallpaper: Boolean,
    onBack: (() -> Unit)?,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    listState: LazyListState? = null,
    content: @Composable (SamplePageLayout) -> Unit,
) {
    val state = listState ?: rememberLazyListState()
    val collapseDistance = with(LocalDensity.current) { 56.dp.toPx() }
    val progress by remember(state, collapseDistance) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    GlasmioPinnedPage(
        title = title,
        bottomPadding = bottomPadding,
        onNavigateBack = onBack,
        collapseProgress = progress,
        backgroundColor = LocalGlassColors.current.groupedBackground,
        actions = {
            GlasmioIconButton(onClick = { onDark(!dark) }) {
                SfIcon(if (dark) "sun.max" else "moon", if (dark) "Light appearance" else "Dark appearance")
            }
        },
    ) { padding ->
        if (wallpaper) SampleBackdrop(true)
        content(SamplePageLayout(title, padding, state, progress))
    }
}

@Composable
private fun SampleLargeTitle(title: String, collapseProgress: Float) {
    Text(
        title,
        style = IosTypography.largeTitle,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
            .graphicsLayer {
                alpha = 1f - collapseProgress
                scaleX = 1f - 0.04f * collapseProgress
                scaleY = scaleX
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .blur(6.dp * collapseProgress)
            .offset(y = (-10).dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun DemoList(layout: SamplePageLayout, content: LazyListScope.() -> Unit) {
    LazyColumn(
        state = layout.listState,
        modifier = Modifier.fillMaxSize().consumeWindowInsets(layout.padding),
        contentPadding = layout.padding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "sample-large-title") { SampleLargeTitle(layout.title, layout.collapseProgress) }
        content()
    }
}

@Composable
private fun SampleBottomBar(
    selected: String,
    onSelected: (String) -> Unit,
    onSearch: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val compactProgress = remember { mutableFloatStateOf(0f) }
    val items = remember(selected) {
        listOf(
            GlasmioTabItem("Home", "主页", if (selected == "Home") SfSymbol.HouseFilled else SfSymbol.House),
            GlasmioTabItem("MaterialTab", "材质", SfSymbol.Layers),
            GlasmioTabItem("About", "关于", SfSymbol.InfoCircle),
        )
    }
    BottomNavigationContact(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
    ) {
        GlasmioNavigationBar(
            items = items,
            selectedKey = selected,
            onSelected = onSelected,
            onExpand = {},
            compactProgress = compactProgress,
            compactSize = 44.dp,
            backdrop = backdrop,
            modifier = Modifier.weight(1f).zIndex(1f),
        )
        Spacer(Modifier.size(width = 8.dp, height = 1.dp))
        val contact = LocalBottomControlContact.current
        Box(
            Modifier.size(NavigationBarSurfaceHeight)
                .then(contact?.takeIf { it.capturesLensSource.value }?.neighborRecordingModifier ?: Modifier),
            contentAlignment = Alignment.CenterEnd,
        ) {
            GlasmioIconButton(
                onClick = onSearch,
                backdrop = backdrop,
                style = GlassSurfaceStyle.Navigation,
                modifier = Modifier.size(NavigationBarSurfaceHeight),
                morphProgress = compactProgress,
                morphCaptureWidth = NavigationBarSurfaceHeight,
            ) {
                SfIcon(SfSymbol.Search, "搜索", size = 26.dp, weight = FontWeight.SemiBold, tint = LocalGlassColors.current.content)
            }
        }
    }
}

@Composable
private fun AboutDemo(layout: SamplePageLayout) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName.orEmpty()
    }
    DemoList(layout) {
        item {
            GlasmioGroupedList {
                GlasmioListRow("Glasmio", detail = version, showTopSeparator = false)
                GlasmioListRow("Apache License 2.0", onClick = { uriHandler.openUri("https://www.apache.org/licenses/LICENSE-2.0") })
                GlasmioListRow("GitHub", onClick = { uriHandler.openUri("https://github.com/NEORUAA/Glasmio") })
            }
        }
    }
}

private val catalog = listOf(
    "Materials" to "square.stack.3d.up",
    "Controls" to "slider.horizontal.3",
    "Navigation" to "rectangle.3.group",
    "Lists" to "list.bullet",
    "Preferences" to "gear",
    "Search" to "magnifyingglass",
    "Menus" to "ellipsis",
    "Sheets" to "inset.filled.bottomthird.rectangle",
)

@Composable
private fun Catalog(layout: SamplePageLayout, onOpen: (String) -> Unit) {
    DemoList(layout) {
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
private fun MaterialsDemo(layout: SamplePageLayout) {
    var separated by rememberSaveable { mutableStateOf(false) }
    DemoList(layout) {
        items(NavigationGlassMaterial.entries, key = { it.name }) { material ->
            GlassSurface(
                modifier = Modifier.fillMaxWidth().height(148.dp),
                shape = ContinuousRoundedRectangle(32.dp),
                material = material,
                onClick = {},
            ) {
                Text(
                    text = when (material) {
                        NavigationGlassMaterial.RegularSmall -> "Regular Small"
                        NavigationGlassMaterial.RegularMedium -> "Regular Medium"
                        NavigationGlassMaterial.RegularLarge -> "Regular Large"
                        NavigationGlassMaterial.Clear -> "Clear"
                    },
                    style = IosTypography.title2,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlasmioButton(onClick = {}, modifier = Modifier.weight(1f), size = GlasmioButtonSize.Large) { Text("Glass") }
                GlasmioButton(onClick = {}, modifier = Modifier.weight(1f), emphasis = GlassEmphasis.Prominent, size = GlasmioButtonSize.Large) { Text("Glass Prominent") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GlasmioGroupedList {
                    GlasmioListRow("Separate Actions", showTopSeparator = false, trailing = { GlasmioSwitch(separated, { separated = it }) })
                }
                GlasmioToolbarActionGroup(style = if (separated) GlasmioToolbarActionStyle.Separated else GlasmioToolbarActionStyle.Merged) {
                    GlasmioIconButton(onClick = {}) { SfIcon("plus", "Add") }
                    GlasmioIconButton(onClick = {}) { SfIcon("arrow.counterclockwise", "Reset") }
                    GlasmioIconButton(onClick = {}) { SfIcon("heart", "Like") }
                }
            }
        }
    }
}

@Composable
private fun ControlsDemo(layout: SamplePageLayout) {
    var enabled by rememberSaveable { mutableStateOf(true) }
    var checked by rememberSaveable { mutableStateOf(true) }
    var value by rememberSaveable { mutableFloatStateOf(0.45f) }
    var step by rememberSaveable { mutableIntStateOf(3) }
    var text by rememberSaveable { mutableStateOf("") }
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var color by remember { mutableStateOf(Color(0xFF0088FF)) }
    var colorPicker by rememberSaveable { mutableStateOf(false) }
    DemoList(layout) {
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
                GlasmioButton(onClick = { colorPicker = true }, enabled = enabled, size = GlasmioButtonSize.Medium) { Text("Color Picker") }
                Box(Modifier.size(34.dp).background(color, Capsule()))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlasmioButton(onClick = {}, enabled = enabled, emphasis = GlassEmphasis.Prominent, size = GlasmioButtonSize.Large) { Text("Continue") }
                GlasmioButton(onClick = {}, enabled = false, size = GlasmioButtonSize.Large) { Text("Disabled") }
            }
        }
        item {
            Section("Buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlasmioButtonSize.entries.forEach { size ->
                        GlasmioButton(onClick = {}, size = size, enabled = enabled) { Text(size.name) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlasmioButton(onClick = {}, size = GlasmioButtonSize.Large, destructive = true, enabled = enabled) { Text("Delete") }
                    GlasmioButton(onClick = {}, size = GlasmioButtonSize.Large, emphasis = GlassEmphasis.Prominent, destructive = true, enabled = enabled) { Text("Delete") }
                }
            }
        }
    }
    GlasmioColorPicker(colorPicker, color, { color = it }, { colorPicker = false }, "Colors")
}

@Composable
private fun NavigationDemo(layout: SamplePageLayout) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var compact by rememberSaveable { mutableStateOf(false) }
    val compactProgress = animateFloatAsState(if (compact) 1f else 0f, label = "Navigation compact progress")
    var style by rememberSaveable { mutableIntStateOf(0) }
    DemoList(layout) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListDemo(onBack: () -> Unit) {
    var reordered by remember { mutableStateOf(listOf("First", "Second", "Third")) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var entries by remember { mutableStateOf((1..24).toList()) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                try {
                    delay(400)
                    entries = entries.drop(1) + entries.first()
                } finally {
                    refreshing = false
                }
            }
        }
    }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val refreshState = rememberPullToRefreshState()
    val collapseDistance = with(LocalDensity.current) { 56.dp.toPx() }
    val listCollapseProgress by remember(listState, collapseDistance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / collapseDistance).coerceIn(0f, 1f)
        }
    }
    val gridCollapseProgress = rememberGlasmioGridCollapseProgress(gridState)
    val collapseProgress = if (grid) gridCollapseProgress else listCollapseProgress
    GlasmioPinnedPage(
        title = "Lists",
        onNavigateBack = onBack,
        collapseProgress = collapseProgress,
        actions = {
            GlasmioIconButton(onClick = refresh, enabled = !refreshing) {
                SfIcon("arrow.clockwise", "Refresh")
            }
            GlasmioIconButton(onClick = { grid = !grid }) {
                SfIcon(if (grid) "list.bullet" else "rectangle.grid.2x2", if (grid) "Show list" else "Show grid")
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().pullToRefresh(
                isRefreshing = refreshing,
                onRefresh = refresh,
                state = refreshState,
            ),
        ) {
            Box(Modifier.fillMaxSize().pullToRefreshRebound(refreshState, refreshing)) {
                if (grid) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                        contentPadding = padding,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "large-title", span = { GridItemSpan(maxLineSpan) }) { SampleLargeTitle("Lists", collapseProgress) }
                        items(entries, key = { it }) { index ->
                            GlasmioCard(Modifier.fillMaxWidth().height(120.dp)) {
                                Text("Item $index", style = IosTypography.headline, modifier = Modifier.padding(20.dp))
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                        contentPadding = padding,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "large-title") { SampleLargeTitle("Lists", collapseProgress) }
                        item(key = "reorderable") {
                            GlasmioReorderableGroup(reordered, { reordered = it }, "Reorder", "Move up", "Move down") {
                                Text(it, style = IosTypography.body, modifier = Modifier.weight(1f))
                            }
                        }
                        item(key = "grouped") {
                            GlasmioGroupedList {
                                GlasmioListRow("Simple row", showTopSeparator = false)
                                GlasmioListRow("Title", subtitle = "Subtitle", detail = "Detail", systemName = "star")
                                GlasmioListRow("A long row title stays inside its layout and truncates cleanly", onClick = {})
                            }
                        }
                        items(entries, key = { it }) { index ->
                            GlasmioCard(Modifier.fillMaxWidth()) {
                                GlasmioListRow("Item $index", showTopSeparator = false, onClick = {})
                            }
                        }
                    }
                }
            }
            PullToRefreshDefaults.Indicator(
                isRefreshing = refreshing,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding()),
            )
        }
    }
}

@Composable
private fun PreferencesDemo(
    layout: SamplePageLayout,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    reduceTransparency: Boolean,
    onReduceTransparency: (Boolean) -> Unit,
    rtl: Boolean,
    onRtl: (Boolean) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("Glasmio") }
    var choice by rememberSaveable { mutableStateOf("System") }
    DemoList(layout) {
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
private fun SearchDemo(layout: SamplePageLayout, onOpen: (String) -> Unit) {
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var active by rememberSaveable { mutableStateOf(false) }
    val matches = catalog.filter { it.first.contains(query.text, ignoreCase = true) }
    DemoList(layout) {
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
                        GlasmioListRow(title, systemName = symbol, showTopSeparator = index > 0, onClick = { onOpen(title) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MenusDemo(layout: SamplePageLayout) {
    var expanded by remember { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf("Default") }
    var alert by rememberSaveable { mutableStateOf(false) }
    var inputAlert by rememberSaveable { mutableStateOf(false) }
    var nestedMenu by remember { mutableStateOf(false) }
    val nestedAnchor = rememberGlasmioMenuAnchorState()
    var name by remember { mutableStateOf(TextFieldValue("")) }
    DemoList(layout) {
        item {
            GlasmioMenu(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                itemCount = 4,
                anchor = { open -> GlasmioButton(onClick = open, size = GlasmioButtonSize.Medium) { Text("Menu") } },
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
            GlasmioButton(onClick = { nestedMenu = true }, modifier = Modifier.glasmioMenuAnchor(nestedAnchor), size = GlasmioButtonSize.Medium) { Text("Cascading Menu") }
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
private fun SheetsDemo(layout: SamplePageLayout) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    var actionSheet by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(true) }
    DemoList(layout) {
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
                GlasmioButton(onClick = { sheet = false }, modifier = Modifier.fillMaxWidth(), emphasis = GlassEmphasis.Prominent, size = GlasmioButtonSize.Large) { Text("Done") }
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
