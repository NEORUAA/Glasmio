package io.github.neoruaa.glasmio.glass

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import io.github.neoruaa.glasmio.glass.render.highlight.NativeCasdfHighlightMaskFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.NativeGlassEdgeFunctions
import io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrix
import io.github.neoruaa.glasmio.glass.render.highlight.NativeVibrantColorMatrixFunctions
import io.github.neoruaa.glasmio.glass.render.optics.GlassSdfShader
import io.github.neoruaa.glasmio.glass.render.optics.NativeGlassRefractionFunctions
import io.github.neoruaa.glasmio.glass.render.optics.glassCornerRadii
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max

/**
 * UIKit's control lens uses distinct backdrop, portal, background and foreground transfers.
 * [liftedContent] is the actual transparent source view tree, including its own item transforms.
 * The result includes selected content at rest as well as while lifted. Geometry is supplied by
 * the control's measured bounds; this function never applies the lift's size transform again.
 */
fun BackdropEffectScope.nativeControlLens(
    style: NativeControlLensStyle,
    liftProgress: Float,
    isDark: Boolean,
    liftedContent: Backdrop? = null,
    restingColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
) {
    val scope = this as? BackdropEffectScopeImpl ?: return
    val small = style == NativeControlLensStyle.SwitchThumb || style == NativeControlLensStyle.SliderThumb
    val lift = liftProgress.takeIf { it.isFinite() } ?: 0f
    val rest = (1f - lift).coerceIn(0f, 1f)
    // UIKit supplies identity endpoints per field. This shared spring progress is the
    // portable interpolation adapter; it is not a recovered Core Animation evaluator.
    val opticalProgress = lift.coerceAtLeast(0f)
    val materialOpacity = lift.coerceIn(0f, 1f)
    val fullyResting = lift <= 0f
    val knownRestingThumb = fullyResting && small && liftedContent == null
    val height = size.height
    val tab = style == NativeControlLensStyle.TabBarIndicator
    val blurScale = if (tab) .25f else 1f
    val belowAmount = when (style) {
        NativeControlLensStyle.SliderThumb -> 0f
        NativeControlLensStyle.SwitchThumb -> 50f + (9f - 50f) * lift
        else -> 9f * lift
    } * density
    // Source support covers the measured SmallVariant's unlift displacement (50 pt),
    // portal warp (17.5 pt) and Gaussian support. The output guard is independent.
    // A known resting thumb only draws its body/shadow and never reads these planes.
    if (!knownRestingThumb) padding = max(padding, 72.dp.toPx())
    scope.auxiliaryBackdrop = liftedContent
    val base = IntSize(ceil(size.width + 2f * padding).toInt().coerceAtLeast(1),
        ceil(size.height + 2f * padding).toInt().coerceAtLeast(1))
    val blur = when {
        small && !fullyResting -> 6.dp.toPx() * rest
        tab -> 2.dp.toPx()
        else -> 0f
    }
    // CA gaussianBlur is an independent layer, not the regular material's variable-blur
    // pyramid. HWUI supplies this sampler adapter; the two native optical kernels below
    // retain their own recovered signed displacement and spectral sampling equations.
    val blurredSize = IntSize(ceil(base.width * blurScale).toInt().coerceAtLeast(1),
        ceil(base.height * blurScale).toInt().coerceAtLeast(1))
    val producerStages = mutableListOf<RenderEffect>()
    val producer = if (blur > 0f && scope.colorManagedNativeGlass) {
        val colorFunctions = NativeControlProducerManagedColor
        val atlasExtract = obtainRuntimeShader("Glasmio.ControlLens.extractAtlas.true",
            colorFunctions + GlassSdfShader + NativeControlDisplacementFunctions +
                NativeControlLensExtractFunctions + NativeControlLensExtractAtlasMain).asAndroidRuntimeShader()
        atlasExtract.setFloatUniform("planeSize", base.width.toFloat(), base.height.toFloat())
        atlasExtract.setFloatUniform("size", size.width, size.height)
        atlasExtract.setFloatUniform("cornerRadii", glassCornerRadii(shape, size, layoutDirection, this))
        atlasExtract.setFloatUniform("extractOrigin", padding, padding)
        atlasExtract.setFloatUniform("extractScale", blurScale)
        atlasExtract.setFloatUniform("extractWarp", if (small) 1f else 0f, belowAmount, 36.dp.toPx())
        atlasExtract.setFloatUniform("blurredSize", blurredSize.width.toFloat(), blurredSize.height.toFloat())
        producerStages += RenderEffect.createRuntimeShaderEffect(atlasExtract, "content")
        val weights = scope.cachedResource("Glasmio.ControlLens.GaussianWeights") { NativeControlGaussianWeights() }
        weights.update(blur * blurScale)
        for (axis in 0..1) {
            val pass = obtainRuntimeShader("Glasmio.ControlLens.gaussianAtlas.${weights.tapLimit}.$axis.$small",
                colorFunctions + nativeControlManagedAtlasGaussian(weights.tapLimit, planeOnly = small)).asAndroidRuntimeShader()
            pass.setFloatUniform("gaussianAxis", if (axis == 0) 1f else 0f, if (axis == 1) 1f else 0f)
            pass.setFloatUniform("gaussianRadius", weights.radius.toFloat())
            pass.setFloatUniform("gaussianWeights", weights.values)
            pass.setFloatUniform("gaussianTotal", weights.total)
            pass.setFloatUniform("gaussianSize", blurredSize.width.toFloat(), blurredSize.height.toFloat())
            if (!small) pass.setFloatUniform("planeSize", base.width.toFloat(), base.height.toFloat())
            producerStages += RenderEffect.createRuntimeShaderEffect(pass, "content")
        }
        null
    } else if (blur > 0f) nativeControlLensReferenceProducer(style, liftProgress) else null
    val precomposedScene = !fullyResting && !small
    val sceneTop = base.height * 2 + blurredSize.height
    val sceneSize = IntSize(ceil(base.width / scope.samplingScale).toInt(),
        ceil(base.height / scope.samplingScale).toInt())
    val rects = if (knownRestingThumb) listOf(IntRect(0, 0, base.width, base.height)) else listOf(
        IntRect(0, 0, base.width, base.height), IntRect(0, base.height, base.width, base.height * 2),
        IntRect(0, base.height * 2, blurredSize.width, sceneTop)) +
        if (precomposedScene) listOf(IntRect(0, sceneTop, sceneSize.width, sceneTop + sceneSize.height)) else emptyList()
    val stages = (producerStages.takeIf { it.isNotEmpty() } ?: listOfNotNull(producer)).toMutableList()
    val storageSize = if (knownRestingThumb) base else IntSize(
        if (precomposedScene) max(base.width, sceneSize.width) else base.width,
        sceneTop + if (precomposedScene) sceneSize.height else 0)
    val atlas = NativeGlassMipAtlas(base, storageSize, rects,
        producer, scope.colorManagedNativeGlass,
        producerStages = stages,
        controlBlurPlane = rects.getOrNull(2).takeIf { small && blur > 0f && scope.colorManagedNativeGlass })
    fun configureLens(shader: android.graphics.RuntimeShader, highlight: Boolean) {
        atlas.configure(shader)
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("cornerRadii", glassCornerRadii(shape, size, layoutDirection, this))
        shader.setFloatUniform("lensOrigin", padding, padding)
        shader.setFloatUniform("lensDensity", density)
        shader.setFloatUniform("lensScale", scope.samplingScale)
        shader.setFloatUniform("lensSmall", if (small) 1f else 0f)
        shader.setFloatUniform("lensTab", if (tab) 1f else 0f)
        shader.setFloatUniform("lensBlurScale", blurScale)
        shader.setFloatUniform("lensRest", rest)
        shader.setFloatUniform("lensMaterialProgress", opticalProgress)
        shader.setFloatUniform("lensMaterialOpacity", materialOpacity)
        shader.setFloatUniform("lensFullyResting", if (fullyResting) 1f else 0f)
        shader.setFloatUniform("lensHighlight", lift.coerceAtLeast(0f))
        shader.setFloatUniform("lensHasContent", if (liftedContent != null) 1f else 0f)
        shader.setFloatUniform("lensHasBlur", if (blur > 0f) 1f else 0f)
        shader.setFloatUniform("lensBelowAmount", belowAmount)
        shader.setFloatUniform("lensInner", (if (small) .18f * height else .1f * height) * opticalProgress,
            (if (small) -.3f * height else -.15f * height) * opticalProgress)
        shader.setFloatUniform("lensPortal", if (small) 0f else .16f * height * opticalProgress,
            if (small) 0f else -.2f * height * opticalProgress)
        shader.setFloatUniform("lensAberration", if (small) 0f else height / 19f * opticalProgress, height / 1.8f)
        val headroom = ((scope.nativeGlassHeadroom - 1f) / .2f).coerceIn(0f, 1f)
        shader.setFloatUniform("lensKeyThreshold", cos((2f * Math.PI / 3f + (Math.PI / 2f - 2f * Math.PI / 3f) * headroom).toFloat()))
        shader.setFloatUniform("lensRingOpacity", (if (small) if (isDark) .4f else .15f else .1f) * materialOpacity)
        shader.setFloatUniform("lensShadowOpacity", (if (small) .0018f else .001f) * materialOpacity)
        shader.setFloatUniform("lensSliderShadow", if (style == NativeControlLensStyle.SliderThumb) .14f * rest else 0f)
        val body = if (restingColor.isSpecified) restingColor else Color.White
        shader.setFloatUniform("lensRestColor", body.red, body.green, body.blue, body.alpha)
        val shadowColor = if (small) scope.cachedResource("Glasmio.ControlLens.shadowFill") {
            NativeControlLensShadowFillCache()
        }.resolve(if (tint.isSpecified) tint else Color.Black) else NativeControlLensBlackShadow
        shader.setFloatUniform("lensShadowColor", shadowColor)
        val idleMatrix = if (isDark) NativeTabIdleDark else NativeTabIdleLight
        for (row in 0..2) shader.setFloatUniform("lensIdleRow$row", idleMatrix.copyOfRange(row * 4, row * 4 + 4))
        if (highlight) NativeVibrantColorMatrix.RegularHighlight.configure(shader::setFloatUniform)
    }
    if (precomposedScene) {
        val sceneShader = obtainRuntimeShader("Glasmio.ControlLens.scene.${atlas.colorManaged}",
            GlassSdfShader + atlas.samplingFunctions + NativeGlassRefractionFunctions + NativeGlassEdgeFunctions +
                NativeControlDisplacementFunctions + NativeControlLensSceneFunctions + NativeControlSceneAtlasMain)
            .asAndroidRuntimeShader()
        configureLens(sceneShader, highlight = false)
        stages += RenderEffect.createRuntimeShaderEffect(sceneShader, "content")
    }
    val shader = obtainRuntimeShader("Glasmio.ControlLens.${atlas.colorManaged}.$fullyResting.$small.$tab.$precomposedScene",
        nativeControlLensProgram(atlas.samplingFunctions, fullyResting, small, tab, precomposedScene)).asAndroidRuntimeShader()
    configureLens(shader, highlight = !fullyResting)
    scope.materializeNativeGlassAtlas(atlas)
    val guard = ceil(25.dp.toPx() / scope.samplingScale)
    scope.materialOutputOrigin = Offset(guard, guard)
    scope.materialOutputSize = IntSize(ceil(scope.fullSize.width + guard * 2f).toInt(), ceil(scope.fullSize.height + guard * 2f).toInt())
    scope.nativeMaterialCompleted = true
    shader.setFloatUniform("lensOutputOrigin", guard, guard)
    shader.setFloatUniform("lensOutputSize", scope.fullSize.width, scope.fullSize.height)
    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

/** Explicit analytic fixture; production managed lenses only build their materialized stages. */
internal fun BackdropEffectScope.nativeControlLensReferenceProducer(
    style: NativeControlLensStyle,
    liftProgress: Float,
): RenderEffect? {
    val scope = this as? BackdropEffectScopeImpl ?: return null
    val small = style == NativeControlLensStyle.SwitchThumb || style == NativeControlLensStyle.SliderThumb
    val tab = style == NativeControlLensStyle.TabBarIndicator
    val lift = liftProgress.takeIf { it.isFinite() } ?: 0f
    val blur = when {
        small && lift > 0f -> 6.dp.toPx() * (1f - lift).coerceIn(0f, 1f)
        tab -> 2.dp.toPx()
        else -> 0f
    }
    if (blur <= 0f) return null
    val blurScale = if (tab) .25f else 1f
    val base = IntSize(ceil(size.width + 2f * padding).toInt().coerceAtLeast(1),
        ceil(size.height + 2f * padding).toInt().coerceAtLeast(1))
    val blurredSize = IntSize(ceil(base.width * blurScale).toInt().coerceAtLeast(1),
        ceil(base.height * blurScale).toInt().coerceAtLeast(1))
    val managed = scope.colorManagedNativeGlass
    val colorFunctions = if (managed) NativeControlProducerManagedColor else NativeControlProducerIdentityColor
    val extract = obtainRuntimeShader("Glasmio.ControlLens.extract.$managed", colorFunctions + GlassSdfShader +
        NativeControlDisplacementFunctions + NativeControlLensExtract).asAndroidRuntimeShader()
    extract.setFloatUniform("planeSize", base.width.toFloat(), base.height.toFloat())
    extract.setFloatUniform("size", size.width, size.height)
    extract.setFloatUniform("cornerRadii", glassCornerRadii(shape, size, layoutDirection, this))
    extract.setFloatUniform("extractOrigin", padding, padding)
    extract.setFloatUniform("extractScale", blurScale)
    val belowAmount = when (style) {
        NativeControlLensStyle.SliderThumb -> 0f
        NativeControlLensStyle.SwitchThumb -> 50f + (9f - 50f) * lift
        else -> 9f * lift
    } * density
    extract.setFloatUniform("extractWarp", if (small) 1f else 0f, belowAmount, 36.dp.toPx())
    val clipped = RenderEffect.createRuntimeShaderEffect(extract, "content")
    val sigma = blur * blurScale
    val gaussian = if (!managed) RenderEffect.createBlurEffect(sigma, sigma, clipped, Shader.TileMode.CLAMP) else {
        var effect = clipped
        for (axis in 0..1) {
            val pass = obtainRuntimeShader("Glasmio.ControlLens.gaussian.$axis", colorFunctions + NativeControlManagedGaussian)
                .asAndroidRuntimeShader()
            pass.setFloatUniform("gaussianAxis", if (axis == 0) 1f else 0f, if (axis == 1) 1f else 0f)
            pass.setFloatUniform("gaussianSigma", sigma)
            pass.setFloatUniform("gaussianSize", blurredSize.width.toFloat(), blurredSize.height.toFloat())
            effect = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(pass, "content"), effect)
        }
        effect
    }
    val shift = obtainRuntimeShader("Glasmio.ControlLens.shift", NativeControlLensShift).asAndroidRuntimeShader()
    shift.setFloatUniform("planeSize", base.width.toFloat(), base.height.toFloat())
    shift.setFloatUniform("blurredSize", blurredSize.width.toFloat(), blurredSize.height.toFloat())
    val shifted = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shift, "content"), gaussian)
    val original = obtainRuntimeShader("Glasmio.ControlLens.preserve", NativeControlLensPreserve).asAndroidRuntimeShader()
    original.setFloatUniform("planeSize", base.width.toFloat(), base.height.toFloat())
    return RenderEffect.createBlendModeEffect(RenderEffect.createRuntimeShaderEffect(original, "content"), shifted, BlendMode.PLUS)
}

