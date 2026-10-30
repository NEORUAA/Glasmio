package io.github.neoruaa.glasmio.scaffold

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberLayerBackdrop
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.ui.*
import io.github.neoruaa.glasmio.icons.LocalSfToolbarSymbols
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext

/** Navigation adapters publish their actual clock, keeping time separate from gesture progress. */
interface GlasmioHeaderMotion { val frame: State<GlasmioNavigationFrame> }
enum class GlasmioNavigationOperation { Push, Pop, Tab }
enum class GlasmioNavigationPhase { Idle, Tracking, Settling, Canceling }
data class GlasmioNavigationFrame(
    val transitionId: Long,
    val fromEntryKey: Any,
    val toEntryKey: Any,
    val operation: GlasmioNavigationOperation = GlasmioNavigationOperation.Push,
    val phase: GlasmioNavigationPhase = GlasmioNavigationPhase.Idle,
    val playTimeNanos: Long = 0L,
    val gestureProgress: Float = 0f,
    /** Gesture velocity in progress per second. */
    val gestureVelocity: Float = 0f,
)

/** The getter reads the entry's own list/grid state, including when it is off screen. */
class GlasmioHeaderScrollState(
    val collapseProgress: () -> Float,
    val scrollEdgeProgress: () -> Float = { 0f },
)
data class GlasmioHeaderSpec(
    val title: String,
    val subtitle: String? = null,
    val style: GlasmioTopBarStyle = GlasmioTopBarStyle.Default,
    val leading: List<GlasmioHeaderGroup> = emptyList(),
    val trailing: List<GlasmioHeaderGroup> = emptyList(),
    val scrollState: GlasmioHeaderScrollState? = null,
    /** Page appearance for shared button surfaces; page content keeps its own theme. */
    val colors: GlassColors? = null,
)
data class GlasmioHeaderGroup(
    val key: Any,
    val actions: List<GlasmioHeaderAction>,
    val style: GlasmioToolbarActionStyle = GlasmioToolbarActionStyle.Merged,
) {
    init { require(actions.map { it.key }.distinct().size == actions.size) { "Duplicate header action keys" } }
}
data class GlasmioHeaderAction(
    val key: Any,
    val visualKey: Any,
    val contentDescription: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val menuAnchor: GlasmioMenuAnchorState? = null,
    val content: @Composable () -> Unit,
)

private data class HeaderEndpointKey(val group: Any, val actions: List<LayerKey>)
private fun endpointKeys(groups: List<GlasmioHeaderGroup>): List<HeaderEndpointKey> =
    groups.filter { it.actions.isNotEmpty() }.map { group ->
        HeaderEndpointKey(group.key, group.actions.map { LayerKey(it.key, it.visualKey) })
    }

private data class PageTitleSpec(
    val title: String,
    val subtitle: String?,
    val style: GlasmioTopBarStyle,
    val scrollState: GlasmioHeaderScrollState?,
    val leading: List<HeaderEndpointKey>,
    val trailing: List<HeaderEndpointKey>,
    val colors: GlassColors?,
)
private fun GlasmioHeaderSpec.pageTitleSpec() = PageTitleSpec(
    title, subtitle, style, scrollState, endpointKeys(leading), endpointKeys(trailing), colors,
)

private data class HeaderEndpointWidth(
    val keys: List<HeaderEndpointKey>,
    val width: Int,
    val density: Float,
    val fontScale: Float,
)
private class PageTitleEntry {
    var spec by mutableStateOf<PageTitleSpec?>(null)
    var leading by mutableStateOf<HeaderEndpointWidth?>(null)
    var trailing by mutableStateOf<HeaderEndpointWidth?>(null)
}

