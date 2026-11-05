/*
 * Copyright 2025 Kyant
 * Copyright 2026 Glasmio contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adapted from AndroidLiquidGlass 2.0.1 generic capture/effect infrastructure.
 * Modified for Glasmio's owned namespace and Android material renderer.
 */

package io.github.neoruaa.glasmio.glass.render


interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl(
    private val pool: () -> RuntimeShaderPool? = SharedRuntimeShaderPool::current,
    private val compile: (String) -> RuntimeShader = ::RuntimeShader,
) : RuntimeShaderCache {

    private data class Entry(val source: String, val shader: RuntimeShader)

    private val runtimeShaders = mutableMapOf<String, Entry>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        val previous = runtimeShaders[key]
        if (previous != null && previous.source == string) return previous.shader
        val shared = pool()
        previous?.let { shared?.release(it.source, it.shader) }
        val shader = shared?.acquire(string) ?: compile(string)
        runtimeShaders[key] = Entry(string, shader)
        return shader
    }

    fun clear() {
        // A detached scope never touches these builders again. Effects and paints created from
        // them hold their own uniform snapshots, so the compiled programs can serve a new scope.
        val shared = pool()
        if (shared != null) runtimeShaders.values.forEach { shared.release(it.source, it.shader) }
        runtimeShaders.clear()
    }
}

/**
 * Idle compiled programs from reset effect scopes, keyed by their complete source.
 *
 * Scopes are recreated whenever their host leaves composition (for example a navigation bar that
 * is hidden on a pushed page), and the same constant sources would otherwise be compiled again on
 * the main thread. A released program is handed out at most once at a time. Before reuse every
 * float and int uniform is rewritten to zero, the state of a freshly compiled builder. Programs
 * that declare a child other than the effect input `content`, color uniforms or any declaration
 * this parser does not recognize are never shared; their owners compile as before.
 */
internal class RuntimeShaderPool(
    private val capacity: Int = 48,
    private val perSource: Int = 4,
) {
    internal class Slot(val name: String, val count: Int, val integer: Boolean)

    // Access order: the least recently used source is evicted first.
    private val idle = LinkedHashMap<String, ArrayDeque<RuntimeShader>>(16, .75f, true)
    // Reset plans are parsed once per source and only when a program is actually reused,
    // never while a detaching scope releases its programs.
    private val plans = HashMap<String, Plan>()
    private class Plan(val slots: List<Slot>?)
    private var size = 0

    internal val idleCount: Int get() = size

    fun acquire(source: String): RuntimeShader? {
        val shaders = idle[source] ?: return null
        val shader = shaders.removeLastOrNull() ?: return null
        size--
        if (shaders.isEmpty()) idle.remove(source)
        if (plans.size >= MaximumPlans && source !in plans) plans.clear()
        val slots = plans.getOrPut(source) { Plan(uniformSlots(source)) }.slots ?: return null
        return try {
            for (slot in slots) {
                if (slot.integer) shader.setIntUniform(slot.name, IntArray(slot.count))
                else shader.setFloatUniform(slot.name, FloatArray(slot.count))
            }
            shader
        } catch (_: RuntimeException) {
            // A declaration the program does not expose with this exact size: compile instead.
            null
        }
    }

    fun release(source: String, shader: RuntimeShader) {
        if (plans[source]?.slots == null && source in plans) return
        val shaders = idle[source] ?: ArrayDeque<RuntimeShader>().also { idle[source] = it }
        if (shaders.size >= perSource || shaders.any { it === shader }) return
        while (size >= capacity) {
            val eldest = idle.entries.iterator()
            if (!eldest.hasNext()) return
            val victim = eldest.next()
            if (victim.value.removeFirstOrNull() == null) return
            size--
            if (victim.value.isEmpty() && victim.key != source) eldest.remove()
        }
        shaders.addLast(shader)
        size++
    }

    internal companion object {
        private const val MaximumPlans = 256
        private val single = Regex("""^\s*(\w+)\s+(\w+)\s*(?:\[\s*(\d+)\s*])?\s*$""")
        private val floatSizes = mapOf(
            "float" to 1, "float2" to 2, "float3" to 3, "float4" to 4,
            "half" to 1, "half2" to 2, "half3" to 3, "half4" to 4,
            "float2x2" to 4, "float3x3" to 9, "float4x4" to 16,
            "half2x2" to 4, "half3x3" to 9, "half4x4" to 16,
        )
        private val intSizes = mapOf("int" to 1, "int2" to 2, "int3" to 3, "int4" to 4)

        private fun identifier(c: Char) = c == '_' || c.isLetterOrDigit()

        private fun withoutComments(source: String): String {
            val out = StringBuilder(source.length)
            var i = 0
            while (i < source.length) {
                val c = source[i]
                if (c == '/' && i + 1 < source.length && source[i + 1] == '/') {
                    val end = source.indexOf('\n', i)
                    i = if (end < 0) source.length else end
                    out.append(' ')
                } else if (c == '/' && i + 1 < source.length && source[i + 1] == '*') {
                    val end = source.indexOf("*/", i + 2)
                    i = if (end < 0) source.length else end + 2
                    out.append(' ')
                } else {
                    out.append(c); i++
                }
            }
            return out.toString()
        }

        private fun hasWord(code: String, word: String, from: Int): Int {
            var index = from
            while (true) {
                val at = code.indexOf(word, index)
                if (at < 0) return -1
                val end = at + word.length
                if ((at == 0 || !identifier(code[at - 1])) && (end >= code.length || !identifier(code[end]))) return at
                index = end
            }
        }

        /** Zero-reset plan for a shareable source, or null when it must not be shared. Linear time. */
        internal fun uniformSlots(source: String): List<Slot>? {
            val code = withoutComments(source)
            var layout = hasWord(code, "layout", 0)
            while (layout >= 0) {
                var next = layout + 6
                while (next < code.length && code[next].isWhitespace()) next++
                if (next < code.length && code[next] == '(') return null
                layout = hasWord(code, "layout", layout + 6)
            }
            val slots = ArrayList<Slot>()
            var at = hasWord(code, "uniform", 0)
            while (at >= 0) {
                val end = code.indexOf(';', at + 7)
                if (end < 0) return null
                val parts = single.matchEntire(code.substring(at + 7, end)) ?: return null
                val type = parts.groupValues[1]
                val name = parts.groupValues[2]
                val length = parts.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
                if (length <= 0) return null
                when (type) {
                    "shader" -> if (name != "content" || parts.groupValues[3].isNotEmpty()) return null
                    in floatSizes -> slots += Slot(name, floatSizes.getValue(type) * length, integer = false)
                    in intSizes -> slots += Slot(name, intSizes.getValue(type) * length, integer = true)
                    else -> return null
                }
                at = hasWord(code, "uniform", end + 1)
            }
            return slots
        }
    }
}

/** One pool per thread: effect scopes record and reset on their owner's UI thread. */
internal object SharedRuntimeShaderPool {
    private val pools = object : ThreadLocal<RuntimeShaderPool>() {
        override fun initialValue(): RuntimeShaderPool = RuntimeShaderPool()
    }

    fun current(): RuntimeShaderPool = pools.get()!!
}