/** One complete kernel per sigma, shared by both axes without per-pixel transcendental work. */
private class NativeControlGaussianWeights {
    private var sigma = Float.NaN
    var radius = 0
        private set
    var tapLimit = 1
        private set
    var values = FloatArray(3)
        private set
    var total = 1f
        private set

    fun update(value: Float) {
        if (sigma == value) return
        sigma = value
        radius = ceil(value * 3f).toInt().coerceAtLeast(1)
        tapLimit = 1
        while (tapLimit < radius) tapLimit *= 2
        val count = tapLimit * 2 + 1
        if (values.size != count) values = FloatArray(count) else values.fill(0f)
        val denominator = max(value * value, .000001f)
        total = 0f
        for (i in -radius..radius) {
            val offset = i.toFloat()
            val weight = exp((-.5f * offset * offset / denominator).toDouble()).toFloat()
            values[i + tapLimit] = weight
            total += weight
        }
    }
}

internal fun nativeControlLensProgram(
    samplingFunctions: String,
    fullyResting: Boolean,
    small: Boolean,
    tab: Boolean,
    precomposedScene: Boolean = false,
): String {
    val scene = if (fullyResting) {
        // UIKit's unlift endpoint has no background/foreground optical transfer.
        // Make that a program boundary rather than asking the GPU to compile both paths.
        NativeGlassEdgeFunctions + NativeControlLensUniforms + NativeControlRestingFunctions +
            nativeControlRestingMain(tab)
    } else {
        NativeGlassRefractionFunctions + NativeGlassEdgeFunctions + NativeCasdfHighlightMaskFunctions +
            NativeVibrantColorMatrixFunctions + NativeControlDisplacementFunctions + NativeControlLensSceneFunctions +
            if (small) NativeControlSmallForegroundFunctions + NativeControlSmallLensMain
            else (if (precomposedScene) NativeControlPrecomposedSource else NativeControlAnalyticSource) +
                NativeControlForegroundFunctions + NativeControlLensMain
    }
    return GlassSdfShader + samplingFunctions + scene
}

