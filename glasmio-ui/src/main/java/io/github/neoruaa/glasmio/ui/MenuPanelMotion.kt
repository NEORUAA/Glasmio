package io.github.neoruaa.glasmio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import io.github.neoruaa.glasmio.glass.GlasmioMenuDragSelection
import io.github.neoruaa.glasmio.glass.LiquidMenuSpring
import io.github.neoruaa.glasmio.glass.LiquidMenuSpringSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

internal class MenuPanelMotion(val drag: GlasmioMenuDragSelection) {
    val panels = mutableStateListOf<MenuPanelFlex>()
}

internal data class MenuPanelTransform(
    val scale: Offset = Offset(1f, 1f),
    val translation: Offset = Offset.Zero,
)

internal fun menuPanelTarget(
    translation: Offset,
    internalTranslation: Offset,
    size: Size,
    verticalVelocity: Float,
    pivotY: Float,
    density: Float,
): MenuPanelTransform {
    fun stretch(distance: Float, internal: Float, dimension: Float): Float =
        if (distance == 0f) 0f
        else (abs(distance) - abs(internal) + abs(internal) / dimension * 40f * density) / (6000f * density)

    fun soften(scale: Float): Float {
        val edge = scale.coerceIn(0.9f, 1.1f)
        val shoulder = 0.2f / 3f
        return edge + shoulder * tanh((scale - edge) * 0.55f / shoulder)
    }

    val dx = stretch(translation.x, internalTranslation.x, size.width)
    val dy = stretch(translation.y, internalTranslation.y, size.height)
    val pivotDirection = 1f - 2f * pivotY
    val pulse = verticalVelocity * pivotDirection / (8000f * density)
    val verticalPulse = pulse * 32f * density
    return MenuPanelTransform(
        scale = Offset(
            soften((1f + dx - dy) * (1f - pulse * 50f * density / size.width)),
            soften((1f + dy - dx) * (1f + verticalPulse / size.height)),
        ),
        translation = Offset(
            size.width * dx * sign(translation.x),
            size.height * dy * sign(translation.y) + verticalPulse * pivotDirection * 0.7f,
        ),
    )
}

internal class MenuPanelFlex(private val density: Float, opensAbove: Boolean) {
    val pivot = TransformOrigin(0.5f, if (opensAbove) 1f else 0f)
    var bounds by mutableStateOf(Rect.Zero)
    var isSettled by mutableStateOf(true)
        private set
    private var frame by mutableStateOf(MenuPanelTransform())
    private val scaleX = LiquidMenuSpring(0.0002f, 0.002f)
    private val scaleY = LiquidMenuSpring(0.0002f, 0.002f)
    private val translationX = LiquidMenuSpring(0.1f * density, density)
    private val translationY = LiquidMenuSpring(0.1f * density, density)
    private val verticalTracking = LiquidMenuSpring(0.1f * density, density)
    private val channels = listOf(scaleX, scaleY, translationX, translationY)

    private var input: MenuPanelInput? = null
    private var animationTime = 0L

    fun applyTo(layer: GraphicsLayerScope, progress: Float = 1f) = with(layer) {
        val transform = frame
        transformOrigin = pivot
        scaleX = 1f + (transform.scale.x - 1f) * progress
        scaleY = 1f + (transform.scale.y - 1f) * progress
        // The native matrix scales the drift along with the contour.
        translationX = transform.translation.x * scaleX * progress
        translationY = transform.translation.y * scaleY * progress
    }

    fun update(next: MenuPanelInput?) {
        if (input == next) return
        if (next != null) {
            if (input?.origin != next.origin) verticalTracking.reset(0f)
            verticalTracking.target(next.translation.y, PulseSpring)
        } else {
            verticalTracking.reset(0f)
        }
        input = next
        retarget()
        isSettled = next == null && channels.all { it.isSettled }
    }

    suspend fun animate() {
        val durationScale = coroutineContext[MotionDurationScale]
        animationTime = 0L
        while (channels.any { !it.isSettled } || !verticalTracking.isSettled) {
            withFrameNanos { now ->
                val factor = durationScale?.scaleFactor ?: 1f
                if (factor <= 0f) {
                    verticalTracking.finish()
                    retarget()
                    channels.forEach { it.finish() }
                } else if (animationTime != 0L) {
                    val seconds = (now - animationTime) / 1_000_000_000f / factor
                    verticalTracking.advance(seconds)
                    retarget()
                    channels.forEach { it.advance(seconds) }
                }
                animationTime = now
                publish()
            }
        }
        // Avoid leaving a subpixel transform when a release already falls inside the tolerance.
        channels.forEach { it.finish() }
        publish()
        isSettled = input == null
    }

    private fun retarget() {
        val drag = input
        val target = if (drag == null) MenuPanelTransform() else {
            menuPanelTarget(
                drag.translation, drag.internalTranslation, drag.bounds.size,
                verticalTracking.velocity, pivot.pivotFractionY, density,
            )
        }
        val spring = if (drag == null) ReleaseSpring else TrackingSpring
        // AnimationKit adds stiffness * target delta * 16 ms to the current velocity.
        scaleX.target(target.scale.x - 1f, spring, RetargetImpulse)
        scaleY.target(target.scale.y - 1f, spring, RetargetImpulse)
        translationX.target(target.translation.x, spring, RetargetImpulse)
        translationY.target(target.translation.y, spring, RetargetImpulse)
    }

    private fun publish() {
        frame = MenuPanelTransform(
            Offset(1f + scaleX.value, 1f + scaleY.value),
            Offset(translationX.value, translationY.value),
        )
    }

    private companion object {
        const val RetargetImpulse = 0.016f
        val TrackingSpring = LiquidMenuSpringSpec(0.5f, 0.15f)
        val ReleaseSpring = LiquidMenuSpringSpec(0.5f, 0.60f)
        val PulseSpring = LiquidMenuSpringSpec(0.548f, 0.574f)
    }
}

internal data class MenuPanelInput(val origin: Offset, val position: Offset, val bounds: Rect) {
    val translation: Offset get() = position - origin
    val internalTranslation: Offset get() = clamp(position) - clamp(origin)

    // Some Glasmio popups have their trigger entirely outside the resting panel.
    private fun clamp(point: Offset) = Offset(
        point.x.coerceIn(bounds.left, bounds.right),
        point.y.coerceIn(bounds.top, bounds.bottom),
    )
}

@Composable
internal fun rememberMenuPanelFlex(
    motion: MenuPanelMotion,
    opensAbove: Boolean,
    isReady: () -> Boolean,
): MenuPanelFlex {
    val density = LocalDensity.current.density
    val latestReady by rememberUpdatedState(isReady)
    val panel = remember(motion, density, opensAbove) { MenuPanelFlex(density, opensAbove) }
    DisposableEffect(motion, panel) {
        motion.panels.add(panel)
        onDispose { motion.panels.remove(panel) }
    }
    LaunchedEffect(motion, panel) {
        var animation: Job? = null
        snapshotFlow {
            val origin = motion.drag.origin
            val position = motion.drag.position
            val bounds = panel.bounds
            if (motion.panels.lastOrNull() === panel && origin != null && position != null &&
                bounds.topLeft.isFinite && !bounds.isEmpty && latestReady()
            ) {
                MenuPanelInput(origin, position, bounds)
            } else null
        }.collect { input ->
            panel.update(input)
            if (animation?.isActive != true) animation = launch { panel.animate() }
        }
    }
    return panel
}
