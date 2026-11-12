// Copyright 2026, Glasmio contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from compose-miuix-ui's ProvideDisplayCutoutFix (Apache-2.0).

package io.github.neoruaa.glasmio.core

import android.graphics.Insets
import android.os.Build
import android.view.DisplayCutout
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsAnimation
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Repairs missing display-cutout insets on Android OEM builds.
 *
 * Some devices omit the cutout from insets dispatched to the Compose view tree, especially in
 * landscape and during system-window animations. The current window metrics provide the stable
 * cutout bounds, which are merged into both regular and animated inset dispatches.
 */
@Composable
fun ProvideGlasmioDisplayCutoutFix() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !LocalGlasmioDisplayCutoutFixEnabled.current) {
        return
    }

    val view = LocalView.current
    DisposableEffect(view) {
        val targetView = (view.parent as? View) ?: view.rootView
        synchronized(cutoutFixRegistrations) {
            val registration = cutoutFixRegistrations[targetView] ?: CutoutFixRegistration(targetView).also {
                cutoutFixRegistrations[targetView] = it
                it.install()
            }
            registration.refCount++
        }

        onDispose {
            synchronized(cutoutFixRegistrations) {
                val registration = cutoutFixRegistrations[targetView] ?: return@synchronized
                if (--registration.refCount <= 0) {
                    cutoutFixRegistrations.remove(targetView)
                    registration.uninstall()
                }
            }
        }
    }
}

/** Allows a host with its own cutout handling to opt out of the automatic repair. */
val LocalGlasmioDisplayCutoutFixEnabled = staticCompositionLocalOf { true }

private val cutoutFixRegistrations = WeakHashMap<View, CutoutFixRegistration>()

@RequiresApi(Build.VERSION_CODES.R)
private class CutoutFixRegistration(private val view: View) {
    var refCount: Int = 0

    private var resolved = false
    private var cutout: DisplayCutout? = null
    private var cutoutInsets: Insets = Insets.of(0, 0, 0, 0)

    private val insetsListener = View.OnApplyWindowInsetsListener { v, insets ->
        resolve()
        v.onApplyWindowInsets(fix(insets))
    }

    private val animationCallback = object : WindowInsetsAnimation.Callback(
        DISPATCH_MODE_CONTINUE_ON_SUBTREE,
    ) {
        override fun onProgress(
            insets: WindowInsets,
            runningAnimations: MutableList<WindowInsetsAnimation>,
        ): WindowInsets {
            if (!resolved) resolve()
            return fix(insets)
        }
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            v.requestApplyInsets()
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    }

    fun install() {
        view.setOnApplyWindowInsetsListener(insetsListener)
        view.setWindowInsetsAnimationCallback(animationCallback)
        view.addOnAttachStateChangeListener(attachListener)
        if (view.isAttachedToWindow) view.requestApplyInsets()
    }

    fun uninstall() {
        view.setOnApplyWindowInsetsListener(null)
        view.setWindowInsetsAnimationCallback(null)
        view.removeOnAttachStateChangeListener(attachListener)
    }

    private fun resolve() {
        resolved = true
        val currentCutout = view.context.getSystemService(WindowManager::class.java)
            ?.currentWindowMetrics?.windowInsets?.displayCutout
        cutout = currentCutout
        cutoutInsets = if (currentCutout == null) {
            Insets.of(0, 0, 0, 0)
        } else {
            Insets.of(
                currentCutout.safeInsetLeft,
                currentCutout.safeInsetTop,
                currentCutout.safeInsetRight,
                currentCutout.safeInsetBottom,
            )
        }
    }

    private fun fix(insets: WindowInsets): WindowInsets {
        val systemCutout = insets.getInsets(WindowInsets.Type.displayCutout())
        val left = maxOf(systemCutout.left, cutoutInsets.left)
        val top = maxOf(systemCutout.top, cutoutInsets.top)
        val right = maxOf(systemCutout.right, cutoutInsets.right)
        val bottom = maxOf(systemCutout.bottom, cutoutInsets.bottom)
        val currentCutout = cutout ?: return insets
        if (left == systemCutout.left && top == systemCutout.top &&
            right == systemCutout.right && bottom == systemCutout.bottom
        ) {
            return insets
        }
        return WindowInsets.Builder(insets)
            .setDisplayCutout(currentCutout)
            .setInsets(WindowInsets.Type.displayCutout(), Insets.of(left, top, right, bottom))
            .build()
    }
}
