package io.github.neoruaa.glasmio.sample

import android.os.Bundle
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.fonts.Font as NativeFont
import android.graphics.fonts.FontFamily as NativeFontFamily
import android.graphics.fonts.FontStyle as NativeFontStyle
import android.content.pm.PackageManager
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import io.github.neoruaa.glasmio.scaffold.navigation.GlasmioNavDisplay
import io.github.neoruaa.glasmio.scaffold.navigation.GlasmioNavHeaderMotion
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigation3.ui.defaultTransitionSpec
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*
import io.github.neoruaa.glasmio.icons.R as GlasmioIconsR
import io.github.neoruaa.glasmio.preference.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToInt

private data class SampleAssets(
    val symbolTypeface: Typeface?,
    val typography: Typography,
    val lightWallpaper: ImageBitmap,
    val darkWallpaper: ImageBitmap,
)

/** Process-owned immutable assets; no Activity, View, or Context is retained. */
private object SampleAssetCache {
    private var assets: SampleAssets? = null

    @Synchronized
    fun load(applicationContext: Context): SampleAssets {
        assets?.let { return it }
        val baseFont = try {
            NativeFont.Builder(applicationContext.assets, "glasmio_symbols.ttf").build()
        } catch (_: IOException) {
            null
        }
        val italicFont = if (baseFont == null) null else try {
            NativeFont.Builder(applicationContext.assets, "glasmio_italic.ttf").build()
        } catch (_: IOException) {
            null
        }
        return SampleAssets(
            symbolTypeface = baseFont?.let(::sampleTypeface),
            typography = baseFont?.let { sampleTypography(it, italicFont) } ?: glasmioTypography(),
            lightWallpaper = requireNotNull(BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ios27_wallpaper_light)).apply { prepareToDraw() }.asImageBitmap(),
            darkWallpaper = requireNotNull(BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ios27_wallpaper_dark)).apply { prepareToDraw() }.asImageBitmap(),
        ).also { assets = it }
    }
}

private val LocalSampleAssets = staticCompositionLocalOf<SampleAssets> { error("Sample assets are not loaded") }
private val topLevelRoutes = setOf("Home", "MaterialTab", "SettingsTab", "About")
private val wallpaperRoutes = setOf("MaterialTab", "Materials")

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
                    val cachedAssets = SampleAssetCache.load(context)
                    val typography = cachedAssets.typography
                    listOf(
                        typography.displayLarge, typography.displayMedium, typography.displaySmall,
                        typography.headlineLarge, typography.headlineMedium, typography.headlineSmall,
                        typography.titleLarge, typography.titleMedium, typography.titleSmall,
                        typography.bodyLarge, typography.bodyMedium, typography.bodySmall,
                        typography.labelLarge, typography.labelMedium, typography.labelSmall,
                    ).mapNotNull { it.fontFamily }.distinct().forEach { fontResolver.preload(it) }
                    cachedAssets
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

/** Every variation retains the same native font buffer; the asset is read only once. */
private fun sampleTypeface(font: NativeFont): Typeface =
    Typeface.CustomFallbackBuilder(NativeFontFamily.Builder(font).build()).setStyle(font.style).build()

private class SampleFont(
    override val weight: FontWeight,
    val typeface: Typeface,
    override val style: FontStyle = FontStyle.Normal,
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = SampleFontLoader,
    variationSettings = FontVariation.Settings(),
)

private object SampleFontLoader : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface = (font as SampleFont).typeface
    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loadBlocking(context, font)
}

