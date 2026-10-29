package io.github.neoruaa.glasmio.glass.render

/** Invalid policies retain full resolution; supported source textures span 1/4x through 1x. */
internal fun resolveBackdropSamplingScale(requestedScale: Float): Float =
    if (requestedScale.isFinite()) requestedScale.coerceIn(0.25f, 1f) else 1f
