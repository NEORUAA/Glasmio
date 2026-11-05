package io.github.neoruaa.glasmio.glass.render

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RuntimeShaderPoolTest {
    private class FakeShader(val source: String) : RuntimeShader {
        val floats = mutableMapOf<String, FloatArray>()
        val ints = mutableMapOf<String, IntArray>()
        val declared: Map<String, Int>? = RuntimeShaderPool.uniformSlots(source)?.associate { it.name to it.count }

        private fun checkSize(name: String, count: Int) {
            val expected = declared?.get(name) ?: throw IllegalArgumentException("unknown uniform $name")
            if (expected != count) throw IllegalArgumentException("$name expects $expected values")
        }
        override fun setFloatUniform(name: String, value: Float) = setFloatUniform(name, floatArrayOf(value))
        override fun setFloatUniform(name: String, value1: Float, value2: Float) = setFloatUniform(name, floatArrayOf(value1, value2))
        override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) =
            setFloatUniform(name, floatArrayOf(value1, value2, value3))
        override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) =
            setFloatUniform(name, floatArrayOf(value1, value2, value3, value4))
        override fun setFloatUniform(name: String, values: FloatArray) { checkSize(name, values.size); floats[name] = values.copyOf() }
        override fun setIntUniform(name: String, value: Int) = setIntUniform(name, intArrayOf(value))
        override fun setIntUniform(name: String, value1: Int, value2: Int) = setIntUniform(name, intArrayOf(value1, value2))
        override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) = setIntUniform(name, intArrayOf(value1, value2, value3))
        override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) =
            setIntUniform(name, intArrayOf(value1, value2, value3, value4))
        override fun setIntUniform(name: String, values: IntArray) { checkSize(name, values.size); ints[name] = values.copyOf() }
        override fun setColorUniform(name: String, color: Color) = throw UnsupportedOperationException()
    }

    private val source = """
        uniform shader content;
        uniform float2 size; // uniform float4 commented;
        /* uniform shader hidden; */
        uniform float4 rects[3];
        uniform float3x3 matrix;
        uniform half4 tint;
        uniform int count;
        half4 main(float2 p) { return content.eval(p); }
    """.trimIndent()

    @Test
    fun parsesEveryUniformAndIgnoresComments() {
        val slots = requireNotNull(RuntimeShaderPool.uniformSlots(source))
        assertEquals(listOf("size" to 2, "rects" to 12, "matrix" to 9, "tint" to 4, "count" to 1),
            slots.map { it.name to it.count })
        assertEquals(listOf(false, false, false, false, true), slots.map { it.integer })
    }

    @Test
    fun rejectsSourcesThatCannotBeResetToFreshState() {
        assertNull(RuntimeShaderPool.uniformSlots("uniform shader contour; uniform float a;"))
        assertNull(RuntimeShaderPool.uniformSlots("layout(color) uniform half4 color;"))
        assertNull(RuntimeShaderPool.uniformSlots("uniform float a, b;"))
        assertNull(RuntimeShaderPool.uniformSlots("uniform bool flag;"))
        assertNull(RuntimeShaderPool.uniformSlots("uniform shader content[2];"))
        assertEquals(0, RuntimeShaderPool.uniformSlots("half4 main(float2 p) { return half4(0); }")?.size)
    }

    @Test
    fun reusedProgramStartsFromZeroUniformsLikeAFreshCompile() {
        val pool = RuntimeShaderPool()
        val shader = FakeShader(source)
        shader.setFloatUniform("size", 3f, 4f)
        shader.setFloatUniform("rects", FloatArray(12) { it + 1f })
        shader.setIntUniform("count", 7)
        pool.release(source, shader)
        val reused = pool.acquire(source)
        assertSame(shader, reused)
        for (name in listOf("size", "rects", "matrix", "tint")) {
            assertEquals(0f, shader.floats.getValue(name).maxOf { kotlin.math.abs(it) }, 0f)
        }
        assertEquals(0, shader.ints.getValue("count").single())
        assertNull(pool.acquire(source))
        assertEquals(0, pool.idleCount)
    }

    @Test
    fun unshareableAndMismatchedProgramsAreNeverHandedOut() {
        val pool = RuntimeShaderPool()
        val child = "uniform shader contour; half4 main(float2 p) { return contour.eval(p); }"
        pool.release(child, FakeShader(child))
        assertNull(pool.acquire(child))
        // A program whose real declarations differ from the parsed plan is dropped, not reused.
        val parsed = "uniform float2 size;"
        pool.release(parsed, FakeShader("uniform float4 size;"))
        assertNull(pool.acquire(parsed))
        assertEquals(0, pool.idleCount)
    }

    @Test
    fun boundsIdleProgramsPerSourceAndInTotal() {
        val pool = RuntimeShaderPool(capacity = 3, perSource = 2)
        val a = "uniform float a;"
        val b = "uniform float b;"
        val first = FakeShader(a)
        pool.release(a, first)
        pool.release(a, first)
        pool.release(a, FakeShader(a))
        pool.release(a, FakeShader(a))
        assertEquals(2, pool.idleCount)
        pool.release(b, FakeShader(b))
        pool.release(b, FakeShader(b))
        assertEquals(3, pool.idleCount)
        assertEquals(2, generateSequence { pool.acquire(b) }.count())
        assertEquals(1, generateSequence { pool.acquire(a) }.count())
    }

    @Test
    fun detachedScopeProgramsServeTheNextScopeWithoutCompiling() {
        val pool = RuntimeShaderPool()
        var compiled = 0
        fun cache() = RuntimeShaderCacheImpl(pool = { pool }, compile = { compiled++; FakeShader(it) })
        val first = cache()
        val shader = first.obtainRuntimeShader("key", source)
        assertSame(shader, first.obtainRuntimeShader("key", source))
        first.clear()
        val second = cache()
        assertSame(shader, second.obtainRuntimeShader("other-key", source))
        assertEquals(1, compiled)
        // Both scopes alive: the second user of a source compiles its own builder.
        val third = cache()
        assertNotSame(shader, third.obtainRuntimeShader("key", source))
        assertEquals(2, compiled)
    }
}
