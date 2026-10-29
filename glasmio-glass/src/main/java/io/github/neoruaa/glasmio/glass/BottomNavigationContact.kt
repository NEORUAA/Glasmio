package io.github.neoruaa.glasmio.glass

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.layerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.PressHighlightRenderer
import io.github.neoruaa.glasmio.glass.render.sampleBackdropLuminance
import io.github.neoruaa.glasmio.glass.render.ProvideGlassMaterialContext
import io.github.neoruaa.glasmio.glass.render.rememberGlassMaterialContext
import kotlin.math.ceil
import kotlin.math.floor

val LocalBottomControlContact = staticCompositionLocalOf<BottomControlContact?> { null }
/** Retained, unplaced controls keep their material owner without contributing geometry or light. */
val LocalBottomControlContactMemberActive = staticCompositionLocalOf<() -> Boolean> { { true } }

/** One material context owns both separated controls and their joined contour. */
@Composable
fun BottomNavigationContact(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    mergedProgress: State<Float>? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val density = LocalDensity.current
    val renderedSurface = remember { mutableStateOf<BottomContactSurface?>(null) }
    val renderedLensSurface = remember { mutableStateOf<BottomContactSurface?>(null) }
    val renderedLensTransform = remember { mutableStateOf<BottomContactLensTransform?>(null) }
    val renderedLensPress = remember { mutableStateOf(0f) }
    val contact = remember(density) {
        BottomControlContact(with(density) { 4.dp.roundToPx().toFloat() }).apply {
            maximumRefractionHeight = with(density) { 24.dp.toPx() }
            nativeSmoothness = with(density) { 6.dp.toPx() }
        }
    }
    contact.mergedProgress = mergedProgress
    val materialBackdrop = rememberLayerBackdrop()
    val neighborContentLayer = rememberGraphicsLayer()
    val drawNeighborContent: ContentDrawScope.() -> Unit = remember(contact, neighborContentLayer, renderedLensTransform) {
        {
            val transform = renderedLensTransform.value
            if (contact.surface.value != null && transform != null) {
                withTransform({
                    scale(transform.scale.x, transform.scale.y, transform.pivot - contact.neighborOrigin)
                }) { drawLayer(neighborContentLayer) }
            }
        }
    }
    val neighborBackdrop = rememberLayerBackdrop(onDraw = drawNeighborContent)
    val neighborRecordingModifier = remember(contact, neighborBackdrop, neighborContentLayer) {
        Modifier.onGloballyPositioned(contact::updateNeighborCoordinates)
            .recordBottomControlBackdrop(neighborBackdrop, neighborContentLayer)
    }
    contact.bindSources(materialBackdrop, neighborBackdrop, neighborRecordingModifier)
    val materialContext = rememberGlassMaterialContext()
    val colors = materialContext.environmentColors
    val appearance = LocalGlassAppearance.current
    val innerShadows = rememberNavigationInnerShadows(appearance.usesBackdrop, renderScale = 0.5f)
    val lensInnerShadows = rememberNavigationInnerShadows(appearance.usesRefraction, renderScale = 0.5f)
    val surfaceReady by remember(contact) { derivedStateOf { contact.surface.value != null } }
    val capturesLensSource by contact.capturesLensSource
    val luminance = materialContext.probe
    val materialDark = materialContext.isDark
    val darkFraction = materialContext.darkFraction
    val sampleMaster = materialContext.luminanceState != null && surfaceReady
    val probeModifier = remember(backdrop, luminance, sampleMaster, contact) {
        if (sampleMaster) Modifier.sampleBackdropLuminance(
            backdrop = backdrop, luminanceState = luminance,
            sampleBounds = { contact.surface.value?.bounds ?: Rect.Zero },
        ) else Modifier
    }
    Box(modifier.onGloballyPositioned(contact::updateCoordinates).then(probeModifier)) {
        // Capture the actual shared material once, before foreground members are drawn.
        // A selected UIKit lens samples this plane without the old optical source transform.
        Box(Modifier.matchParentSize().layerBackdrop(materialBackdrop)) {
            if (surfaceReady) {
                val shape = remember(renderedSurface) { { renderedSurface.value?.shape ?: Capsule() } }
                val press = remember(contact) { derivedStateOf { contact.pressProgress() } }
                val background = remember(contact, backdrop, shape, press, renderedSurface, colors, appearance, innerShadows, materialDark, darkFraction) {
                    Modifier.navigationGlassBackgroundResolved(
                        backdrop = backdrop,
                        shape = shape,
                        containerColor = colors.container,
                        navigationMaterial = appearance.usesBackdrop,
                        innerShadows = innerShadows,
                        isDark = materialDark,
                        darkFraction = darkFraction,
                        pressProgressState = press,
                        opticalCenter = {
                            contact.referenceCenter() - (renderedSurface.value?.bounds?.topLeft ?: Offset.Zero)
                        },
                        pressOverlay = {
                            renderedSurface.value?.let { contact.drawPressHighlight(this, it.bounds.topLeft, isDark = materialDark) }
                        },
                        appearance = appearance,
                        sampledPressHighlight = {
                            renderedSurface.value?.let { contact.sampledPressHighlight(it.bounds.topLeft) }
                        },
                    )
                }
                Box(Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        // Publish one geometry snapshot for measurement, placement and every draw
                        // pass. Child layout callbacks may already describe the next morph frame.
                        val surface = contact.surface.value
                        renderedSurface.value = surface
                        val bounds = surface?.bounds ?: Rect.Zero
                        val placeable = measurable.measure(Constraints.fixed(
                            bounds.width.toInt().coerceAtLeast(0),
                            bounds.height.toInt().coerceAtLeast(0),
                        ))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(bounds.left.toInt(), bounds.top.toInt())
                        }
                    }.then(background))
            }
        }
        if (capturesLensSource) {
            val shape = remember(renderedLensSurface) { { renderedLensSurface.value?.shape ?: Capsule() } }
            val background = remember(
                contact, backdrop, shape, renderedSurface, renderedLensSurface, renderedLensTransform,
                renderedLensPress, colors, appearance, lensInnerShadows,
                materialDark,
            ) {
                Modifier.navigationLensSourceBackground(
                    backdrop = backdrop,
                    shape = shape,
                    containerColor = colors.container,
                    isDark = materialDark,
                    appearance = appearance,
                    pressProgressState = renderedLensPress,
                    innerShadows = lensInnerShadows,
                    opticalCenter = {
                        contact.referenceCenter() - (renderedLensSurface.value?.bounds?.topLeft ?: Offset.Zero)
                    },
                    pressGlow = {
                        val surface = renderedLensSurface.value
                        val transform = renderedLensTransform.value
                        if (surface != null && transform != null)
                            contact.sampledPressHighlight(surface.bounds.topLeft, transform)?.highlight else null
                    },
                    pressGlowBounds = { size ->
                        val surface = renderedLensSurface.value
                        val transform = renderedLensTransform.value
                        if (surface != null && transform != null)
                            contact.sampledPressHighlight(surface.bounds.topLeft, transform)?.bounds ?: Rect(Offset.Zero, size)
                        else Rect(Offset.Zero, size)
                    },
                    pressOverlay = {
                        if (contact.surface.value != null) {
                            val source = renderedSurface.value
                            val sampled = renderedLensSurface.value
                            val transform = renderedLensTransform.value
                            if (source != null && sampled != null && transform != null) {
                                contact.drawPressHighlight(
                                    this,
                                    origin = sampled.bounds.topLeft,
                                    sourceBounds = source.bounds,
                                    transform = transform,
                                    isDark = materialDark,
                                )
                            }
                        }
                    },
                )
            }
            // Re-render the complete source outline after its common affine transform.
            // Optical radii stay in pixels, and contact never replaces the source layer.
            Box(Modifier.matchParentSize().alpha(0f)) {
                Box(Modifier.matchParentSize()
                    .layout { measurable, constraints ->
                        val surface = contact.lensSurface.value
                        renderedLensSurface.value = surface
                        renderedLensTransform.value = contact.lensTransform.value
                        renderedLensPress.value = contact.lensSourcePressProgress()
                        val bounds = surface?.bounds ?: Rect.Zero
                        val placeable = measurable.measure(Constraints.fixed(
                            bounds.width.toInt().coerceAtLeast(0),
                            bounds.height.toInt().coerceAtLeast(0),
                        ))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(bounds.left.toInt(), bounds.top.toInt())
                        }
                    }.then(background))
            }
        }
        ProvideGlassMaterialContext(materialContext) {
            CompositionLocalProvider(LocalBottomControlContact provides contact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

private fun Modifier.recordBottomControlBackdrop(
    backdrop: LayerBackdrop,
    contentLayer: GraphicsLayer,
): Modifier = layerBackdrop(backdrop).drawWithContent {
    // Record at the unchanged host size with default unclipped, transparent bounds.
    // The visible pass and backdrop recording replay this same layer without redrawing content.
    contentLayer.record { this@drawWithContent.drawContent() }
    drawLayer(contentLayer)
}

@Composable
fun rememberBottomControlContactMember(
    visualBounds: (Size) -> Rect,
    materialPressProgress: () -> Float,
    highlight: (Size) -> GlassPressHighlight,
    lensSourceActive: (() -> Boolean)? = null,
    lensSourceScale: ((Size) -> Offset)? = null,
    lensSourcePressProgress: (() -> Float)? = null,
): BottomControlContactMember? = rememberBottomControlContactMember(
    visualBounds, materialPressProgress, highlight,
    lensSourceActive, lensSourceScale, lensSourcePressProgress, sourceSize = null,
)

@Composable
fun rememberBottomControlContactMember(
    visualBounds: (Size) -> Rect,
    materialPressProgress: () -> Float,
    highlight: (Size) -> GlassPressHighlight,
    lensSourceActive: (() -> Boolean)? = null,
    lensSourceScale: ((Size) -> Offset)? = null,
    lensSourcePressProgress: (() -> Float)? = null,
    sourceSize: (() -> Size)?,
): BottomControlContactMember? {
    val contact = LocalBottomControlContact.current
    val currentActive = rememberUpdatedState(LocalBottomControlContactMemberActive.current)
    val currentBounds = rememberUpdatedState(visualBounds)
    val currentMaterialPress = rememberUpdatedState(materialPressProgress)
    val currentHighlight = rememberUpdatedState(highlight)
    val currentLensSourceActive = rememberUpdatedState(lensSourceActive)
    val currentLensSourceScale = rememberUpdatedState(lensSourceScale)
    val currentLensSourcePress = rememberUpdatedState(lensSourcePressProgress)
    val currentSourceSize = rememberUpdatedState(sourceSize)
    val member = remember(contact) {
        contact?.let {
            BottomControlContactMember(
                it, currentActive, currentBounds, currentMaterialPress, currentHighlight,
                currentLensSourceActive, currentLensSourceScale, currentLensSourcePress,
                currentSourceSize,
            )
        }
    }
    DisposableEffect(member) {
        member?.contact?.members?.add(member)
        onDispose { member?.contact?.members?.remove(member) }
    }
    return member
}

class BottomControlContactMember(
    val contact: BottomControlContact,
    private val active: State<() -> Boolean>,
    private val visualBounds: State<(Size) -> Rect>,
    private val materialPressProgress: State<() -> Float>,
    private val highlight: State<(Size) -> GlassPressHighlight>,
    private val lensSourceActive: State<(() -> Boolean)?>,
    private val lensSourceScale: State<((Size) -> Offset)?>,
    private val lensSourcePress: State<(() -> Float)?>,
    private val sourceSize: State<(() -> Size)?>?,
) {
    constructor(
        contact: BottomControlContact,
        active: State<() -> Boolean>,
        visualBounds: State<(Size) -> Rect>,
        materialPressProgress: State<() -> Float>,
        highlight: State<(Size) -> GlassPressHighlight>,
        lensSourceActive: State<(() -> Boolean)?>,
        lensSourceScale: State<((Size) -> Offset)?>,
        lensSourcePress: State<(() -> Float)?>,
    ) : this(contact, active, visualBounds, materialPressProgress, highlight,
        lensSourceActive, lensSourceScale, lensSourcePress, sourceSize = null)

    private var coordinates: LayoutCoordinates? = null
    private var bounds: Rect? by mutableStateOf(null)

    val connected: Boolean get() = contact.connected.value
    val sharesSurface: Boolean get() = contact.surface.value != null

    fun capsule(): LiquidContactCapsule? = if (!active.value()) null else bounds?.let {
        // A resizing source can advance before its global layout registration. Keep
        // that current source rectangle independent of the actual press transform.
        LiquidContactCapsule(sourceSize?.value?.invoke() ?: it.size,
            visualBounds.value(it.size).translate(it.topLeft))
    }

    fun needsLensSource(): Boolean = active.value() && lensSourceActive.value?.invoke() == true

    fun lensTransform(): BottomContactLensTransform? {
        val capsule = capsule() ?: return null
        val scale = lensSourceScale.value?.invoke(capsule.size) ?: return null
        if (!scale.x.isFinite() || !scale.y.isFinite() || scale.x <= 0f || scale.y <= 0f) return null
        return BottomContactLensTransform(scale, capsule.bounds.center)
    }

    fun lensSourcePressProgress(): Float? = if (active.value()) lensSourcePress.value?.invoke() else null

    fun pressHighlight(): GlassPressHighlight =
        if (active.value()) bounds?.let { highlight.value(it.size) } ?: GlassPressHighlight() else GlassPressHighlight()

    fun materialPressProgress(): Float = if (active.value()) materialPressProgress.value() else 0f

    fun updateCoordinates(value: LayoutCoordinates) {
        coordinates = value
        updateBounds()
    }

    fun updateBounds() {
        val parent = contact.coordinates
        val child = coordinates
        bounds = if (parent?.isAttached == true && child?.isAttached == true &&
            parent.findRootCoordinates() === child.findRootCoordinates()
        ) parent.localBoundingBoxOf(child, clipBounds = false) else null
    }
}

data class BottomContactSurface(
    val bounds: Rect,
    val shape: Shape,
)

data class BottomContactLensTransform(val scale: Offset, val pivot: Offset) {
    fun point(value: Offset) = Offset(
        pivot.x + (value.x - pivot.x) * scale.x,
        pivot.y + (value.y - pivot.y) * scale.y,
    )

    fun bounds(value: Rect) = Rect(point(value.topLeft), point(value.bottomRight))
}

class BottomControlContact(
    private val contactDistance: Float,
) {
    lateinit var materialBackdrop: LayerBackdrop
        private set
    lateinit var neighborBackdrop: LayerBackdrop
        private set
    var neighborRecordingModifier: Modifier = Modifier
        private set
    val members = mutableStateListOf<BottomControlContactMember>()
    var coordinates: LayoutCoordinates? = null
        private set
    private var neighborCoordinates: LayoutCoordinates? = null
    var neighborOrigin: Offset by mutableStateOf(Offset.Zero)
        private set
    private val pressRenderer = PressHighlightRenderer()
    private val lensPressRenderer = PressHighlightRenderer()
    private val refractionField = NavigationContactField()
    internal var maximumRefractionHeight: Float = 0f
    internal var nativeSmoothness: Float? = null
    internal var mergedProgress: State<Float>? by mutableStateOf(null)
    private var cachedFirst: LiquidContactCapsule? = null
    private var cachedSecond: LiquidContactCapsule? = null
    private var cachedRefractionBand = Float.NaN
    private var cachedNativeSmoothness: Float? = null
    private var cachedContactShape: LiquidContactShape? = null
    private var cachedSurface: BottomContactSurface? = null
    val capturesLensSource = derivedStateOf(structuralEqualityPolicy()) { members.any { it.needsLensSource() } }

    val surface = derivedStateOf(referentialEqualityPolicy()) { createSurface() }
    val connected = derivedStateOf(structuralEqualityPolicy()) {
        (surface.value?.shape as? LiquidContactShape)?.isConnected == true
    }
    val lensTransform = derivedStateOf(structuralEqualityPolicy()) { members.firstNotNullOfOrNull { it.lensTransform() } }
    val lensSurface = derivedStateOf {
        val source = surface.value ?: return@derivedStateOf null
        val transform = lensTransform.value ?: return@derivedStateOf null
        val transformedBounds = transform.bounds(source.bounds)
        val bounds = Rect(
            floor(transformedBounds.left), floor(transformedBounds.top),
            ceil(transformedBounds.right), ceil(transformedBounds.bottom),
        )
        BottomContactSurface(bounds, (source.shape as LiquidContactShape).withOutlineTransform(
            scale = transform.scale,
            translation = transformedBounds.topLeft - bounds.topLeft,
        ))
    }

    private fun createSurface(): BottomContactSurface? {
        var first: LiquidContactCapsule? = null
        var second: LiquidContactCapsule? = null
        val progress = mergedProgress?.value?.coerceIn(0f, 1f)
        for (member in members) {
            val capsule = member.capsule() ?: continue
            if (first == null) first = capsule
            else if (second == null) second = capsule
            else return null
        }
        val firstCapsule = first ?: return null
        val secondCapsule = second
        // The merged source has one contour. Its auxiliary gains contact curvature
        // continuously in the same morph transaction, without a separate delay.
        val nativeSmoothness = this.nativeSmoothness?.let { if (secondCapsule == null) it else it * (progress ?: 1f) }
        if (secondCapsule == null && nativeSmoothness == null) return null
        val bounds = Rect(
            floor(minOf(firstCapsule.bounds.left, secondCapsule?.bounds?.left ?: firstCapsule.bounds.left)),
            floor(minOf(firstCapsule.bounds.top, secondCapsule?.bounds?.top ?: firstCapsule.bounds.top)),
            ceil(maxOf(firstCapsule.bounds.right, secondCapsule?.bounds?.right ?: firstCapsule.bounds.right)),
            ceil(maxOf(firstCapsule.bounds.bottom, secondCapsule?.bounds?.bottom ?: firstCapsule.bounds.bottom)),
        )
        val localFirst = firstCapsule.copy(bounds = firstCapsule.bounds.translate(-bounds.topLeft))
        val localSecond = secondCapsule?.copy(bounds = secondCapsule.bounds.translate(-bounds.topLeft))
        // Both material passes share the exact same native contour and refraction field.
        // Callback rebinding or a common translation must not regenerate that contour.
        val scale = lensTransform.value?.scale ?: Offset(1f, 1f)
        val minimumScale = minOf(1f, scale.x, scale.y).coerceAtLeast(0.0001f)
        val band = maximumRefractionHeight / minimumScale
        val shape = cachedContactShape.takeIf {
            localFirst == cachedFirst && localSecond == cachedSecond && band == cachedRefractionBand &&
                nativeSmoothness == cachedNativeSmoothness
        } ?: run {
            // Refraction support changes with lens scale, but the immutable zero contour
            // only depends on capsule geometry and smoothness. Retain its lazy path.
            val native = cachedContactShape?.nativeUnion?.takeIf {
                localFirst == cachedFirst && localSecond == cachedSecond && nativeSmoothness == cachedNativeSmoothness
            } ?: nativeSmoothness?.let { NavigationNativeUnion.create(localFirst, localSecond, it) }
            if (native != null) LiquidContactShape(native.first, native.second ?: native.first, native.smoothness)
                .withNativeUnion(native)
            else localSecond?.let { liquidContactShape(localFirst, it, contactDistance, includeDisconnected = true)
                as? LiquidContactShape }
        }?.withRefractionField(refractionField, band)?.also {
            cachedFirst = localFirst
            cachedSecond = localSecond
            cachedRefractionBand = band
            cachedNativeSmoothness = nativeSmoothness
            cachedContactShape = it
        } ?: return null
        return cachedSurface?.takeIf { it.bounds == bounds && it.shape === shape }
            ?: BottomContactSurface(bounds, shape).also { cachedSurface = it }
    }

    fun bindSources(material: LayerBackdrop, neighbor: LayerBackdrop, recordingModifier: Modifier) {
        materialBackdrop = material
        neighborBackdrop = neighbor
        neighborRecordingModifier = recordingModifier
    }

    fun updateCoordinates(value: LayoutCoordinates) {
        coordinates = value
        members.forEach { it.updateBounds() }
        updateNeighborOrigin()
    }

    fun updateNeighborCoordinates(value: LayoutCoordinates) {
        neighborCoordinates = value
        updateNeighborOrigin()
    }

    private fun updateNeighborOrigin() {
        val parent = coordinates
        val child = neighborCoordinates
        if (parent?.isAttached == true && child?.isAttached == true &&
            parent.findRootCoordinates() === child.findRootCoordinates()
        ) neighborOrigin = parent.localPositionOf(child, Offset.Zero)
    }

    fun pressProgress(): Float = members.maxOfOrNull { it.materialPressProgress() } ?: 0f

    /** Layout coordinates exclude member press deformation and contact-bound expansion. */
    fun referenceCenter(): Offset = coordinates?.size?.let { Offset(it.width / 2f, it.height / 2f) } ?: Offset.Zero

    fun lensSourcePressProgress(): Float = members.firstNotNullOfOrNull { it.lensSourcePressProgress() } ?: 0f

    fun lensContactDirection(member: BottomControlContactMember): Float {
        if (!connected.value) return 0f
        val navigation = member.capsule()?.bounds ?: return 0f
        val neighbor = members.firstOrNull { it !== member }?.capsule()?.bounds ?: return 0f
        val direction = if (neighbor.center.x > navigation.center.x) 1f else -1f
        val gap = if (direction > 0f) neighbor.left - navigation.right else navigation.left - neighbor.right
        val proximity = ((contactDistance - gap) / contactDistance).coerceIn(0f, 1f)
        return direction * proximity * proximity * (3f - 2f * proximity)
    }

    internal fun sampledPressHighlight(origin: Offset, transform: BottomContactLensTransform? = null): SampledPressHighlight? {
        val active = members.maxByOrNull { it.pressHighlight().glowProgress } ?: return null
        val press = active.pressHighlight()
        val bounds = active.capsule()?.bounds ?: return null
        val point = bounds.topLeft + Offset(bounds.width * press.position.x, bounds.height * press.position.y)
        val projectedBounds = transform?.bounds(bounds) ?: bounds
        val projectedPoint = transform?.point(point) ?: point
        val local = projectedPoint - projectedBounds.topLeft
        return SampledPressHighlight(press.copy(position = Offset(
            local.x / projectedBounds.width.coerceAtLeast(1f), local.y / projectedBounds.height.coerceAtLeast(1f))),
            projectedBounds.translate(-origin))
    }

    fun drawPressHighlight(
        scope: DrawScope,
        origin: Offset,
        sourceBounds: Rect = Rect(origin, scope.size),
        transform: BottomContactLensTransform? = null,
        isDark: Boolean = false,
    ) {
        val active = members.maxByOrNull { it.pressHighlight().glowProgress } ?: return
        val press = active.pressHighlight()
        val bounds = active.capsule()?.bounds ?: return
        val sourcePosition = bounds.topLeft +
            Offset(bounds.width * press.position.x, bounds.height * press.position.y)
        val position = (transform?.point(sourcePosition) ?: sourcePosition) - origin
        val localBounds = (transform?.bounds(bounds) ?: bounds).translate(-origin)
        val horizontalRadius = transform?.let { bounds.size.minDimension * .75f * it.scale.x }
        // Keep the press wash and radial light on the full sampled outline. Its vertical
        // radius follows the transformed source bounds; the horizontal radius follows scaleX.
        val renderer = if (transform == null) pressRenderer else lensPressRenderer
        renderer.draw(scope, press.glowProgress, position, sourceBounds = localBounds,
            horizontalRadius = horizontalRadius, isDark = isDark, dissipationProgress = press.dissipationProgress)
    }
}
