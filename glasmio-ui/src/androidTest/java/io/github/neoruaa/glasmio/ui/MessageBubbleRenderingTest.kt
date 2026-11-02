package io.github.neoruaa.glasmio.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class MessageBubbleRenderingTest {

    @Test fun everyNativeTapbackDecodesAsAnAnimatedImageAtItsOriginalResolution() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        for (artwork in GlasmioMessageReactionArtwork.entries) {
            val decoded = ImageDecoder.decodeDrawable(ImageDecoder.createSource(resources, tapbackArtworkResource(artwork)))
            assertTrue("$artwork remains animated after packaging", decoded is AnimatedImageDrawable)
            assertEquals(132, decoded.intrinsicWidth)
            assertEquals(132, decoded.intrinsicHeight)
            (decoded as AnimatedImageDrawable).repeatCount = 0
            assertEquals(0, decoded.repeatCount)
            decoded.stop()
        }
    }

    @Test fun contextStackReservesSafeEdgesForTopBottomAndOversizedMessages() {
        for (viewport in listOf(Rect(0f, 32f, 375f, 780f), Rect(0f, 32f, 320f, 380f), Rect(0f, 32f, 320f, 198f))) {
            for (source in listOf(Rect(220f, 35f, 310f, 84f), Rect(15f, 700f, 295f, 749f), Rect(20f, 10f, 300f, 1010f))) {
                for (right in listOf(false, true)) {
                    val result = placeMessageContext(source, viewport,
                        List(7) { GlasmioCascadingMenuItem("Action $it") }, true, right, 1f)
                    assertTrue(result.preview.top >= viewport.top)
                    assertTrue(result.preview.left >= viewport.left)
                    assertTrue(result.preview.right <= viewport.right)
                    assertTrue(result.menu.bottom <= viewport.bottom)
                    assertTrue(result.menu.top > result.preview.bottom)
                    assertTrue(result.reactions!!.top >= viewport.top)
                    assertTrue(result.reactions.bottom < result.menu.top)
                }
            }
        }
    }

    @Test fun directionMirrorsTheWholeOutlineWithoutStretchingLongBubbleCorners() {
        for (density in listOf(1f, 2.75f, 3.25f)) {
            for (tail in listOf(false, true)) {
                for (height in listOf(36, 220)) {
                    val incoming = raster(false, tail, 200, height, density, LayoutDirection.Ltr)
                    val outgoing = raster(true, tail, 200, height, density, LayoutDirection.Ltr)
                    val rtl = raster(true, tail, 200, height, density, LayoutDirection.Rtl)
                    for (y in 0 until incoming.height) for (x in 0 until incoming.width) {
                        assertEquals("RTL $density/$tail/$height at $x,$y", incoming.getPixel(x, y), rtl.getPixel(x, y))
                        val mirroredX = outgoing.width - 1 - x
                        if (incoming.getPixel(x, y) != outgoing.getPixel(mirroredX, y)) {
                            // Skia's directional scan conversion can break ties differently
                            // at a pixel-center boundary after Float coordinate conversion.
                            assertTrue("Mirror boundary $density/$tail/$height at $x,$y",
                                isBoundary(incoming, x, y) && isBoundary(outgoing, mirroredX, y))
                        }
                    }
                }
                val short = raster(true, tail, 200, 80, density, LayoutDirection.Ltr)
                val tall = raster(true, tail, 200, 220, density, LayoutDirection.Ltr)
                for (y in 0 until (16 * density).toInt()) for (x in 0 until short.width) {
                    assertEquals("The top curvature stays fixed as content grows", short.getPixel(x, y), tall.getPixel(x, y))
                }
                assertTrue(short.getPixel(short.width / 2, short.height / 2) != 0)
                if (tail) assertEquals("Round tails reserve a native bottom inset", 0, short.getPixel(short.width / 2, short.height - 2))
            }
        }
    }

    private fun raster(outgoing: Boolean, tail: Boolean, width: Int, height: Int, density: Float, direction: LayoutDirection): Bitmap {
        val pixels = Size((width * density).roundToInt().toFloat(), (height * density).roundToInt().toFloat())
        val outline = MessageBubbleShape(outgoing, tail).createOutline(pixels, direction, Density(density)) as Outline.Generic
        return Bitmap.createBitmap(pixels.width.toInt(), pixels.height.toInt(), Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawPath(outline.path.asAndroidPath(), Paint().apply {
                color = android.graphics.Color.BLUE
                isAntiAlias = false
            })
        }
    }

    private fun isBoundary(bitmap: Bitmap, x: Int, y: Int): Boolean {
        var filled = false
        var empty = false
        for (dy in -1..1) for (dx in -1..1) {
            val px = x + dx
            val py = y + dy
            val inside = px in 0 until bitmap.width && py in 0 until bitmap.height && bitmap.getPixel(px, py) != 0
            filled = filled || inside
            empty = empty || !inside
        }
        return filled && empty
    }
}