/** Compose sees real weight entries; their Typefaces clone the shared native Font data. */
private fun sampleTypography(baseFont: NativeFont, italicFont: NativeFont?): Typography {
    val base = glasmioTypography()
    val families = mutableMapOf<Float, FontFamily>()
    val typefaces = mutableMapOf<Triple<Float, Float, FontStyle>, Typeface>()
    val weights = listOf(400 to 400f, 500 to 510f, 510 to 510f, 590 to 590f, 591 to 590.7999878f, 600 to 590f, 700 to 700f)
    val fontStyles = if (italicFont == null) listOf(FontStyle.Normal) else listOf(FontStyle.Normal, FontStyle.Italic)
    fun resolve(style: TextStyle): TextStyle {
        val opticalSize = style.fontSize.value.coerceIn(17f, 28f)
        val family = families.getOrPut(opticalSize) {
            FontFamily(weights.flatMap { (request, axis) ->
                fontStyles.map { fontStyle ->
                    // Named SF Pro Italic instances carry a YAXS value as well as weight.
                    val (weightAxis, italicYAxis) = if (fontStyle == FontStyle.Italic) {
                        when (request) {
                            500, 510 -> 508f to 436f
                            590, 591, 600 -> 590.800003f to 419.199997f
                            700 -> 700f to 430f
                            else -> axis to 400f
                        }
                    } else axis to 400f
                    val typeface = typefaces.getOrPut(Triple(weightAxis, opticalSize, fontStyle)) {
                        val source = if (fontStyle == FontStyle.Italic) requireNotNull(italicFont) else baseFont
                        val axes = "'wght' $weightAxis, 'opsz' $opticalSize" +
                            if (fontStyle == FontStyle.Italic) ", 'YAXS' $italicYAxis" else ""
                        val variation = NativeFont.Builder(source)
                            .setWeight(weightAxis.roundToInt())
                            .setSlant(if (fontStyle == FontStyle.Italic) NativeFontStyle.FONT_SLANT_ITALIC else NativeFontStyle.FONT_SLANT_UPRIGHT)
                            .setFontVariationSettings(axes)
                            .build()
                        sampleTypeface(variation)
                    }
                    SampleFont(FontWeight(request), typeface, fontStyle)
                }
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
    ) { mutableStateListOf(sampleEntryKey("Home")) }
    val homeListState = rememberLazyListState()
    val materialsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val aboutListState = rememberLazyListState()
    val rootListStates = remember(homeListState, materialsListState, settingsListState, aboutListState) {
        mapOf("Home" to homeListState, "MaterialTab" to materialsListState, "SettingsTab" to settingsListState, "About" to aboutListState)
    }
    var preferenceName by rememberSaveable { mutableStateOf("Glasmio") }
    var preferenceChoice by rememberSaveable { mutableStateOf("System") }
    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var navigationBarVisible by rememberSaveable { mutableStateOf(true) }
    var usesMiuixTransitionEffects by remember { mutableStateOf(true) }
    val currentRoute = rememberUpdatedState(backStack.lastOrNull()?.sampleRouteName())
    val searchTransition = rememberGlasmioSearchTransition(currentRoute.value == "Search")
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (currentRoute.value !in topLevelRoutes) return Offset.Zero
                if (available.y < -10f) navigationBarVisible = false
                else if (available.y > 10f) navigationBarVisible = true
                return Offset.Zero
            }
        }
    }
    val compactProgress = animateFloatAsState(
        targetValue = if (navigationBarVisible) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "CompactBottomNavigation",
    )
    val currentDark by rememberUpdatedState(dark)
    val currentOnDark by rememberUpdatedState(onDark)
    val currentReduceTransparency by rememberUpdatedState(reduceTransparency)
    val currentOnReduceTransparency by rememberUpdatedState(onReduceTransparency)
    val currentRtl by rememberUpdatedState(rtl)
    val currentOnRtl by rememberUpdatedState(onRtl)
    val currentPreferenceName by rememberUpdatedState(preferenceName)
    val currentPreferenceChoice by rememberUpdatedState(preferenceChoice)
    val navigate: (String) -> Unit = { route ->
        if (backStack.lastOrNull()?.sampleRouteName() != route) {
            usesMiuixTransitionEffects = backStack.lastOrNull()?.sampleRouteName() !in topLevelRoutes || route !in topLevelRoutes
            backStack.add(sampleEntryKey(route))
        }
    }
    val navigateBack: () -> Unit = {
        if (backStack.size > 1) {
            val target = backStack[backStack.lastIndex - 1].sampleRouteName()
            usesMiuixTransitionEffects = backStack.lastOrNull()?.sampleRouteName() !in topLevelRoutes || target !in topLevelRoutes
            backStack.removeAt(backStack.lastIndex)
        }
    }
    val selectTab: (String) -> Unit = { route ->
        if (backStack.lastOrNull()?.sampleRouteName() != route) {
            usesMiuixTransitionEffects = backStack.lastOrNull()?.sampleRouteName() !in topLevelRoutes || route !in topLevelRoutes
            backStack.clear()
            backStack.add(sampleEntryKey("Home"))
            if (route != "Home") backStack.add(sampleEntryKey(route))
        }
    }
    val navigationBackdrop = rememberLayerBackdrop()
    val headerMotion = remember {
        GlasmioNavHeaderMotion(backStack.last()) { from, to, pop ->
            if ((from as? String)?.sampleRouteName() in topLevelRoutes && (to as? String)?.sampleRouteName() in topLevelRoutes) GlasmioNavigationOperation.Tab
            else if (pop) GlasmioNavigationOperation.Pop else GlasmioNavigationOperation.Push
        }
    }
    val headerEntries = remember { mutableMapOf<String, SampleHeaderEntry>() }
    fun headerEntry(key: String): SampleHeaderEntry = headerEntries.getOrPut(key) {
        SampleHeaderEntry()
    }
    SideEffect {
        val frame = headerMotion.frame.value
        headerEntries.keys.retainAll(backStack.toSet() + setOf(frame.fromEntryKey, frame.toEntryKey))
    }
    val bottomBackdrop = rememberSharedGlassBackdrop(navigationBackdrop, navigationBackdrop)
    val editingValue = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("Hello World"))
    }
    val editingActions = textEditingDemoActions(editingValue, navigationBackdrop)
    GlasmioTextSelectionHost(
        backdrop = navigationBackdrop,
        additionalItems = if (currentRoute.value == "Text Editing") editingActions.items else emptyList(),
    ) {
        editingActions.dialog()
        GlasmioFloatingSearch(
            active = currentRoute.value == "Search",
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = {},
            onDismiss = navigateBack,
            placeholder = "Search Components",
            cancelLabel = "Cancel",
            backdrop = navigationBackdrop,
            transition = searchTransition,
            results = { padding -> SearchResults(searchQuery.text, padding, navigate) },
        ) {
            Box(Modifier.fillMaxSize().nestedScroll(scrollConnection)) {
                GlasmioNavigationScaffold(
                    motion = headerMotion,
                    header = { entryKey ->
                        val key = entryKey as String
                        val route = key.sampleRouteName()
                        val entry = headerEntry(key)
                        val appearanceAction = GlasmioHeaderAction(
                            key = "appearance.toggle", visualKey = if (dark) "sun.max" else "moon",
                            contentDescription = if (dark) "Light appearance" else "Dark appearance",
                            onClick = { currentOnDark(!currentDark) },
                        ) { SfIcon(if (dark) "sun.max" else "moon", null) }
                        GlasmioHeaderSpec(
                            title = when (route) { "Home" -> "Glasmio"; "MaterialTab" -> "材质"; "SettingsTab" -> "Settings"; "About" -> "关于"; else -> route },
                            leading = if (route in topLevelRoutes) emptyList() else listOf(GlasmioHeaderGroup("navigation", listOf(
                                GlasmioHeaderAction("navigation.back", "chevron.backward", "Back", navigateBack) { SfIcon(SfSymbol.ChevronBack, null, mirrored = true) },
                            ))),
                            trailing = listOf(GlasmioHeaderGroup("primary", entry.actions ?: listOf(appearanceAction))),
                            scrollState = entry.scroll,
                        )
                    },
                ) { headerPadding ->
                CompositionLocalProvider(LocalSampleBodyPadding provides headerPadding) {
                Box(Modifier.fillMaxSize().glassBackdropSource(navigationBackdrop).trackBackdropPosition(navigationBackdrop)) {
                    GlasmioNavDisplay(
                        motion = headerMotion,
                        backStack = backStack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = navigateBack,
                        transitionSpec = {
                            if (usesRootTabTransition()) rootTabTransition()
                            else defaultTransitionSpec<String>().invoke(this)
                        },
                        popTransitionSpec = {
                            if (usesRootTabTransition()) rootTabTransition()
                            else defaultPopTransitionSpec<String>().invoke(this)
                        },
                        predictivePopTransitionSpec = { swipeEdge ->
                            if (usesRootTabTransition()) rootTabTransition()
                            else defaultPredictivePopTransitionSpec<String>().invoke(this, swipeEdge)
                        },
                        transitionEffects = remember(usesMiuixTransitionEffects) {
                            NavDisplayTransitionEffects(
                                enableCornerClip = usesMiuixTransitionEffects,
                                dimAmount = if (usesMiuixTransitionEffects) 0.5f else 0f,
                                blockInputDuringTransition = false,
                            )
                        },
                        entryProvider = { entryKey ->
                            NavEntry(key = entryKey, contentKey = entryKey) {
                                val route = entryKey.sampleRouteName()
                                val entryListState = rootListStates[route] ?: rememberLazyListState()
                                CompositionLocalProvider(LocalSampleHeaderEntry provides headerEntry(entryKey)) {
                                val wallpaper = route in wallpaperRoutes
                                GlasmioBackdropHost(
                                    modifier = Modifier.fillMaxSize(),
                                    sampledContent = { SampleBackdrop(wallpaper) },
                                    overlayContent = {
                                        val isTopLevel = route in topLevelRoutes
                                        val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                                        val bottomPadding = navigationInset + if (isTopLevel || route == "Search") NavigationBarSurfaceHeight + 8.dp else 0.dp
                                        val pageBackdrop = rememberLayerBackdrop()
                                        CompositionLocalProvider(LocalBlurBackdrop provides pageBackdrop) {
                                            Box(Modifier.fillMaxSize()) {
                                                Box(Modifier.fillMaxSize().glassBackdropSource(pageBackdrop).trackBackdropPosition(pageBackdrop)) {
                                                    if (route == "Lists") {
                                                        ListDemo()
                                                    } else if (route == "Menus") {
                                                        MenusDemo(bottomPadding, currentDark, currentOnDark)
                                                    } else {
                                                        val title = when (route) {
                                                            "Home" -> "Glasmio"
                                                            "MaterialTab" -> "材质"
                                                            "SettingsTab" -> "Settings"
                                                            "About" -> "关于"
                                                            else -> route
                                                        }
                                                        SamplePinnedPage(
                                                            title = title,
                                                            bottomPadding = bottomPadding,
                                                            wallpaper = wallpaper,
                                                            listState = entryListState,
                                                            alwaysCompact = route == "Text Editing",
                                                            backgroundColor = if (route == "Text Editing") {
                                                                if (LocalGlassColors.current.isDark) Color.Black else Color.White
                                                            } else LocalGlassColors.current.groupedBackground,
                                                        ) { layout ->
                                                            when (route) {
                                                                "Home" -> Catalog(layout, navigate)
                                                                "MaterialTab", "Materials" -> MaterialsDemo(layout)
                                                                "Controls" -> ControlsDemo(layout)
                                                                "Navigation" -> NavigationDemo(layout)
                                                                "Preferences", "SettingsTab" -> PreferencesDemo(
                                                                    layout, currentDark, currentOnDark, currentReduceTransparency,
                                                                    currentOnReduceTransparency, currentRtl, currentOnRtl,
                                                                    currentPreferenceName, { preferenceName = it },
                                                                    currentPreferenceChoice, { preferenceChoice = it },
                                                                )
                                                                "Search" -> SearchLanding(layout, navigate)
                                                                "Text Styles and Dynamic Type" -> TypographyDemo(layout.listState, layout.padding) {
                                                                    SampleLargeTitle(layout.title, layout.collapseProgress)
                                                                }
                                                                "Text Editing" -> TextEditingDemo(editingValue, layout.padding)
                                                                "Sheets" -> SheetsDemo(layout)
                                                                "About" -> AboutDemo(layout)
                                                            }
                                                        }
                                                    }
                                                }

                                            }
                                        }
                                    },
                                )
                                }
                            }
                        },
                    )
                }
                }
                }
                val activeRoute = currentRoute.value
                val searchMorph = searchTransition.progressState.value.coerceIn(0f, 1f)
                if (activeRoute != null && (activeRoute in topLevelRoutes || searchMorph > 0f)) {
                    GlasmioBackdropProvider(bottomBackdrop) {
                        SampleBottomBar(
                            selected = activeRoute,
                            onSelected = selectTab,
                            onSearch = { navigate("Search") },
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .graphicsLayer {
                                    alpha = 1f - searchMorph
                                    val scale = 1f - 0.08f * searchMorph
                                    scaleX = scale
                                    scaleY = scale
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                                },
                            backdrop = bottomBackdrop,
                            compactProgress = compactProgress,
                            onExpand = { navigationBarVisible = true },
                        )
                    }
                }
            }
        }
    }
}

