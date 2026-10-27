package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import io.github.neoruaa.glasmio.glass.InteractiveHighlight


enum class GlasmioAlertButtonLayout {
    SideBySide,
    Stacked,
}

enum class GlasmioAlertButtonRole {
    Default,
    Cancel,
    Destructive,
}

data class GlasmioAlertButtonSpec(
    val label: String,
    val onClick: () -> Unit,
    val role: GlasmioAlertButtonRole = GlasmioAlertButtonRole.Default,
    val enabled: Boolean = true,
)

/**
 * Figma node 65:57149. The alert samples the screen behind its dialog window.
 *
 * Keep this call in composition and change [visible] to retain the window through the exit
 * animation. Removing the call itself immediately removes the window.
 */
@Composable
fun GlasmioAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    buttonLayout: GlasmioAlertButtonLayout = GlasmioAlertButtonLayout.SideBySide,
    buttons: List<GlasmioAlertButtonSpec> = emptyList(),
    properties: DialogProperties = DialogProperties(
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        usePlatformDefaultWidth = false,
    ),
    titleContent: (@Composable () -> Unit)? = null,
    visible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val scale = remember { Animatable(IosAlertMotion.InitialScale) }
    val opacity = remember { Animatable(0f) }
    val dimming = remember { Animatable(0f) }
    var presented by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            if (!presented) {
                scale.snapTo(IosAlertMotion.InitialScale)
                opacity.snapTo(0f)
                dimming.snapTo(0f)
                presented = true
            }
            IosAlertMotion.animate(true, scale, opacity, dimming)
        } else if (presented) {
            IosAlertMotion.animate(false, scale, opacity, dimming)
            presented = false
        }
    }
    if (!presented) return

    val colors = LocalGlassColors.current
    val dimAmount = if (colors.isDark) 0.2f else 0.08f
    val fullScreenProperties = DialogProperties(
        dismissOnBackPress = properties.dismissOnBackPress,
        dismissOnClickOutside = properties.dismissOnClickOutside,
        securePolicy = properties.securePolicy,
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
        windowTitle = properties.windowTitle,
        windowType = properties.windowType,
        windowToken = properties.windowToken,
    )
    val dismiss = { if (visible) onDismissRequest() }
    Dialog(onDismissRequest = dismiss, properties = fullScreenProperties) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val focusManager = LocalFocusManager.current
        SideEffect { window?.setWindowAnimations(0) }
        LaunchedEffect(visible) {
            if (!visible) focusManager.clearFocus(force = true)
        }
        GlasmioTextEditingWindow(backdrop) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = dimming.value }
                        .background(Color.Black.copy(alpha = dimAmount))
                        .then(
                            if (properties.dismissOnClickOutside) {
                                Modifier.clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = dismiss,
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                        // Fade the viewport backing so the panel's exterior shadows stay inside
                        // the alpha layer. A panel-sized offscreen layer would crop those shadows.
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                            alpha = opacity.value
                        },
                ) {
                    GlasmioAlertSurface(
                        modifier = modifier.align(Alignment.Center)
                            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
                        backdrop = backdrop,
                        title = title,
                        message = message,
                        dimAmount = 0f,
                        titleContent = titleContent,
                    ) {
                        content()
                        if (buttons.isNotEmpty()) {
                            when (buttonLayout) {
                                GlasmioAlertButtonLayout.SideBySide -> {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        buttons.forEach { button ->
                                            GlasmioAlertButton(
                                                text = button.label,
                                                onClick = { if (visible) button.onClick() },
                                                modifier = Modifier.weight(1f),
                                                role = button.role,
                                                enabled = button.enabled,
                                            )
                                        }
                                    }
                                }

                                GlasmioAlertButtonLayout.Stacked -> {
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        buttons.forEach { button ->
                                            GlasmioAlertButton(
                                                text = button.label,
                                                onClick = { if (visible) button.onClick() },
                                                modifier = Modifier.fillMaxWidth(),
                                                role = button.role,
                                                enabled = button.enabled,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!visible) {
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    })
                }
            }
        }
    }
}

