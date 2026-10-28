package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import com.kyant.shapes.Capsule
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.kyant.capsule.ContinuousRoundedRectangle


private val LocalGroupedListCoordinates = staticCompositionLocalOf<State<LayoutCoordinates?>?> { null }

/** Suppress only a separator placed on the framed group's actual top edge. */
@Composable
fun Modifier.glasmioGroupedListSeparator(
    color: Color,
    visible: Boolean = true,
    horizontalInset: Dp = 0.dp,
): Modifier {
    if (!visible) return this
    val group = LocalGroupedListCoordinates.current
    val inSheet = LocalIosSheetMaterialContext.current != null
    var coordinates by remember(group) { mutableStateOf<LayoutCoordinates?>(null) }
    var position by remember(group) { mutableStateOf(Offset.Unspecified) }
    return this
        .then(if (group != null) Modifier.onGloballyPositioned {
            coordinates = it
            // Coordinates are reused during keyed moves; the position invalidates drawing.
            position = it.positionInRoot()
        } else Modifier)
        .drawBehind {
            val owner = group?.value
            val row = coordinates.takeIf { position.isSpecified }
            val atTopEdge = owner != null && row != null && owner.isAttached && row.isAttached &&
                owner.findRootCoordinates() === row.findRootCoordinates() &&
                kotlin.math.abs(owner.localPositionOf(row, Offset.Zero).y) < 0.5f
            if (!atTopEdge) {
                val inset = horizontalInset.toPx()
                // Framed groups own the shared row boundary. A virtual row clips its own
                // surface, so its entire separator must stay inside that row's bounds.
                val y = if (inSheet) (if (group == null) 0.5 else -0.5).dp.toPx() else 0f
                drawLine(color, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
            }
        }
}

/** The same grouped-cell material for virtualized rows that own their first/last corner shape. */
@Composable
fun Modifier.glasmioGroupedListBackground(
    shape: Shape = ContinuousRoundedRectangle(26.dp),
): Modifier {
    val colors = LocalGlassColors.current
    val sheetMaterial = LocalIosSheetMaterialContext.current
    val sheetBackdrop = LocalIosSheetBackdrop.current
    return clip(shape).then(
        if (sheetMaterial != null) Modifier.sheetCellBackground(sheetMaterial, sheetBackdrop, shape)
        else Modifier.background(colors.elevatedBackground.copy(
            alpha = LocalGroupedListBackgroundAlpha.current.coerceIn(0f, 1f),
        ), shape),
    )
}

/** Figma node 781:15374 grouped-list container. Use ordinary rows inside for GPU efficiency. */
@Composable
fun GlasmioGroupedList(
    modifier: Modifier = Modifier,
    // Disable the frame only when the parent already owns the grouped-cell material.
    framed: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalGlassColors.current
    val shape = ContinuousRoundedRectangle(26.dp)
    val groupCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (framed) {
                    Modifier
                        .glasmioGroupedListBackground(shape)
                        .onGloballyPositioned { groupCoordinates.value = it }
                } else {
                    Modifier
                },
            )
            // Rows own their horizontal insets. This avoids the accidental double inset
            // produced by secondary-page cards while preserving the Settings geometry.
            .padding(horizontal = 0.dp),
    ) {
        CompositionLocalProvider(
            LocalGroupedListCoordinates provides if (framed) groupCoordinates else null,
            LocalMergedGlassCards provides true,
            LocalGroupedListIconColor provides colors.accent,
            LocalContentColor provides colors.content,
        ) { content() }
    }
}