private val NativeControlLensBlackShadow = FloatArray(3)

private class NativeControlLensShadowFillCache {
    private var lastTint: Color? = null
    private val result = FloatArray(3)

    fun resolve(tint: Color): FloatArray {
        if (lastTint != tint) {
            val resolved = tint.convert(ColorSpaces.ExtendedSrgb)
            nativeControlLensShadowFill(resolved.red, resolved.green, resolved.blue, result)
            lastTint = tint
        }
        return result
    }
}

/**
 * DesignLibrary ControlSliderRecipe's source transfer: HSL(h, S * .25, .45, 1).
 * Its SliderSpecModifier calls the recovered RGB/HSL helpers; 102 real UIKit samples also
 * confirm this transfer. Tint alpha is deliberately absent. The algebra below
 * preserves hue without converting through a hue angle or quantizing the output to Color.
 * The validated domain is gamma sRGB [0,1]. Unprobed extended/out-of-gamut inputs use their
 * finite SDR projection; this does not claim native Display-P3 or HDR tint behavior.
 */
internal fun nativeControlLensShadowFill(
    red: Float,
    green: Float,
    blue: Float,
    output: FloatArray = FloatArray(3),
): FloatArray {
    if (!red.isFinite() || !green.isFinite() || !blue.isFinite()) {
        output.fill(.45f)
        return output
    }
    val r = red.coerceIn(0f, 1f)
    val g = green.coerceIn(0f, 1f)
    val b = blue.coerceIn(0f, 1f)
    val high = maxOf(r, g, b)
    val low = minOf(r, g, b)
    if (high == low) {
        output.fill(.45f)
        return output
    }
    val chroma = high - low
    val denominator = if (high + low <= 1f) high + low else (1f - high) + (1f - low)
    val shadowChroma = (.9f * .25f) * (chroma / denominator)
    val shadowMinimum = .45f - shadowChroma * .5f
    output[0] = shadowMinimum + (r - low) / chroma * shadowChroma
    output[1] = shadowMinimum + (g - low) / chroma * shadowChroma
    output[2] = shadowMinimum + (b - low) / chroma * shadowChroma
    return output
}

