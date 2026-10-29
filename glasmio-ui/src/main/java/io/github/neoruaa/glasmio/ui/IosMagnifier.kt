/*
 * Magnifier optics adapted from AndroidLiquidGlass MagnifierContent,
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * https://github.com/Kyant0/AndroidLiquidGlass
 */
package io.github.neoruaa.glasmio.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberBackdrop
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.LocalBottomControlContact
import io.github.neoruaa.glasmio.glass.LocalIosToolbarActionGroup
import io.github.neoruaa.glasmio.glass.LocalToolbarActionSeparation
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.core.LocalGlassColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Position of the touch point inside the lens body, on the axis the lens is lifted along.
 *
 * Half: the magnified caret sits in the middle of the body. `_contentOffsetPortalContainerView` is
 * translated by the same `±75 dp` the body is lifted by, which cancels against the lift instead of
 * pushing the edited line towards an edge, so the portal ends up centred on the body. This is a
 * device-verified fit rather than a recovered constant: no fraction appears in the binary, only the
 * two cancelling translations.
 */
private const val LoupeSampleAnchorFraction = 0.5f

/** Lens body height in the horizontal orientation (`preferredSize`), also the widget-height limit. */
internal val GlasmioLoupeBodyHeight: Dp = 85.dp

/**
 * A visual magnifier for caller-owned selection, cursor or inspection gestures.
 *
 * Place this in a bounded overlay that shares the source content's coordinate space.
 * [sourcePosition] and optional [viewportBounds] use physical pixels relative to this
 * overlay's top-left, including in RTL. [backdrop] must record the source content without
 * this magnifier, preventing a recursive sample graph. Include selection widgets in the source
 * when they should stay attached to the magnified text through deformation and refraction.
 * [originSize] is the shape the lens grows out of: the caret rect, or the selection lollipop dot
 * when a selection widget owns the gesture. UIKit reads it from `originShape` rather than assuming
 * a size, so a line height change changes the opening shape too.
 *
 * Geometry and timing follow UIKit's glass caret loupe: a `115 × 85 dp` capsule at `1.25×` centred
 * `75 dp` from the touch point along the writing axis, a `0.3` low-pass feeding a `damping 0.7 /
 * 0.2 s` spring ([GlasmioLoupeMotion] holds every constant), and two opening passes that UIKit
 * starts together — an in-place reveal, and a `damping 0.8 / 0.5 s` pass that grows the body and
 * lifts it off the finger. Dismissal is `damping 1 / 0.23 s` while the sampled content collapses
 * towards `0.125×`.
 *
 * [identity] identifies the widget the lens is attached to. Changing it while visible replays the
 * opening passes, which is what UIKit does when the caret and a selection widget swap mid-gesture.
 * [onDismissed] fires once the dismissal spring has landed, so an owner can stop supplying this
 * composable without cutting the closing animation short.
 *
 * Four behaviours are Android adaptations rather than recovered UIKit code, because the native
 * branches depend on window control this library does not own:
 *
 * - The body keeps its `75 dp` lift while it fits the container and is clamped towards the centre
 *   when it does not, so a field near the top edge still gets a lens. UIKit hides the body there
 *   (`needsHideLoupe:`), a branch with no callers in 26 that would hide the lens for every such
 *   field; flipping it below the finger would break the offset the rest of the geometry assumes.
 * - A body larger than the viewport is scaled down uniformly rather than clipped.
 * - The lens and its sample stay inside system bar and IME insets. UIKit hides the status bar
 *   (`curve 5`, `0.2 s`) when the lens intersects it; hiding system UI is the host's decision here.
 * - The follower rescales UIKit's per-frame low-pass to the real frame time. UIKit keeps a 60 Hz
 *   display link even on ProMotion, so a 120 Hz display is smoother than the reference, not more
 *   faithful.
 *
 * The opening passes animate alpha, size and lift; UIKit also drives `borderWidth 42.5 → 2` and the
 * corner radius through `GlassSurface`, whose border is part of the shared material and stays static
 * here. Size and lift keep the ~1.5% overshoot of UIKit's 0.8 damped morph pass, and the lens is
 * kicked by a one-shot non-uniform scale when the interaction activates, standing in for
 * `scaleSpring.retargetImpulse`. While [tracking] the body deforms with acceleration
 * ([GlasmioLoupeFlexMotion]): a constant-speed drag leaves it undeformed, a flick stretches the
 * motion axis and compresses the perpendicular one. Passing `1` as [dismissalProgress] closes the lens the way a line break does on
 * iOS, without ending the caller's gesture; UIKit only writes that progress when
 * `YukonMagnifiersDisabled` is set, so the default is `0f` and the branch stays dormant.
 */