/** Apple's 52dp regular row and 68dp title/subtitle row. Accessories remain caller-owned slots. */
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
    enabled: Boolean = true,
    showDisclosure: Boolean = onClick != null && trailing == null,
    titleColor: Color = Color.Unspecified,
    titleAlignment: TextAlign = TextAlign.Start,
) {
    val colors = LocalGlassColors.current
    val inSheet = LocalIosSheetMaterialContext.current != null
    Row(
        modifier
            .fillMaxWidth()
            .height(if (subtitle == null) 52.dp else 68.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = if (inSheet) 20.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Box(Modifier.alpha(if (enabled) 1f else 0.4f)) { it() }
            Spacer(Modifier.width(8.dp))
        }
        systemName?.let {
            SfIcon(it, null, modifier = Modifier.alpha(if (enabled) 1f else 0.4f), size = 23.dp, tint = colors.accent)
            Spacer(Modifier.width(12.dp))
        }
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .glasmioGroupedListSeparator(colors.separator, visible = showTopSeparator)
                .alpha(if (enabled) 1f else 0.4f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                IosSheetText(
                    title,
                    modifier = Modifier.fillMaxWidth(),
                    style = IosTypography.body,
                    color = if (titleColor == Color.Unspecified) colors.content else titleColor,
                    useMaterial = titleColor == Color.Unspecified,
                    textAlign = titleAlignment,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    IosSheetText(
                        it,
                        style = IosTypography.subheadline,
                        color = colors.secondaryContent,
                        role = IosSheetTextRole.Secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                detail?.let {
                    IosSheetText(it, style = IosTypography.body, color = colors.secondaryContent,
                        role = IosSheetTextRole.Secondary, maxLines = 1)
                }
                trailing?.let { trailingContent ->
                    CompositionLocalProvider(
                        LocalGroupedListIconColor provides colors.content,
                        LocalContentColor provides colors.content,
                    ) { trailingContent() }
                }
                if (showDisclosure) {
                    Box(Modifier.size(width = 8.dp, height = 22.dp), contentAlignment = Alignment.Center) {
                        SfIcon(
                            "chevron.forward",
                            null,
                            modifier = Modifier.requiredSize(22.dp),
                            size = 22.dp,
                            fontSize = 17.sp,
                            weight = FontWeight(590),
                            mirrored = true,
                            tint = colors.tertiaryContent,
                            fontFeatureSettings = "'ss15' 1",
                            fitGlyphToBounds = false,
                        )
                    }
                }
            }
        }
    }
}

/** Figma 50:56535: the section label is emphasized body text, with its original casing. */
@Composable
fun GlasmioSectionTitle(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(39.dp).padding(horizontal = 16.dp).padding(bottom = 9.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        IosSheetText(
            title,
            style = IosTypography.body.copy(fontWeight = FontWeight(590)),
            color = LocalGlassColors.current.secondaryContent,
            role = IosSheetTextRole.Secondary,
        )
    }
}

/** Figma 35:55757. The footer expands naturally when its explanatory text wraps. */
@Composable
fun GlasmioGroupedTableFooter(text: String, modifier: Modifier = Modifier) {
    IosSheetText(
        text,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
        style = IosTypography.body.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = GlasmioTheme.letterSpacing((-0.08).sp)),
        color = LocalGlassColors.current.secondaryContent,
        role = IosSheetTextRole.Secondary,
    )
}