private val NativeTabIdleLight = floatArrayOf(
    1.1850999594f, -.0502000004f, -.0049999999f, -.1999999881f,
    -.0148999998f, 1.1498999596f, -.0050999997f, -.1999999881f,
    -.0148999998f, -.0499999970f, 1.1948999166f, -.1999999881f,
)
private val NativeTabIdleDark = floatArrayOf(
    1.1297999620f, -.2360999882f, -.0236999989f, -.0700000003f,
    -.0700999945f, .9639999866f, -.0237999987f, -.0700000003f,
    -.0701999962f, -.2360000014f, 1.1761999130f, -.0700000003f,
)

private const val NativeControlLensExtractFunctions = """
uniform shader content;
uniform float2 planeSize;
uniform float2 extractOrigin;
uniform float extractScale;
uniform float3 extractWarp;
float4 nativeControlCaptureTexel(float2 index) {
    return nativeControlProducerDecode(float4(content.eval(clamp(index, float2(0.0), planeSize - 1.0) + .5)));
}
float4 nativeControlCaptureSample(float2 p) {
    float2 pixel = clamp(p - .5, float2(0.0), planeSize - 1.0);
    float2 index = floor(pixel);
    float2 t = pixel - index;
    return mix(mix(nativeControlCaptureTexel(index), nativeControlCaptureTexel(index + float2(1.0, 0.0)), t.x),
        mix(nativeControlCaptureTexel(index + float2(0.0, 1.0)), nativeControlCaptureTexel(index + 1.0), t.x), t.y);
}
half4 nativeControlExtract(float2 p) {
    float2 q = p / extractScale;
    float mask = 1.0;
    if (extractWarp.x > .5) {
        float3 f = glassField(q - extractOrigin, 0.0);
        float4 map = nativeControlDisplacementMap(float4(f, 1.0), extractWarp.z,
            1.0, 0.0, 0.0, float2(1.0, 0.0));
        q += map.rg * extractWarp.y;
        mask = map.b;
    }
    return half4(nativeControlProducerEncode(nativeControlCaptureSample(q) * mask));
}
"""

private const val NativeControlLensExtract = NativeControlLensExtractFunctions + """
half4 main(float2 p) { return nativeControlExtract(p); }
"""