private fun Scene<String>.sampleRoute(): String? = entries.lastOrNull()?.contentKey?.let { (it as? String)?.sampleRouteName() }

private fun AnimatedContentTransitionScope<Scene<String>>.usesRootTabTransition(): Boolean =
    initialState.sampleRoute() in topLevelRoutes && targetState.sampleRoute() in topLevelRoutes

private fun AnimatedContentTransitionScope<Scene<String>>.rootTabTransition(): ContentTransform =
    fadeIn() togetherWith fadeOut()

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
    listState: LazyListState? = null,
    alwaysCompact: Boolean = false,
    backgroundColor: Color = LocalGlassColors.current.groupedBackground,
    content: @Composable (SamplePageLayout) -> Unit,
) {
    val state = listState ?: rememberLazyListState()
    // The title collapses on a breakpoint; the top bar material keeps tracking the scroll offset.
    val animatedCollapse = rememberGlasmioHeaderCollapse(state)
    val animatedEdge = rememberGlasmioHeaderScrollEdge(state)
    val collapseProgress = if (alwaysCompact) 1f else animatedCollapse
    val edgeProgress = animatedEdge
    val entry = LocalSampleHeaderEntry.current
    val currentCollapse = rememberUpdatedState(collapseProgress)
    val currentEdge = rememberUpdatedState(edgeProgress)
    val scroll = remember {
        GlasmioHeaderScrollState({ currentCollapse.value }, { currentEdge.value })
    }
    SideEffect { entry.scroll = scroll }
    val hostPadding = LocalSampleBodyPadding.current
    val bodyPadding = PaddingValues(
        start = IosPageHorizontalPadding, top = hostPadding.calculateTopPadding(),
        end = IosPageHorizontalPadding, bottom = bottomPadding + 24.dp,
    )
    GlasmioPageBody(
        contentPadding = bodyPadding,
        backgroundColor = backgroundColor,
    ) { padding ->
        if (wallpaper) SampleBackdrop(true)
        content(SamplePageLayout(title, padding, state, collapseProgress))
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
    compactProgress: State<Float>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(selected) {
        listOf(
            GlasmioTabItem("Home", "主页", if (selected == "Home") SfSymbol.HouseFilled else SfSymbol.House),
            GlasmioTabItem("MaterialTab", "材质", SfSymbol.Layers),
            GlasmioTabItem("SettingsTab", "设置", SfSymbol.Gearshape),
            GlasmioTabItem("About", "关于", SfSymbol.InfoCircle),
        )
    }
    BottomNavigationContact(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
            .offset { IntOffset(0, (8.dp * compactProgress.value).roundToPx()) },
    ) {
        GlasmioNavigationBar(
            items = items,
            selectedKey = selected,
            onSelected = onSelected,
            onExpand = onExpand,
            compactProgress = compactProgress,
            compactSize = 44.dp,
            backdrop = backdrop,
            modifier = Modifier.weight(1f).zIndex(1f),
        )
        Spacer(Modifier.size(width = 8.dp, height = 1.dp))
        GlasmioFloatingSearchButton(
            compactProgress = compactProgress,
            onClick = onSearch,
            contentDescription = "搜索",
            backdrop = backdrop,
        )
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
    "Search" to "magnifyingglass",
    "Menus" to "ellipsis",
    "Sheets" to "inset.filled.bottomthird.rectangle",
    "Text Styles and Dynamic Type" to "textformat.size",
    "Text Editing" to "character.cursor.ibeam",
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
                            Text(title, style = IosTypography.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    var favorite by rememberSaveable { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GlasmioButton(onClick = {}, modifier = Modifier.fillMaxWidth(), size = GlasmioButtonSize.Large) { Text("Glass") }
                GlasmioButton(onClick = {}, modifier = Modifier.fillMaxWidth(), emphasis = GlassEmphasis.Prominent, size = GlasmioButtonSize.Large) { Text("Glass Prominent") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GlasmioGroupedList {
                    GlasmioListRow("Separate Actions", showTopSeparator = false, trailing = { GlasmioSwitch(separated, { separated = it }) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlasmioToolbarActionGroup(style = if (separated) GlasmioToolbarActionStyle.Separated else GlasmioToolbarActionStyle.Merged) {
                        GlasmioIconButton(onClick = {}) { SfIcon("plus", "Add") }
                        GlasmioIconButton(onClick = { favorite = false }) { SfIcon("arrow.counterclockwise", "Reset") }
                        GlasmioIconButton(onClick = { favorite = !favorite }) {
                            SfIcon(if (favorite) "heart.fill" else "heart", if (favorite) "Unlike" else "Like")
                        }
                    }
                    GlasmioMenu(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = it },
                        itemCount = 3,
                        anchor = { open -> GlasmioIconButton(open) { SfIcon("ellipsis", "Example menu") } },
                    ) { backdrop, close ->
                        GlasmioMenuItem(if (separated) "Merge Actions" else "Separate Actions",
                            { separated = !separated; close() }, systemName = if (separated) "checkmark" else null, backdrop = backdrop)
                        GlasmioMenuItem(if (favorite) "Unlike" else "Like", { favorite = !favorite; close() },
                            systemName = if (favorite) "heart.fill" else "heart", backdrop = backdrop)
                        GlasmioMenuItem("Reset", { favorite = false; separated = false; close() },
                            systemName = "arrow.counterclockwise", backdrop = backdrop)
                    }
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
    var iconSegment by rememberSaveable { mutableIntStateOf(1) }
    var adaptivePosition by rememberSaveable { mutableFloatStateOf(.5f) }
    var adaptiveMode by rememberSaveable { mutableIntStateOf(4) }
    var groupedAdaptivePosition by rememberSaveable { mutableFloatStateOf(.4f) }
    var color by remember { mutableStateOf(Color(0xFF0088FF)) }
    var colorPicker by rememberSaveable { mutableStateOf(false) }
    DemoList(layout) {
        item {
            GlasmioGroupedList {
                GlasmioListRow("Enabled", showTopSeparator = false, trailing = { GlasmioSwitch(enabled, { enabled = it }) })
                GlasmioListRow("Switch", trailing = { GlasmioSwitch(checked, { checked = it }, enabled = enabled) })
                GlasmioListRow("Stepper", trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$step", style = IosTypography.body, color = LocalGlassColors.current.secondaryContent)
                        GlasmioStepper(step, { step = it }, range = 0..10, enabled = enabled)
                    }
                })
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
            Section("Volume Slider") {
                GlasmioVolumeSlider(value, { value = it }, Modifier.fillMaxWidth(), enabled = enabled)
            }
        }
        item {
            Section("Adaptive Slider") {
                GlasmioAdaptiveSlider(
                    value = adaptivePosition,
                    onValueChange = { adaptivePosition = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    showLabels = true,
                    labels = listOf("More Noise", "Default", "Less Noise"),
                )
            }
        }
        item {
            Section("Segmented Control") {
                GlasmioSegmentedControl(listOf(0 to "Day", 1 to "Week", 2 to "Month"), segment, { segment = it }, Modifier.fillMaxWidth())
            }
        }
        item {
            Section("Icon Segmented Control") {
                GlasmioSegmentedControl(
                    items = listOf(0 to "Muted", 1 to "Low", 2 to "Medium", 3 to "High"),
                    selected = iconSegment,
                    onSelected = { iconSegment = it },
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                    enabled = enabled,
                    itemContent = { choice, _ ->
                        SfIcon(when (choice) {
                            0 -> "speaker.slash.fill"
                            1 -> "speaker.wave.1.fill"
                            2 -> "speaker.wave.2.fill"
                            else -> "speaker.wave.3.fill"
                        }, null, size = 25.dp)
                    },
                )
            }
        }
        item {
            Section("Adaptive Segmented Control") {
                GlasmioSegmentedControl(
                    items = listOf(1 to "关闭", 3 to "通透模式", 4 to "自适应", 2 to "降噪"),
                    selected = adaptiveMode,
                    onSelected = { selected ->
                        if (selected == 4 && adaptiveMode != 4) groupedAdaptivePosition = .5f
                        adaptiveMode = selected
                    },
                    adaptiveItem = 4,
                    adaptiveValue = groupedAdaptivePosition,
                    onAdaptiveValueChange = { groupedAdaptivePosition = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    hiddenItems = if (adaptiveMode == 4) setOf(1) else emptySet(),
                    showLabels = true,
                    adaptiveLabels = listOf("较多噪声", "默认", "较少噪声"),
                    itemContent = { choice, _ ->
                        val colors = LocalGlassColors.current
                        val resource = when (choice) {
                            1 -> GlasmioIconsR.drawable.glasmio_listening_off
                            3 -> GlasmioIconsR.drawable.glasmio_listening_transparency
                            4 -> GlasmioIconsR.drawable.glasmio_adaptive_audio
                            else -> GlasmioIconsR.drawable.glasmio_listening_anc
                        }
                        val tint = when {
                            choice != 1 -> ColorFilter.tint(colors.content)
                            !colors.isDark -> null
                            else -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f,
                            )))
                        }
                        Image(painterResource(resource), null, Modifier.size(30.dp), colorFilter = tint)
                    },
                )
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
private fun ListDemo() {
    var reordered by remember { mutableStateOf(listOf("First", "Second", "Third")) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var entries by remember { mutableStateOf((1..24).toList()) }
    var refreshing by remember { mutableStateOf(false) }
    var month by rememberSaveable { mutableStateOf("June 2024") }
    var time by rememberSaveable { mutableStateOf("9:41 AM") }
    var swipeRevealed by remember { mutableStateOf(false) }
    var swipeItemVisible by rememberSaveable { mutableStateOf(true) }
    val colors = LocalGlassColors.current
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            scope.launch {
                try {
                    delay(400)
                    entries = entries.drop(1) + entries.first()
                    swipeRevealed = false
                    swipeItemVisible = true
                } finally {
                    refreshing = false
                }
            }
        }
    }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val refreshState = rememberPullToRefreshState()
    val collapseProgress = if (grid) rememberGlasmioHeaderCollapse(gridState) else rememberGlasmioHeaderCollapse(listState)
    val edgeProgress = if (grid) rememberGlasmioHeaderScrollEdge(gridState) else rememberGlasmioHeaderScrollEdge(listState)
    val headerEntry = LocalSampleHeaderEntry.current
    val currentCollapse = rememberUpdatedState(collapseProgress)
    val currentEdge = rememberUpdatedState(edgeProgress)
    val headerScroll = remember { GlasmioHeaderScrollState({ currentCollapse.value }, { currentEdge.value }) }
    val currentRefresh = rememberUpdatedState(refresh)
    val headerActions = remember(grid, refreshing) {
        val layoutSymbol = if (grid) "list.bullet" else "rectangle.grid.2x2"
        listOf(
            GlasmioHeaderAction("list.refresh", "arrow.clockwise", "Refresh", { currentRefresh.value() }, enabled = !refreshing) { SfIcon("arrow.clockwise", null) },
            GlasmioHeaderAction("list.layout", if (grid) "list.bullet" else "rectangle.grid.2x2", if (grid) "Show list" else "Show grid", { grid = !grid }) {
                SfIcon(layoutSymbol, null)
            },
        )
    }
    SideEffect { headerEntry.scroll = headerScroll; headerEntry.actions = headerActions }
    val headerPadding = LocalSampleBodyPadding.current
    GlasmioPageBody(
        PaddingValues(start = IosPageHorizontalPadding, top = headerPadding.calculateTopPadding(), end = IosPageHorizontalPadding,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
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
                            GlasmioSectionTitle("Grouped")
                            GlasmioGroupedList {
                                GlasmioListRow("Simple row", showTopSeparator = false)
                                GlasmioListRow(
                                    "Title",
                                    subtitle = "Subtitle",
                                    detail = "Detail",
                                    leading = {
                                        Box(
                                            Modifier.size(30.dp).clip(ContinuousRoundedRectangle(7.dp)).background(colors.accent),
                                            contentAlignment = Alignment.Center,
                                        ) { SfIcon("house.fill", null, size = 17.dp, tint = Color.White) }
                                    },
                                    trailing = {
                                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                            SfIcon("star", null, size = 17.dp, tint = colors.accent)
                                        }
                                    },
                                    showDisclosure = true,
                                )
                                GlasmioListRow("Value", detail = "Detail")
                                GlasmioListRow("Selection", trailing = {
                                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                        SfIcon("checkmark", null, size = 17.dp, tint = colors.accent)
                                    }
                                })
                                GlasmioListRow("Information", trailing = {
                                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                        SfIcon("info.circle", "Information", size = 17.dp, tint = colors.accent)
                                    }
                                })
                                GlasmioListRow("Date and Time", trailing = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        GlasmioListValuePill(month, { month = if (month == "June 2024") "July 2024" else "June 2024" })
                                        GlasmioListValuePill(time, { time = if (time == "9:41 AM") "10:41 AM" else "9:41 AM" })
                                    }
                                })
                                GlasmioListRow("A long row title stays inside its layout and truncates cleanly", onClick = {})
                            }
                            GlasmioGroupedTableFooter("Footer")
                        }
                        item(key = "flat-actions") {
                            GlasmioGroupedList(framed = false) {
                                GlasmioListRow("Action", onClick = {}, showTopSeparator = false, showDisclosure = false,
                                    titleColor = colors.accent, titleAlignment = TextAlign.Center)
                                GlasmioListRow("Delete", onClick = {}, showDisclosure = false,
                                    titleColor = colors.destructive, titleAlignment = TextAlign.Center)
                                GlasmioListRow("Unavailable", onClick = {}, enabled = false, showDisclosure = false)
                            }
                        }
                        if (swipeItemVisible) {
                            item(key = "swipe-actions") {
                                GlasmioListSwipeActions(
                                    revealed = swipeRevealed,
                                    onRevealedChange = { swipeRevealed = it },
                                    actions = {
                                        GlasmioListSwipeAction(
                                            "circle.dashed", { swipeRevealed = false },
                                            if (colors.isDark) Color(0xFF636366) else Color(0xFFAEAEB2),
                                            label = "Action",
                                        )
                                        GlasmioListSwipeAction(
                                            "circle.dashed", { swipeRevealed = false },
                                            if (colors.isDark) Color(0xFF0091FF) else Color(0xFF0088FF),
                                            label = "Action",
                                        )
                                        GlasmioListSwipeAction(
                                            "circle.dashed", { swipeRevealed = false; swipeItemVisible = false },
                                            if (colors.isDark) Color(0xFF30D158) else Color(0xFF34C759),
                                            label = "Action",
                                        )
                                    },
                                ) {
                                    GlasmioListRow(
                                        "Swipe Actions", subtitle = "Subtitle", detail = "Detail",
                                        showTopSeparator = false, showDisclosure = true,
                                    )
                                }
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
    name: String,
    onNameChange: (String) -> Unit,
    choice: String,
    onChoiceChange: (String) -> Unit,
) {
    DemoList(layout) {
        item {
            // Keep a full viewport below the independently scrolling large title.
            // Short preference groups must still let the title leave the viewport.
            Column(Modifier.fillParentMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Section("Appearance") {
                    GlasmioGroupedList {
                        GlasmioSwitchPreference(title = { Text("Dark Appearance") }, checked = dark, onCheckedChange = onDark, showTopSeparator = false)
                        GlasmioSwitchPreference(title = { Text("Reduce Transparency") }, checked = reduceTransparency, onCheckedChange = onReduceTransparency, showTopSeparator = true)
                        GlasmioSwitchPreference(title = { Text("Right to Left") }, checked = rtl, onCheckedChange = onRtl, showTopSeparator = true)
                    }
                }
                Section("Preferences") {
                    GlasmioGroupedList {
                        GlasmioEditTextPreference(title = { Text("Name") }, value = name, onValueChange = onNameChange, showTopSeparator = false)
                        GlasmioListPreference(title = { Text("Appearance") }, selectedValue = choice, values = listOf("System", "Light", "Dark"), valueText = { it }, onValueSelected = onChoiceChange, showTopSeparator = true)
                        GlasmioSwitchPreference(title = { Text("Disabled Preference") }, checked = true, onCheckedChange = {}, isEnabled = false, showTopSeparator = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchLanding(layout: SamplePageLayout, onOpen: (String) -> Unit) {
    DemoList(layout) {
        item {
            Column(Modifier.fillParentMaxHeight()) {
                GlasmioGroupedList {
                    catalog.filter { it.first != "Search" }.forEachIndexed { index, (title, symbol) ->
                        GlasmioListRow(title, systemName = symbol, showTopSeparator = index > 0, onClick = { onOpen(title) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(query: String, padding: PaddingValues, onOpen: (String) -> Unit) {
    val matches = catalog.filter { it.first != "Search" && it.first.contains(query, ignoreCase = true) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
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
private fun MenusDemo(bottomPadding: Dp, dark: Boolean, onDark: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selection by rememberSaveable { mutableStateOf("Default") }
    var alert by rememberSaveable { mutableStateOf(false) }
    var inputAlert by rememberSaveable { mutableStateOf(false) }
    var nestedMenu by remember { mutableStateOf(false) }
    val nestedAnchor = rememberGlasmioMenuAnchorState()
    val toolbarAnchor = rememberGlasmioMenuAnchorState()
    val headerEntry = LocalSampleHeaderEntry.current
    val headerActions = remember(dark, toolbarAnchor) {
        listOf(
            GlasmioHeaderAction("appearance.toggle", if (dark) "sun.max" else "moon", if (dark) "Light appearance" else "Dark appearance", { onDark(!dark) }) {
                SfIcon(if (dark) "sun.max" else "moon", null)
            },
            GlasmioHeaderAction("menu.options", "ellipsis", "Menu", { expanded = true }, menuAnchor = toolbarAnchor) { SfIcon("ellipsis", null) },
        )
    }
    SideEffect { headerEntry.actions = headerActions }
    var name by remember { mutableStateOf(TextFieldValue("")) }
    SamplePinnedPage(
        title = "Menus",
        bottomPadding = bottomPadding,
        wallpaper = false,
    ) { layout ->
        DemoList(layout) {
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
    }
    GlasmioMenu(
        expanded = expanded, onExpandedChange = { expanded = it }, itemCount = 4,
        menuAnchorState = toolbarAnchor, externalAnchorBounds = toolbarAnchor.bounds,
        anchor = {},
    ) { backdrop, close ->
        GlasmioMenuItem("Favorite", { selection = "Favorite"; close() }, systemName = "heart", backdrop = backdrop)
        GlasmioMenuItem("Duplicate", { selection = "Duplicate"; close() }, systemName = "plus.square.on.square", backdrop = backdrop)
        GlasmioMenuItem("Unavailable", {}, enabled = false, backdrop = backdrop)
        GlasmioMenuItem("Delete", { selection = "Delete"; close() }, destructive = true, systemName = "trash", backdrop = backdrop)
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
    GlasmioAlertDialog(
        visible = alert,
        onDismissRequest = { alert = false },
        title = "Save Changes?",
        message = "Your changes will appear in this sample.",
        buttons = listOf(
            GlasmioAlertButtonSpec("Cancel", { alert = false }, GlasmioAlertButtonRole.Cancel),
            GlasmioAlertButtonSpec("Save", { alert = false }),
        ),
    )
    GlasmioAlertDialog(
        visible = inputAlert,
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

@Composable
private fun SheetsDemo(layout: SamplePageLayout) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    var actionSheet by rememberSaveable { mutableStateOf(false) }
    var checked by rememberSaveable { mutableStateOf(true) }
    val sheetListState = rememberLazyListState()
    val sheetEdgeProgress = rememberGlasmioHeaderScrollEdge(sheetListState)
    DemoList(layout) {
        item {
            GlasmioGroupedList {
                GlasmioListRow("Sheet", showTopSeparator = false, onClick = { sheet = true })
                GlasmioListRow("Action Sheet", onClick = { actionSheet = true })
            }
        }
    }
    GlasmioBottomSheet(
        visible = sheet,
        skipPartiallyExpanded = false,
        onDismissRequest = { sheet = false },
        topBarBlurProgress = sheetEdgeProgress,
        topBar = {
            GlasmioSheetTopToolbar("Sheet", actions = {
                GlasmioSheetToolbarButton(onClick = { sheet = false }) { SfIcon("xmark", "Close") }
            })
        },
    ) {
        val sheetPadding = LocalGlasmioSheetContentPadding.current
        Column(Modifier.fillMaxWidth().weight(1f).background(LocalGlassColors.current.groupedBackground)) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = sheetListState, contentPadding = PaddingValues(
            start = 16.dp, top = sheetPadding.calculateTopPadding() + 8.dp, end = 16.dp, bottom = 8.dp,
        )) {
            item {
                GlasmioGroupedList {
                    GlasmioListRow("Notifications", showTopSeparator = false, trailing = { GlasmioSwitch(checked, { checked = it }) })
                    GlasmioListRow("Privacy", systemName = "lock", onClick = {})
                    GlasmioListRow("Appearance", systemName = "circle.lefthalf.filled", onClick = {})
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
            item {
                GlasmioGroupedList {
                    repeat(20) { index ->
                        GlasmioListRow("Item ${index + 1}", subtitle = if (index % 5 == 0) "Subtitle" else null,
                            showTopSeparator = index > 0, onClick = {})
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = sheetPadding.calculateBottomPadding()).padding(16.dp)) {
            GlasmioButton(onClick = { sheet = false }, modifier = Modifier.fillMaxWidth(), emphasis = GlassEmphasis.Prominent, size = GlasmioButtonSize.Large) { Text("Done") }
        }
        }
    }
    GlasmioBottomSheet(visible = actionSheet, fitContent = true, onDismissRequest = { actionSheet = false }) {
        GlasmioActionSheetContent("Actions", message = "Choose an action") {
            GlasmioListRow("Share", systemName = "square.and.arrow.up", showTopSeparator = false, onClick = { actionSheet = false })
            GlasmioListRow("Duplicate", systemName = "plus.square.on.square", onClick = { actionSheet = false })
            GlasmioListRow("Cancel", onClick = { actionSheet = false })
        }
    }
}