/** Figma node 65:57149 alert surface, reusable by list and form alerts. */
@Composable
fun GlasmioAlertSurface(
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    title: String,
    message: String? = null,
    dimAmount: Float? = null,
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = LocalGlassColors.current
    val appearance = LocalGlassAppearance.current
    val innerShadows = rememberNavigationInnerShadows(true)
    val light = !colors.isDark
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        dialogWindow?.setDimAmount((dimAmount ?: if (light) 0.01f else 0.08f).coerceIn(0f, 1f))
    }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val samplingBackdrop = if (appearance.usesBackdrop) {
        rememberCrossWindowBackdrop(backdrop)
    } else {
        emptyBackdrop()
    }
    val alertShape = ContinuousRoundedRectangle(34.dp)
    val pressProgress = remember(interactiveHighlight) {
        derivedStateOf { interactiveHighlight.pressProgress }
    }
    val alertLayerBlock: GraphicsLayerScope.() -> Unit = {
        applyGlassDragScale(
            pressProgress = interactiveHighlight.pressProgress,
            offset = interactiveHighlight.offset,
        )
    }
    BoxWithConstraints(modifier) {
        val alertWidth = 300.dp.coerceAtMost(maxWidth)
        Column(
            Modifier
                .width(alertWidth)
                .navigationGlassBackground(
                    backdrop = samplingBackdrop,
                    shape = { alertShape },
                    containerColor = colors.container,
                    navigationMaterial = true,
                    innerShadows = innerShadows,
                    isDark = colors.isDark,
                    blurRadius = NavigationGlassMaterial.RegularMedium.frostRadius,
                    appearance = appearance,
                    material = NavigationGlassMaterial.RegularMedium,
                    pressProgressState = pressProgress,

                    layerBlock = alertLayerBlock,
                )
                .then(interactiveHighlight.modifier)
                .then(interactiveHighlight.gestureModifier)
                .padding(14.dp),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.content,
                LocalGlassContentColor provides colors.content,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (titleContent != null) {
                        ProvideTextStyle(IosTypography.headline.copy(color = colors.content), titleContent)
                    } else {
                        Text(title, style = IosTypography.headline, color = colors.content)
                    }
                    message?.let {
                        Text(
                            it,
                            style = IosTypography.body,
                            color = colors.content,
                        )
                    }
                }
                content()
            }
        }
    }
}

/** The standard 48dp action used by [GlasmioAlertDialog]. */
@Composable
fun GlasmioAlertButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: GlasmioAlertButtonRole = GlasmioAlertButtonRole.Default,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit = {
        Text(
            text,
            style = IosTypography.headline,
            color = when (role) {
                GlasmioAlertButtonRole.Default -> Color.White
                GlasmioAlertButtonRole.Cancel -> LocalGlassColors.current.content
                GlasmioAlertButtonRole.Destructive -> LocalGlassColors.current.destructive
            },
        )
    },
) {
    val colors = LocalGlassColors.current
    val baseFill = when (role) {
        GlasmioAlertButtonRole.Default -> Color(0xFF0088FF)
        GlasmioAlertButtonRole.Cancel,
        GlasmioAlertButtonRole.Destructive -> if (colors.isDark) {
            Color(0x52787880)
        } else {
            Color(0x28787880)
        }
    }
    val fill = baseFill.copy(alpha = baseFill.alpha * if (enabled) 1f else 0.45f)

    Row(
        modifier
            .height(48.dp)
            .clip(Capsule())
            .background(fill)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The grouped field backing used by form alerts in the referenced Figma component. */
@Composable
fun GlasmioAlertFieldGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 19.dp)
            .clip(ContinuousRoundedRectangle(26.dp))
            .background(
                if (colors.isDark) Color.White.copy(alpha = 0.16f) else Color(0x28787880),
            ),
        content = content,
    )
}

@Composable
fun GlasmioAlertTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = LocalGlassColors.current
    val fieldTextStyle = IosTypography.body.copy(fontWeight = FontWeight(510), lineHeight = 20.sp)
    GlasmioEditableTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        textStyle = fieldTextStyle.copy(color = colors.content),
        cursorBrush = SolidColor(colors.accent),
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { innerTextField ->
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isEmpty() && placeholder != null) {
                    Text(
                        placeholder,
                        style = fieldTextStyle,
                        color = colors.tertiaryContent,
                    )
                }
                innerTextField()
            }
        },
    )
}