private const val NativeControlLensExtractAtlasMain = """
uniform float2 blurredSize;
half4 main(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x >= planeSize.x) return half4(0.0);
    if (p.y < planeSize.y * 2.0) return content.eval(p);
    float2 q = p - float2(0.0, planeSize.y * 2.0);
    if (q.x >= blurredSize.x || q.y >= blurredSize.y) return half4(0.0);
    return nativeControlExtract(q);
}
"""

private fun nativeControlManagedAtlasGaussian(tapLimit: Int, planeOnly: Boolean): String = """
uniform shader content;
uniform float2 gaussianAxis;
uniform float gaussianRadius;
uniform float gaussianWeights[${tapLimit * 2 + 1}];
uniform float gaussianTotal;
uniform float2 gaussianSize;
${if (planeOnly) "" else "uniform float2 planeSize;"}
half4 main(float2 p) {
${if (planeOnly) """
    float2 origin = float2(0.0);
    float2 local = p;
    if (local.x < 0.0 || local.y < 0.0 || local.x >= gaussianSize.x || local.y >= gaussianSize.y) return half4(0.0);
""" else """
    if (p.x < 0.0 || p.y < 0.0 || p.x >= planeSize.x) return half4(0.0);
    if (p.y < planeSize.y * 2.0) return content.eval(p);
    float2 origin = float2(0.0, planeSize.y * 2.0);
    float2 local = p - origin;
    if (local.x >= gaussianSize.x || local.y >= gaussianSize.y) return half4(0.0);
"""}
    float4 result = float4(0.0);
    for (int i = -$tapLimit; i <= $tapLimit; i++) {
        float offset = float(i);
        if (abs(offset) > gaussianRadius) continue;
        float weight = gaussianWeights[i + $tapLimit];
        float2 q = clamp(local + gaussianAxis * offset, float2(.5), gaussianSize - .5);
        result += nativeControlProducerDecode(float4(content.eval(origin + q))) * weight;
    }
    return half4(nativeControlProducerEncode(result / max(gaussianTotal, .000001)));
}
"""

private const val NativeControlProducerIdentityColor = """
float4 nativeControlProducerDecode(float4 value) { return value; }
float4 nativeControlProducerEncode(float4 value) { return value; }
"""
private const val NativeControlProducerManagedColor = GlassColorSpaceShaderFunctions + """
float4 nativeControlProducerDecode(float4 value) { return glassWorkingToNativeGammaPremul(value); }
float4 nativeControlProducerEncode(float4 value) { return glassNativeGammaToWorkingPremul(value); }
"""
private const val NativeControlManagedGaussian = """
uniform shader content;
uniform float2 gaussianAxis;
uniform float gaussianSigma;
uniform float2 gaussianSize;
half4 main(float2 p) {
    float radius = ceil(gaussianSigma * 3.0);
    float4 result = float4(0.0);
    float total = 0.0;
    for (int i = -192; i <= 192; i++) {
        float offset = float(i);
        if (abs(offset) > radius) continue;
        float weight = exp(-.5 * offset * offset / max(gaussianSigma * gaussianSigma, .000001));
        float2 q = clamp(p + gaussianAxis * offset, float2(.5), gaussianSize - .5);
        result += nativeControlProducerDecode(float4(content.eval(q))) * weight;
        total += weight;
    }
    return half4(nativeControlProducerEncode(result / max(total, .000001)));
}
"""
private const val NativeControlLensShift = """
uniform shader content;
uniform float2 planeSize;
uniform float2 blurredSize;
half4 main(float2 p) {
    float2 q = p - float2(0.0, planeSize.y * 2.0);
    if (q.x < 0.0 || q.y < 0.0 || q.x >= blurredSize.x || q.y >= blurredSize.y) return half4(0.0);
    return content.eval(q);
}
"""
private const val NativeControlLensPreserve = """
uniform shader content;
uniform float2 planeSize;
half4 main(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x >= planeSize.x || p.y >= planeSize.y * 2.0) return half4(0.0);
    return content.eval(p);
}
"""

private const val NativeControlLensUniforms = """
uniform float2 lensOrigin;
uniform float lensDensity;
uniform float lensScale;
uniform float lensSmall;
uniform float lensTab;
uniform float lensBlurScale;
uniform float lensRest;
uniform float lensMaterialProgress;
uniform float lensMaterialOpacity;
uniform float lensFullyResting;
uniform float lensHighlight;
uniform float lensHasContent;
uniform float lensHasBlur;
uniform float lensBelowAmount;
uniform float2 lensInner;
uniform float2 lensPortal;
uniform float2 lensAberration;
uniform float lensKeyThreshold;
uniform float lensRingOpacity;
uniform float lensShadowOpacity;
uniform float lensSliderShadow;
uniform float4 lensRestColor;
uniform float3 lensShadowColor;
uniform float4 lensIdleRow0;
uniform float4 lensIdleRow1;
uniform float4 lensIdleRow2;
"""

