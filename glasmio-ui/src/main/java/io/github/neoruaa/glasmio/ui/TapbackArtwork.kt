package io.github.neoruaa.glasmio.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The six classic Tapback image sequences, played once when their picker appears. */
enum class GlasmioMessageReactionArtwork {
    Heart, ThumbsUp, ThumbsDown, Laugh, Exclamation, Question,
}

internal fun tapbackArtworkResource(artwork: GlasmioMessageReactionArtwork): Int = when (artwork) {
    GlasmioMessageReactionArtwork.Heart -> R.drawable.glasmio_tapback_heart
    GlasmioMessageReactionArtwork.ThumbsUp -> R.drawable.glasmio_tapback_thumbsup
    GlasmioMessageReactionArtwork.ThumbsDown -> R.drawable.glasmio_tapback_thumbsdown
    GlasmioMessageReactionArtwork.Laugh -> R.drawable.glasmio_tapback_laugh
    GlasmioMessageReactionArtwork.Exclamation -> R.drawable.glasmio_tapback_exclamation
    GlasmioMessageReactionArtwork.Question -> R.drawable.glasmio_tapback_question
}

/** Drawable scheduling invalidates only drawing; it does not recompose the glass surface. */
@Composable
internal fun TapbackArtwork(artwork: GlasmioMessageReactionArtwork, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, context, artwork) {
        value = withContext(Dispatchers.IO) {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, tapbackArtworkResource(artwork)))
        }
    }
    val invalidation = remember { mutableIntStateOf(0) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(drawable) {
        val image = drawable
        if (image != null) {
            image.callback = object : Drawable.Callback {
                override fun invalidateDrawable(who: Drawable) {
                    invalidation.intValue++
                }

                override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                    handler.postAtTime(what, who, `when`)
                }

                override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                    handler.removeCallbacks(what, who)
                }
            }
            image.setVisible(true, true)
            if (image is AnimatedImageDrawable) {
                // CKTapbackClassicView starts on appearing and keeps its final frame.
                // Its disappearing hook is empty: playback continues through dismissal.
                image.repeatCount = 0
                image.start()
            }
        }
        onDispose {
            if (image is AnimatedImageDrawable) image.stop()
            image?.callback = null
            if (image != null) {
                handler.removeCallbacksAndMessages(image)
                image.setVisible(false, false)
            }
        }
    }
    Canvas(modifier) {
        invalidation.intValue
        drawable?.let { image ->
            image.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawIntoCanvas { image.draw(it.nativeCanvas) }
        }
    }
}
