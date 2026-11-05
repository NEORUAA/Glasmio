package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import kotlin.math.min

/**
 * UIKit's sidebar-to-top-tabs toggle, drawn independently of the host symbol font.
 * The native private symbol `rectangle.line.horizontal.inset.top` aliases to
 * `inset.line.horizontal.top.rectangle`. Its bar-item configuration is preferred Body,
 * Medium weight and Large symbol scale. At the default 17-point Body size its actual ink
 * measures 25.257129 by 19.782044 points, rather than occupying a square font-glyph box.
 * Native references and CoreGlyphs path extraction: docs/ios-adaptive-navigation.md and
 * .local-research/ios/adaptive-sidebar/appearance/icon/README.md.
 */
@Composable
fun GlasmioSidebarToggleIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalGlassColors.current.content,
) {
    val pointSize = IosTypography.body.fontSize.takeUnless { it.isUnspecified } ?: 17.sp
    val bodyScale = with(LocalDensity.current) { pointSize.toDp().value / 17f }
    Canvas(modifier.size((SidebarToggleWidth * bodyScale).dp, (SidebarToggleHeight * bodyScale).dp)) {
        val fit = min(size.width / SidebarToggleWidth, size.height / SidebarToggleHeight)
        withTransform({
            translate(
                (size.width - SidebarToggleWidth * fit) / 2f - SidebarToggleLeft * fit,
                (size.height - SidebarToggleHeight * fit) / 2f - SidebarToggleTop * fit,
            )
            scale(fit, fit, Offset.Zero)
        }) {
            drawPath(SidebarTogglePath, tint)
        }
    }
}

private const val SidebarToggleWidth = 25.257129f
private const val SidebarToggleHeight = 19.782044f
private const val SidebarToggleLeft = -0.000006588f
private const val SidebarToggleTop = -0.000022825093f

// Exact filled contour from the local iOS 27 CoreGlyphsPrivate vector, not a traced screenshot.
private val SidebarTogglePath = PathParser().parsePathString(
    """
        M 3.38275954 19.7820212
        L 21.8743564 19.7820212
        C 24.104915 19.7820212 25.2571225 18.6216013 25.2571225 16.4266951
        L 25.2571225 3.35940946
        C 25.2571225 1.16039706 24.104915 -2.28250929e-05 21.8743564 -2.28250929e-05
        L 3.38275954 -2.28250929e-05
        C 1.15997886 -2.28250929e-05 -6.588e-06 1.15218468 -6.588e-06 3.35940946
        L -6.588e-06 16.4266951
        C -6.588e-06 18.6298137 1.15997886 19.7820212 3.38275954 19.7820212
        Z
        M 3.48874091 17.9511398
        C 2.4071527 17.9511398 1.82309679 17.3897888 1.82309679 16.2777178
        L 1.82309679 3.50428058
        C 1.82309679 2.3844316 2.4071527 1.8308585 3.48874091 1.8308585
        L 21.768375 1.8308585
        C 22.8335384 1.8308585 23.4340191 2.3844316 23.4340191 3.50428058
        L 23.4340191 16.2777178
        C 23.4340191 17.3897888 22.8335384 17.9511398 21.768375 17.9511398
        Z
        M 7.95408658 4.82706416
        L 17.3030293 4.82706416
        C 17.6966983 4.82706416 17.9790144 4.5369701 17.9790144 4.15518526
        C 17.9790144 3.77340043 17.6984856 3.48929707 17.3030293 3.48929707
        L 7.95408658 3.48929707
        C 7.5705145 3.48929707 7.27810148 3.77340043 7.27810148 4.15518526
        C 7.27810148 4.5369701 7.57230174 4.82706416 7.95408658 4.82706416
        Z
    """.trimIndent(),
).toPath()