private const val NativeControlLensSceneFunctions = NativeControlLensUniforms + """
float4 nativeControlOver(float4 source, float4 destination) {
    return source + destination * (1.0 - source.a);
}
float4 nativeControlIdentityCapture(float4 filtered, float4 captured) {
    if (captured.a >= 1.0 || lensMaterialOpacity >= 1.0) return filtered;
    // Captured filter output is an operator over its own input, not an independent
    // translucent image. Remove its neutral capture contribution as the material
    // approaches identity while retaining the complete optical/color difference.
    // This portable alpha extension preserves both the active endpoint and every
    // opaque-input composite: P' + C * (1-A') == P + C * (1-A).
    float alpha = filtered.a * lensMaterialOpacity;
    float3 straight = captured.rgb / max(captured.a, .000001);
    return float4(filtered.rgb - straight * (filtered.a - alpha), alpha);
}
float4 nativeControlNatural(float2 p, bool blurred) {
    if (!blurred || lensHasBlur < .5) return nativeAtlasBilinear(lensOrigin + p, 0);
    // Keep the captured .25 transform, including odd extents. Do not stretch the
    // rounded quarter-sized texture to the padded full source's width or height.
    float4 rect = nativeAtlasRect(2);
    float2 pixel = clamp((lensOrigin + p) * lensBlurScale - .5, float2(0.0), rect.zw - 1.0);
    float2 index = floor(pixel);
    float2 t = pixel - index;
    return mix(mix(nativeAtlasTexel(index, rect), nativeAtlasTexel(index + float2(1.0, 0.0), rect), t.x),
        mix(nativeAtlasTexel(index + float2(0.0, 1.0), rect), nativeAtlasTexel(index + 1.0, rect), t.x), t.y);
}
float4 nativeControlMap(float2 p, float height) {
    float3 f = glassField(p, 0.0);
    return nativeControlDisplacementMapForRaster(float4(f, 1.0), max(height, .000001), 1.0,
        0.0, 0.0, float2(1.0, 0.0), lensScale);
}
float nativeControlCoverage(float3 field) {
    return nativeGlassShapeCoverage(float3(field.x / lensScale, field.yz), 1.0);
}
float4 nativeControlPortal(float2 p) {
    if (lensHasContent < .5) return float4(0.0);
    float4 map = nativeControlMap(p, 11.2 * lensDensity);
    float2 q = p + map.rg * (-17.5 * lensDensity * lensMaterialProgress);
    return nativeAtlasBilinear(lensOrigin + q, 1) * map.b;
}
float4 nativeControlBelow(float2 p) {
    float4 map = nativeControlMap(p, 36.0 * lensDensity);
    float4 original = nativeControlNatural(p, false);
    // SmallVariant's filter order is map -> Gaussian; the producer has already
    // warped its blurred plane. The Tab resting blur belongs to its separate body.
    float4 warped = lensSmall > .5 && lensHasBlur > .5 ? nativeControlNatural(p, true)
        : nativeControlNatural(p + map.rg * lensBelowAmount, false) * map.b;
    // The temporary below-glass backdrop is absent after native unlift cleanup.
    // Interpolate this optional stage's contribution without fading the complete lens.
    float4 natural = nativeControlOver(warped * lensMaterialOpacity, original);
    float3 f = glassField(p, 0.0);
    float coverage = nativeControlCoverage(f);
    float4 body = lensRestColor;
    if (lensTab > .5) {
        float4 source = nativeControlNatural(p, true);
        float4 straight = float4(source.rgb / max(source.a, .000001), 1.0);
        body = float4(dot(lensIdleRow0, straight), dot(lensIdleRow1, straight), dot(lensIdleRow2, straight), 1.0);
    }
    body = float4(body.rgb * body.a, body.a) * lensRest * coverage;
    return nativeControlOver(body, natural);
}
float4 nativeControlClearContent(float2 p) {
    float coverage = nativeControlCoverage(glassField(p, 0.0));
    // Clear content is the source of the provider's color portal. The provider wrapper
    // has zPosition -1 and hides the ordinary Clear source layer; BG must not sample it.
    float radius = 3.0 * lensDensity * lensMaterialProgress;
    float inner = 0.0;
    if (radius > 0.0) {
        float innerD = glassField(p - float2(0.0, 7.0 * lensDensity * lensMaterialProgress), 0.0).x;
        inner = (1.0 - nativeGaussianTail(innerD / radius)) * .06 * lensMaterialOpacity * coverage;
    }
    return nativeControlOver(float4(0.0, 0.0, 0.0, inner), nativeControlPortal(p));
}
float3 nativeControlFace(float3 color) {
    float y = dot(color, float3(.2126, .7152, .0722));
    float black = .1 * lensSmall;
    return mix(color, color + black * (1.0 - y), lensMaterialOpacity);
}
float4 nativeControlBackground(float2 p) {
    float3 f = glassField(p, 0.0);
    float aa = max((abs(f.y) + abs(f.z)) * lensScale, .0001);
    float coverage = nativeControlCoverage(f);
    float strip = nativeHighlightStrip(-(f.x - (2.0 / 3.0) * lensDensity), lensDensity, .75, aa);
    float key = strip * (nativeHighlightAngular(f.y, lensKeyThreshold) + nativeHighlightAngular(-f.y, lensKeyThreshold))
        * lensMaterialOpacity;
    float shifted = glassField(p - float2(0.0, 8.0 * lensDensity), 0.0).x;
    float ring = clamp(nativeGaussianTail(shifted / (3.0 * lensDensity) * .7071067811865476)
        - nativeGaussianTail((shifted + 4.0 * lensDensity) / (3.0 * lensDensity) * .7071067811865476), 0.0, 1.0) * lensRingOpacity;
    float shadowD = glassField(p - float2(0.0, 7.0 * lensDensity), 0.0).x;
    float shadow = nativeGaussianTail(shadowD / (8.0 * lensDensity)) * lensShadowOpacity;
    if (coverage <= 0.0 && key <= .000001 && ring <= .000001 && shadow <= .000001) return float4(0.0);
    float4 originalRaw = nativeControlBelow(p);
    float3 original = originalRaw.rgb / max(originalRaw.a, .000001);
    float displacement = nativeGlassDisplacement(f.x, 1.0 / max(lensInner.x, .000001), lensInner.y);
    float4 sampled = nativeControlBelow(p + f.yz * displacement);
    float4 face = float4(nativeControlFace(sampled.rgb / max(sampled.a, .000001)), 1.0);
    // The style-0 shadow's tint-derived fill is supplied independently from the face.
    float4 shadowResult = float4(lensShadowColor * shadow, shadow);
    float4 covered = mix(shadowResult, face, coverage);
    covered.rgb *= 1.0 - ring;
    covered.a = covered.a * (1.0 - ring) + ring;
    float4 completed = nativeGlassBackgroundCompletion(covered, original, nativeControlFace(original), key,
        lensMaterialOpacity, -.3);
    return nativeControlIdentityCapture(completed, originalRaw);
}
float4 nativeControlAfterBackground(float2 p, float4 background) {
    float4 original = nativeControlBelow(p);
    float4 scene = nativeControlOver(background, original);
    float4 map = nativeControlMap(p, lensPortal.x);
    float2 q = lensPortal.y == 0.0 ? p : p + map.rg * lensPortal.y;
    float4 portal = nativeControlClearContent(q);
    // A nil control-displacement field is the identity endpoint. Its optional mask
    // must also become identity, rather than leaving an extra fixed AA multiplication.
    if (lensPortal.y != 0.0) portal *= mix(1.0, map.b, lensMaterialOpacity);
    return nativeControlOver(portal, scene);
}
float4 nativeControlBeforeForeground(float2 p) {
    return nativeControlAfterBackground(p, nativeControlBackground(p));
}
float4 nativeControlResting(float2 p) {
    float coverage = nativeControlCoverage(glassField(p, 0.0));
    float4 body = lensRestColor;
    if (lensTab > .5) {
        float4 sampled = nativeControlNatural(p, true);
        float4 straight = float4(sampled.rgb / max(sampled.a, .000001), 1.0);
        body = float4(dot(lensIdleRow0, straight), dot(lensIdleRow1, straight), dot(lensIdleRow2, straight), 1.0);
    }
    float4 result = float4(body.rgb * body.a, body.a) * coverage;
    if (lensHasContent > .5 && coverage > 0.0) {
        // UIKit removes its _Glass background at rest and sets wrapper displacement to0.
        // The selected source remains an ordinary, unwarped content view in this state.
        float4 selected = nativeAtlasBilinear(lensOrigin + p, 1) * coverage;
        result = nativeControlOver(selected, result);
    }
    if (lensSliderShadow > 0.0) {
        float d = glassField(p - float2(0.0, lensDensity), 0.0).x;
        float shadow = nativeGaussianTail(d / (5.0 * lensDensity)) * lensSliderShadow;
        result = nativeControlOver(result, float4(0.0, 0.0, 0.0, shadow));
    }
    return result;
}
"""