/** Stores title data only; action callbacks and composed button content stay in the shared bar. */
private class PageTitleRegistry {
    private val entries = mutableMapOf<Any, PageTitleEntry>()
    private val mounted = mutableMapOf<Any, Int>()
    private var active = emptySet<Any>()
    fun entry(key: Any): PageTitleEntry = entries.getOrPut(key) { PageTitleEntry() }
    fun mount(key: Any) { mounted[key] = (mounted[key] ?: 0) + 1 }
    fun unmount(key: Any) {
        val count = (mounted[key] ?: 1) - 1
        if (count == 0) mounted.remove(key) else mounted[key] = count
        prune()
    }
    fun update(frame: GlasmioNavigationFrame, source: GlasmioHeaderSpec, target: GlasmioHeaderSpec) {
        active = setOf(frame.fromEntryKey, frame.toEntryKey)
        entry(frame.fromEntryKey).spec = source.pageTitleSpec()
        entry(frame.toEntryKey).spec = target.pageTitleSpec()
        prune()
    }
    fun publish(key: Any, leading: Boolean, groups: List<GlasmioHeaderGroup>, width: Int?, density: Density) {
        if (width == null || key !in active && key !in mounted) return
        val value = HeaderEndpointWidth(endpointKeys(groups), width, density.density, density.fontScale)
        val entry = entry(key)
        if (leading) entry.leading = value else entry.trailing = value
    }
    private fun prune() { entries.keys.retainAll(active + mounted.keys) }
}

private val LocalPageTitles = staticCompositionLocalOf<PageTitleRegistry?> { null }

/**
 * Place after a page's body inside its navigation entry. The title follows that page's transform,
 * while its space is reserved from that entry's measured button endpoints, not the shared morph.
 */
