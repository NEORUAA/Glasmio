package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.emptyBackdrop
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.GlasmioTheme
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.glass.LiquidMenuMorphMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Renderer parity cases migrated with navigation so private implementation stays private. */
@RunWith(AndroidJUnit4::class)
class NavigationRenderingTest {
    @get:Rule val compose = createComposeRule()
    private val library = GlasmioTabItem("library", "Library", SfSymbol.MusicNoteList)

    @Test
    fun opaqueLensSourceTintPreservesSelectedTabPixelsWithoutInnerFilters() {
        val progress = mutableFloatStateOf(0f)
        val press = mutableFloatStateOf(0f)
        val paletteIndex = mutableStateOf(0)
        val palettes = listOf(
            defaultGlassColors(false).copy(accent = Color(0xFFFF3B30), content = Color(0xFF263648)),
            defaultGlassColors(true).copy(accent = Color(0xFF51B6FF), content = Color.White),
            defaultGlassColors(false).copy(accent = Color(0xFFFF3B30).copy(alpha = 0.6f)),
            defaultGlassColors(false).copy(content = Color(0xFF263648).copy(alpha = 0.55f)),
        )
        val backing = Color(0xFF2E233B)
        compose.setContent {
            val colors = palettes[paletteIndex.value]
            val selectedFilter = remember(colors) {
                derivedStateOf {
                    val compactTint = androidx.compose.ui.graphics.lerp(colors.accent, colors.content, progress.floatValue)
                    ColorFilter.tint(androidx.compose.ui.graphics.lerp(compactTint, colors.content, press.floatValue))
                }
            }
            val optimizedFilter = navigationLensSourceColorFilter(colors, selectedFilter)
            val opaque = colors.accent.alpha == 1f && colors.content.alpha == 1f
            assertTrue("Only fully opaque source colors can skip the inner filter",
                if (opaque) optimizedFilter == null else optimizedFilter === selectedFilter)
            val sourceScale = remember {
                { lerp(1f, 1.2f, press.floatValue) * (1f - progress.floatValue).coerceAtLeast(0.82f) }
            }
            val labelAlpha = remember { { (1f - progress.floatValue * 1.35f).coerceIn(0f, 1f) } }
            val lateScale = remember {
                { lerp(1f, (22f / 28f) / 0.82f, compactIconLateProgress(progress.floatValue)) }
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalGlassColors provides colors, LocalLiquidTabScale provides sourceScale) {
                MaterialTheme {
                    Row {
                        listOf(false, true).forEachIndexed { index, optimized ->
                            if (index > 0) Spacer(Modifier.width(16.dp))
                            Box(
                                Modifier.size(160.dp, 80.dp).background(backing)
                                    .testTag(if (optimized) "optimizedLensSourceTab" else "referenceLensSourceTab"),
                                contentAlignment = Alignment.Center,
                            ) {
                                val width = lerp(160f, 48f, progress.floatValue).dp - 8.dp
                                Box(
                                    Modifier.width(width).height(56.dp).graphicsLayer {
                                        colorFilter = ColorFilter.tint(colors.accent)
                                    },
                                ) {
                                    MorphingSelectedNavigationItem(
                                        item = library,
                                        compactProgress = progress,
                                        fullWidthPx = with(density) { 160.dp.toPx() },
                                        compactWidthPx = with(density) { 48.dp.toPx() },
                                        itemCount = 3,
                                        selectedIndex = 1,
                                        isLtr = true,
                                        horizontalInsetPx = with(density) { 4.dp.toPx() },
                                        selectedContentLateScale = lateScale,
                                        labelAlpha = labelAlpha,
                                        selectedIconColorFilter = if (optimized) optimizedFilter else selectedFilter,
                                        contentColor = colors.content,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        palettes.indices.forEach { palette ->
            listOf(0f, 0.5f, 0.99f).forEach { compact ->
                listOf(0f, 0.5f, 0.99f).forEach { pressed ->
                    compose.runOnIdle {
                        paletteIndex.value = palette
                        progress.floatValue = compact
                        press.floatValue = pressed
                    }
                    compose.waitForIdle()
                    val reference = compose.onNodeWithTag("referenceLensSourceTab").captureToImage().toPixelMap()
                    val actual = compose.onNodeWithTag("optimizedLensSourceTab").captureToImage().toPixelMap()
                    assertEquals(reference.width, actual.width)
                    assertEquals(reference.height, actual.height)
                    var maximum = 0
                    var total = 0L
                    var count = 0
                    var contentPixels = 0
                    var labelPixels = 0
                    var maximumPoint = Offset.Zero
                    var maximumExpected = Color.Transparent
                    var maximumObserved = Color.Transparent
                    fun compare(expected: Float, observed: Float, x: Int, y: Int) {
                        val error = abs((expected * 255f).roundToInt() - (observed * 255f).roundToInt())
                        if (error > maximum) {
                            maximum = error
                            maximumPoint = Offset(x.toFloat(), y.toFloat())
                            maximumExpected = reference[x, y]
                            maximumObserved = actual[x, y]
                        }
                        total += error
                        count++
                    }
                    repeat(reference.height) { y ->
                        repeat(reference.width) { x ->
                            val expected = reference[x, y]
                            val observed = actual[x, y]
                            compare(expected.red, observed.red, x, y)
                            compare(expected.green, observed.green, x, y)
                            compare(expected.blue, observed.blue, x, y)
                            compare(expected.alpha, observed.alpha, x, y)
                            val contentDifference = maxOf(
                                abs(expected.red - backing.red), abs(expected.green - backing.green),
                                abs(expected.blue - backing.blue),
                            )
                            if (contentDifference > 8f / 255f) {
                                contentPixels++
                                if (y > reference.height * 0.6f) labelPixels++
                            }
                        }
                    }
                    val label = "Lens source tab: palette=$palette, compact=$compact, press=$pressed"
                    assertTrue("$label must contain the real selected icon", contentPixels > 20)
                    if (compact == 0f) assertTrue("$label must contain the visible label", labelPixels > 10)
                    assertTrue(
                        "$label maximum RGBA error: $maximum at $maximumPoint, " +
                            "expected=$maximumExpected, actual=$maximumObserved",
                        maximum <= 2,
                    )
                    assertTrue("$label mean RGBA error: ${total.toDouble() / count}", total.toDouble() / count <= 1.0)
                }
            }
        }
    }

    @Test
    fun visibleTabSemanticsFollowSelectionAndCompactState() {
        val selectedKey = mutableStateOf("home")
        val compact = mutableFloatStateOf(0f)
        compose.setContent {
            GlasmioTheme(appearance = GlassAppearance(reduceTransparency = true)) {
                GlasmioNavigationBar(
                    items = listOf(
                        GlasmioTabItem("home", "Home", SfSymbol.House),
                        library,
                        GlasmioTabItem("favorites", "Favorites", SfSymbol.Heart),
                    ),
                    selectedKey = selectedKey.value,
                    onSelected = { selectedKey.value = it },
                    onExpand = { compact.floatValue = 0f },
                    compactProgress = compact,
                    compactSize = 48.dp,
                    backdrop = emptyBackdrop(),
                )
            }
        }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onAllNodes(tab).assertCountEquals(3)
        compose.onNode(tab and hasContentDescription("Home")).assertIsSelected()
        compose.onNode(tab and hasContentDescription("Library")).assertIsNotSelected().performClick()
        compose.onNode(tab and hasContentDescription("Library")).assertIsSelected()
        compose.onNode(tab and hasContentDescription("Home")).assertIsNotSelected()
        compose.runOnIdle { compact.floatValue = 1f }
        compose.onAllNodes(tab).assertCountEquals(1)
        compose.onNode(tab and hasContentDescription("Library")).assertIsSelected()
    }

    @Test
    fun plainMenuClosingOpacityPreservesShadowBeyondEveryLogicalEdge() {
        val motion = LiquidMenuMorphMotion()
        val glassSourceReference = mutableStateOf(false)
        var hostScreenOrigin = Offset.Zero
        var targetScreenBounds: Rect? = null
        var pixelDensity = 1f
        compose.setContent {
            pixelDensity = LocalDensity.current.density
            GlasmioTheme(isDark = false) {
                Box(
                    Modifier.size(330.dp).background(Color.White)
                        .testTag("closingMenuShadowHost")
                        .onGloballyPositioned { hostScreenOrigin = it.localToScreen(Offset.Zero) },
                    contentAlignment = Alignment.Center,
                ) {
                    GlasmioContextMenu(
                        visible = true,
                        compact = true,
                        menuWidth = 160.dp,
                        heightOverride = 100.dp,
                        backdrop = emptyBackdrop(),
                        exportChildBackdrop = false,
                        morphMotion = motion,
                        morphSourceHasGlassBackground = glassSourceReference.value,
                        onMenuBoundsChanged = { targetScreenBounds = it },
                    ) { }
                }
            }
        }
        compose.waitForIdle()
        lateinit var logicalMenuBounds: Rect
        compose.runOnIdle {
            val target = requireNotNull(targetScreenBounds)
            logicalMenuBounds = target.translate(-hostScreenOrigin)
            val sourceSize = Size(84f * pixelDensity, 44f * pixelDensity)
            val source = Rect(target.center - Offset(sourceSize.width, sourceSize.height) / 2f, sourceSize)
            motion.initialize(source, target, 34f * pixelDensity, pixelDensity)
            motion.retarget(true)
            motion.advanceBy(3f)
            motion.retarget(false)
            motion.advanceBy(0.06f)
            assertTrue("The fixed frame must exercise fractional closing material opacity",
                motion.frame.contentProgress in 0.05f..0.99f)
            val sourceLobe = requireNotNull(motion.frame.sourceLobe)
            assertTrue("The reference source must remain inside the menu's incoming skin",
                motion.frame.bounds.contains(sourceLobe.topLeft) && motion.frame.bounds.contains(sourceLobe.bottomRight))
        }
        compose.waitForIdle()
        val actual = compose.onNodeWithTag("closingMenuShadowHost").captureToImage().toPixelMap()
        // A fully enclosed glass source uses the same shadow geometry, with opacity
        // applied directly to its native shadow node instead of the plain material group.
        compose.runOnIdle { glassSourceReference.value = true }
        compose.waitForIdle()
        val reference = compose.onNodeWithTag("closingMenuShadowHost").captureToImage().toPixelMap()
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        val gap = 6f * pixelDensity
        val band = 8f * pixelDensity
        val span = 12f * pixelDensity
        val menu = logicalMenuBounds
        val strips = linkedMapOf(
            "top" to Rect(menu.center.x - span, menu.top - gap - band, menu.center.x + span, menu.top - gap),
            "right" to Rect(menu.right + gap, menu.center.y - span, menu.right + gap + band, menu.center.y + span),
            "bottom" to Rect(menu.center.x - span, menu.bottom + gap, menu.center.x + span, menu.bottom + gap + band),
            "left" to Rect(menu.left - gap - band, menu.center.y - span, menu.left - gap, menu.center.y + span),
        )
        strips.forEach { (edge, strip) ->
            val left = strip.left.roundToInt()
            val top = strip.top.roundToInt()
            val right = strip.right.roundToInt()
            val bottom = strip.bottom.roundToInt()
            assertTrue("The $edge shadow sample must fit outside the logical menu in the capture host",
                left >= 0 && top >= 0 && right <= actual.width && bottom <= actual.height && right > left && bottom > top)
            var actualShade = 0.0
            var referenceShade = 0.0
            var totalError = 0.0
            var maximumError = 0f
            for (y in top until bottom) for (x in left until right) {
                val expected = reference[x, y]
                val observed = actual[x, y]
                actualShade += (3f - observed.red - observed.green - observed.blue) / 3f
                referenceShade += (3f - expected.red - expected.green - expected.blue) / 3f
                val error = maxOf(abs(expected.red - observed.red), abs(expected.green - observed.green), abs(expected.blue - observed.blue))
                totalError += error
                maximumError = maxOf(maximumError, error)
            }
            val pixels = (right - left) * (bottom - top)
            assertTrue("The native reference must contain a visible $edge shadow outside the logical menu",
                referenceShade / pixels > 2.0 / 255.0)
            assertTrue("Plain material opacity must retain the $edge shadow outside its menu-sized RenderNode",
                actualShade / pixels > 2.0 / 255.0)
            assertTrue("The $edge shadow must match native post-blur opacity: max=$maximumError, mean=${totalError / pixels}",
                maximumError <= 3f / 255f && totalError / pixels <= 1.5 / 255.0)
        }
    }

    @Test
    fun popupShadowShellPreservesAnchorEdgesAboveAndBelow() {
        val expanded = mutableStateOf(false)
        val anchorAtBottom = mutableStateOf(false)
        val anchorBounds = mutableStateOf<Rect?>(null)
        val menuBounds = mutableStateOf<Rect?>(null)
        val closedCount = mutableStateOf(0)
        var pixelDensity = 1f
        compose.setContent {
            pixelDensity = LocalDensity.current.density
            GlasmioTheme(isDark = false) {
                Box(Modifier.fillMaxSize().padding(100.dp)) {
                    GlasmioMenu(
                        expanded = expanded.value,
                        onExpandedChange = { expanded.value = it },
                        itemCount = 3,
                        modifier = Modifier.align(if (anchorAtBottom.value) Alignment.BottomEnd else Alignment.TopEnd),
                        menuWidth = 238.dp,
                        backdrop = emptyBackdrop(),
                        exportChildBackdrop = false,
                        onMenuBoundsChanged = { menuBounds.value = it },
                        onClosed = { closedCount.value++ },
                        anchor = {
                            Box(Modifier.size(80.dp, 44.dp).onGloballyPositioned {
                                anchorBounds.value = Rect(it.localToScreen(Offset.Zero), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                            })
                        },
                    ) { childBackdrop, close ->
                        repeat(3) { index ->
                            GlasmioMenuItem("Item ${index + 1}", close, backdrop = childBackdrop)
                        }
                    }
                }
            }
        }
        listOf(false, true).forEachIndexed { cycle, above ->
            compose.runOnIdle {
                assertEquals("The previous Popup must finish closing before moving its anchor", cycle, closedCount.value)
                anchorAtBottom.value = above
                menuBounds.value = null
            }
            compose.waitForIdle()
            compose.runOnIdle { expanded.value = true }
            compose.waitUntil(timeoutMillis = 10_000) { menuBounds.value != null }
            compose.waitForIdle()
            compose.runOnIdle {
                val anchor = requireNotNull(anchorBounds.value)
                val menu = requireNotNull(menuBounds.value)
                assertTrue("The test anchor must leave the requested menu clear of horizontal clamping",
                    anchor.right - 238f * pixelDensity >= 12f * pixelDensity)
                assertEquals("The real Popup must retain its requested menu width", 238f * pixelDensity, menu.width, 1f)
                assertEquals("The real Popup must retain its requested menu height", 152f * pixelDensity, menu.height, 1f)
                // Popup positions use integer pixels; screen-coordinate callbacks use floats.
                assertEquals("The shadow shell must preserve right alignment", anchor.right, menu.right, 1f)
                if (above) {
                    assertEquals("An upward Popup must end at its source's bottom edge", anchor.bottom, menu.bottom, 1f)
                } else {
                    assertEquals("A downward Popup must begin at its source's top edge", anchor.top, menu.top, 1f)
                }
                expanded.value = false
            }
            compose.waitUntil(timeoutMillis = 10_000) { closedCount.value == cycle + 1 }
            compose.waitForIdle()
        }
    }

}
