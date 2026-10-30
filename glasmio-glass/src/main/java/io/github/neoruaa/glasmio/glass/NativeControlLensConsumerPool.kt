package io.github.neoruaa.glasmio.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import java.util.LinkedHashMap

/** Fully configured built-in consumers only; mutable shaders never leave a snapshot lease. */
internal class NativeControlLensConsumerPool(
    private val capacity: Int = 8,
    private val factory: (String) -> RuntimeShader = ::RuntimeShader,
) {
    private class Entry(val shader: RuntimeShader) {
        var inUse: Boolean = false
    }

    private val threadId = Thread.currentThread().id
    private val entries = LinkedHashMap<String, Entry>(capacity, .75f, true)

    init {
        require(capacity in 1..8)
    }

    internal val retainedSourceCount: Int
        get() {
            checkThread()
            return entries.size
        }

    /** [privateShader] retains the original owner cache contract when a source is busy. */
    fun snapshot(
        source: String,
        privateShader: () -> RuntimeShader,
        configure: (RuntimeShader) -> RenderEffect,
    ): RenderEffect {
        checkThread()
        var entry = entries[source]
        if (entry?.inUse == true) return configure(privateShader())
        if (entry == null) {
            if (entries.size == capacity && !removeIdleEntry()) {
                return configure(privateShader())
            }
            entry = Entry(factory(source))
            entries[source] = entry
        }
        entry.inUse = true
        return try {
            configure(entry.shader)
        } finally {
            entry.inUse = false
        }
    }

    private fun removeIdleEntry(): Boolean {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (!iterator.next().value.inUse) {
                iterator.remove()
                return true
            }
        }
        return false
    }

    private fun checkThread() {
        check(Thread.currentThread().id == threadId) {
            "Control-lens consumers must be configured on their pool's thread"
        }
    }
}

/** No scopes, Contexts, input shaders or bitmaps are retained by the shared cache. */
internal object NativeControlLensConsumerShaders {
    private val pools = object : ThreadLocal<NativeControlLensConsumerPool>() {
        override fun initialValue(): NativeControlLensConsumerPool = NativeControlLensConsumerPool()
    }

    fun snapshot(
        source: String,
        privateShader: () -> RuntimeShader,
        configure: (RuntimeShader) -> RenderEffect,
    ): RenderEffect = pools.get().snapshot(source, privateShader, configure)
}