@Composable
fun GlasmioPageTitle(entryKey: Any, modifier: Modifier = Modifier) {
    val registry = requireNotNull(LocalPageTitles.current) { "GlasmioPageTitle requires GlasmioNavigationScaffold" }
    val entry = remember(registry, entryKey) { registry.entry(entryKey) }
    DisposableEffect(registry, entryKey) {
        registry.mount(entryKey)
        onDispose { registry.unmount(entryKey) }
    }
    val spec = entry.spec ?: return
    val density = LocalDensity.current
    fun width(keys: List<HeaderEndpointKey>, measured: HeaderEndpointWidth?): Int? = when {
        keys.isEmpty() -> 0
        measured?.keys == keys && measured.density == density.density && measured.fontScale == density.fontScale -> measured.width
        else -> null
    }
    val leading = width(spec.leading, entry.leading) ?: return
    val trailing = width(spec.trailing, entry.trailing) ?: return
    val startsAtLeading = spec.style == GlasmioTopBarStyle.TwoLineLeading || spec.style == GlasmioTopBarStyle.CompactLargeTitle
    val widest = maxOf(leading, trailing)
    val start = with(density) { (if (startsAtLeading) leading else widest).toDp() } +
        if ((if (startsAtLeading) leading else widest) > 0) 8.dp else 0.dp
    val end = with(density) { (if (startsAtLeading) trailing else widest).toDp() } +
        if ((if (startsAtLeading) trailing else widest) > 0) 8.dp else 0.dp
    val colors = spec.colors ?: LocalGlassColors.current
    CompositionLocalProvider(LocalGlassColors provides colors, LocalContentColor provides colors.content) {
        Box(modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = IosPageHorizontalPadding)) {
            Column(
                Modifier.align(if (startsAtLeading) Alignment.CenterStart else Alignment.Center)
                    .fillMaxWidth().padding(start = start, end = end)
                    .graphicsLayer { alpha = (spec.scrollState?.collapseProgress?.invoke() ?: 1f).coerceIn(0f, 1f) },
                horizontalAlignment = if (startsAtLeading) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                Text(spec.title, style = if (spec.style == GlasmioTopBarStyle.CompactLargeTitle) IosTypography.largeTitle else IosTypography.headline,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                spec.subtitle?.let { Text(it, style = IosTypography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** Shared controls remain outside NavDisplay transforms; each page owns its scroll-edge blur. */
@Composable
fun GlasmioNavigationScaffold(
    motion: GlasmioHeaderMotion,
    header: @Composable (entryKey: Any) -> GlasmioHeaderSpec,
    modifier: Modifier = Modifier,
    bottomBar: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val rawFrame = motion.frame
    val frameSource = remember { mutableStateOf(rawFrame, referentialEqualityPolicy()) }
    frameSource.value = rawFrame
    val frame by remember {
        derivedStateOf(structuralEqualityPolicy()) {
            // Header geometry uses its physical frame clock; public readers keep the full motion.
            frameSource.value.value.copy(playTimeNanos = 0L, gestureVelocity = 0f)
        }
    }
    val source = key(frame.fromEntryKey) { header(frame.fromEntryKey) }
    val target = key(frame.toEntryKey) { header(frame.toEntryKey) }
    val titles = remember { PageTitleRegistry() }
    SideEffect { titles.update(frame, source, target) }
    val status = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val padding = PaddingValues(start = IosPageHorizontalPadding, top = status + 72.dp, end = IosPageHorizontalPadding)
    CompositionLocalProvider(LocalPageTitles provides titles) {
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).trackBackdropPosition(backdrop)) { content(padding) }
        GlasmioBackdropProvider(backdrop) {
            val owner = if (frame.phase == GlasmioNavigationPhase.Tracking || frame.phase == GlasmioNavigationPhase.Canceling) source else target
            CompositionLocalProvider(LocalGlassColors provides (owner.colors ?: LocalGlassColors.current)) {
            GlasmioNavigationHeader(source, target, frame, titles, Modifier.fillMaxWidth().padding(top = status))
            }
            bottomBar()
        }
    }
    }
}

/**
 * Page-owned body and scroll-edge blur. Insets are supplied by the navigation host.
 * The untouched background and faded foreground feed [backdrop]; the complete blur profile is
 * overlaid afterward so it follows this page's transitions without restoring unfaded content.
 * Descendant glass controls keep their existing backdrop provider.
 */
@Composable
fun GlasmioPageBody(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color = LocalGlassColors.current.groupedBackground,
    backgroundBrush: androidx.compose.ui.graphics.Brush? = null,
    topBarBlurProgress: Float = 0f,
    backdrop: LayerBackdrop = rememberLayerBackdrop(),
    background: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    val appearance = LocalGlassAppearance.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = statusBarHeight + 62.dp
    val progress = topBarBlurProgress.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().glassBackdropSource(backdrop).trackBackdropPosition(backdrop),
        ) {
            Box(
                Modifier.matchParentSize()
                    .then(if (backgroundBrush != null) Modifier.background(backgroundBrush) else Modifier.background(backgroundColor)),
            ) { background?.invoke(this) }
            Box(Modifier.fillMaxSize().scrollEdgeContentFade(progress, headerHeight, headerTop = statusBarHeight)) {
                GlasmioOverscrollHost { content(contentPadding) }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(headerHeight + if (appearance.usesBackdrop) 34.dp else 0.dp)
                .then(if (appearance.usesBackdrop) {
                    Modifier.progressiveBlurBackdrop(backdrop, progress)
                } else {
                    Modifier.graphicsLayer { alpha = progress }.background(backgroundColor.copy(alpha = 1f))
                }),
        )
    }
}

private data class LayerKey(val action: Any, val visual: Any)
private class HeaderLayer(var action: GlasmioHeaderAction, visible: Float) {
    var content = action.content
    val visibility = HeaderTrack(visible)
    val x = HeaderTrack(0f)
    var width = 0
    var positioned = false
}
private class HeaderGroupPresentation {
    val width = HeaderTrack(0f)
    val presence = HeaderTrack(0f)
    val pulse = HeaderPulse()
    val kick = HeaderTrack(0f)
    var kickRequested = false
    var returnIn = 0f
    val layers = linkedMapOf<LayerKey, HeaderLayer>()
    val measuredWidths = mutableMapOf<LayerKey, Int>()
    var initialized = false
    var signature: List<LayerKey>? = null
    var lastTick = 0L
    var durationScale = 1f
    fun tick(now: Long, reduced: Boolean) {
        // The shared Compose frame clock is monotonic. Navigation playTimeNanos is a seek
        // coordinate (and may ease or reverse), so it must never be integrated as elapsed time.
        val dt = (if (lastTick == 0L) 0f else (now - lastTick) / 1e9f / durationScale.coerceAtLeast(.01f)).coerceIn(0f, 1f)
        lastTick = now
        if (durationScale == 0f) {
            (listOf(width, presence, kick) + layers.values.flatMap { listOf(it.visibility, it.x) }).forEach { it.snap(it.target) }
            pulse.snap()
            returnIn = 0f
        }
        width.advance(dt, reduced); presence.advance(dt, reduced); pulse.advance(dt, reduced)
        kick.advance(dt, reduced)
        if (returnIn > 0f) {
            returnIn -= dt
            if (returnIn <= 0f) { kick.target = 0f; kick.spring = HeaderSprings.returning; kick.delay = 0f }
        }
        layers.values.forEach { it.visibility.advance(dt, reduced); it.x.advance(dt, reduced) }
        layers.entries.removeAll { it.value.visibility.target == 0f && it.value.visibility.settled }
    }
    val settled get() = width.settled && presence.settled && pulse.settled && kick.settled && returnIn <= 0f && layers.values.all { it.visibility.settled && it.x.settled }
}

@Composable
private fun GlasmioNavigationHeader(
    source: GlasmioHeaderSpec,
    target: GlasmioHeaderSpec,
    frame: GlasmioNavigationFrame,
    titles: PageTitleRegistry,
    modifier: Modifier,
) {
    val tracking = frame.phase == GlasmioNavigationPhase.Tracking
    val canceling = frame.phase == GlasmioNavigationPhase.Canceling
    val owner = if (tracking || canceling) source else target
    val progress = when { tracking -> frame.gestureProgress.coerceIn(0f, 1f); canceling -> 0f; else -> 1f }
    val retainedLeading = remember { linkedMapOf<Any, GlasmioHeaderGroup>() }
    val retainedTrailing = remember { linkedMapOf<Any, GlasmioHeaderGroup>() }
    val density = LocalDensity.current
    // Retained groups are disposed only after their own tracks settle, not when the body ends.
    CompositionLocalProvider(LocalContentColor provides LocalGlassColors.current.content) {
        Box(modifier.height(62.dp).padding(horizontal = IosPageHorizontalPadding)) {
            HeaderSide(source.leading, target.leading, owner.leading, retainedLeading, progress, tracking,
                Modifier.align(Alignment.CenterStart)) { old, new ->
                titles.publish(frame.fromEntryKey, true, source.leading, old, density)
                titles.publish(frame.toEntryKey, true, target.leading, new, density)
            }
            HeaderSide(source.trailing, target.trailing, owner.trailing, retainedTrailing, progress, tracking,
                Modifier.align(Alignment.CenterEnd)) { old, new ->
                titles.publish(frame.fromEntryKey, false, source.trailing, old, density)
                titles.publish(frame.toEntryKey, false, target.trailing, new, density)
            }
        }
    }
}

@Composable
private fun HeaderSide(
    from: List<GlasmioHeaderGroup>, to: List<GlasmioHeaderGroup>, owner: List<GlasmioHeaderGroup>,
    retained: MutableMap<Any, GlasmioHeaderGroup>, progress: Float, tracking: Boolean,
    modifier: Modifier, onEndpointWidths: (Int?, Int?) -> Unit,
) {
    require(from.map { it.key }.distinct().size == from.size && to.map { it.key }.distinct().size == to.size) { "Duplicate header group keys" }
    var retainedRevision by remember { mutableIntStateOf(0) }
    val measured = remember { mutableMapOf<Any, GroupEndpointWidths>() }
    val density = LocalDensity.current
    fun total(groups: List<GlasmioHeaderGroup>, old: Boolean): Int? {
        val active = groups.filter { it.actions.isNotEmpty() }
        if (active.isEmpty()) return 0
        var width = with(density) { 8.dp.roundToPx() } * (active.size - 1)
        active.forEach { group ->
            val endpoint = measured[group.key] ?: return null
            if (endpoint.density != density.density || endpoint.fontScale != density.fontScale) return null
            val keys = group.actions.map { LayerKey(it.key, it.visualKey) }
            if ((if (old) endpoint.oldKeys else endpoint.newKeys) != keys) return null
            width += if (old) endpoint.oldWidth else endpoint.newWidth
        }
        return width
    }
    fun publish() { onEndpointWidths(total(from, true), total(to, false)) }
    SideEffect { publish() }
    retainedRevision
    (from + to).forEach { retained[it.key] = it }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        retained.toMap().forEach { (groupKey, fallback) -> key(groupKey) {
            val old = from.firstOrNull { it.key == groupKey }
            val new = to.firstOrNull { it.key == groupKey }
            HeaderGroup(old, new, owner.firstOrNull { it.key == groupKey }, fallback, progress, tracking,
                onEndpoints = { oldWidth, newWidth ->
                    measured[groupKey] = GroupEndpointWidths(
                        old?.actions.orEmpty().map { LayerKey(it.key, it.visualKey) },
                        new?.actions.orEmpty().map { LayerKey(it.key, it.visualKey) },
                        oldWidth, newWidth, density.density, density.fontScale,
                    )
                    publish()
                },
                onGone = {
                    retained.remove(groupKey)
                    measured.remove(groupKey)
                    retainedRevision++
                },
            )
        } }
    }
}

private data class GroupEndpointWidths(
    val oldKeys: List<LayerKey>, val newKeys: List<LayerKey>,
    val oldWidth: Int, val newWidth: Int,
    val density: Float, val fontScale: Float,
)

@Composable
private fun HeaderGroup(
    from: GlasmioHeaderGroup?, to: GlasmioHeaderGroup?, owner: GlasmioHeaderGroup?, fallback: GlasmioHeaderGroup,
    progress: Float, tracking: Boolean, onEndpoints: (Int, Int) -> Unit, onGone: () -> Unit,
) {
    val state = remember { HeaderGroupPresentation() }
    var revision by remember { mutableIntStateOf(0) }
    var layoutRevision by remember { mutableIntStateOf(0) }
    val targetActions = to?.actions.orEmpty()
    val fromKeys = from?.actions.orEmpty().map { LayerKey(it.key, it.visualKey) }
    val toKeys = to?.actions.orEmpty().map { LayerKey(it.key, it.visualKey) }
    val signature = if (progress == 0f && !tracking) fromKeys else toKeys
    val reduced = remember { mutableStateOf(false) }
    val changed = state.signature != signature
    if (changed) {
        if (state.signature != null) { state.pulse.trigger(); state.kickRequested = true }
        state.signature = signature
    }
    (from?.actions.orEmpty() + targetActions).forEach { action ->
        val identity = LayerKey(action.key, action.visualKey)
        val desiredVisibility = (if (identity in fromKeys) 1f - progress else 0f) +
            (if (identity in toKeys) progress else 0f)
        val existing = state.layers[identity]
        if (existing != null) existing.action = action
        else if (desiredVisibility > 0f || identity !in state.measuredWidths) {
            state.layers[identity] = HeaderLayer(action, if (!state.initialized && identity in fromKeys) 1f else 0f)
        }
    }
    state.measuredWidths.keys.retainAll((fromKeys + toKeys + state.layers.keys).toSet())
    state.layers.forEach { (identity, layer) ->
        val start = if (identity in fromKeys) 1f else 0f
        val end = if (identity in toKeys) 1f else 0f
        val targetVisibility = start + (end - start) * progress
        if (targetVisibility > 0f) {
            // Stable visual identities still need the current owner's label, theme and state.
            // Only an exiting layer freezes its foreground while its recorded pixels fade.
            val liveAction = owner?.actions?.firstOrNull {
                LayerKey(it.key, it.visualKey) == identity
            } ?: layer.action
            layer.content = liveAction.content
        }
        layer.visibility.target = targetVisibility
        layer.visibility.spring = if (tracking) HeaderSprings.interactive else HeaderSprings.geometry
    }
    state.presence.target = (if (from != null) 1f else 0f) * (1f - progress) + (if (to != null) 1f else 0f) * progress
    SideEffect {
        if (state.initialized && state.settled && state.presence.target == 0f && from == null && to == null) onGone()
    }
    LaunchedEffect(state, signature, progress, layoutRevision) {
        val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        state.durationScale = scale
        reduced.value = scale == 0f
        do {
            withFrameNanos { now -> state.tick(now, reduced.value); revision++ }
        } while (!state.settled)
        state.lastTick = 0L
    }
    revision // Recompose shell, foreground and geometry from one presentation frame.
    val layerEntries = state.layers.toList()
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val pointLimit = with(density) { 16.dp.toPx() }
    val height = with(density) { 44.dp.toPx() }
    val presence = state.presence.value.coerceIn(0f, 1f)
    val sx = if (reduced.value) 1f else state.pulse.scale(state.width.value, pointLimit, true)
    val sy = if (reduced.value) 1f else state.pulse.scale(height, pointLimit, false)
    val appearScale = if (reduced.value) 1f else 1.2f - .2f * presence
    GlasmioToolbarActionHost(
        modifier = Modifier.testTag("GlasmioHeaderGroup:${fallback.key}").graphicsLayer {
            alpha = presence
            // Group opacity allocates an offscreen layer. Preserve the enlarged shell and
            // its shadow outside the layout slot throughout appearance and disappearance.
            outsets = LayerOutsets(64.dp, 64.dp, 64.dp, 64.dp)
        },
        style = (owner ?: to ?: from ?: fallback).style,
        presentationBounds = { Rect(0f, 0f, state.width.value.coerceAtLeast(0f), height) },
        presentationTransform = {
            val xScale = sx * appearScale
            val yScale = sy * appearScale
            ToolbarActionTransform(xScale, yScale,
                state.width.value * (1f - xScale) / 2f + (if (reduced.value) 0f else state.kick.value * if (direction == LayoutDirection.Rtl) -1f else 1f),
                height * (1f - yScale) / 2f)
        },
    ) {
    val toolbarGroup = LocalIosToolbarActionGroup.current
    SubcomposeLayout { constraints ->
        revision // Observe all physical tracks in measurement and drawing in the same frame.
        val visualPlaceables = layerEntries.map { (identity, layer) ->
            subcompose(identity) {
                val foreground = rememberGraphicsLayer()
                val recorded = remember { booleanArrayOf(false) }
                Box(Modifier
                    .defaultMinSize(minWidth = 44.dp).height(44.dp).padding(horizontal = 10.dp)
                    .graphicsLayer {
                        val member = toolbarGroup?.members?.firstOrNull()
                        if (member != null) {
                            val x = if (direction == LayoutDirection.Rtl) state.width.value - layer.x.value - layer.width else layer.x.value
                            toolbarGroup.applyTransform(this, Rect(x, 0f, x + layer.width, height), toolbarGroup.pressTransform(member))
                        }
                    }
                    .graphicsLayer {
                        // RenderEffect also needs room for the glyph's blur outside its slot.
                        outsets = LayerOutsets(32.dp, 32.dp, 32.dp, 32.dp)
                        val p = layer.visibility.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = if (reduced.value) 1f else .2f + .8f * p
                        scaleY = if (reduced.value) 1f else .2f + .8f * p
                        val blur = if (reduced.value) 0f else 10.dp.toPx() * (1f - p).coerceIn(0f, 1f)
                        renderEffect = if (blur > .01f) RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL).asComposeRenderEffect() else null
                    }.drawWithContent {
                        if (!recorded[0] || layer.visibility.target > 0f) {
                            foreground.record { this@drawWithContent.drawContent() }
                            recorded[0] = true
                        }
                        drawLayer(foreground)
                    }.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalSfToolbarSymbols provides true, LocalGlassContentColor provides LocalGlassColors.current.content) {
                        layer.content()
                    }
                }
            }.single().measure(if (layer.visibility.target == 0f && layer.width > 0) {
                Constraints.fixed(layer.width, height.roundToInt())
            } else Constraints(maxWidth = constraints.maxWidth, maxHeight = height.roundToInt()))
                .also { layer.width = it.width; state.measuredWidths[identity] = it.width }
        }
        fun endpoint(keys: List<LayerKey>): Pair<Float, Map<Any, Float>> {
            var x = 0f
            val positions = linkedMapOf<Any, Float>()
            keys.forEachIndexed { index, identity ->
                positions[identity.action] = x
                x += state.measuredWidths[identity] ?: 0
                if (index < keys.lastIndex) x += 8.dp.toPx()
            }
            return x to positions
        }
        val (oldWidth, oldX) = endpoint(fromKeys)
        val (newWidth, newX) = endpoint(toKeys)
        onEndpoints(oldWidth.roundToInt(), newWidth.roundToInt())
        if (state.kickRequested) {
            // Compatibility policy: only semantic matches contribute local member offsets.
            // UIKit's complete offset-vector membership rules remain unverified.
            val offsets = oldX.keys.intersect(newX.keys).map { newX.getValue(it) - oldX.getValue(it) }
            state.kick.target = if (offsets.isEmpty()) 0f else offsets.average().toFloat() / 6f
            state.kick.spring = HeaderSprings.kick
            state.kick.delay = .05f
            state.returnIn = .15f
            state.kickRequested = false
        }
        val width = when {
            from == null && to == null -> state.width.target
            from == null -> newWidth
            to == null -> oldWidth
            else -> oldWidth + (newWidth - oldWidth) * progress
        }
        var layoutChanged = state.width.target != width
        state.width.target = width
        state.width.spring = if (tracking) HeaderSprings.interactive else HeaderSprings.geometry
        layerEntries.forEach { (identity, layer) ->
            val start = oldX[identity.action] ?: newX[identity.action] ?: layer.x.target
            val end = newX[identity.action] ?: oldX[identity.action] ?: layer.x.target
            val targetX = start + (end - start) * progress
            layoutChanged = layoutChanged || layer.x.target != targetX
            layer.x.target = targetX
            if (!layer.positioned) { layer.x.snap(layer.x.target); layer.positioned = true }
        }
        if (!state.initialized) {
            state.width.snap(width)
            val targetPresence = state.presence.target
            state.presence.snap(if (from == null) 0f else 1f)
            state.presence.target = targetPresence
            state.initialized = true
        }
        if (layoutChanged) layoutRevision++
        val hitActions = owner?.actions ?: (to ?: from ?: fallback).actions
        val hits = hitActions.map { action ->
            val layer = state.layers[LayerKey(action.key, action.visualKey)]
            val ownsInteraction = owner?.actions?.any { it.key == action.key } == true
            val hit = subcompose("hit" to action.key) {
                GlassSurface(
                    modifier = Modifier.fillMaxSize()
                        .then(if (ownsInteraction) action.menuAnchor?.let { Modifier.glasmioMenuAnchor(it) } ?: Modifier else Modifier)
                        .then(if (ownsInteraction) Modifier else Modifier.clearAndSetSemantics {}),
                    style = GlassSurfaceStyle.Navigation,
                    enabled = ownsInteraction && action.enabled,
                    onClick = if (ownsInteraction) action.onClick else null,
                ) {
                    if (ownsInteraction) Box(Modifier.matchParentSize().semantics { contentDescription = action.contentDescription })
                }
            }.single().measure(Constraints.fixed(layer?.width ?: 44.dp.roundToPx(), height.roundToInt()))
            (layer?.x?.value ?: 0f) to hit
        }
        layout(state.width.value.roundToInt().coerceIn(0, constraints.maxWidth), height.roundToInt()) {
            visualPlaceables.forEachIndexed { index, placeable ->
                val layer = layerEntries[index].second
                placeable.placeRelative(layer.x.value.roundToInt(), 0)
            }
            hits.forEach { (x, placeable) -> placeable.placeRelative(x.roundToInt(), 0) }
        }
    }
    }
}
