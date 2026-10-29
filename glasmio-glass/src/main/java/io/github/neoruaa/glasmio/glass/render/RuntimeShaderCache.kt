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

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private data class Entry(val source: String, val shader: RuntimeShader)

    private val runtimeShaders = mutableMapOf<String, Entry>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        val previous = runtimeShaders[key]
        if (previous != null && previous.source == string) return previous.shader
        return RuntimeShader(string).also { runtimeShaders[key] = Entry(string, it) }
    }

    fun clear() {
        runtimeShaders.clear()
    }
}
