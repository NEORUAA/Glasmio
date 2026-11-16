package io.github.neoruaa.glasmio.core

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** Controls horizontal hardware-safe spacing without changing vertical insets or sidebar space. */
val LocalGlasmioHorizontalSafeAreaEnabled = compositionLocalOf { true }

/**
 * Drawing-safe insets that follow [LocalGlasmioHorizontalSafeAreaEnabled].
 *
 * Disabling horizontal safety leaves status/navigation-bar and IME spacing on the vertical axis
 * unchanged. Provide the local around a screen or app to update its Glasmio surfaces together.
 */
val WindowInsets.Companion.glasmioSafeDrawing: WindowInsets
    @Composable
    get() = if (LocalGlasmioHorizontalSafeAreaEnabled.current) {
        safeDrawing
    } else {
        safeDrawing.only(WindowInsetsSides.Vertical)
    }
