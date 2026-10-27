package io.github.neoruaa.glasmio.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoupeMotionTest {
    private val frame = 1f / 60f

    @Test fun convertsUIKitResponseToComposeStiffness() {
        // stiffness = (2π / response)², the unit-mass spring UIKit's response describes.
        assertEquals(987f, loupeSpringStiffness(0.2f), 0.5f)
        assertEquals(158f, loupeSpringStiffness(0.5f), 0.5f)
        assertEquals(246.7f, loupeSpringStiffness(0.4f), 0.5f)
        assertEquals(745.6f, loupeSpringStiffness(0.23f), 1f)
    }

    @Test fun keepsLowPassCoefficientAcrossRefreshRates() {
        val motion = GlasmioLoupeMotion()
        assertEquals(motion.followLowPass, loupeLowPassAlpha(motion.followLowPass, frame), 0.000001f)
        // Two half-length frames must land on the same point as one full 1/30 s frame.
        val longFrame = 2f * frame
        val direct = loupeLowPassAlpha(motion.followLowPass, longFrame)
        val first = loupeLowPassAlpha(motion.followLowPass, frame)
        val chained = 1f - (1f - first) * (1f - first)
        assertEquals(direct, chained, 0.00001f)
        // A 120 Hz panel needs two frames to cover one 60 Hz frame's worth of distance.
        val half = loupeLowPassAlpha(motion.followLowPass, 1f / 120f)
        assertEquals(motion.followLowPass, 1f - (1f - half) * (1f - half), 0.00001f)
    }

    @Test fun convergesWithinUIKitTimeConstant() {
        // τ = -Δt / ln(0.7) ≈ 46.8 ms, so 140 ms of 60 Hz frames leaves under 5% of the distance.
        var current = Offset.Zero
        val target = Offset(200f, -80f)
        repeat(9) { current = loupeLowPass(current, target, 0.3f, frame) }
        val remaining = (target - current).getDistance() / target.getDistance()
        assertTrue("remaining after 150 ms was $remaining", remaining < 0.05f)
        assertEquals(target, loupeLowPass(target, target, 0.3f, frame))
    }

    @Test fun clampsOnlyTheHorizontalAxis() {
        val left = 0f
        val right = 400f
        val lensWidth = 115f
        val inset = 8f
        assertEquals(8f + 57.5f, loupeClampX(-40f, left, right, lensWidth, inset), 0.0001f)
        // UIKit insets the container by `inset` for the upper bound but uses the same `inset` as
        // the lower bound, so the reachable maximum is width - 2 * inset - radius.
        assertEquals(400f - 16f - 57.5f, loupeClampX(500f, left, right, lensWidth, inset), 0.0001f)
        assertEquals(200f, loupeClampX(200f, left, right, lensWidth, inset), 0.0001f)
        // A viewport narrower than the inset lens centres instead of inverting its bounds.
        assertEquals(50f, loupeClampX(120f, 0f, 100f, lensWidth, inset), 0.0001f)
    }

    @Test fun liftKeepsItsOffsetOrClampsIntoTheViewport() {
        // Room above: the body sits exactly `lift` away from the touch point.
        assertEquals(150f, loupeLiftCenter(300f, 150f, 100f, 0f, 2000f), 0.0001f)
        // Near the top edge the body is clamped inside instead of disappearing.
        assertEquals(50f, loupeLiftCenter(120f, 150f, 100f, 0f, 2000f), 0.0001f)
        // Near the bottom edge the same clamp applies on the other side, and vertical text lifts +x.
        assertEquals(1950f, loupeLiftCenter(2100f, 150f, 100f, 0f, 2000f), 0.0001f)
        assertEquals(450f, loupeLiftCenter(300f, -150f, 100f, 0f, 2000f), 0.0001f)
    }

    @Test fun followerLagsThenSettlesOnTarget() {
        val motion = GlasmioLoupeMotion()
        val target = Offset(100f, 0f)
        val first = loupeFollowStep(Offset.Zero, Offset.Zero, Offset.Zero, target, motion, frame)
        assertTrue(first.position.x > 0f && first.position.x < 30f)
        assertTrue(first.velocity.x > 0f)

        var position = Offset.Zero
        var velocity = Offset.Zero
        var model = Offset.Zero
        repeat(120) {
            val step = loupeFollowStep(position, velocity, model, target, motion, frame)
            position = step.position
            velocity = step.velocity
            model = step.model
        }
        assertEquals(0f, (position - target).getDistance(), 0.05f)
        assertTrue(velocity.getDistance() < 1f)
    }

    @Test fun steadyDragLagMatchesTheTwoSeriesFollower() {
        // A 500 dp/s drag at 60 Hz settles with the model ~19.4 dp behind (0.3 blending: 0.7/0.3 *
        // v/60) and the sprung body ANOTHER ~16 dp behind the model, so the body trails by ~35.5 dp
        // while the sampled content trails by ~19.4 dp. Low-passing the presentation folds those two
        // lags together and trails 73 dp instead, which is the "lens does not keep up" regression
        // this test pins.
        val motion = GlasmioLoupeMotion()
        val speed = 500f
        var target = Offset.Zero
        var position = Offset.Zero
        var velocity = Offset.Zero
        var model = Offset.Zero
        repeat(180) {
            target += Offset(speed * frame, 0f)
            val step = loupeFollowStep(position, velocity, model, target, motion, frame)
            position = step.position
            velocity = step.velocity
            model = step.model
        }
        val bodyLag = target.x - position.x
        val sampleLag = target.x - model.x
        assertTrue("body lag $bodyLag", bodyLag in 34f..37f)
        assertTrue("sample lag $sampleLag", sampleLag in 18.5f..20.5f)
        assertTrue("content must lead the body", sampleLag < bodyLag)
    }

    @Test fun followerRestartsWhenItsTargetJumpsOutsideTheLens() {
        val motion = GlasmioLoupeMotion()
        var position = Offset.Zero
        var velocity = Offset(120f, 0f)
        val far = Offset(600f, 0f)
        // A single step cannot reach the far point; the caller is responsible for snapping and
        // GlasmioMagnifier does so through GlasmioLoupeFollowState.snapTo.
        val step = loupeFollowStep(position, velocity, position, far, motion, frame)
        assertTrue(step.position.getDistance() < 60f)
        position = step.position
        assertTrue(position.x < 60f)
        val snapped = GlasmioLoupeFollowState(position)
        snapped.snapTo(far)
        assertEquals(far, snapped.position)
        assertEquals(Offset.Zero, snapped.velocity)
    }

    @Test fun sampleLeadsTheBodyDuringADrag() {
        // UIKit anchors the portal to the low-passed position and only then springs the body towards
        // it, so the content sits closer to the finger than the outline does.
        val motion = GlasmioLoupeMotion()
        val state = GlasmioLoupeFollowState(Offset.Zero)
        val target = Offset(200f, 0f)
        repeat(6) { state.advance(target, motion, frame) }
        assertTrue("sample ${state.samplePosition} body ${state.position}", state.samplePosition.x > state.position.x)
        assertTrue(state.samplePosition.x < target.x)
        state.snapTo(target)
        assertEquals(target, state.samplePosition)
        assertEquals(target, state.position)
    }

    @Test fun activationPulseDeformsThenSettlesToIdentity() {
        // UIKit's retargetImpulse is unverified, so the port reproduces its envelope: a small
        // non-uniform scale at activation that relaxes on the flex spring.
        val motion = GlasmioLoupeFlexMotion()
        assertEquals(Offset(0.98f, 1.012f), loupeActivationScale(1f, motion))
        assertEquals(Offset(1f, 1f), loupeActivationScale(0f, motion))
        assertEquals(Offset(1f, 1f), loupeActivationScale(1f, motion.copy(activationEnabled = false)))
        assertEquals(Offset(1f, 1f), loupeActivationScale(1f, motion.copy(enabled = false)))
        // The pulse squashes the motion axis and stretches the perpendicular one.
        assertTrue(loupeActivationScale(1f, motion).x < 1f)
        assertTrue(loupeActivationScale(1f, motion).y > 1f)
    }

    @Test fun identityFlexAtRest() {
        val motion = GlasmioLoupeFlexMotion()
        assertTrue(loupeFlex(Offset.Zero, Offset.Zero, 115f, 85f, motion).isIdentity)
        assertTrue(loupeFlex(Offset.Zero, Offset(900f, 0f), 115f, 85f, motion).isIdentity)
        assertTrue(loupeFlex(Offset(4000f, 0f), Offset.Zero, 115f, 85f, motion.copy(enabled = false)).isIdentity)
    }

    @Test fun stretchesAlongAccelerationAndCompressesBesideIt() {
        val motion = GlasmioLoupeFlexMotion()
        val horizontal = loupeFlex(Offset(2500f, 0f), Offset(10f, 0f), 115f, 85f, motion)
        assertEquals(1.15f, horizontal.scale.x, 0.0001f)
        assertEquals(0.75f, horizontal.scale.y, 0.0001f)
        assertEquals(-8.625f, horizontal.drift.x, 0.0001f)
        assertEquals(0f, horizontal.drift.y, 0f)

        val reversed = loupeFlex(Offset(2500f, 0f), Offset(-10f, 0f), 115f, 85f, motion)
        assertEquals(8.625f, reversed.drift.x, 0.0001f)

        val vertical = loupeFlex(Offset(0f, 2500f), Offset(0f, 5f), 115f, 85f, motion)
        assertEquals(1.15f, vertical.scale.y, 0.0001f)
        assertEquals(1f, vertical.scale.x, 0.0001f)
        assertEquals(-6.375f, vertical.drift.y, 0.0001f)
    }

    @Test fun signedAccelerationMirrorsTheDeformation() {
        // loupeFlex mirrors on the sign of the acceleration it is given. The sampler never hands it a
        // mirrored pair for mirrored travel, because UIKit differentiates absolute velocity; see
        // deformationIgnoresTravelDirection below.
        val motion = GlasmioLoupeFlexMotion()
        val forward = loupeFlex(Offset(1800f, 0f), Offset(10f, 0f), 115f, 85f, motion)
        val backward = loupeFlex(Offset(-1800f, 0f), Offset(10f, 0f), 115f, 85f, motion)
        assertTrue(forward.scale.x > 1f && forward.scale.y < 1f)
        assertTrue(backward.scale.x < 1f && backward.scale.y > 1f)
        assertTrue(forward.drift.x < 0f && backward.drift.x > 0f)
    }

    @Test fun deformationIgnoresTravelDirection() {
        // _UIFlexInteraction sets prefersDirectionlessAcceleration = YES (0x1c5079d30), so the
        // integrator differentiates |v|. Speeding up in either direction is the same positive value,
        // which is why the native lens flattens for a drag both ways instead of mirroring.
        val rightward = loupeDirectionlessAcceleration(Offset(900f, 0f), Offset.Zero, 0.05f)
        val leftward = loupeDirectionlessAcceleration(Offset(-900f, 0f), Offset.Zero, 0.05f)
        assertEquals(rightward.x, leftward.x, 0.0001f)
        assertTrue(rightward.x > 0f)

        val motion = GlasmioLoupeFlexMotion()
        val right = loupeFlex(rightward, Offset(900f, 0f), 115f, 85f, motion)
        val left = loupeFlex(leftward, Offset(-900f, 0f), 115f, 85f, motion)
        assertEquals(right.scale, left.scale)
        assertTrue(right.scale.x > 1f && right.scale.y < 1f)      // 两侧都变扁
        assertEquals(-right.drift.x, left.drift.x, 0.0001f)       // 只有漂移跟着方向翻转

        // Arresting the drag is the negative half, again in either direction.
        val brakingRight = loupeDirectionlessAcceleration(Offset(100f, 0f), Offset(900f, 0f), 0.05f)
        val brakingLeft = loupeDirectionlessAcceleration(Offset(-100f, 0f), Offset(-900f, 0f), 0.05f)
        assertEquals(brakingRight.x, brakingLeft.x, 0.0001f)
        assertTrue(brakingRight.x < 0f)
        assertTrue(loupeFlex(brakingRight, Offset(100f, 0f), 115f, 85f, motion).scale.y > 1f)
    }

    @Test fun softClampSaturatesInsteadOfOvershooting() {
        // UIKit runs each interpolated term through a tanh shoulder with a third of the range as its
        // margin (0x1c553f980), so the loupe saturates at 0.617 / 1.283 no matter how hard the flick.
        val motion = GlasmioLoupeFlexMotion()
        val hard = loupeFlex(Offset(7500f, 0f), Offset(40f, 0f), 115f, 85f, motion)
        assertEquals(1.2626f, hard.scale.x, 0.002f)
        assertEquals(0.6209f, hard.scale.y, 0.002f)
        assertEquals(-15.10f, hard.drift.x, 0.05f)

        val saturated = loupeFlex(Offset(125_000f, 0f), Offset(40f, 0f), 115f, 85f, motion)
        assertEquals(1.2833f, saturated.scale.x, 0.001f)
        assertEquals(0.6167f, saturated.scale.y, 0.001f)

        // The mirrored direction stays inside the same envelope instead of blowing up.
        val reversed = loupeFlex(Offset(-125_000f, 0f), Offset(-40f, 0f), 115f, 85f, motion)
        assertEquals(0.6167f, reversed.scale.x, 0.001f)
        assertEquals(1.2833f, reversed.scale.y, 0.001f)
        assertEquals(1f, loupeSoftClamp(1f, 0.75f, 1.15f), 0f)
    }

    @Test fun driftFollowsTheLiveBodySize() {
        // UIKit reads the interaction view's bounds every update; during the opening pass the body is
        // still caret-sized, so the drift must shrink with it instead of using the resting 115 dp.
        val motion = GlasmioLoupeFlexMotion()
        val opening = loupeFlex(
            acceleration = Offset(2500f, 0f),
            velocity = Offset(10f, 0f),
            width = 115f,
            height = 85f,
            motion = motion,
            bodyScale = Offset(0.05f, 0.05f),
        )
        assertEquals(1.15f, opening.scale.x, 0.0001f)
        assertEquals(0.75f, opening.scale.y, 0.0001f)
        assertEquals(-0.43125f, opening.drift.x, 0.0001f)

        val resting = loupeFlex(Offset(2500f, 0f), Offset(10f, 0f), 115f, 85f, motion)
        assertEquals(-8.625f, resting.drift.x, 0.0001f)
    }

    @Test fun windowedVelocityRecoversASlopeFromQuantisedSamples() {
        // Pointer positions arrive quantised to whole pixels; the fit must still return the slope.
        val count = 8
        val times = LongArray(count)
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        repeat(count) { index ->
            val seconds = index / 120f
            times[index] = (seconds * 1_000_000_000L).toLong()
            xs[index] = (1200f * seconds).toInt().toFloat()
            ys[index] = 0f
        }
        val velocity = requireNotNull(loupeWindowedVelocity(times, xs, ys, count, 56_000_000L))
        assertEquals(1200f, velocity.x, 40f)
        assertEquals(0f, velocity.y, 0.0001f)

        repeat(count) { xs[it] = 320f }
        val still = requireNotNull(loupeWindowedVelocity(times, xs, ys, count, 56_000_000L))
        assertEquals(0f, still.x, 0.0001f)
    }

    @Test fun accelerationIsQuietForSteadyMotion() {
        // Constant velocity must leave the deformation untouched; only speed changes deform.
        val count = 12
        val times = LongArray(count)
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        repeat(count) {
            val seconds = it / 120f
            times[it] = (seconds * 1_000_000_000L).toLong()
            xs[it] = 900f * seconds
            ys[it] = 0f
        }
        val head = loupeWindowedVelocity(times, xs, ys, count, 56_000_000L)
        assertEquals(900f, requireNotNull(head).x, 1f)
    }

    @Test fun springRetainsMomentumWhenItsTargetChanges() {
        // A per-frame retargeted spring must not lose the velocity a drag established; that loss is
        // what makes a released deformation crawl back instead of returning.
        val stiffness = loupeSpringStiffness(0.5f)
        val stretched = Offset(1.15f, 0.75f)
        val rest = Offset(1f, 1f)
        var position = stretched
        var velocity = Offset.Zero
        val step = loupeSpringStep(position, velocity, rest, 1f, stiffness, frame)
        position = step.position
        velocity = step.velocity
        assertTrue(velocity.x < 0f)
        val settled = velocity
        repeat(2) {
            val next = loupeSpringStep(position, velocity, rest, 1f, stiffness, frame)
            position = next.position
            velocity = next.velocity
        }
        assertTrue("velocity was reset instead of carried", velocity.x < settled.x)
        assertTrue("value did not move back towards rest", position.y > 0.75f)
    }

    @Test fun releasedDeformationReturnsWithinAQuarterSecond() {
        val stiffness = loupeSpringStiffness(0.5f)
        var position = Offset(1.15f, 0.75f)
        var velocity = Offset.Zero
        val rest = Offset(1f, 1f)
        repeat(15) {
            val step = loupeSpringStep(position, velocity, rest, 1f, stiffness, frame)
            position = step.position
            velocity = step.velocity
        }
        // Within 5% of rest after 0.25 s: the 0.5 s response settles, it does not crawl.
        assertEquals(1f, position.x, 0.05f)
        assertEquals(1f, position.y, 0.05f)
    }

    @Test fun substepsKeepAlongFrameStable() {
        // A 30 Hz device or a stalled frame must not overshoot a damping-0.7 spring at omega 31 rad/s.
        val motion = GlasmioLoupeMotion()
        val target = Offset(400f, 200f)
        var position = Offset.Zero
        var velocity = Offset.Zero
        repeat(40) {
            val step = loupeFollowStep(position, velocity, position, target, motion, 0.1f)
            position = step.position
            velocity = step.velocity
            assertTrue("diverged at frame " + it, (position - target).getDistance() < 2000f)
        }
        assertEquals(0f, (position - target).getDistance(), 0.5f)
    }

    @Test fun springSubstepsStayStableAtLowFrameRates() {
        val stiffness = loupeSpringStiffness(0.5f)
        var position = Offset(1.15f, 0.75f)
        var velocity = Offset.Zero
        repeat(20) {
            val step = loupeSpringStep(position, velocity, Offset(1f, 1f), 1f, stiffness, 0.1f)
            position = step.position
            velocity = step.velocity
        }
        assertEquals(1f, position.x, 0.05f)
        assertEquals(1f, position.y, 0.05f)
    }

    @Test fun reportsDismissalFromLineBreakDistance() {
        assertEquals(0f, loupeLineBreakProgress(0f), 0f)
        assertEquals(0.5f, loupeLineBreakProgress(LoupeLineBreakDistance / 2f), 0f)
        assertEquals(1f, loupeLineBreakProgress(LoupeLineBreakDistance * 4f), 0f)
        val motion = GlasmioLoupeMotion()
        assertFalse(isLoupeDismissed(0.84f, motion))
        assertTrue(isLoupeDismissed(LoupeDismissalThreshold, motion))
        assertEquals(LoupeDismissalThreshold, motion.dismissalThreshold, 0f)
    }
}
