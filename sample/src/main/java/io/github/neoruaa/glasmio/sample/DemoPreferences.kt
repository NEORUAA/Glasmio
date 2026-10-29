package io.github.neoruaa.glasmio.sample

import android.content.Context

/** Host settings survive a fresh process; component examples keep their own local state. */
internal data class DemoPreferences(
    val dark: Boolean,
    val reduceTransparency: Boolean = false,
    val hdrEffects: Boolean = false,
    val fpsCounter: Boolean = false,
    val rtl: Boolean = false,
    val separateNavigationItem: Boolean = true,
) {
    val hdrOutput: Boolean get() = hdrEffects && !reduceTransparency

    fun save(context: Context) {
        // Complete the small settings write before a new Activity/process reads its output mode.
        context.getSharedPreferences(StoreName, Context.MODE_PRIVATE).edit()
            .putBoolean("dark", dark)
            .putBoolean("reduceTransparency", reduceTransparency)
            .putBoolean("hdrEffects", hdrEffects)
            .putBoolean("fpsCounter", fpsCounter)
            .putBoolean("rtl", rtl)
            .putBoolean("separateNavigationItem", separateNavigationItem)
            .commit()
    }

    companion object {
        private const val StoreName = "demo-settings"

        fun load(context: Context, systemDark: Boolean): DemoPreferences {
            val values = context.getSharedPreferences(StoreName, Context.MODE_PRIVATE)
            return DemoPreferences(
                dark = values.getBoolean("dark", systemDark),
                reduceTransparency = values.getBoolean("reduceTransparency", false),
                hdrEffects = values.getBoolean("hdrEffects", false),
                fpsCounter = values.getBoolean("fpsCounter", false),
                rtl = values.getBoolean("rtl", false),
                separateNavigationItem = values.getBoolean("separateNavigationItem", true),
            )
        }
    }
}
