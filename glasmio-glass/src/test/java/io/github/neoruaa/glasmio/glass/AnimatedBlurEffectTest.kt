package io.github.neoruaa.glasmio.glass

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimatedBlurEffectTest {
    @Test
    fun shaderUsesUnifiedAxisAndBoundsUniforms() {
        assertTrue(AnimatedGaussianBlurShader.contains("uniform float sigma;"))
        assertTrue(AnimatedGaussianBlurShader.contains("uniform float2 axis;"))
        assertTrue(AnimatedGaussianBlurShader.contains("uniform float4 bounds;"))
        assertTrue(AnimatedGaussianBlurShader.contains("for (int k = 0; k < 16; ++k)"))
    }

    @Test
    fun nonPositiveOrNonFiniteRadiusReturnsNull() {
        assertNull(createAnimatedBlurRenderEffect(0f))
        assertNull(createAnimatedBlurRenderEffect(0.005f))
        assertNull(createAnimatedBlurRenderEffect(-4f))
        assertNull(createAnimatedBlurRenderEffect(Float.NaN))
        assertNull(createAnimatedBlurRenderEffect(Float.POSITIVE_INFINITY))
    }
}
