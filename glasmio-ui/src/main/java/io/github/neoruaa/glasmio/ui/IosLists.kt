package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.flow.first


/** Figma node 781:15374 grouped-list container. Use ordinary rows inside for GPU efficiency. */
@Composable
fun GlasmioGroupedList(
    modifier: Modifier = Modifier,
    // Sheets and alert surfaces already provide the glass; their lists must render
    // unframed, otherwise the opaque card reads as a second corner radius inside the sheet.
    framed: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val background = colors.elevatedBackground.copy(
        alpha = LocalGroupedListBackgroundAlpha.current.coerceIn(0f, 1f),
    )
    val shape = ContinuousRoundedRectangle(26.dp)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (framed) {
                    Modifier
                        .clip(shape)
                        .background(background, shape)
                        .drawWithContent {
                            drawContent()
                            // Hide only the first merged row's inset separator. Clipping the
                            // container prevents the cover leaking across the top corners.
                            val inset = 16.dp.toPx()
                            drawRect(
                                color = background,
                                topLeft = androidx.compose.ui.geometry.Offset(inset, 0f),
                                size = Size((size.width - inset * 2f).coerceAtLeast(0f), 1.dp.toPx()),
                            )
                        }
                } else {
                    Modifier
                },
            )
            // Rows own their horizontal insets. This avoids the accidental double inset
            // produced by secondary-page cards while preserving the Settings geometry.
            .padding(horizontal = 0.dp),
    ) {
        CompositionLocalProvider(
            LocalMergedGlassCards provides true,
            LocalGroupedListIconColor provides colors.accent,
            LocalContentColor provides colors.content,
        ) { content() }
    }
}

@Composable
fun GlasmioListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    detail: String? = null,
    systemName: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showTopSeparator: Boolean = true,
) {
    val colors = LocalGlassColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 52.dp else 62.dp)
            .drawBehind {
                if (showTopSeparator) {
                    drawLine(
                        colors.separator,
                        androidx.compose.ui.geometry.Offset(16.dp.toPx(), 0f),
                        androidx.compose.ui.geometry.Offset(size.width - 16.dp.toPx(), 0f),
                        1.dp.toPx(),
                    )
                }
            }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(12.dp)) }
        systemName?.let {
            SfIcon(it, null, size = 23.dp, tint = colors.accent)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = IosTypography.body,
                color = colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = IosTypography.subheadline,
                    color = colors.secondaryContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        detail?.let { Text(it, style = IosTypography.subheadline, color = colors.secondaryContent) }
        trailing?.let { trailingContent ->
            CompositionLocalProvider(
                LocalGroupedListIconColor provides colors.content,
                LocalContentColor provides colors.content,
            ) {
                trailingContent()
            }
        }
        if (onClick != null && trailing == null) {
            Spacer(Modifier.width(8.dp)); SfIcon("chevron.forward", null, size = 12.dp, tint = colors.separator)
        }
    }
}

/** Figma node 5661:43368 stepper. */
