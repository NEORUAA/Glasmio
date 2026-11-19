package io.github.neoruaa.glasmio.glass

import android.os.Build
import android.util.LruCache
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Gives a static outline with a single closed contour the same optics as regular navigation glass.
 * [bodyInset] excludes reserved space around the main body from material size calibration.
 */
class NavigationGlassOutlineShape(
    private val outline: Shape,
    private val bodyInset: Dp = 0.dp,
) : Shape by outline {
    private data class Geometry(
        val size: Size,
        val layoutDirection: LayoutDirection,
        val density: Float,
        val fontScale: Float,
    )

    private data class FieldKey(val outline: Shape, val geometry: Geometry, val band: Float)
    private data class RetainedField(val field: NativeGlassFilterField, val textureBytes: Int)

    private var geometry: Geometry? = null
    private var builtBand = 0f
    private var field: NativeGlassFilterField? = null

    internal fun bodyDimension(size: Size, density: Density): Float = with(density) {
        (size.minDimension - 2f * bodyInset.roundToPx()).coerceAtLeast(0f) / this.density
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    internal fun nativeField(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
        minimumBand: Float,
    ): NativeGlassFilterField? {
        if (!size.width.isFinite() || !size.height.isFinite() || size.minDimension <= 0f) return null
        val next = Geometry(size, layoutDirection, density.density, density.fontScale)
        if (next == geometry && minimumBand <= builtBand) return field
        val key = FieldKey(outline, next, minimumBand)
        field = recentFields.get(key)?.field ?: run {
            val path = when (val result = createOutline(size, layoutDirection, density)) {
                is Outline.Generic -> result.path
                is Outline.Rectangle -> Path().apply { addRect(result.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(result.roundRect) }
            }
            val contour = NavigationContactContour(path.asAndroidPath().approximate(0.125f))
            if (contour.edges.isEmpty()) return null
            val storage = NavigationContactField()
            val grid = storage.grid(contour, minimumBand)
            val result = navigationContourGlassField(grid, storage.input(grid))
            val bytes = requireNotNull(grid.bitmap).byteCount
            // Only the immutable shader input survives. The rasterizer's staging buffers do not.
            if (bytes <= recentFields.maxSize()) recentFields.put(key, RetainedField(result, bytes))
            result
        }
        geometry = next
        builtBand = minimumBand
        return field
    }

    private companion object {
        // Keep repeat openings warm without retaining a texture for every dismissed anchor.
        val recentFields = object : LruCache<FieldKey, RetainedField>(4 * 1024 * 1024) {
            override fun sizeOf(key: FieldKey, value: RetainedField): Int = value.textureBytes
        }
    }
}
