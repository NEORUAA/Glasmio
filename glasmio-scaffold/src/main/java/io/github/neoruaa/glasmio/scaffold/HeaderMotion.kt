package io.github.neoruaa.glasmio.scaffold

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.*

/** SwiftUI Spring(duration:bounce:) expressed in seconds and logical units. */
internal data class HeaderSpring(val duration: Float, val bounce: Float) {
    val omega = (2.0 * PI / duration).toFloat()
    val damping = if (bounce >= 0f) 1f - bounce else 1f / (1f + bounce)
}

internal object HeaderSprings {
    val geometry = HeaderSpring(.416f, .25f)
    val interactive = HeaderSpring(.2f, 0f)
    val up = HeaderSpring(.292f, .5f)
    val downHeight = HeaderSpring(.416f, .416f)
    val downWidth = HeaderSpring(.416f, .5f)
    val kick = HeaderSpring(.25f, 0f)
    val returning = HeaderSpring(.5f, .166f)
}

/** Exact damped oscillator integration; retargeting preserves both position and velocity. */
internal class HeaderTrack(initial: Float) {
    var value by mutableFloatStateOf(initial)
        private set
    var velocity = 0f
        private set
    var target = initial
    var spring = HeaderSprings.geometry
    var delay = 0f
    val settled get() = abs(value - target) < .0005f && abs(velocity) < .005f && delay <= 0f

    fun snap(value: Float, velocity: Float = 0f) {
        this.value = value
        this.velocity = velocity
        target = value
        delay = 0f
    }

    fun advance(seconds: Float, reduceMotion: Boolean = false) {
        val dt = (seconds - delay.coerceAtLeast(0f)).coerceAtLeast(0f)
        delay = (delay - seconds).coerceAtLeast(0f)
        if (dt == 0f) return
        val w = spring.omega
        val z = if (reduceMotion) 1f else spring.damping
        val x = value - target
        val v = velocity
        val decay = exp(-z * w * dt)
        if (z < 1f) {
            val wd = w * sqrt(1f - z * z)
            val c = cos(wd * dt)
            val s = sin(wd * dt)
            value = target + decay * (x * c + (v + z * w * x) / wd * s)
            velocity = decay * (v * c - (z * w * v + w * w * x) / wd * s)
        } else {
            val b = v + w * x
            value = target + decay * (x + b * dt)
            velocity = decay * (v - w * b * dt)
        }
        if (settled) { value = target; velocity = 0f }
    }
}

internal class HeaderPulse {
    private val x = arrayOf(HeaderTrack(0f), HeaderTrack(1f))
    private val y = arrayOf(HeaderTrack(0f), HeaderTrack(1f))
    private var rising = 0
    fun trigger() {
        val up = rising
        val down = 1 - up
        for (channels in listOf(x, y)) {
            channels[up].target = 1f
            channels[up].spring = HeaderSprings.up
            channels[up].delay = 0f
            channels[down].target = 0f
        }
        x[down].spring = HeaderSprings.downWidth
        x[down].delay = .142f
        y[down].spring = HeaderSprings.downHeight
        y[down].delay = .113f
        rising = down
    }
    fun snap() { (x + y).forEach { it.snap(it.target) } }
    fun advance(dt: Float, reduced: Boolean) {
        (x + y).forEach { it.advance(dt, reduced) }
    }
    fun scale(size: Float, pointLimit: Float, horizontal: Boolean): Float {
        val r = min(sqrt(1.2f), sqrt(1f + pointLimit / size.coerceAtLeast(1f)))
        val channels = if (horizontal) x else y
        fun l(p: Float) = 1f / r + (r - 1f / r) * p
        return l(channels[0].value) * l(channels[1].value)
    }
    val settled get() = (x + y).all { it.settled }
}