private const val NativeControlAnalyticSource = """
float4 nativeControlForegroundSource(float2 p) { return nativeControlBeforeForeground(p); }
"""

private const val NativeControlPrecomposedSource = """
float4 nativeControlForegroundSource(float2 p) { return nativeAtlasBilinear(lensOrigin + p, 3); }
"""

private const val NativeControlSceneAtlasMain = """
half4 main(float2 p) {
    float4 rect = nativeAtlasRect(3);
    float2 local = p - rect.xy;
    if (local.x < 0.0 || local.y < 0.0 || local.x >= rect.z || local.y >= rect.w) return content.eval(p);
    // Scene geometry stays on the device raster even when backdrop capture is reduced.
    float2 point = local * nativeAtlasBaseSize / rect.zw - lensOrigin;
    return half4(nativeAtlasEncode(nativeControlBeforeForeground(point)));
}
"""

private const val NativeControlLensMainPrefix = """
uniform float2 lensOutputOrigin;
uniform float2 lensOutputSize;
half4 main(float2 coordinate) {
    if (coordinate.x < 0.0 || coordinate.y < 0.0 || coordinate.x >= lensOutputSize.x + lensOutputOrigin.x * 2.0
        || coordinate.y >= lensOutputSize.y + lensOutputOrigin.y * 2.0) return half4(0.0);
    float2 p = (coordinate - lensOutputOrigin) * lensScale;
    if (lensFullyResting > .5) return half4(nativeAtlasEncode(nativeControlResting(p)));
    float3 f = glassField(p, 0.0);
    float4 source = nativeControlNatural(p, false);
    float4 background = nativeControlBackground(p);
    float4 actual = nativeControlAfterBackground(p, background);
"""

private const val NativeControlFullForegroundMain = """
    float4 foreground = nativeControlForegroundForRaster(p, float4(f, 1.0), float4(1.0, 0.0, 0.0, 1.0),
        float3(0.0), float2(1.0, 0.0), float3(lensAberration.x, -1.0, lensAberration.y),
        float2(.9659258262890683, -.2588190451025207), float4(-size.y / 5.0, 0.0, 1.0, 0.0), 1.0, lensScale);
"""

private const val NativeControlSmallForegroundMain = """
    float4 foreground = nativeControlSmallForeground(actual, float4(f, 1.0),
        float4(-size.y / 5.0, 0.0, 1.0, 0.0), lensScale);
"""

