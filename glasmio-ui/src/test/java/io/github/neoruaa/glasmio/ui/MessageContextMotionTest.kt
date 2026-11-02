package io.github.neoruaa.glasmio.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContextMotionTest {
    @Test fun tapbackPartsKeepTheirNativeIndependentDelaysAndSettleExactly() {
        fun at(channel: MessageContextMotion.Channel, millis: Long) =
            MessageContextMotion.value(channel, millis * 1_000_000, true)
        assertEquals(0f, at(MessageContextMotion.Channel.Pill, 110), 0f)
        assertTrue(at(MessageContextMotion.Channel.Pill, 140) > 0f)
        assertEquals(0f, at(MessageContextMotion.Channel.EmojiSize, 190), 0f)
        assertTrue(at(MessageContextMotion.Channel.EmojiY, 190) > 0f)
        assertEquals(0f, at(MessageContextMotion.Channel.MediumY, 225), 0f)
        assertTrue(at(MessageContextMotion.Channel.SmallY, 225) > 0f)
        for (channel in MessageContextMotion.Channel.entries) {
            assertEquals(1f, at(channel, MessageContextMotion.EnterDurationMillis), 0f)
        }
    }

    @Test fun dismissalKeepsDelayedEmojiMotionAndReleasesEveryChannel() {
        val channels = MessageContextMotion.Channel.entries
        for (channel in channels) {
            assertEquals(1f, MessageContextMotion.value(channel, 0L, false), 0f)
            assertEquals(0f, MessageContextMotion.value(channel, MessageContextMotion.ExitRetentionMillis * 1_000_000, false), 0f)
        }
        assertEquals(1f, MessageContextMotion.value(MessageContextMotion.Channel.EmojiY, 90_000_000L, false), 0f)
        assertTrue(MessageContextMotion.value(MessageContextMotion.Channel.Pill, 90_000_000L, false) < 1f)
    }
}
