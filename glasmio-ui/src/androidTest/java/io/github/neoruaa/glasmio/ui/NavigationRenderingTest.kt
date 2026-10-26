package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.icons.SfSymbol
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

}