private const val NativeControlLensMainSuffix = """
    actual = nativeControlOver(nativeControlIdentityCapture(foreground, actual), actual);
    float coverage = nativeControlCoverage(f);
    float alpha = max(background.a, coverage);
    if (source.a < 1.0) {
        // Convert the fully composed scene back to an operator over this source.
        // Keep the opaque path unchanged; its source alpha cannot recover relative alpha.
        alpha = (actual.a - source.a) / (1.0 - source.a);
    }
    if (alpha <= .000001 && lensSliderShadow <= 0.0) return half4(0.0);
    float4 result = float4(actual.rgb - source.rgb * (1.0 - alpha), alpha);
    float3 fieldFull = float3(f.x / lensScale, f.yz);
    float mask = nativeCasdfHighlightCoverage(fieldFull.x, fieldFull.yz,
        lensDensity / lensScale, 0.0, .75) * lensHighlight;
    if (mask > 0.0) result = nativeControlOver(nativeVibrantColorMatrix(float4(0.0, 0.0, 0.0, mask), actual), result);
    if (lensSliderShadow > 0.0) {
        float d = glassField(p - float2(0.0, lensDensity), 0.0).x;
        float shadow = nativeGaussianTail(d / (5.0 * lensDensity)) * lensSliderShadow;
        result = nativeControlOver(result, float4(0.0, 0.0, 0.0, shadow));
    }
    return half4(nativeAtlasEncodeRelative(result, source));
}
"""

private const val NativeControlLensMain = NativeControlLensMainPrefix + NativeControlFullForegroundMain + NativeControlLensMainSuffix
private const val NativeControlSmallLensMain = NativeControlLensMainPrefix + NativeControlSmallForegroundMain + NativeControlLensMainSuffix

/** SmallVariant's zero refraction/aberration reads the already reconstructed scene once. */
private const val NativeControlSmallForegroundFunctions = """
float4 nativeControlSmallForeground(float4 source, float4 field, float4 edge, float pixelScale) {
    if (field.a < .001) return float4(0.0);
    float opacity = mix(edge.z, edge.w, clamp((field.x - edge.x) / (edge.y - edge.x), 0.0, 1.0));
    float attenuation = 1.0 - opacity;
    if (attenuation <= 0.0) return float4(0.0);
    float aa = max((abs(field.y) + abs(field.z)) * pixelScale, .0001);
    float coverage = clamp(.5 - field.x / aa, 0.0, 1.0) * field.a;
    float3 straight = source.rgb / max(source.a, .000001);
    // Preserve the seven-sample channel/alpha arithmetic at its shared-address limit.
    float3 color = float3((straight.r + straight.r * (2.0 / 3.0) + straight.r * (1.0 / 3.0)) * .5,
        (straight.g * (1.0 / 3.0) + straight.g * (2.0 / 3.0) + straight.g
            + straight.g * (2.0 / 3.0) + straight.g * (1.0 / 3.0)) / 3.0,
        (straight.b * (1.0 / 3.0) + straight.b * (2.0 / 3.0) + straight.b) * .5);
    float alpha = (source.a + source.a + source.a + source.a + source.a + source.a + source.a) / 7.0 * coverage;
    return float4(color * alpha, alpha) * attenuation;
}
"""

private const val NativeControlRestingFunctions = """
float4 nativeControlOver(float4 source, float4 destination) {
    return source + destination * (1.0 - source.a);
}
float4 nativeControlRestingBlur(float2 p) {
    if (lensHasBlur < .5) return nativeAtlasBilinear(lensOrigin + p, 0);
    float4 rect = nativeAtlasRect(2);
    float2 pixel = clamp((lensOrigin + p) * lensBlurScale - .5, float2(0.0), rect.zw - 1.0);
    float2 index = floor(pixel);
    float2 t = pixel - index;
    return mix(mix(nativeAtlasTexel(index, rect), nativeAtlasTexel(index + float2(1.0, 0.0), rect), t.x),
        mix(nativeAtlasTexel(index + float2(0.0, 1.0), rect), nativeAtlasTexel(index + 1.0, rect), t.x), t.y);
}
float4 nativeControlResting(float2 p, bool tab) {
    float3 field = glassField(p, 0.0);
    float coverage = nativeGlassShapeCoverage(float3(field.x / lensScale, field.yz), 1.0);
    float4 body = lensRestColor;
    if (tab) {
        float4 sampled = nativeControlRestingBlur(p);
        float4 straight = float4(sampled.rgb / max(sampled.a, .000001), 1.0);
        body = float4(dot(lensIdleRow0, straight), dot(lensIdleRow1, straight), dot(lensIdleRow2, straight), 1.0);
    }
    float4 result = float4(body.rgb * body.a, body.a) * coverage;
    if (lensHasContent > .5 && coverage > 0.0) {
        float4 selected = nativeAtlasBilinear(lensOrigin + p, 1) * coverage;
        result = nativeControlOver(selected, result);
    }
    if (lensSliderShadow > 0.0) {
        float d = glassField(p - float2(0.0, lensDensity), 0.0).x;
        float shadow = nativeGaussianTail(d / (5.0 * lensDensity)) * lensSliderShadow;
        result = nativeControlOver(result, float4(0.0, 0.0, 0.0, shadow));
    }
    return result;
}
"""

private fun nativeControlRestingMain(tab: Boolean): String = """
uniform float2 lensOutputOrigin;
uniform float2 lensOutputSize;
half4 main(float2 coordinate) {
    if (coordinate.x < 0.0 || coordinate.y < 0.0 || coordinate.x >= lensOutputSize.x + lensOutputOrigin.x * 2.0
        || coordinate.y >= lensOutputSize.y + lensOutputOrigin.y * 2.0) return half4(0.0);
    float2 p = (coordinate - lensOutputOrigin) * lensScale;
    return half4(nativeAtlasEncode(nativeControlResting(p, $tab)));
}
"""
