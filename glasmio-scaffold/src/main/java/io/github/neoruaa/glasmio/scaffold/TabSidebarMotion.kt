package io.github.neoruaa.glasmio.scaffold

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * UIKit's background-only intermediate capsule and AnimationKit content destinations.
 * Geometry remains in physical coordinates at the API boundary; normalized tracks keep the
 * native spring tolerance independent of the viewport's pixel density.
 */
internal class TabSidebarMotion(initialExpanded: Boolean) {
    var running by mutableStateOf(false)
        private set
    var expanded by mutableStateOf(initialExpanded)
        private set

    /** -1 is resting, 0 is the intermediate capsule, 1 the gated target, 2 a direct target. */
    var phase by mutableIntStateOf(-1)
        private set

    private var initialized = false
    private var tabs = Rect.Zero
    private var sidebar = Rect.Zero
    private var pixelsPerPoint = 1f
    private var scaleX = 1f
    private var scaleY = 1f
    private var sourceWide = false
    private var twoStage = false

    private val centerX = HeaderTrack(0f)
    private val centerY = HeaderTrack(0f)
    private val width = HeaderTrack(0f)
    private val height = HeaderTrack(0f)
    private val corner = HeaderTrack(0f)
    private val material = HeaderTrack(if (initialExpanded) 1f else 0f)
    private val inset = HeaderTrack(if (initialExpanded) 1f else 0f)
    private val tabPreview = HeaderTrack(if (initialExpanded) 0f else 1f)
    private val sidebarPreview = HeaderTrack(if (initialExpanded) 1f else 0f)
    private val rope = HeaderTrack(0f)
    private val geometryTracks = listOf(centerX, centerY, width, height)
    private val presentationTracks = geometryTracks + listOf(corner, material, inset, tabPreview, sidebarPreview)

