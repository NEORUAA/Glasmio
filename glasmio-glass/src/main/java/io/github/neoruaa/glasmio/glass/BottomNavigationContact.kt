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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.PressHighlightRenderer
import kotlin.math.ceil
import kotlin.math.floor

val LocalBottomControlContact = staticCompositionLocalOf<BottomControlContact?> { null }

/** One material context owns both separated controls and their joined contour. */
@Composable
fun BottomNavigationContact(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
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
        }
    }
    val materialBackdrop = rememberLayerBackdrop()
    val materialRecordingModifier = remember(materialBackdrop) {
        Modifier.layerBackdrop(materialBackdrop).drawWithContent {
            if (drawContext.graphicsLayer === materialBackdrop.graphicsLayer) drawContent()
        }
    }
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
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    val innerShadows = rememberNavigationInnerShadows(appearance.usesBackdrop, renderScale = 0.5f)
    val lensInnerShadows = rememberNavigationInnerShadows(appearance.usesRefraction, renderScale = 0.5f)
    val surfaceReady by remember(contact) { derivedStateOf { contact.surface.value != null } }
    val capturesLensSource by contact.capturesLensSource
    Box(modifier.onGloballyPositioned(contact::updateCoordinates)) {
        Box(Modifier.matchParentSize()) {
            if (surfaceReady) {
                val shape = remember(renderedSurface) { { renderedSurface.value?.shape ?: Capsule() } }
                val press = remember(contact) { derivedStateOf { contact.pressProgress() } }
                val background = remember(contact, backdrop, shape, press, renderedSurface, colors, appearance, innerShadows) {
                    Modifier.navigationGlassBackground(
                        backdrop = backdrop,
                        shape = shape,
                        containerColor = colors.container,
                        navigationMaterial = appearance.usesBackdrop,
                        innerShadows = innerShadows,
                        isDark = colors.isDark,
                        pressProgressState = press,
                        opticalCenter = {
                            contact.referenceCenter() - (renderedSurface.value?.bounds?.topLeft ?: Offset.Zero)
                        },
                        pressOverlay = {
                            renderedSurface.value?.let { contact.drawPressHighlight(this, it.bounds.topLeft) }
                        },
                        appearance = appearance,
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
            ) {
                Modifier.navigationLensSourceBackground(
                    backdrop = backdrop,
                    shape = shape,
                    containerColor = colors.container,
                    isDark = colors.isDark,
                    appearance = appearance,
                    pressProgressState = renderedLensPress,
                    innerShadows = lensInnerShadows,
                    opticalCenter = {
                        contact.referenceCenter() - (renderedLensSurface.value?.bounds?.topLeft ?: Offset.Zero)
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
                                )
                            }
                        }
                    },
                )
            }
            // Re-render the complete source outline after its common affine transform.
            // Optical radii stay in pixels, and contact never replaces the source layer.
            Box(Modifier.matchParentSize().alpha(0f).then(materialRecordingModifier)) {
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
        CompositionLocalProvider(LocalBottomControlContact provides contact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
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
): BottomControlContactMember? {
    val contact = LocalBottomControlContact.current
    val currentBounds = rememberUpdatedState(visualBounds)
    val currentMaterialPress = rememberUpdatedState(materialPressProgress)
    val currentHighlight = rememberUpdatedState(highlight)
    val currentLensSourceActive = rememberUpdatedState(lensSourceActive)
    val currentLensSourceScale = rememberUpdatedState(lensSourceScale)
    val currentLensSourcePress = rememberUpdatedState(lensSourcePressProgress)
    val member = remember(contact) {
        contact?.let {
            BottomControlContactMember(
                it, currentBounds, currentMaterialPress, currentHighlight,
                currentLensSourceActive, currentLensSourceScale, currentLensSourcePress,
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
    private val visualBounds: State<(Size) -> Rect>,
    private val materialPressProgress: State<() -> Float>,
    private val highlight: State<(Size) -> GlassPressHighlight>,
    private val lensSourceActive: State<(() -> Boolean)?>,
    private val lensSourceScale: State<((Size) -> Offset)?>,
    private val lensSourcePress: State<(() -> Float)?>,
) {
    private var coordinates: LayoutCoordinates? = null
    private var bounds: Rect? by mutableStateOf(null)

    val connected: Boolean get() = contact.connected.value
    val sharesSurface: Boolean get() = contact.surface.value != null

    fun capsule(): LiquidContactCapsule? = bounds?.let {
        LiquidContactCapsule(it.size, visualBounds.value(it.size).translate(it.topLeft))
    }

    fun needsLensSource(): Boolean = lensSourceActive.value?.invoke() == true

    fun lensTransform(): BottomContactLensTransform? {
        val capsule = capsule() ?: return null
        val scale = lensSourceScale.value?.invoke(capsule.size) ?: return null
        if (!scale.x.isFinite() || !scale.y.isFinite() || scale.x <= 0f || scale.y <= 0f) return null
        return BottomContactLensTransform(scale, capsule.bounds.center)
    }

    fun lensSourcePressProgress(): Float? = lensSourcePress.value?.invoke()

    fun pressHighlight(): GlassPressHighlight = bounds?.let { highlight.value(it.size) } ?: GlassPressHighlight()

    fun materialPressProgress(): Float = materialPressProgress.value()

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
    private var cachedFirst: LiquidContactCapsule? = null
    private var cachedSecond: LiquidContactCapsule? = null
    private var cachedRefractionBand = Float.NaN
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
        for (member in members) {
            val capsule = member.capsule() ?: continue
            if (first == null) first = capsule
            else if (second == null) second = capsule
            else return null
        }
        val firstCapsule = first ?: return null
        val secondCapsule = second ?: return null
        val bounds = Rect(
            floor(minOf(firstCapsule.bounds.left, secondCapsule.bounds.left)),
            floor(minOf(firstCapsule.bounds.top, secondCapsule.bounds.top)),
            ceil(maxOf(firstCapsule.bounds.right, secondCapsule.bounds.right)),
            ceil(maxOf(firstCapsule.bounds.bottom, secondCapsule.bounds.bottom)),
        )
        val localFirst = firstCapsule.copy(bounds = firstCapsule.bounds.translate(-bounds.topLeft))
        val localSecond = secondCapsule.copy(bounds = secondCapsule.bounds.translate(-bounds.topLeft))
        // Both material passes share the exact same native contour and refraction field.
        // Callback rebinding or a common translation must not regenerate that contour.
        val scale = lensTransform.value?.scale ?: Offset(1f, 1f)
        val minimumScale = minOf(1f, scale.x, scale.y).coerceAtLeast(0.0001f)
        val band = maximumRefractionHeight / minimumScale
        val shape = cachedContactShape.takeIf {
            localFirst == cachedFirst && localSecond == cachedSecond && band == cachedRefractionBand
        } ?: (liquidContactShape(localFirst, localSecond, contactDistance, includeDisconnected = true)
            as? LiquidContactShape)?.withRefractionField(refractionField, band)?.also {
            cachedFirst = localFirst
            cachedSecond = localSecond
            cachedRefractionBand = band
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

    fun drawPressHighlight(
        scope: DrawScope,
        origin: Offset,
        sourceBounds: Rect = Rect(origin, scope.size),
        transform: BottomContactLensTransform? = null,
    ) {
        val active = members.maxByOrNull { it.pressHighlight().progress } ?: return
        val press = active.pressHighlight()
        val bounds = active.capsule()?.bounds ?: return
        val sourcePosition = bounds.topLeft +
            Offset(bounds.width * press.position.x, bounds.height * press.position.y)
        val position = (transform?.point(sourcePosition) ?: sourcePosition) - origin
        val localBounds = (transform?.bounds(sourceBounds) ?: sourceBounds).translate(-origin)
        val horizontalRadius = transform?.let { sourceBounds.size.minDimension * 1.5f * it.scale.x }
        // Keep the press wash and radial light on the full sampled outline. Its vertical
        // radius follows the transformed source bounds; the horizontal radius follows scaleX.
        val renderer = if (transform == null) pressRenderer else lensPressRenderer
        renderer.draw(scope, press.progress, position, sourceBounds = localBounds, horizontalRadius = horizontalRadius)
    }
}
