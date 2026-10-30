package io.github.neoruaa.glasmio.glass.render

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.GlassColors
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.LocalGlassContentColor
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.glass.adaptiveGlassContentColors

internal val LocalBackdropLuminance = staticCompositionLocalOf<BackdropLuminance?> { null }
private val LocalGlassMaterialContext = compositionLocalOf<GlassMaterialContext?> { null }

/** One surface's appearance and foreground share the same retained local luminance sample. */
@Stable
class GlassMaterialContext internal constructor(
    val environmentDark: Boolean,
    /** The retained hysteresis target, independent of the currently displayed material. */
    val isDark: Boolean,
    /** Semantic labels adapt; [GlassColors.isDark] remains the stable environment trait. */
    val colors: GlassColors,
    internal val environmentColors: GlassColors,
    internal val probe: BackdropLuminance,
    val adaptive: Boolean,
    private val ownsLuminance: Boolean,
    /** Presentation weight: zero is the light recipe and one is the dark recipe. */
    val darkFraction: Float = if (isDark) 1f else 0f,
) {
    /** Custom renderers sample their raw backing here; an inherited surface has one writer. */
    val luminanceState: BackdropLuminance? get() = probe.takeIf { adaptive && ownsLuminance }

    internal fun foreground(color: Color, inherited: GlassMaterialContext? = null): Color {
        // A parent may already have interpolated these roles. Recover their semantic source
        // before resolving a child surface, rather than mistaking an intermediate gray for custom ink.
        val source = if (inherited == null) color else when (color) {
            inherited.colors.content -> inherited.environmentColors.content
            inherited.colors.secondaryContent -> inherited.environmentColors.secondaryContent
            inherited.colors.tertiaryContent -> inherited.environmentColors.tertiaryContent
            else -> color
        }
        if (!adaptive) return source
        val environment = defaultGlassColors(environmentDark, environmentColors.accent)
        val opposite = defaultGlassColors(!environmentDark, environmentColors.accent)
        return when (source) {
            environmentColors.content, environment.content, opposite.content -> colors.content
            environmentColors.secondaryContent, environment.secondaryContent, opposite.secondaryContent -> colors.secondaryContent
            environmentColors.tertiaryContent, environment.tertiaryContent, opposite.tertiaryContent -> colors.tertiaryContent
            else -> source
        }
    }
}

/** Remember this above alternate rendering branches so their appearance never restarts. */
@Composable
fun rememberGlassMaterialContext(
    sampleBackdrop: Boolean = true,
    inheritLuminance: Boolean = true,
): GlassMaterialContext {
    val inherited = LocalBackdropLuminance.current.takeIf { inheritLuminance }
    val probe = inherited ?: rememberBackdropLuminance()
    val localColors = LocalGlassColors.current
    val parent = LocalGlassMaterialContext.current
    val colors = if (parent != null && localColors == parent.colors) parent.environmentColors else localColors
    return rememberResolvedGlassMaterialContext(
        colors, LocalGlassAppearance.current, colors.isDark, probe, inherited == null, sampleBackdrop,
    )
}

@Composable
internal fun rememberResolvedGlassMaterialContext(
    colors: GlassColors,
    appearance: GlassAppearance,
    environmentDark: Boolean,
    probe: BackdropLuminance,
    ownsLuminance: Boolean,
    sampleBackdrop: Boolean,
): GlassMaterialContext {
    val adaptive = appearance.adaptiveColorScheme && appearance.usesBackdrop && sampleBackdrop
    val parent = LocalGlassMaterialContext.current
    val environmentColors = if (parent != null && colors == parent.colors) parent.environmentColors else colors
    val sharedPresentation = parent?.takeIf { adaptive && it.adaptive && it.probe === probe }
    val dark = sharedPresentation?.isDark ?: rememberAdaptiveGlassDark(probe, environmentDark, adaptive)
    val darkFraction = if (!adaptive) {
        if (environmentDark) 1f else 0f
    } else if (sharedPresentation != null) sharedPresentation.darkFraction else {
        val fraction by animateFloatAsState(
            targetValue = if (dark) 1f else 0f,
            // Native adaptive material transactions use the modern Animation.default spring.
            animationSpec = spring(dampingRatio = 1f, stiffness = 157.91367f),
            visibilityThreshold = .001f,
            label = "GlassMaterialAppearance",
        )
        fraction
    }
    return remember(environmentColors, environmentDark, probe, ownsLuminance, adaptive, dark, darkFraction) {
        GlassMaterialContext(environmentDark, dark,
            adaptiveGlassContentColors(environmentColors, dark, adaptive, darkFraction),
            environmentColors, probe, adaptive, ownsLuminance, darkFraction)
    }
}

/** Preserve the environment trait and explicit accents while adapting semantic foregrounds. */
@Composable
fun ProvideGlassMaterialContext(context: GlassMaterialContext, content: @Composable () -> Unit) {
    val inherited = LocalGlassMaterialContext.current
    CompositionLocalProvider(
        LocalGlassMaterialContext provides context,
        LocalBackdropLuminance provides context.probe.takeIf { context.adaptive },
        LocalGlassColors provides context.colors,
        LocalContentColor provides context.foreground(LocalContentColor.current, inherited),
        LocalGlassContentColor provides (LocalGlassContentColor.current?.let { context.foreground(it, inherited) }
            ?: context.colors.content),
        content = content,
    )
}

/** The environment selects the range; the previous resolved appearance supplies its memory. */
internal class GlassLuminanceHysteresis(initialEnvironmentDark: Boolean) {
    private var resolvedDark = initialEnvironmentDark

    fun resolve(luminance: Float?, environmentDark: Boolean, enabled: Boolean = true): Boolean {
        if (!enabled) {
            resolvedDark = environmentDark
            return environmentDark
        }
        if (luminance == null || !luminance.isFinite()) return resolvedDark
        val lower = if (environmentDark) .6f else .2f
        val upper = if (environmentDark) .9f else .5f
        if (luminance < lower) resolvedDark = true
        else if (luminance > upper) resolvedDark = false
        return resolvedDark
    }
}

@Composable
internal fun rememberAdaptiveGlassDark(
    luminance: BackdropLuminance,
    environmentDark: Boolean,
    enabled: Boolean,
): Boolean {
    val hysteresis = remember { GlassLuminanceHysteresis(environmentDark) }
    return hysteresis.resolve(luminance.value, environmentDark, enabled)
}
