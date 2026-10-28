package io.github.neoruaa.glasmio.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetMotionTest {
    private val anchors = GlasmioSheetAnchors(hidden = 800f, large = 60f, medium = 400f)

    @Test fun projectionUsesNativeMinimumSpeedAndPointUnits() {
        assertEquals(420f, IosSheetMotion.projectedOffset(420f, 249f), 0f)
        assertEquals(444.75f, IosSheetMotion.projectedOffset(420f, 250f), 0.0001f)
        assertEquals(321f, IosSheetMotion.projectedOffset(420f, -1000f), 0.0001f)
        assertEquals(840f, IosSheetMotion.projectedOffset(840f, 499f, density = 2f), 0f)
        assertEquals(889.5f, IosSheetMotion.projectedOffset(840f, 500f, density = 2f), 0.0001f)
    }

    @Test fun highSpeedReleaseAdvancesOnlyAfterPassingItsNearestDetent() {
        // Each projection remains nearest medium. Only the releases already beyond medium in
        // their velocity direction advance an adjacent detent in _UISheetInteraction.
        assertEquals(GlasmioSheetValue.Hidden, IosSheetMotion.closestValue(410f, 1000f, anchors, true))
        assertEquals(GlasmioSheetValue.Large, IosSheetMotion.closestValue(390f, -1000f, anchors, true))
        assertEquals(GlasmioSheetValue.Medium, IosSheetMotion.closestValue(390f, 1000f, anchors, true))
        assertEquals(GlasmioSheetValue.Medium, IosSheetMotion.closestValue(410f, -1000f, anchors, true))
        assertEquals(GlasmioSheetValue.Medium, IosSheetMotion.closestValue(410f, 999f, anchors, true))
    }

    @Test fun slowReleaseUsesDetentDistanceAndExcludedDismissalCannotWin() {
        assertEquals(GlasmioSheetValue.Large, IosSheetMotion.closestValue(150f, 0f, anchors, true))
        assertEquals(GlasmioSheetValue.Large, IosSheetMotion.closestValue(230f, 249f, anchors, true))
        assertEquals(GlasmioSheetValue.Large, IosSheetMotion.closestValue(230f, -249f, anchors, true))
        assertEquals(GlasmioSheetValue.Medium, IosSheetMotion.closestValue(430f, 0f, anchors, true))
        assertEquals(GlasmioSheetValue.Hidden, IosSheetMotion.closestValue(690f, 0f, anchors, true))
        assertEquals(GlasmioSheetValue.Medium, IosSheetMotion.closestValue(690f, 5000f, anchors, false))
    }

    @Test fun rubberBandMatchesNativeEquationAndGeometryCaps() {
        // 100 * (1 - 1 / (50 / 100 * .55 + 1)).
        assertEquals(21.568626f, IosSheetMotion.rubberBand(50f, 100f), 0.00001f)
        assertEquals(-21.568626f, IosSheetMotion.rubberBand(-50f, 100f), 0.00001f)
        assertEquals(0f, IosSheetMotion.rubberBand(50f, 0f), 0f)
        assertTrue(IosSheetMotion.rubberBand(100000f, 100f) < 100f)
        assertEquals(6f, IosSheetMotion.upperRubberBandExtent(anchors, 54f, 1f), 0f)
        assertEquals(185f, IosSheetMotion.lowerRubberBandExtent(anchors, 1f), 0f)
        assertEquals(200f, IosSheetMotion.lowerRubberBandExtent(anchors.copy(hidden = 1600f), 1f), 0f)
        listOf(-150f, -20f, 1f, 50f, 500f).forEach { excess ->
            assertEquals(excess, IosSheetMotion.unconstrainedExcess(IosSheetMotion.rubberBand(excess, 100f), 100f), 0.001f)
        }
    }

    @Test fun springUsesNativeResponseRatherThanResponseAsDuration() {
        assertEquals(333.33334f, IosSheetMotion.stiffness, 0.0002f)
        assertEquals(1f, IosSheetMotion.springSpec().dampingRatio, 0f)
        assertEquals(0.8f, IosSheetMotion.springSpec(highSpeed = true).dampingRatio, 0f)
    }

    @Test fun settlingDurationMatchesPhysicalCAAnimationsAndIgnoresPixelAmplitude() {
        val critical = IosSheetMotion.springSpec()
        // UIKit's normalized Lambert-W approximation produces these actual device durations.
        val samples = listOf(
            0f to 505_823_787L,
            4.987681832f to 488_682_201L,
            -42.362020257f to 574_437_762L,
            43.190616844f to 513_649_587L,
        )
        samples.forEach { (normalizedVelocity, duration) ->
            assertEquals(duration.toDouble(), critical.getDurationNanos(0f, 1f, normalizedVelocity).toDouble(), 10.0)
        }
        assertEquals(505_823_787L, critical.getDurationNanos(0f, 700f, 0f))
        assertEquals(505_823_787L, critical.getDurationNanos(0.3f, 0.8f, 0f))
        assertEquals(505_823_787L, IosSheetMotion.materialSpringSpec().getDurationNanos(0.2f, 1f, 0f))
        assertEquals(530_952_192L, IosSheetMotion.springSpec(highSpeed = true).getDurationNanos(0f, 700f, 0f))
        assertTrue(critical.settlingDurationSeconds(IosSheetMotion.angularFrequency).isFinite())
    }

    @Test fun settlingSpringSamplesContinuousTimeAndPreservesInitialVelocity() {
        val spec = IosSheetMotion.springSpec()
        assertEquals(0f, spec.getValueFromNanos(0L, 0f, 700f, 123f), 0f)
        assertEquals(123f, spec.getVelocityFromNanos(0L, 0f, 700f, 123f), 0f)
        val first = spec.getValueFromNanos(100_000_000L, 0f, 700f, 0f)
        val submillisecond = spec.getValueFromNanos(100_500_000L, 0f, 700f, 0f)
        assertTrue(submillisecond > first)
        val end = spec.getDurationNanos(0f, 700f, 0f)
        assertEquals(700f, spec.getValueFromNanos(end, 0f, 700f, 0f), 0f)
        assertEquals(0f, spec.getVelocityFromNanos(end, 0f, 700f, 0f), 0f)
    }

    @Test fun controllerPresentationUsesTheMeasuredCriticalSpringForExactly500Millis() {
        // The physical iPhone owner-layer samples match this mass3/stiffness1000 critical
        // response within .061pt. Interaction springs intentionally have a separate duration.
        val samples = listOf(
            50L to 0.23223049f,
            100L to 0.54477841f,
            200L to 0.87928194f,
            300L to 0.97291929f,
            400L to 0.99440765f,
        )
        samples.forEach { (millis, progress) ->
            assertEquals(progress, IosSheetPresentationMotion.getValueFromNanos(millis * 1_000_000L, 0f, 1f, 0f), 0.000001f)
        }
        assertEquals(500_000_000L, IosSheetPresentationMotion.getDurationNanos(812f, 370.3333f, 0f))
        assertEquals(1f, IosSheetPresentationMotion.getValueFromNanos(500_000_000L, 0f, 1f, 0f), 0f)
        assertEquals(0f, IosSheetPresentationMotion.getVelocityFromNanos(500_000_000L, 0f, 1f, 0f), 0f)
        assertEquals(-125f, IosSheetPresentationMotion.getVelocityFromNanos(0L, 812f, 370.3333f, -125f), 0f)
        assertEquals(5.3699365f, IosSheetPresentationMotion.getVelocityFromNanos(100_000_000L, 0f, 1f, 0f), 0.00001f)
    }

    @Test fun releaseVelocityUsesConstrainedFractionInsteadOfRubberDerivative() {
        fun constrained(raw: Float, velocity: Float) = IosSheetMotion.constrainedReleaseVelocity(
            velocity, raw, minimumOffset = 60f, maximumOffset = 400f,
            minimumExtent = 10f, maximumExtent = 100f, density = 1f,
        )
        assertEquals(1000f, constrained(300f, 1000f), 0f)
        assertEquals(0f, constrained(300f, 249f), 0f)
        assertEquals(431.37253f, constrained(450f, 1000f), 0.0001f)
        assertEquals(-84.61538f, constrained(-40f, -1000f), 0.0001f)
    }

    @Test fun layoutMatchesNativeIPhone13MiniMediumAndLargeFrames() {
        // iPhone 13 mini's physical-pixel grid. Native probe records 375x812 point bounds,
        // 375x441.6667 point medium content and an inset frame (8,381.1778,359,422.8222).
        val layout = IosSheetLayout(1125f, 2436f, fullTop = 150f, safeBottom = 102f, density = 3f)
        val resolved = layout.anchors(skipPartiallyExpanded = false)
        val mediumTop = requireNotNull(resolved.medium)
        assertEquals(2184f, layout.maximumDetentValue, 0f)
        assertEquals(1325f, requireNotNull(layout.mediumNaturalHeight), 0f)
        assertEquals(1111f, mediumTop, 0f)
        val medium = layout.frame(mediumTop, mediumTop)
        assertEquals(8f, medium.sideInset / 3f, 0f)
        assertEquals(381.1778f, medium.visibleTop / 3f, 0.0001f)
        assertEquals(359f, medium.visibleWidth / 3f, 0.0001f)
        assertEquals(422.8222f, medium.visibleHeight / 3f, 0.0001f)
        assertEquals(441.6667f, medium.naturalHeight / 3f, 0.0001f)
        val large = layout.frame(resolved.large, mediumTop)
        assertEquals(50f, large.visibleTop / 3f, 0f)
        assertEquals(375f, large.visibleWidth / 3f, 0f)
        assertEquals(762f, large.visibleHeight / 3f, 0f)
        assertEquals(1f, large.expansionProgress, 0f)
        assertEquals(1f, large.scale, 0f)
    }

    @Test fun presentationTranslatesRestingViewportRatherThanCollapsingItsHeight() {
        val layout = IosSheetLayout(1125f, 2436f, fullTop = 150f, safeBottom = 102f, density = 3f)
        val mediumTop = requireNotNull(layout.mediumNaturalTop)
        val resting = layout.frame(mediumTop, mediumTop)
        val entering = layout.frame(mediumTop + 600f, mediumTop)
        val hidden = layout.frame(layout.containerHeight, mediumTop)
        assertEquals(resting.naturalHeight, entering.naturalHeight, 0f)
        assertEquals(resting.visibleHeight, entering.visibleHeight, 0f)
        assertEquals(resting.visibleTop + 600f, entering.visibleTop, 0f)
        assertEquals(resting.naturalHeight, hidden.naturalHeight, 0f)
        assertTrue(hidden.visibleTop > layout.containerHeight)
    }

    @Test fun fittedShortPanelKeepsItsFloatingFrameAndCompactHeightDropsMedium() {
        val layout = IosSheetLayout(1125f, 2436f, fullTop = 150f, safeBottom = 102f, density = 3f)
        val fitted = layout.anchors(skipPartiallyExpanded = true, fitContentHeight = 600f)
        val frame = layout.frame(fitted.large, fitted.large, fullHeightDetentAvailable = false)
        assertEquals(0f, frame.expansionProgress, 0f)
        assertEquals(0f, frame.capsularProgress, 0f)
        assertEquals(24f, frame.sideInset, 0f)
        assertEquals(600f, frame.naturalHeight, 0f)
        val capsule = layout.frame(2136f, 2136f, fullHeightDetentAvailable = false)
        assertEquals(1f, capsule.capsularProgress, 0f)
        assertEquals(84f, capsule.sideInset, 0f)
        assertEquals(84f, capsule.bottomInset, 0f)
        val tallFit = layout.frame(layout.fullTop, layout.fullTop, fullHeightDetentAvailable = false)
        assertEquals(0f, tallFit.expansionProgress, 0f)
        val compact = layout.copy(containerWidth = 2436f, containerHeight = 1125f, compactHeight = true)
        assertEquals(null, compact.mediumNaturalHeight)
        assertEquals(null, compact.anchors(skipPartiallyExpanded = false).medium)
        val shortDisplay = IosSheetLayout(960f, 1704f, fullTop = 90f, safeBottom = 0f, density = 3f)
        assertEquals(1017f, requireNotNull(shortDisplay.mediumNaturalHeight), 0f)
    }

    @Test fun sheetKeepsPresentationUntilHiddenSpringFinishes() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false)
        state.updateAnchors(anchors)
        assertFalse(state.isVisible)
        assertEquals(800f, state.offset, 0f)
        assertEquals(0f, state.presentationProgress, 0f)
        state.show()
        assertEquals(GlasmioSheetValue.Medium, state.currentValue)
        assertEquals(400f, state.offset, 0f)
        assertTrue(state.isVisible)
        assertEquals(1f, state.presentationProgress, 0f)
        val closing = launch(start = CoroutineStart.UNDISPATCHED) { state.hide() }
        assertTrue(state.isVisible)
        assertEquals(GlasmioSheetValue.Medium, state.currentValue)
        assertEquals(GlasmioSheetValue.Hidden, state.targetValue)
        closing.join()
        assertEquals(GlasmioSheetValue.Hidden, state.currentValue)
        assertEquals(800f, state.offset, 0f)
        assertFalse(state.isVisible)
        assertFalse(state.isAnimationRunning)
        assertEquals(0f, state.presentationProgress, 0f)
    }

    @Test fun userDragInterruptsOpeningWithoutStaleCompletion() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false)
        state.updateAnchors(anchors, safeAreaTop = 50f)
        val opening = launch { state.show() }
        while (state.offset >= anchors.hidden) yield()
        val interruptedOffset = state.offset
        state.beginDrag()
        assertTrue(state.isDragging)
        assertFalse(state.isAnimationRunning)
        assertEquals(interruptedOffset, state.offset, 0f)
        state.dragBy(-20f)
        val draggedOffset = state.offset
        opening.join()
        assertEquals(draggedOffset, state.offset, 0f)
        state.cancelDrag()
        assertEquals(GlasmioSheetValue.Medium, state.currentValue)
        assertEquals(400f, state.offset, 0f)
    }

    @Test fun scrollDeltaHandsOffAtLargeWhileHandleRetainsRubberBand() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false)
        state.updateAnchors(anchors, safeAreaTop = 50f)
        state.show()
        assertEquals(-340f, state.dragBy(-500f, allowOverdrag = false), 0f)
        assertEquals(60f, state.offset, 0f)
        assertEquals(0f, state.dragBy(-40f, allowOverdrag = false), 0f)
        state.endDrag(0f)
        assertEquals(GlasmioSheetValue.Large, state.currentValue)
        state.dragBy(-100f)
        assertTrue(state.offset < 60f && state.offset > 50f)
        state.endDrag(0f)
        assertEquals(60f, state.offset, 0f)
    }

    @Test fun releaseAtItsDestinationKeepsTheNativeOneMillisecondVelocityNudge() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState()
        state.updateAnchors(anchors, safeAreaTop = 50f)
        state.show()
        state.beginDrag()
        val release = launch(start = CoroutineStart.UNDISPATCHED) { state.endDrag(-1000f) }
        assertEquals(59f, state.offset, 0f)
        release.join()
        assertEquals(60f, state.offset, 0f)
        assertEquals(GlasmioSheetValue.Large, state.currentValue)
    }

    @Test fun deniedDismissalRubberBandsThenReturnsToVisibleDetent() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false) { it != GlasmioSheetValue.Hidden }
        state.updateAnchors(anchors, safeAreaTop = 50f)
        state.show()
        state.dragBy(1000f)
        assertTrue(state.offset > 400f && state.offset < 585f)
        state.endDrag(5000f)
        assertEquals(GlasmioSheetValue.Medium, state.currentValue)
        assertEquals(400f, state.offset, 0f)
        state.hide()
        assertTrue(state.isVisible)
        assertEquals(GlasmioSheetValue.Medium, state.currentValue)
    }

    @Test fun resizingReanchorsSettledAndAnimatingSheets() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false)
        state.updateAnchors(anchors)
        state.show()
        val smaller = GlasmioSheetAnchors(hidden = 600f, large = 60f, medium = 300f)
        state.updateAnchors(smaller)
        assertEquals(300f, state.offset, 0f)
        val opening = launch { state.expand() }
        while (state.velocity == 0f) yield()
        val beforeOffset = state.offset
        val beforeVelocity = state.velocity
        state.updateAnchors(smaller.copy(large = 80f))
        assertEquals(beforeOffset, state.offset, 0f)
        assertEquals(beforeVelocity, state.velocity, 0f)
        opening.join()
        assertEquals(80f, state.offset, 0f)
        assertEquals(GlasmioSheetValue.Large, state.currentValue)
        assertEquals(1f, state.progress, 0f)
        assertEquals(1f, state.presentationProgress, 0f)
    }

    @Test fun resizingDuringOverdragKeepsTheNextFingerDeltaContinuous() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState()
        state.updateAnchors(anchors, safeAreaTop = 50f)
        state.show()
        state.dragBy(-100f)
        val previousOffset = state.offset
        state.updateAnchors(anchors.copy(hidden = 820f, large = 62f), safeAreaTop = 50f)
        assertEquals(previousOffset, state.offset, 0f)
        val displayedDelta = state.dragBy(1f)
        assertTrue(displayedDelta > 0f && displayedDelta <= 1f)
        state.cancelDrag()
        assertEquals(62f, state.offset, 0f)
    }

    @Test fun programmaticReversalCarriesCurrentVelocity() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState()
        state.updateAnchors(anchors)
        val opening = launch { state.show() }
        while (state.velocity == 0f) yield()
        val openingVelocity = state.velocity
        assertTrue(openingVelocity < 0f)
        val closing = launch(start = CoroutineStart.UNDISPATCHED) { state.hide() }
        assertEquals(openingVelocity, state.velocity, 0f)
        opening.join()
        closing.join()
        assertEquals(GlasmioSheetValue.Hidden, state.currentValue)
        assertEquals(800f, state.offset, 0f)
    }

    @Test fun initialLargePresentationKeepsLargeViewportAndItsOwnDimmingTimeline() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false, initialValue = GlasmioSheetValue.Large)
        state.updateAnchors(anchors)
        val opening = launch(start = CoroutineStart.UNDISPATCHED) { state.show() }
        assertEquals(GlasmioSheetValue.Large, state.presentationDetent)
        assertEquals(0f, state.presentationProgress, 0f)
        while (state.offset >= anchors.medium!!) yield()
        assertTrue(state.isAnimationRunning)
        assertEquals(GlasmioSheetValue.Large, state.presentationDetent)
        assertTrue(state.presentationProgress < 1f)
        assertEquals((anchors.hidden - state.offset) / (anchors.hidden - anchors.large), state.presentationProgress, 0.00001f)
        opening.join()
        assertEquals(GlasmioSheetValue.Large, state.currentValue)
        assertEquals(null, state.presentationDetent)
        assertEquals(1f, state.presentationProgress, 0f)
    }

    @Test fun dismissingLargeKeepsItsViewportAndImmediatelyFadesDimming() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false)
        state.updateAnchors(anchors)
        state.show()
        state.expand()
        val closing = launch(start = CoroutineStart.UNDISPATCHED) { state.hide() }
        assertEquals(GlasmioSheetValue.Large, state.presentationDetent)
        while (state.offset <= anchors.large) yield()
        assertTrue(state.offset < anchors.medium!!)
        assertTrue(state.presentationProgress < 1f && state.presentationProgress > 0f)
        assertEquals(GlasmioSheetValue.Large, state.presentationDetent)
        closing.join()
        assertEquals(GlasmioSheetValue.Hidden, state.currentValue)
        assertEquals(GlasmioSheetValue.Large, state.presentationDetent)
        assertEquals(0f, state.presentationProgress, 0f)
    }

    @Test fun reversingLargeDismissalRestoresLargeRatherThanTheInitialMedium() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false)
        state.updateAnchors(anchors)
        state.show()
        state.expand()
        val closing = launch { state.hide() }
        while (state.offset <= anchors.large) yield()
        val interruptedOffset = state.offset
        val interruptedVelocity = state.velocity
        val interruptedDimming = state.presentationProgress
        val opening = launch(start = CoroutineStart.UNDISPATCHED) { state.show() }
        assertEquals(GlasmioSheetValue.Large, state.targetValue)
        assertEquals(GlasmioSheetValue.Large, state.presentationDetent)
        assertEquals(interruptedOffset, state.offset, 0f)
        assertEquals(interruptedVelocity, state.velocity, 0f)
        assertEquals(interruptedDimming, state.presentationProgress, 0f)
        closing.join()
        opening.join()
        assertEquals(GlasmioSheetValue.Large, state.currentValue)
        assertEquals(anchors.large, state.offset, 0f)
        assertEquals(null, state.presentationDetent)
        assertEquals(1f, state.presentationProgress, 0f)
    }

    @Test fun directDragClearsControllerGeometryWithoutMovingThePresentation() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false, initialValue = GlasmioSheetValue.Large)
        state.updateAnchors(anchors)
        val opening = launch { state.show() }
        while (state.offset >= anchors.hidden) yield()
        val previousOffset = state.offset
        state.beginDrag()
        assertEquals(previousOffset, state.offset, 0f)
        assertEquals(null, state.presentationDetent)
        opening.join()
        state.cancelDrag()
        assertEquals(GlasmioSheetValue.Large, state.currentValue)
    }

    @Test fun unavailableMediumFallsBackToLargeAndNonFiniteDragIsIgnored() = runBlocking(SheetFrameClock()) {
        val state = GlasmioSheetState(skipPartiallyExpanded = false)
        state.updateAnchors(anchors.copy(medium = null))
        state.show()
        assertEquals(GlasmioSheetValue.Large, state.currentValue)
        assertEquals(0f, state.dragBy(Float.NaN), 0f)
        assertEquals(0f, state.dragBy(Float.POSITIVE_INFINITY), 0f)
        assertFalse(state.isDragging)
        assertEquals(60f, state.offset, 0f)
    }

    private class SheetFrameClock : MonotonicFrameClock {
        private var frameTime = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            frameTime += 16_666_667L
            return onFrame(frameTime)
        }
    }
}
