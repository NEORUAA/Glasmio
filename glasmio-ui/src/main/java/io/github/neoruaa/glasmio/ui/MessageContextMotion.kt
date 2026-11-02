package io.github.neoruaa.glasmio.ui

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Physical timing from ChatKit's TapbackPickerBalloonView and accessory in iOS 27. */
internal object MessageContextMotion {
    const val PickerHeight = 64.24f
    const val TailExtent = 50f
    const val ExitRetentionMillis = 900L
    const val EnterDurationMillis = 1250L

    enum class Channel {
        Pill, StageSize, StageY, StageX, TailSize, EmojiSize, EmojiX, EmojiY, EmojiAlpha,
        EmojiBlur, MediumX, MediumY, SmallX, SmallY, Content, Smoothness,
    }

    private data class Spring(
        val mass: Double, val stiffness: Double, val damping: Double,
        val velocity: Double = 0.0, val delay: Double = 0.0,
    ) {
        fun value(time: Double): Float {
            val t = time - delay
            if (t <= 0.0) return 0f
            val gamma = damping / (2.0 * mass)
            val omega2 = stiffness / mass
            val discriminant = gamma * gamma - omega2
            val displacement = when {
                abs(discriminant) < 0.001 -> exp(-gamma * t) * (1.0 + (gamma - velocity) * t)
                discriminant < 0.0 -> {
                    val frequency = sqrt(-discriminant)
                    exp(-gamma * t) * (cos(frequency * t) + (gamma - velocity) / frequency * sin(frequency * t))
                }
                else -> {
                    val frequency = sqrt(discriminant)
                    val slow = -gamma + frequency
                    val fast = -gamma - frequency
                    val a = (-velocity - fast) / (slow - fast)
                    a * exp(slow * t) + (1.0 - a) * exp(fast * t)
                }
            }
            return (1.0 - displacement).toFloat()
        }
    }

    private val entry = mapOf(
        Channel.Pill to Spring(1.0, 400.0, 35.3217, 1.75, .11),
        Channel.StageSize to Spring(1.0, 240.915, 38.1725, 6.0),
        Channel.StageX to Spring(1.0, 320.0, 28.0217, delay = .105),
        Channel.StageY to Spring(1.0, 165.025, 18.1227, .2, .1175),
        Channel.TailSize to Spring(1.0, 290.915, 33.1725, delay = .1),
        Channel.EmojiSize to Spring(1.0, 463.915, 35.1725, delay = .19),
        Channel.EmojiX to Spring(1.0, 320.0, 28.6217, delay = .105),
        Channel.EmojiY to Spring(1.05, 164.025, 18.8227, delay = .09),
        Channel.EmojiAlpha to Spring(1.0, 972.986, 62.3854, delay = .2223),
        Channel.EmojiBlur to Spring(2.0, 300.0, 50.0, delay = .18),
        Channel.MediumX to Spring(1.0, 147.639, 18.5413, 3.0, .19),
        Channel.MediumY to Spring(1.0, 142.639, 17.6413, 3.0, .225),
        Channel.SmallX to Spring(1.0, 149.639, 18.5413, 4.0, .17),
        Channel.SmallY to Spring(1.0, 144.639, 17.6413, 4.0, .19),
        Channel.Smoothness to Spring(1.0, 2798.18, 116.588, delay = .2721),
        Channel.Content to Spring(1.0, 550.915, 35.6725, delay = .14),
    )
    private val exit = Spring(2.0, 300.0, 39.0)

    /** UIKit supplies normalized initial velocity to independent point-space spring channels. */
    fun value(channel: Channel, elapsedNanos: Long, entering: Boolean): Float {
        val seconds = elapsedNanos.coerceAtLeast(0) / 1_000_000_000.0
        return if (entering) {
            if (elapsedNanos >= EnterDurationMillis * 1_000_000) 1f else entry.getValue(channel).value(seconds)
        } else {
            val delay = when (channel) {
                Channel.EmojiX -> .015
                Channel.EmojiY -> .0925
                else -> 0.0
            }
            if (elapsedNanos >= ExitRetentionMillis * 1_000_000) 0f else 1f - exit.value(seconds - delay)
        }
    }
}
