package io.github.neoruaa.glasmio.icons

import android.graphics.Typeface
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Resolves semantic system names without requiring a bundled font or a resource ID. */
fun interface GlasmioIconResolver {
    fun resolve(systemName: String): ImageVector?
}

/** Optional host-owned symbol font. The library never ships or loads an Apple font. */
val LocalGlasmioSymbolTypeface = staticCompositionLocalOf<Typeface?> { null }
val LocalGlasmioIconResolver = staticCompositionLocalOf<GlasmioIconResolver> { MaterialGlasmioIconResolver }
/** Evaluated at each icon call site so nested theme and row locals remain visible. */
val LocalGlasmioIconTint = staticCompositionLocalOf<@Composable () -> Color> {
    { LocalContentColor.current }
}

/** An Apache-2.0 Material icon fallback for the controls included in Glasmio. */
object MaterialGlasmioIconResolver : GlasmioIconResolver {
    override fun resolve(systemName: String): ImageVector = when {
        systemName == "xmark" || systemName.startsWith("xmark.circle") -> Icons.Default.Close
        systemName.startsWith("checkmark") -> Icons.Default.Check
        systemName.startsWith("plus.square.on.square") -> Icons.Default.AddToPhotos
        systemName == "plus" || systemName.startsWith("plus.") -> Icons.Default.Add
        systemName == "minus" || systemName.startsWith("minus.") -> Icons.Default.Remove
        systemName.startsWith("chevron.backward") || systemName.startsWith("arrow.backward") -> Icons.AutoMirrored.Filled.ArrowBack
        systemName.startsWith("chevron.forward") || systemName.startsWith("arrow.forward") -> Icons.AutoMirrored.Filled.ArrowForward
        systemName.startsWith("arrow.uturn.backward") -> Icons.AutoMirrored.Filled.Undo
        systemName.startsWith("rectangle.portrait.and.arrow") -> Icons.AutoMirrored.Filled.ExitToApp
        systemName.startsWith("chevron.left") -> Icons.Default.ChevronLeft
        systemName.startsWith("chevron.right") -> Icons.Default.ChevronRight
        systemName.startsWith("chevron.up") -> Icons.Default.KeyboardArrowUp
        systemName.startsWith("chevron.down") -> Icons.Default.KeyboardArrowDown
        systemName.startsWith("arrow.left") -> Icons.Default.ArrowBack
        systemName.startsWith("arrow.right") -> Icons.Default.ArrowForward
        systemName.startsWith("arrow.up.arrow.down") -> Icons.Default.SwapVert
        systemName.startsWith("arrow.up") -> Icons.Default.ArrowUpward
        systemName.startsWith("arrow.down") -> Icons.Default.ArrowDownward
        systemName.startsWith("arrow.clockwise") || systemName.startsWith("arrow.triangle") -> Icons.Default.Refresh
        systemName.startsWith("arrow.counterclockwise") -> Icons.Default.Replay
        systemName.startsWith("magnifyingglass") -> Icons.Default.Search
        systemName.startsWith("house") -> Icons.Default.Home
        systemName.startsWith("gear") -> Icons.Default.Settings
        systemName.startsWith("heart") -> Icons.Default.Favorite
        systemName.startsWith("star") -> Icons.Default.Star
        systemName.startsWith("person") -> Icons.Default.Person
        systemName.startsWith("play") -> Icons.Default.PlayArrow
        systemName.startsWith("pause") -> Icons.Default.Pause
        systemName.startsWith("backward") -> Icons.Default.SkipPrevious
        systemName.startsWith("forward") -> Icons.Default.SkipNext
        systemName.startsWith("music.note.list") -> Icons.Default.QueueMusic
        systemName.startsWith("music.note") || systemName == "waveform" -> Icons.Default.MusicNote
        systemName.startsWith("mic") -> Icons.Default.Mic
        systemName.startsWith("radio") || systemName.startsWith("dot.radiowaves") -> Icons.Default.Radio
        systemName.startsWith("safari") -> Icons.Default.Explore
        systemName.startsWith("icloud") || systemName.startsWith("cloud") -> Icons.Default.Cloud
        systemName.startsWith("clock") -> Icons.Default.Schedule
        systemName.startsWith("exclamationmark") -> Icons.Default.Warning
        systemName.startsWith("ellipsis") -> Icons.Default.MoreHoriz
        systemName.startsWith("line.3.horizontal") || systemName.startsWith("list.") -> Icons.Default.Menu
        systemName.startsWith("trash") -> Icons.Default.Delete
        systemName.startsWith("pencil") -> Icons.Default.Edit
        systemName.startsWith("square.and.arrow.up") -> Icons.Default.Share
        systemName.startsWith("doc.on.doc") -> Icons.Default.ContentCopy
        systemName.startsWith("calendar") -> Icons.Default.DateRange
        systemName.startsWith("photo") -> Icons.Default.Image
        systemName.startsWith("camera") -> Icons.Default.CameraAlt
        systemName.startsWith("lock") -> Icons.Default.Lock
        systemName.startsWith("eye") -> Icons.Default.Visibility
        systemName.startsWith("bell") -> Icons.Default.Notifications
        systemName.startsWith("info") -> Icons.Default.Info
        systemName.startsWith("questionmark") -> Icons.Default.HelpOutline
        systemName.startsWith("slider") -> Icons.Default.Tune
        systemName.startsWith("speaker") -> Icons.Default.VolumeUp
        systemName.startsWith("wifi") -> Icons.Default.Wifi
        systemName.startsWith("paintpalette") -> Icons.Default.Palette
        systemName.startsWith("eyedropper") -> Icons.Default.Colorize
        systemName.startsWith("sun") -> Icons.Default.Brightness7
        systemName.startsWith("moon") -> Icons.Default.Brightness2
        systemName.startsWith("square.stack") -> Icons.Default.Layers
        systemName.startsWith("rectangle.3.group") -> Icons.Default.ViewCarousel
        systemName.startsWith("rectangle.bottomthird") || systemName.startsWith("inset.filled.bottomthird.rectangle") -> Icons.Default.VerticalAlignBottom
        systemName.startsWith("rectangle") || systemName.startsWith("square") -> Icons.Default.CropSquare
        systemName.startsWith("circle") -> Icons.Default.RadioButtonUnchecked
        systemName.startsWith("text") || systemName == "character" -> Icons.Default.TextFields
        systemName.startsWith("book") -> Icons.Default.Book
        systemName.startsWith("folder") -> Icons.Default.Folder
        systemName.startsWith("globe") -> Icons.Default.Language
        systemName.startsWith("sparkles") -> Icons.Default.AutoAwesome
        else -> Icons.Default.Widgets
    }
}

@Composable
fun GlasmioIconProvider(
    resolver: GlasmioIconResolver = MaterialGlasmioIconResolver,
    symbolTypeface: Typeface? = null,
    defaultTint: @Composable () -> Color = { LocalContentColor.current },
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalGlasmioIconResolver provides resolver,
        LocalGlasmioSymbolTypeface provides symbolTypeface,
        LocalGlasmioIconTint provides defaultTint,
        content = content,
    )
}

@Composable
internal fun GlasmioResolvedIcon(
    systemName: String,
    contentDescription: String?,
    modifier: Modifier,
    tint: Color,
    size: Dp,
    mirrored: Boolean,
) {
    val vector = LocalGlasmioIconResolver.current.resolve(systemName)
        ?: MaterialGlasmioIconResolver.resolve(systemName)
    Icon(vector, contentDescription,
        modifier.size(size).then(if (mirrored && !vector.autoMirror) Modifier.scale(-1f, 1f) else Modifier), tint)
}

@Composable
fun GlasmioIcon(
    systemName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalGlasmioIconTint.current(),
    size: Dp = 24.dp,
) = SfIcon(systemName, contentDescription, modifier, tint, size)