/** Figma 30:53802: the collapsed date/time accessory uses a flat 34dp capsule. */
@Composable
fun GlasmioListValuePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalGlassColors.current
    Box(
        modifier.height(34.dp).clip(Capsule()).background(colors.segmentedControlBackground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosTypography.body, color = colors.content.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

/** Reveals independent actions beside the row, on the system secondary background. */
@Composable
fun GlasmioListSwipeActions(
    revealed: Boolean,
    onRevealedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    actionsAtStart: Boolean = false,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var actionWidth by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val towardRight = actionsAtStart == (layoutDirection == LayoutDirection.Ltr)
    val sign = if (towardRight) 1f else -1f
    val distance = actionWidth + with(density) { 10.dp.toPx() }
    val target = if (revealed) sign * distance else 0f
    val offset by animateFloatAsState(
        targetValue = if (dragging) dragOffset else target,
        animationSpec = if (dragging) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "ListSwipeActions",
    )
    val visible = revealed || dragging || kotlin.math.abs(offset) > 0.5f
    val dragState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(
            if (towardRight) 0f else -distance,
            if (towardRight) distance else 0f,
        )
    }
    val colors = LocalGlassColors.current
    val background = if (colors.isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val rowBackground = if (colors.isDark) Color.Black else Color.White
    Layout(modifier = modifier.swipeBackExcluded().fillMaxWidth().clipToBounds().background(background), content = {
        Row(
            Modifier.onSizeChanged { actionWidth = it.width }
                .padding(start = if (actionsAtStart) 16.dp else 0.dp, end = if (actionsAtStart) 0.dp else 16.dp)
                .alpha(if (visible) 1f else 0f)
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
        Box(
            Modifier.fillMaxWidth().absoluteOffset { IntOffset(offset.roundToInt(), 0) }
                .clip(Capsule()).background(rowBackground)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { dragOffset = offset; dragging = true },
                    onDragStopped = {
                        onRevealedChange(kotlin.math.abs(dragOffset) >= distance / 2f)
                        dragging = false
                    },
                ),
            propagateMinConstraints = true,
        ) { content() }
    }) { measurables, constraints ->
        val actionPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val rowPlaceable = measurables[1].measure(
            constraints.copy(minHeight = maxOf(constraints.minHeight, actionPlaceable.height)),
        )
        val width = constraints.constrainWidth(maxOf(actionPlaceable.width, rowPlaceable.width))
        val height = constraints.constrainHeight(maxOf(actionPlaceable.height, rowPlaceable.height))
        layout(width, height) {
            actionPlaceable.placeRelative(
                if (actionsAtStart) 0 else width - actionPlaceable.width,
                (height - actionPlaceable.height) / 2,
            )
            rowPlaceable.placeRelative(0, 0)
        }
    }
}

/** Apple's symbol-only, inline-label and tall external-caption variants. */
enum class GlasmioSwipeActionStyle { SymbolOnly, SymbolAndLabel, SymbolAndLabelTall }

/** Figma 550:50318: a 60×38 symbol capsule with its label outside, in a 60×68 slot. */
@Composable
fun GlasmioListSwipeAction(
    systemName: String,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    label: String? = null,
    contentDescription: String? = label,
    style: GlasmioSwipeActionStyle = if (label == null) GlasmioSwipeActionStyle.SymbolOnly
        else GlasmioSwipeActionStyle.SymbolAndLabelTall,
) {
    val colors = LocalGlassColors.current
    val captionStyle = IosTypography.body.copy(
        fontSize = 13.sp,
        lineHeight = 15.sp,
        letterSpacing = GlasmioTheme.letterSpacing(0.sp),
        fontWeight = if (style == GlasmioSwipeActionStyle.SymbolAndLabelTall) FontWeight(510) else FontWeight.Normal,
    )
    val captionColor = if (colors.isDark) Color(0xFFEBEBF5).copy(alpha = 0.7f)
        else Color(0xFF3C3C43).copy(alpha = 0.6f)
    val descriptionModifier = if (contentDescription != null) Modifier.semantics {
        this.contentDescription = contentDescription
    } else Modifier
    val symbol: @Composable () -> Unit = {
        CompositionLocalProvider(LocalSfToolbarSymbols provides true) {
            SfIcon(
                systemName,
                contentDescription = null,
                modifier = Modifier.size(
                    width = if (style == GlasmioSwipeActionStyle.SymbolAndLabel) 15.dp else 44.dp,
                    height = 20.dp,
                ),
                size = 20.dp,
                fontSize = if (style == GlasmioSwipeActionStyle.SymbolAndLabel) 16.sp else 17.sp,
                weight = FontWeight.Normal,
                fontFeatureSettings = if (style == GlasmioSwipeActionStyle.SymbolAndLabel) "'ss15' 1" else "'ss16' 1",
                tint = Color.White,
            )
        }
    }
    if (style == GlasmioSwipeActionStyle.SymbolAndLabelTall) {
        Column(
            modifier.width(60.dp).height(68.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .then(descriptionModifier)
                .padding(top = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(38.dp).clip(Capsule()).background(containerColor),
                contentAlignment = Alignment.Center,
            ) { symbol() }
            Text(
                text = label.orEmpty(),
                modifier = Modifier.fillMaxWidth(),
                style = captionStyle,
                color = captionColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Row(
            modifier.height(44.dp).width(if (style == GlasmioSwipeActionStyle.SymbolOnly) 60.dp else 84.dp)
                .clip(Capsule()).background(containerColor)
                .clickable(role = Role.Button, onClick = onClick)
                .then(descriptionModifier)
                .padding(horizontal = if (style == GlasmioSwipeActionStyle.SymbolOnly) 8.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            symbol()
            if (style == GlasmioSwipeActionStyle.SymbolAndLabel) {
                Text(label.orEmpty(), style = captionStyle, color = Color.White, maxLines = 1)
            }
        }
    }
}
