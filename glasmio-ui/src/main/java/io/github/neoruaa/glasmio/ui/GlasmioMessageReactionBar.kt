package io.github.neoruaa.glasmio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.LiquidContactCapsule
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.glass.LocalBottomControlContact
import io.github.neoruaa.glasmio.glass.LocalIosToolbarActionGroup
import io.github.neoruaa.glasmio.glass.LocalToolbarActionSeparation
import io.github.neoruaa.glasmio.glass.NavigationGlassMaterial
import io.github.neoruaa.glasmio.glass.NavigationGlassUnionShape
import io.github.neoruaa.glasmio.glass.render.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/** Host-owned reaction identity and label, with optional classic Tapback artwork. */
@Immutable
data class GlasmioMessageReaction(
    val id: String,
    val label: String,
    val emoji: String,
    val artwork: GlasmioMessageReactionArtwork? = null,
)

/** Tapback's pill and three tail lobes share one material and one animated silhouette. */
@Composable
internal fun GlasmioMessageReactionBar(
    reactions: List<GlasmioMessageReaction>,
    outgoing: Boolean,
    expanded: Boolean,
    onReaction: (GlasmioMessageReaction) -> Unit,
    modifier: Modifier = Modifier,
    selectedReaction: String? = null,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    tailCenterX: Dp? = null,
    onMoreReactions: (() -> Unit)? = null,
) {
    val colors = LocalGlassColors.current
    val density = LocalDensity.current
    val tailOnRight = outgoing == (LocalLayoutDirection.current == LayoutDirection.Ltr)
    val bridgeBuilder = remember { ReactionBridgeBuilder() }
    val channels = MessageContextMotion.Channel.entries
    var elapsed by remember { mutableLongStateOf(0L) }
    var entering by remember { mutableStateOf(true) }
    var initial by remember { mutableStateOf(FloatArray(channels.size)) }
    fun values(): FloatArray = FloatArray(channels.size) { index ->
        val p = MessageContextMotion.value(channels[index], elapsed, entering)
        if (entering) initial[index] + (1f - initial[index]) * p else initial[index] * p
    }
    LaunchedEffect(expanded) {
        initial = values()
        entering = expanded
        elapsed = 0L
        val start = withFrameNanos { it }
        val duration = if (expanded) MessageContextMotion.EnterDurationMillis else MessageContextMotion.ExitRetentionMillis
        while (elapsed < duration * 1_000_000) elapsed = withFrameNanos { it } - start
    }
    val progress = values()
    val scale = density.fontScale.coerceIn(.88f, 1.3f)
    val pillHeight = MessageContextMotion.PickerHeight * scale
    val totalHeight = pillHeight + MessageContextMotion.TailExtent
    BoxWithConstraints(modifier.fillMaxWidth().height(totalHeight.dp)) {
        val width = maxWidth.value
        val lobeX = tailCenterX?.value ?: if (tailOnRight) width - 74f else 74f
        val geometry = reactionGeometry(width, pillHeight, lobeX, tailOnRight, progress)
        CompositionLocalProvider(
            LocalIosToolbarActionGroup provides null,
            LocalToolbarActionSeparation provides null,
            LocalBottomControlContact provides null,
        ) {
            GlassSurface(
                Modifier.fillMaxSize(),
                backdrop = backdrop,
                shape = reactionMaterialShape(geometry, bridgeBuilder, density, pillHeight),
                style = GlassSurfaceStyle.Navigation,
                material = NavigationGlassMaterial.RegularMedium,
                navigationSurfaceAlphaMultiplier = 1f,
                contentAlignment = Alignment.TopStart,
            ) {
                Row(
                    Modifier.fillMaxWidth().height(pillHeight.dp)
                        .graphicsLayer { alpha = progress[MessageContextMotion.Channel.Content.ordinal].coerceIn(0f, 1f) }
                        .blur((3f * (1f - progress[MessageContextMotion.Channel.Content.ordinal]).coerceIn(0f, 1f)).dp, BlurredEdgeTreatment.Unbounded)
                        .horizontalScroll(rememberScrollState()).padding(horizontal = (10.12f * scale).dp),
                    horizontalArrangement = Arrangement.spacedBy((5f * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    reactions.forEach { reaction ->
                        val isSelected = reaction.id == selectedReaction
                        Box(
                            Modifier.size((44f * scale).dp).clip(CircleShape)
                                .background(if (isSelected) colors.accent.copy(alpha = .2f) else Color.Transparent)
                                .semantics { contentDescription = reaction.label; selected = isSelected }
                                .clickable(enabled = expanded, role = Role.Button) { onReaction(reaction) },
                            contentAlignment = Alignment.Center,
                        ) {
                            val artwork = reaction.artwork
                            if (artwork != null) {
                                TapbackArtwork(artwork, Modifier.fillMaxSize())
                            } else {
                                val stacked = '\n' in reaction.emoji
                                Text(
                                    reaction.emoji,
                                    fontSize = ((if (stacked) 16f else 28f) * scale / density.fontScale).sp,
                                    lineHeight = ((if (stacked) 16f else 32f) * scale / density.fontScale).sp,
                                    fontWeight = if (stacked) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    color = if (stacked) Color(0xFF00B9E7) else colors.content,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
                Box(
                    Modifier.offset { IntOffset(with(density) { (geometry.emoji.x - 22f).dp.roundToPx() }, with(density) { (geometry.emoji.y - 22f).dp.roundToPx() }) }
                        .size(44.dp).clip(CircleShape)
                        .blur((3f * (1f - progress[MessageContextMotion.Channel.EmojiBlur.ordinal]).coerceIn(0f, 1f)).dp, BlurredEdgeTreatment.Unbounded)
                        .graphicsLayer {
                            val p = progress[MessageContextMotion.Channel.EmojiSize.ordinal]
                            scaleX = (.5f + .5f * p).coerceAtLeast(0f); scaleY = scaleX
                            alpha = progress[MessageContextMotion.Channel.EmojiAlpha.ordinal].coerceIn(0f, 1f)
                        }
                        .then(if (onMoreReactions != null) Modifier.clickable(enabled = expanded, role = Role.Button, onClickLabel = "More reactions", onClick = onMoreReactions).semantics { contentDescription = "More reactions" } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    SfIcon("face.smiling", null, size = 28.dp, fontSize = (28f / density.fontScale).sp, tint = colors.secondaryContent)
                }
            }
        }
    }
}

private data class ReactionGeometry(
    val pill: Rect,
    val emoji: Offset, val emojiRadius: Float,
    val medium: Offset, val mediumRadius: Float,
    val small: Offset, val smallRadius: Float,
    val smoothness: Float,
)

/** SelectionLayout: 44pt emoji, 15.84pt medium, 7.92pt small; outgoing reverses the angles. */
private fun reactionGeometry(width: Float, height: Float, x: Float, right: Boolean, p: FloatArray): ReactionGeometry {
    fun value(c: MessageContextMotion.Channel) = p[c.ordinal]
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    val stage = value(MessageContextMotion.Channel.StageSize).coerceAtLeast(0f)
    val pillWidth = (height * stage + (width - height) * value(MessageContextMotion.Channel.Pill)).coerceIn(0f, width)
    val sourceX = x + if (right) 28f else -28f
    val sourceY = height + 5f
    val centerX = lerp(sourceX, width / 2f, value(MessageContextMotion.Channel.Pill))
    val centerY = lerp(sourceY, height / 2f, value(MessageContextMotion.Channel.Pill))
    val emoji = Offset(
        lerp(sourceX, x, value(MessageContextMotion.Channel.EmojiX)),
        lerp(sourceY, height + 14.3f, value(MessageContextMotion.Channel.EmojiY)),
    )
    // ChatKit first carries both dots toward the emoji lobe (0x1d0b843f4/0x1d0b84070),
    // then their staggered independent X/Y animations separate them from that shared path.
    val stagedTail = Offset(
        lerp(sourceX, x, value(MessageContextMotion.Channel.StageX)),
        lerp(sourceY, height + 14.3f, value(MessageContextMotion.Channel.StageY)),
    )
    val mediumAngle = Math.toRadians(if (right) 124.0 else 56.0)
    val smallAngle = Math.toRadians(if (right) 127.0 else 53.0)
    val medium = Offset(
        lerp(stagedTail.x, x + 23.584f * cos(mediumAngle).toFloat(), value(MessageContextMotion.Channel.MediumX)),
        lerp(stagedTail.y, height + 14.3f + 23.584f * sin(mediumAngle).toFloat(), value(MessageContextMotion.Channel.MediumY)),
    )
    val small = Offset(
        lerp(stagedTail.x, x + 23.584f * cos(mediumAngle).toFloat() + 14.96f * cos(smallAngle).toFloat(), value(MessageContextMotion.Channel.SmallX)),
        lerp(stagedTail.y, height + 14.3f + 23.584f * sin(mediumAngle).toFloat() + 14.96f * sin(smallAngle).toFloat(), value(MessageContextMotion.Channel.SmallY)),
    )
    return ReactionGeometry(
        Rect(centerX - pillWidth / 2f, centerY - height * stage / 2f, centerX + pillWidth / 2f, centerY + height * stage / 2f),
        // Bounds grow from zero independently of glyph alpha (0x1d0e17810/0x1d0e17a04).
        emoji, 22f * value(MessageContextMotion.Channel.EmojiSize).coerceAtLeast(0f),
        medium, 7.92f * value(MessageContextMotion.Channel.TailSize).coerceAtLeast(0f),
        small, 3.96f * value(MessageContextMotion.Channel.TailSize).coerceAtLeast(0f),
        lerp(3.9f, 1.66f, value(MessageContextMotion.Channel.Smoothness)),
    )
}

private fun reactionMaterialShape(geometry: ReactionGeometry, bridges: ReactionBridgeBuilder, density: Density, pillHeight: Float): Shape = with(density) {
    fun Rect.pixels() = Rect(left.dp.toPx(), top.dp.toPx(), right.dp.toPx(), bottom.dp.toPx())
    val capsules = buildList {
        val pill = geometry.pill.pixels()
        if (pill.width > 0f && pill.height > 0f) add(LiquidContactCapsule(pill.size, pill))
        for ((center, radius) in listOf(geometry.emoji to geometry.emojiRadius, geometry.medium to geometry.mediumRadius, geometry.small to geometry.smallRadius)) {
            if (radius <= 0f) continue
            val bounds = Rect((center.x - radius).dp.toPx(), (center.y - radius).dp.toPx(), (center.x + radius).dp.toPx(), (center.y + radius).dp.toPx())
            add(LiquidContactCapsule(bounds.size, bounds))
        }
    }
    NavigationGlassUnionShape(ReactionBalloonShape(geometry, bridges), capsules,
        geometry.smoothness.dp.toPx().coerceAtLeast(0f), pillHeight.dp.toPx())
}

private val ReactionPillShape = ContinuousRoundedRectangle(50)

private data class ReactionBalloonShape(val geometry: ReactionGeometry, val bridges: ReactionBridgeBuilder) : Shape {
    private var cachedDensity = Float.NaN
    private var cachedOutline: Outline? = null
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        cachedOutline?.takeIf { cachedDensity == density.density }?.let { return@with it }
        fun Rect.pixels() = Rect(left.dp.toPx(), top.dp.toPx(), right.dp.toPx(), bottom.dp.toPx())
        val pill = geometry.pill.pixels()
        val elements = ArrayList<Rect>(4)
        if (pill.width > 0f && pill.height > 0f) elements.add(pill)
        var path = Path().apply {
            if (pill.width > 0f && pill.height > 0f) {
                when (val outline = ReactionPillShape.createOutline(pill.size, layoutDirection, Density(1f))) {
                    is Outline.Generic -> addPath(outline.path, pill.topLeft)
                    is Outline.Rounded -> addPath(Path().apply { addRoundRect(outline.roundRect) }, pill.topLeft)
                    is Outline.Rectangle -> addRect(outline.rect.translate(pill.topLeft))
                }
            }
        }
        for ((center, radius) in listOf(geometry.emoji to geometry.emojiRadius, geometry.medium to geometry.mediumRadius, geometry.small to geometry.smallRadius)) {
            if (radius <= 0f) continue
            val bounds = Rect((center.x - radius).dp.toPx(), (center.y - radius).dp.toPx(), (center.x + radius).dp.toPx(), (center.y + radius).dp.toPx())
            elements.add(bounds)
            val circle = Path().apply { addOval(bounds) }
            path = Path.combine(PathOperation.Union, path, circle)
        }
        val smoothness = geometry.smoothness.dp.toPx().coerceAtLeast(0f)
        for (i in elements.indices) for (j in i + 1 until elements.size) {
            bridges.patch(elements[i], elements[j], smoothness)?.let {
                path = Path.combine(PathOperation.Union, path, it)
            }
        }
        Outline.Generic(path).also { cachedDensity = density.density; cachedOutline = it }
    }
}

/** Local zero-contour patches use the same normal-weighted CASDF union as navigation glass.
 * Only neighboring boundaries are sampled; the long pill and disconnected dots retain exact arcs.
 */
private class ReactionBridgeBuilder {
    private var samples = FloatArray(0)
    private val first = FloatArray(3)
    private val second = FloatArray(3)

    private fun field(rect: Rect, x: Float, y: Float, result: FloatArray) {
        val r = min(rect.width, rect.height) * .5f
        val dx = x - rect.center.x
        val dy = y - rect.center.y
        val qx = abs(dx) - (rect.width * .5f - r)
        val qy = abs(dy) - (rect.height * .5f - r)
        val px = max(qx, 0f)
        val py = max(qy, 0f)
        val length = sqrt(px * px + py * py)
        result[0] = length + min(max(qx, qy), 0f) - r
        if (length > .000001f) {
            result[1] = px / length * if (dx < 0f) -1f else 1f
            result[2] = py / length * if (dy < 0f) -1f else 1f
        } else {
            result[1] = if (qx > qy) (if (dx < 0f) -1f else 1f) else 0f
            result[2] = if (qx > qy) 0f else (if (dy < 0f) -1f else 1f)
        }
    }

    private fun distance(a: Rect, b: Rect, clip: Rect, k: Float, x: Float, y: Float): Float {
        field(a, x, y, first)
        field(b, x, y, second)
        val radius = k * (.5f - .5f * (first[1] * second[1] + first[2] * second[2])).coerceIn(0f, 1f)
        val h = if (radius <= .000001f) (if (first[0] < second[0]) 1f else 0f)
            else (.5f + .5f * (second[0] - first[0]) / radius).coerceIn(0f, 1f)
        val union = second[0] + (first[0] - second[0]) * h - radius * h * (1f - h)
        // Intersect with a small neck window. Its straight cuts lie inside the exact base union.
        val window = max(max(clip.left - x, x - clip.right), max(clip.top - y, y - clip.bottom))
        return max(union, window)
    }

    fun patch(a: Rect, b: Rect, k: Float): Path? {
        if (k <= .000001f) return null
        val guard = k + 1f
        val clip = Rect(max(a.left, b.left) - guard, max(a.top, b.top) - guard,
            min(a.right, b.right) + guard, min(a.bottom, b.bottom) + guard)
        if (clip.width <= 0f || clip.height <= 0f) return null
        // No smoothing is possible when the nearest exterior gap exceeds k/4.
        val r = min(a.width, a.height) * .5f
        val axisLeft = a.left + r
        val axisRight = a.right - r
        val axisTop = a.top + r
        val axisBottom = a.bottom - r
        val cx = b.center.x.coerceIn(min(axisLeft, axisRight), max(axisLeft, axisRight))
        val cy = b.center.y.coerceIn(min(axisTop, axisBottom), max(axisTop, axisBottom))
        val dx = b.center.x - cx
        val dy = b.center.y - cy
        if (sqrt(dx * dx + dy * dy) > min(a.width, a.height) * .5f + min(b.width, b.height) * .5f + k * .25f) return null
        val step = .75f
        val bounds = Rect(clip.left - step, clip.top - step, clip.right + step, clip.bottom + step)
        val columns = ceil(bounds.width / step).toInt().coerceAtLeast(2)
        val rows = ceil(bounds.height / step).toInt().coerceAtLeast(2)
        val stride = columns + 1
        val count = stride * (rows + 1)
        if (samples.size < count) samples = FloatArray(count)
        val sx = bounds.width / columns
        val sy = bounds.height / rows
        for (y in 0..rows) for (x in 0..columns) {
            samples[y * stride + x] = distance(a, b, clip, k, bounds.left + x * sx, bounds.top + y * sy)
        }
        val vertices = HashMap<Long, Offset>()
        val neighbors = HashMap<Long, MutableList<Long>>()
        fun link(firstKey: Long, secondKey: Long) {
            neighbors.getOrPut(firstKey) { ArrayList(2) }.add(secondKey)
            neighbors.getOrPut(secondKey) { ArrayList(2) }.add(firstKey)
        }
        val indices = IntArray(4)
        val keys = LongArray(4)
        val crossings = IntArray(4)
        for (y in 0 until rows) for (x in 0 until columns) {
            indices[0] = y * stride + x; indices[1] = indices[0] + 1
            indices[3] = (y + 1) * stride + x; indices[2] = indices[3] + 1
            keys[0] = indices[0].toLong() * 2; keys[1] = indices[1].toLong() * 2 + 1
            keys[2] = indices[3].toLong() * 2; keys[3] = keys[0] + 1
            var crossingCount = 0
            for (edge in 0..3) {
                val start = indices[edge]
                val end = indices[(edge + 1) % 4]
                val d0 = samples[start]
                val d1 = samples[end]
                if ((d0 < 0f) == (d1 < 0f)) continue
                crossings[crossingCount++] = edge
                val key = keys[edge]
                if (key !in vertices) {
                    val t = (d0 / (d0 - d1)).coerceIn(0f, 1f)
                    val ax = bounds.left + (start % stride) * sx
                    val ay = bounds.top + (start / stride) * sy
                    val bx = bounds.left + (end % stride) * sx
                    val by = bounds.top + (end / stride) * sy
                    vertices[key] = Offset(ax + (bx - ax) * t, ay + (by - ay) * t)
                }
            }
            if (crossingCount == 2) link(keys[crossings[0]], keys[crossings[1]])
            else if (crossingCount == 4) {
                val centerInside = distance(a, b, clip, k, bounds.left + (x + .5f) * sx, bounds.top + (y + .5f) * sy) < 0f
                for (corner in 0..3) if ((samples[indices[corner]] < 0f) != centerInside) {
                    link(keys[(corner + 3) % 4], keys[corner])
                }
            }
        }
        if (vertices.isEmpty()) return null
        val visited = HashSet<Long>()
        return Path().apply {
            for (start in vertices.keys) {
                if (start in visited) continue
                val origin = vertices.getValue(start)
                moveTo(origin.x, origin.y)
                var previous = -1L
                var current = start
                while (visited.add(current)) {
                    val next = neighbors[current]?.firstOrNull { it != previous } ?: break
                    if (next == start) { close(); break }
                    val point = vertices.getValue(next)
                    lineTo(point.x, point.y)
                    previous = current
                    current = next
                }
            }
        }
    }
}
