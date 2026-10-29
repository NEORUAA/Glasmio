package io.github.neoruaa.glasmio.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun pressHighlightSpring() = spring(0.5f, 300f, 0.001f)

class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

    internal var pressAnimationSpec: ((isPressed: Boolean) -> AnimationSpec<Float>)? = null
    private val pressProgressAnimationSpec = pressHighlightSpring()
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    // UIFlexInteraction uses separate critical springs for the light's appearance and dissipation.
    private val glowProgressAnimation = Animatable(0f, 0.001f)
    private val dissipationAnimation = Animatable(0f, 0.001f)
    internal var materialIsDark: Boolean = false
    internal var materialTint: Color? = null
    internal var materialClear: Boolean = false
    val glowProgress: Float get() = if (pressCancelled) 0f else glowProgressAnimation.value
    val dissipationProgress: Float get() = if (pressCancelled) 0f else dissipationAnimation.value

    private var startPosition = Offset.Zero
    private var pressCancelled by mutableStateOf(false)
    var isPressed by mutableStateOf(false)
        private set
    val pressProgress: Float get() = if (pressCancelled) 0f else pressProgressAnimation.value
    val offset: Offset get() = if (pressCancelled) Offset.Zero else positionAnimation.value - startPosition

    fun startPress(position: Offset) {
        isPressed = true
        pressCancelled = false
        startPosition = position
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressAnimationSpec?.invoke(true) ?: pressProgressAnimationSpec) }
            launch { glowProgressAnimation.animateTo(1f, spring(1f, 3947.8418f, 0.001f)) }
            launch { dissipationAnimation.snapTo(0f) }
            launch { positionAnimation.snapTo(startPosition) }
        }
    }

    fun endPress() {
        isPressed = false
        if (!pressCancelled) animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressAnimationSpec?.invoke(false) ?: pressProgressAnimationSpec) }
            launch { glowProgressAnimation.animateTo(0f, spring(1f, 157.91367f, 0.001f)) }
            launch { dissipationAnimation.animateTo(1f, spring(1f, 157.91367f, 0.001f)) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }

    /** A sheet has taken this gesture; only a new down may restore feedback. */
    fun cancelPress() {
        isPressed = false
        pressCancelled = true
        animationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            pressProgressAnimation.snapTo(0f)
            glowProgressAnimation.snapTo(0f)
            dissipationAnimation.snapTo(0f)
            positionAnimation.snapTo(startPosition)
        }
    }

    private val renderer = PressHighlightRenderer()

    fun highlightPosition(size: Size): Offset = position(size, positionAnimation.value)

    fun highlightModifier(
        progressProvider: () -> Float = { glowProgress },
        positionProvider: (Size) -> Offset = ::highlightPosition,
        horizontalRadiusProvider: ((Size) -> Float)? = null,
        dissipationProgressProvider: () -> Float = { dissipationProgress },
    ): Modifier = Modifier.drawWithContent {
        val progress = progressProvider()
        if (progress > 0f) renderer.draw(
            this, progress, positionProvider(size),
            horizontalRadius = horizontalRadiusProvider?.invoke(size),
            isDark = materialIsDark,
            tint = materialTint,
            clear = materialClear,
            dissipationProgress = dissipationProgressProvider(),
        )
        drawContent()
    }

    val modifier: Modifier = highlightModifier()

    val gestureModifier: Modifier =
        // Geometry or layout-direction changes can replace this owner while retaining its scope.
        Modifier.pointerInput(this@InteractiveHighlight) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPress(down.position)
                },
                onDragEnd = {
                    endPress()
                },
                onDragCancel = {
                    endPress()
                }
            ) { change, _ ->
                if (!pressCancelled) animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