    val bounds: Rect
        get() {
            val w = max(width.value * scaleX, .001f)
            val h = max(height.value * scaleY, .001f)
            val x = centerX.value * scaleX
            val y = centerY.value * scaleY
            return Rect(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
        }
    val cornerRadius: Float
        get() = (corner.value * scaleX).coerceIn(0f,
            min(max(width.value * scaleX, .001f), max(height.value * scaleY, .001f)) / 2f)
    val materialProgress: Float get() = material.value.coerceIn(0f, 1f)
    val contentInsetProgress: Float get() = inset.value.coerceIn(0f, 1f)

    /** TabMorph enables crossBlurWhenMorphing: opacity=p and blur=(1-p)*4 pt. */
    val tabsPreviewProgress: Float get() = tabPreview.value.coerceIn(0f, 1f)
    val sidebarPreviewProgress: Float get() = sidebarPreview.value.coerceIn(0f, 1f)

    /** Viewport/configuration changes establish a new resting layout rather than a fake morph. */
    fun updateGeometry(tabs: Rect, sidebar: Rect, density: Float, expanded: Boolean) {
        if (!valid(tabs) || !valid(sidebar)) return
        val newDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        if (initialized && this.tabs == tabs && this.sidebar == sidebar && pixelsPerPoint == newDensity) return
        this.tabs = tabs
        this.sidebar = sidebar
        pixelsPerPoint = newDensity
        scaleX = maxOf(abs(tabs.left), abs(tabs.right), abs(sidebar.left), abs(sidebar.right), tabs.width, sidebar.width, 1f)
        scaleY = maxOf(abs(tabs.top), abs(tabs.bottom), abs(sidebar.top), abs(sidebar.bottom), tabs.height, sidebar.height, 1f)
        initialized = true
        snap(expanded)
    }

    /** Reusing the tracks keeps the presentation and velocity of interrupted native contexts. */
    fun retarget(expanded: Boolean) {
        if (this.expanded == expanded) return
        this.expanded = expanded
        if (!initialized) return
        val source = bounds
        val target = targetBounds()
        sourceWide = source.width / source.height > 2f
        val threshold = 100f * pixelsPerPoint
        twoStage = abs(source.center.x - target.center.x) > threshold &&
            abs(source.center.y - target.center.y) > threshold
        running = true
        rope.snap(0f)
        rope.spring = RopeSpring
        rope.target = if (twoStage) 2f else 1f
        if (twoStage) {
            phase = 0
            val middle = if (sourceWide) {
                Rect(target.center.x - target.width / 2f, source.top,
                    target.center.x + target.width / 2f, source.bottom)
            } else {
                Rect(source.left, target.center.y - target.height / 2f,
                    source.right, target.center.y + target.height / 2f)
            }
            targetGeometry(middle, InitialSpring)
            targetCorner(min(middle.height, middle.width) / 2f, InitialSpring)
            // The intermediate MorphableItem has no layer/coordinator: old content leaves,
            // while there is no new content destination until the rope crosses its gate.
            targetPreviews(0f, 0f, InitialSpring)
            if (sourceWide) targetInset()
        } else {
            beginTarget(2)
        }
    }

    fun advance(seconds: Float, reduceMotion: Boolean = false) {
        if (!running) return
        if (reduceMotion) {
            snap(expanded)
            return
        }
        val dt = seconds.coerceIn(0f, 1f)
        if (!dt.isFinite() || dt == 0f) return
        geometryTracks.forEach { it.advance(dt) }
        corner.advance(dt)
        inset.advance(dt)
        tabPreview.advance(dt)
        sidebarPreview.advance(dt)
        // Stage zero assigns no new material target. An interrupted old spring keeps moving;
        // the copied new recipe starts only in the target-stage block, independently of rope.
        material.advance(dt)
        rope.advance(dt)
        if (phase == 0 && rope.value >= 1f) beginTarget(1)
        if (phase != 0 && presentationTracks.all { it.settled } && rope.settled) {
            snap(expanded)
        }
    }

    /** Layout/visibility policy can suppress the presentation without changing saved preference. */
    fun snapTo(expanded: Boolean) = snap(expanded)

    private fun beginTarget(targetPhase: Int) {
        phase = targetPhase
        val spring = if (expanded) SidebarSpring else TabsSpring
        targetGeometry(targetBounds(), spring)
        targetCorner(if (expanded) 0f else tabs.height / 2f, CornerSpring)
        material.spring = spring
        material.target = if (expanded) 1f else 0f
        targetPreviews(if (expanded) 0f else 1f, if (expanded) 1f else 0f, spring)
        if (!twoStage || !sourceWide) targetInset()
    }

    private fun targetInset() {
        inset.spring = ContentSpring
        inset.target = if (expanded) 1f else 0f
    }

    private fun targetGeometry(rect: Rect, spring: HeaderSpring) {
        centerX.spring = spring; centerX.target = rect.center.x / scaleX
        centerY.spring = spring; centerY.target = rect.center.y / scaleY
        width.spring = spring; width.target = rect.width / scaleX
        height.spring = spring; height.target = rect.height / scaleY
    }

    private fun targetCorner(radius: Float, spring: HeaderSpring) {
        corner.spring = spring
        corner.target = radius / scaleX
    }

    private fun targetPreviews(tabs: Float, sidebar: Float, spring: HeaderSpring) {
        tabPreview.spring = spring; tabPreview.target = tabs
        sidebarPreview.spring = spring; sidebarPreview.target = sidebar
    }

    private fun targetBounds() = if (expanded) sidebar else tabs

    private fun snap(expanded: Boolean) {
        this.expanded = expanded
        val rect = targetBounds()
        centerX.snap(rect.center.x / scaleX)
        centerY.snap(rect.center.y / scaleY)
        width.snap(rect.width / scaleX)
        height.snap(rect.height / scaleY)
        corner.snap(if (expanded) 0f else tabs.height / (2f * scaleX))
        material.snap(if (expanded) 1f else 0f)
        inset.snap(if (expanded) 1f else 0f)
        tabPreview.snap(if (expanded) 0f else 1f)
        sidebarPreview.snap(if (expanded) 1f else 0f)
        rope.snap(2f)
        running = false
        phase = -1
    }

    private fun valid(rect: Rect) = rect.left.isFinite() && rect.top.isFinite() &&
        rect.right.isFinite() && rect.bottom.isFinite() && rect.width > 0f && rect.height > 0f

    private companion object {
        val RopeSpring = HeaderSpring(.3f, 0f)
        val InitialSpring = HeaderSpring(.2f, 0f)
        val SidebarSpring = HeaderSpring(.4f, .1f)
        val TabsSpring = HeaderSpring(.45f, 0f)
        val CornerSpring = HeaderSpring(.2f, .2f)
        val ContentSpring = HeaderSpring(.4f, .1f)
    }
}