@Composable
fun GlasmioMagnifier(
    visible: Boolean,
    sourcePosition: Offset,
    backdrop: Backdrop,
    originSize: DpSize,
    modifier: Modifier = Modifier,
    viewportBounds: Rect? = null,
    lensSize: DpSize = DpSize(115.dp, GlasmioLoupeBodyHeight),
    verticalOffset: Dp = 75.dp,
    orientation: GlasmioLoupeOrientation = GlasmioLoupeOrientation.Horizontal,
    magnification: Float = 1.25f,
    collapsedMagnification: Float = 0.125f,
    horizontalInset: Dp = 8.dp,
    motion: GlasmioLoupeMotion = GlasmioLoupeMotion(),
    tracking: Boolean = visible,
    identity: Any? = Unit,
    dismissalProgress: Float = 0f,
    backgroundColor: Color = if (LocalGlassColors.current.isDark) Color.Black else Color.White,
    onDismissed: (() -> Unit)? = null,
) {
    require(magnification.isFinite() && magnification > 0f) { "Magnification must be finite and positive." }
    require(collapsedMagnification.isFinite() && collapsedMagnification > 0f) {
        "Collapsed magnification must be finite and positive."
    }
    require(dismissalProgress.isFinite()) { "Dismissal progress must be finite." }
    require(lensSize.width.value.isFinite() && lensSize.width > 0.dp &&
        lensSize.height.value.isFinite() && lensSize.height > 0.dp) { "Lens size must be finite and positive." }
    // A caret reports a zero-width rect: only its stroke has width. The opening shape is therefore
    // clamped instead of rejected, so a caller cannot crash the lens over a layout detail.
    require(originSize.width.value.isFinite() && originSize.height.value.isFinite()) {
        "Origin size must be finite."
    }
    require(verticalOffset.value.isFinite() && verticalOffset >= 0.dp) { "Vertical offset must be finite and non-negative." }
    require(horizontalInset.value.isFinite() && horizontalInset >= 0.dp) { "Horizontal inset must be finite and non-negative." }
    if (!sourcePosition.x.isFinite() || !sourcePosition.y.isFinite()) return

    val vertical = orientation == GlasmioLoupeOrientation.Vertical
    // preferredSize: 115 × 85 horizontal, 85 × 115 vertical.
    val body = if (vertical) DpSize(lensSize.height, lensSize.width) else lensSize
    val origin = if (vertical) DpSize(originSize.height, originSize.width) else originSize
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowSize = LocalWindowInfo.current.containerSize
    val safeInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)
    val bodyWidthPx = with(density) { body.width.toPx() }
    val bodyHeightPx = with(density) { body.height.toPx() }
    val openSpring = spring<Float>(
        dampingRatio = motion.openRevealDampingRatio,
        stiffness = loupeSpringStiffness(motion.openRevealResponseSeconds),
    )
    val morphSpring = spring<Float>(
        dampingRatio = motion.openMorphDampingRatio,
        stiffness = loupeSpringStiffness(motion.openMorphResponseSeconds),
    )
    val closeSpring = spring<Float>(
        dampingRatio = motion.closeDampingRatio,
        stiffness = loupeSpringStiffness(motion.closeResponseSeconds),
    )
    val activationSpring = spring<Float>(
        dampingRatio = motion.flex.trackingDampingRatio,
        stiffness = loupeSpringStiffness(motion.flex.responseSeconds),
    )

    val shown = visible && !isLoupeDismissed(dismissalProgress, motion)
    var mounted by remember { mutableStateOf(shown) }
    var closing by remember { mutableStateOf(false) }
    val currentSource = rememberUpdatedState(sourcePosition)
    val dismissed = rememberUpdatedState(onDismissed)
    val follow = rememberGlasmioLoupeFollow(
        target = sourcePosition,
        motion = motion,
        snapDistance = loupeSnapDistance(bodyWidthPx, bodyHeightPx),
        enabled = mounted,
    )
    val reveal = remember { Animatable(0f) }
    val morph = remember { Animatable(0f) }
    // One-shot activation pulse; see GlasmioLoupeFlexMotion.activationSquash.
    val activation = remember { Animatable(0f) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy()) }
    LaunchedEffect(shown, identity) {
        if (shown) {
            mounted = true
            closing = false
            follow.snapTo(currentSource.value)
            // The interaction activates with an impulse, not from rest: the body starts slightly
            // deformed and relaxes on the same spring the drag deformation uses.
            activation.snapTo(1f)
            // Both passes start together, as the two consecutive _animateUsingSpringBehavior calls
            // do: the reveal lands in 0.4 s while the body finishes growing and lifting in 0.5 s.
            coroutineScope {
                launch { reveal.animateTo(1f, openSpring) }
                launch { morph.animateTo(1f, morphSpring) }
                launch { activation.animateTo(0f, activationSpring) }
            }
        } else if (mounted) {
            closing = true
            coroutineScope {
                launch { reveal.animateTo(0f, closeSpring) }
                launch { morph.animateTo(0f, closeSpring) }
                launch { activation.animateTo(0f, closeSpring) }
            }
            mounted = false
            dismissed.value?.invoke()
        }
    }
    // Stay mounted through the dismissal spring, but request no frames once parked.
    if (!mounted) return

    val velocity = rememberLoupeVelocitySample(follow.position, enabled = tracking)
    BoxWithConstraints(
        modifier.fillMaxSize().onGloballyPositioned { coordinates = it },
        contentAlignment = AbsoluteAlignment.TopLeft,
    ) {
        if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return@BoxWithConstraints
        val root = coordinates?.takeIf { it.isAttached } ?: return@BoxWithConstraints
        val fullViewport = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val safeViewport = Rect(
            root.windowToLocal(Offset(safeInsets.getLeft(density, layoutDirection).toFloat(), safeInsets.getTop(density).toFloat())),
            root.windowToLocal(Offset((windowSize.width - safeInsets.getRight(density, layoutDirection)).toFloat(),
                (windowSize.height - safeInsets.getBottom(density)).toFloat())),
        ).intersect(fullViewport)
        val requestedViewport = viewportBounds ?: fullViewport
        if (!requestedViewport.left.isFinite() || !requestedViewport.top.isFinite() ||
            !requestedViewport.right.isFinite() || !requestedViewport.bottom.isFinite()) return@BoxWithConstraints
        val viewport = requestedViewport.intersect(safeViewport)
        if (viewport.width < 1f || viewport.height < 1f) return@BoxWithConstraints

        val fit = minOf(1f, viewport.width / bodyWidthPx, viewport.height / bodyHeightPx)
        val width = (bodyWidthPx * fit).coerceAtLeast(1f)
        val height = (bodyHeightPx * fit).coerceAtLeast(1f)
        val originWidth = (with(density) { origin.width.toPx() } * fit).coerceIn(1f, width)
        val originHeight = (with(density) { origin.height.toPx() } * fit).coerceIn(1f, height)
        val lift = with(density) { verticalOffset.toPx() }
        val inset = with(density) { horizontalInset.toPx() }
        val center = follow.position
        // Only the axis the body spans wider than the finger is clamped; the lift axis is free and
        // the body hides instead when it would leave the container.
        val lensCenterX: Float
        val lensCenterY: Float
        if (vertical) {
            // Vertical text lifts to +x, so the body leans right of the touch point.
            lensCenterX = loupeLiftCenter(center.x, -lift, width, viewport.left, viewport.right)
            lensCenterY = loupeClampX(center.y, viewport.top, viewport.bottom, height, inset)
        } else {
            lensCenterX = loupeClampX(center.x, viewport.left, viewport.right, width, inset)
            lensCenterY = loupeLiftCenter(center.y, lift, height, viewport.top, viewport.bottom)
        }
        val lensLeft = lensCenterX - width / 2f
        val lensTop = lensCenterY - height / 2f

        // The opening pass keeps the lens on the touch point and only then moves it to the lifted
        // position, so the growth and the lift read as one motion.
        //
        // `morph` is deliberately not clamped: the 0.8 damped pass overshoots its target by ~1.5%,
        // and that momentary excess size and lift is the only momentum UIKit's opening has.
        val progress = morph.value
        val anchorX = (1f - progress) * (center.x - lensCenterX)
        val anchorY = (1f - progress) * (center.y - lensCenterY)
        val bodyScaleX = 1f - (1f - progress) * (1f - originWidth / width)
        val bodyScaleY = 1f - (1f - progress) * (1f - originHeight / height)
        val activationScale = loupeActivationScale(activation.value, motion.flex)

        // UIKit reports the identity deformation whenever the interaction is not tracking.
        val bodyScale = Offset(bodyScaleX, bodyScaleY)
        val flexTarget = remember(velocity, width, height, bodyScale, motion.flex, density, tracking) {
            if (!tracking) GlasmioLoupeFlex()
            else {
                // UIKit expresses the deformation budget in points, so both the body and the sampled
                // motion are converted before the absolute term is applied. `bodyScale` is the live
                // size UIKit reads from the interaction view's bounds while the lens grows out of
                // the caret; without it the drift is ~20x too large during the opening pass.
                loupeFlex(
                    acceleration = velocity.acceleration / density.density,
                    velocity = velocity.velocity / density.density,
                    width = width / density.density,
                    height = height / density.density,
                    motion = motion.flex,
                    bodyScale = bodyScale,
                )
            }
        }
        val flexScale = rememberLoupeSpringOffset(
            target = flexTarget.scale,
            dampingRatio = if (tracking) motion.flex.trackingDampingRatio else motion.flex.dampingRatio,
            stiffness = loupeSpringStiffness(motion.flex.responseSeconds),
            initial = Offset(1f, 1f),
            enabled = mounted,
        )
        val flexDriftDp = rememberLoupeSpringOffset(
            target = flexTarget.drift,
            dampingRatio = if (tracking) motion.flex.trackingDampingRatio else motion.flex.dampingRatio,
            stiffness = loupeSpringStiffness(motion.flex.responseSeconds),
            initial = Offset.Zero,
            enabled = mounted,
        )
        val flexDrift = flexDriftDp * density.density

        val zoom = if (closing) {
            loupeLerp(progress.coerceIn(0f, 1f), collapsedMagnification, magnification)
        } else magnification
        val currentZoom = rememberUpdatedState(zoom)
        val currentTouchLocal = rememberUpdatedState(
            Offset(follow.samplePosition.x - lensLeft, follow.samplePosition.y - lensTop),
        )
        val currentAnchor = rememberUpdatedState(
            if (vertical) Offset(width * LoupeSampleAnchorFraction, height / 2f)
            else Offset(width / 2f, height * LoupeSampleAnchorFraction),
        )
        val currentBackground = rememberUpdatedState(backgroundColor)
        val transform: DrawScope.(DrawScope.() -> Unit) -> Unit = remember {
            { drawSource ->
                // The magnified region can extend beyond the source window near an edge.
                // Fill that transparent part before transforming the recorded scene.
                drawRect(currentBackground.value)
                val touch = currentTouchLocal.value
                val anchor = currentAnchor.value
                withTransform(
                    {
                        // The recorded scene is drawn in surface-local coordinates and DrawScope
                        // scales about its own centre, so this is T(anchor - s·touch) · S(s): the
                        // touch point lands on the anchor, magnified about itself.
                        val scale = currentZoom.value
                        translate(scale * (anchor.x - touch.x), scale * (anchor.y - touch.y))
                        scale(scale, scale, pivot = anchor)
                    },
                    drawSource,
                )
            }
        }
        val magnifiedBackdrop = rememberBackdrop(backdrop, transform)
        // A selection lens never participates in a toolbar's connected glass geometry.
        CompositionLocalProvider(
            LocalIosToolbarActionGroup provides null,
            LocalToolbarActionSeparation provides null,
            LocalBottomControlContact provides null,
        ) {
            GlassSurface(
                modifier = Modifier.absoluteOffset { IntOffset(lensLeft.roundToInt(), lensTop.roundToInt()) }
                    .graphicsLayer {
                        alpha = reveal.value.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin.Center
                        scaleX = bodyScaleX * flexScale.x * activationScale.x
                        scaleY = bodyScaleY * flexScale.y * activationScale.y
                        translationX = anchorX + flexDrift.x
                        translationY = anchorY + flexDrift.y
                    }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() }),
                backdrop = magnifiedBackdrop,
                shape = Capsule(),
                // Keep the Clear edge layers without frosting the magnified glyphs.
                material = NavigationGlassMaterial.Clear,
                frostRadius = 0.dp,
                preserveBackdropColors = true,
                refractionHeight = 8.dp,
                refractionAmount = 24.dp,
            ) {}
        }
    }
}
