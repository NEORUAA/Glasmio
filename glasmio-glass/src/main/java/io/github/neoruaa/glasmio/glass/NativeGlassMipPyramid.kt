package io.github.neoruaa.glasmio.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import io.github.neoruaa.glasmio.glass.render.RuntimeShaderCache
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScopeImpl
import io.github.neoruaa.glasmio.glass.render.GlassColorSpaceShaderFunctions
import io.github.neoruaa.glasmio.glass.render.asAndroidRuntimeShader
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.ceil

/** Recovered VariableBlurDownsample profiles; their rounded sums are deliberately retained. */
internal data class NativeGlassMipKernel(
    val innerOffset: Float,
    val outerOffset: Float,
    val centerWeight: Float,
    val innerWeight: Float,
    val diagonalWeight: Float,
    val outerWeight: Float,
    val outputGridOffsets: Boolean = false,
    val halfArithmetic: Boolean = false,
    val flushSmallRgb: Boolean = false,
    val oddQuadExpansion: Boolean = false,
    val clampToZero: Boolean = false,
) {
    companion object {
        val Fragment13Tap = NativeGlassMipKernel(
            innerOffset = 1.9600850343704224f,
            outerOffset = 3.9206759929656982f,
            centerWeight = .10549700260162354f,
            innerWeight = .09018599987030029f,
            diagonalWeight = .07709799706935883f,
            outerWeight = .05634099990129471f,
            oddQuadExpansion = true,
            clampToZero = true,
        )
        val Compute13Tap = NativeGlassMipKernel(
            innerOffset = 1f,
            outerOffset = 2f,
            centerWeight = .10546875f,
            innerWeight = .0902099609375f,
            diagonalWeight = .07708740234375f,
            outerWeight = .05633544921875f,
            outputGridOffsets = true,
            halfArithmetic = true,
            flushSmallRgb = true,
        )
    }
}

/** A discrete native mip grid represented over HWUI's unchanged source-coordinate extent. */
internal class NativeGlassMipLevel(
    val index: Int,
    val size: IntSize,
    val effect: RenderEffect?,
    private val sourceSize: IntSize,
    private val lastLevel: Int,
) {
    fun configure(shader: RuntimeShader, sampling: Boolean = true, weighting: Boolean = true) {
        if (sampling) {
            shader.setFloatUniform("nativeMipBaseSize", sourceSize.width.toFloat(), sourceSize.height.toFloat())
            shader.setFloatUniform("nativeMipLevelSize", size.width.toFloat(), size.height.toFloat())
            shader.setFloatUniform("nativeMipClampZero", 0f)
        }
        if (weighting) {
            shader.setFloatUniform("nativeMipLevel", index.toFloat())
            shader.setFloatUniform("nativeMipLastLevel", lastLevel.toFloat())
        }
    }
}

internal class NativeGlassMipProducer(val effect: RenderEffect?, val stages: List<RenderEffect>)

/** Packed real-resolution mip texels. The producer must render over [size] before sampling it. */
internal class NativeGlassMipAtlas(
    val baseSize: IntSize,
    val size: IntSize,
    val rects: List<IntRect>,
    producerEffect: RenderEffect?,
    val colorManaged: Boolean = false,
    /** Each entry consumes a materialized texture from the preceding pass. */
    producerStages: List<RenderEffect> = listOfNotNull(producerEffect),
    /** Real-resolution tile passes; the compatibility producer DAG is never drawn here. */
    val textureStages: List<RenderEffect> = emptyList(),
    val textureSource: RenderEffect? = null,
    /** Control lenses retain the packed extract but blur only this plane before any scene pass. */
    val controlBlurPlane: IntRect? = null,
    /** A separately materialized optical scene; packing happens only at the consumer. */
    val controlScenePlane: IntRect? = null,
    /** Internal native-gamma channel transport; only the sampler may display these values. */
    val nativeGammaStorage: Boolean = false,
    /** Only the owned regular pyramid uses an analytically addressed row or column strip. */
    private val regularMipRow: Boolean? = null,
    allowHardwareLinearSampling: Boolean = false,
    compatibilityProducer: (() -> NativeGlassMipProducer)? = null,
) {
    var controlSceneEffect: RenderEffect? = null
    internal val composeControlSceneEffect by lazy(LazyThreadSafetyMode.NONE) {
        controlSceneEffect?.asComposeRenderEffect()
    }
    internal val hasControlPlanes: Boolean
        get() = controlBlurPlane != null || controlScenePlane != null
    private val immediateEffect = producerEffect
    private val immediateStages = producerStages
    private val compatibility by lazy(LazyThreadSafetyMode.NONE) { compatibilityProducer?.invoke() }
    val producerEffect: RenderEffect? get() = compatibility?.effect ?: immediateEffect
    val producerStages: List<RenderEffect> get() = compatibility?.stages ?: immediateStages
    // AndroidRenderEffect wrappers use reference equality. Retain one wrapper per immutable
    // filter so a source-only redraw does not repeatedly replace every RenderNode's filter.
    internal val composeProducerStages by lazy(LazyThreadSafetyMode.NONE) {
        producerStages.map { it.asComposeRenderEffect() }
    }
    internal val composeTextureStages = textureStages.map { it.asComposeRenderEffect() }
    internal val composeTextureSource = textureSource?.asComposeRenderEffect()
    val lookupCapacity: Int = when {
        rects.size <= 1 -> 1
        rects.size <= 2 -> 2
        rects.size <= 4 -> 4
        rects.size <= 8 -> 8
        rects.size <= 16 -> 16
        else -> 32
    }
    private val hardwareLinearSampling = allowHardwareLinearSampling && (nativeGammaStorage || !colorManaged)
    val samplingLayoutKey: String = (if (regularMipRow == null) "packed$lookupCapacity" else "mip") +
        ".linear=$hardwareLinearSampling"
    val samplingFunctions: String = (if (regularMipRow == null)
        NativeAtlasSamplingFunctionBuckets.getValue(lookupCapacity)
    else NativeAtlasRegularSamplingFunctions)[if (hardwareLinearSampling) 1 else 0][when {
        nativeGammaStorage -> 2
        colorManaged -> 1
        else -> 0
    }]

    private val packedRects = FloatArray(32 * 4).apply {
        rects.forEachIndexed { index, rect ->
            this[index * 4] = rect.left.toFloat()
            this[index * 4 + 1] = rect.top.toFloat()
            this[index * 4 + 2] = rect.width.toFloat()
            this[index * 4 + 3] = rect.height.toFloat()
        }
    }

    fun configure(shader: RuntimeShader) {
        shader.setFloatUniform("nativeAtlasBaseSize", baseSize.width.toFloat(), baseSize.height.toFloat())
        shader.setFloatUniform("nativeAtlasRects", packedRects)
        shader.setFloatUniform("nativeAtlasLastLevel", (rects.size - 1).toFloat())
        if (regularMipRow != null) shader.setFloatUniform("nativeAtlasMipRow", if (regularMipRow) 1f else 0f)
    }
}

/** Keep packed storage separate from the padded base image and its original coordinates. */
internal fun BackdropEffectScope.materializeNativeGlassAtlas(atlas: NativeGlassMipAtlas): Boolean {
    val storage = this as? BackdropEffectScopeImpl ?: return false
    storage.effectTextureSize = atlas.size
    storage.materializedMipAtlas = atlas.takeIf { it.textureStages.isNotEmpty() || it.hasControlPlanes }
    storage.materializedInputEffects = if (storage.materializedMipAtlas != null) emptyList()
        else atlas.composeProducerStages
    return true
}

/** Explicit texture barriers keep child image-filter programs from expanding across passes. */
internal class NativeGlassAtlasPasses(private val context: GraphicsContext) {
    private val retained = ArrayList<GraphicsLayer>()

    fun layers(effects: List<androidx.compose.ui.graphics.RenderEffect>): List<GraphicsLayer> {
        recordedPyramid = null
        recordedControl = null
        return configureLayers(effects)
    }

    private fun configureLayers(effects: List<androidx.compose.ui.graphics.RenderEffect>): List<GraphicsLayer> {
        while (retained.size > effects.size) context.releaseGraphicsLayer(retained.removeAt(retained.lastIndex))
        while (retained.size < effects.size) retained += context.createGraphicsLayer()
        retained.forEachIndexed { index, layer ->
            layer.clip = true
            layer.compositingStrategy = CompositingStrategy.Offscreen
            layer.renderEffect = effects[index]
        }
        return retained
    }

    private var base: GraphicsLayer? = null
    private var assembled: GraphicsLayer? = null
    private val tiles = ArrayList<GraphicsLayer>()
    private data class RecordingContext(
        val densityBits: Int,
        val fontScaleBits: Int,
        val layoutDirection: LayoutDirection,
        val hardware: Boolean,
    )
    private fun DrawScope.recordingContext() = RecordingContext(
        density.toRawBits(), fontScale.toRawBits(), layoutDirection,
        drawContext.canvas.nativeCanvas.isHardwareAccelerated,
    )
    private var recordedPyramid: NativeGlassMipAtlas? = null
    private var pyramidContext: RecordingContext? = null
    private var recordedControl: NativeGlassMipAtlas? = null
    private var controlContext: RecordingContext? = null
    private var controlInput: GraphicsLayer? = null
    private var controlOutput: GraphicsLayer? = null
    private var controlSource: GraphicsLayer? = null
    private var sceneInput: GraphicsLayer? = null

    /** Capture, optional blur and optical scene have independent GPU owners and extents. */
    fun recordControlPlanes(
        atlas: NativeGlassMipAtlas,
        record: (GraphicsLayer, IntSize, DrawScope.() -> Unit) -> Unit,
        sourceBlock: DrawScope.() -> Unit,
    ) {
        if (atlas.controlScenePlane == null) {
            recordControlBlur(atlas, record, sourceBlock)
            return
        }
        recordedPyramid = null
        val scene = atlas.controlScenePlane
        val supportSize = IntSize(
            maxOf(atlas.baseSize.width, scene.width),
            maxOf(atlas.rects[1].bottom, atlas.controlBlurPlane?.bottom ?: 0, scene.height),
        )
        if (atlas.controlBlurPlane != null) {
            recordControlBlur(atlas, record, sourceBlock)
        } else {
            val raw = controlSource ?: context.createGraphicsLayer().also { controlSource = it }
            raw.clip = true
            raw.compositingStrategy = CompositingStrategy.Offscreen
            raw.renderEffect = null
            record(raw, supportSize, sourceBlock)
        }
        val input = sceneInput ?: context.createGraphicsLayer().also { sceneInput = it }
        input.clip = true
        input.compositingStrategy = CompositingStrategy.Offscreen
        input.renderEffect = requireNotNull(atlas.composeControlSceneEffect)
        // HWUI's image-filter input subset is the logical RenderNode size. Keep every
        // input plane available; clip only its output when packing the consumer input.
        record(input, supportSize) { drawControlInputs(this, atlas) }
        recordedControl = atlas
    }

    fun drawControlPlanes(scope: DrawScope, atlas: NativeGlassMipAtlas) = with(scope) {
        if (atlas.controlScenePlane == null) {
            drawControlBlurAtlas(this, atlas)
            return@with
        }
        drawControlInputs(this, atlas)
        val scene = atlas.controlScenePlane
        translate(scene.left.toFloat(), scene.top.toFloat()) {
            clipRect(0f, 0f, scene.width.toFloat(), scene.height.toFloat()) {
                // The consumer's materialized input is already the scene-output barrier.
                drawLayer(requireNotNull(sceneInput))
            }
        }
    }

    private fun drawControlInputs(scope: DrawScope, atlas: NativeGlassMipAtlas) = with(scope) {
        if (atlas.controlBlurPlane != null) drawControlBlurPlanes(this, atlas)
        else clipRect(0f, 0f, atlas.baseSize.width.toFloat(), atlas.rects[1].bottom.toFloat()) {
            drawLayer(requireNotNull(controlSource))
        }
    }

    fun recordControlBlur(
        atlas: NativeGlassMipAtlas,
        record: (GraphicsLayer, IntSize, DrawScope.() -> Unit) -> Unit,
        sourceBlock: DrawScope.() -> Unit,
    ) {
        recordedPyramid = null
        val plane = requireNotNull(atlas.controlBlurPlane)
        check(atlas.producerStages.size in 3..4)
        val stages = configureLayers(atlas.composeProducerStages)
        // Keep the original extract's raw and portal pixels. Its two Gaussian successors
        // consume only the third plane, retaining the same independent texture barriers.
        val scene = atlas.controlScenePlane
        val support = if (scene == null) atlas.size else IntSize(
            maxOf(atlas.baseSize.width, scene.width),
            maxOf(plane.bottom, scene.height),
        )
        record(stages[0], support, sourceBlock)
        val planeSize = IntSize(plane.width, plane.height)
        var environment: RecordingContext? = null
        record(stages[1], planeSize) {
            environment = recordingContext()
            translate(-plane.left.toFloat(), -plane.top.toFloat()) { drawLayer(stages[0]) }
        }
        // The source callback can mutate stage 0's outline, shadow or other layer state.
        // Always draw it through stage 1 so Compose configures that state on every call.
        // Stage 2 references only the private stage 1 RenderNode, whose input stays live.
        if (recordedControl !== atlas || controlInput !== stages[1] || controlOutput !== stages[2] ||
            controlContext != environment || environment?.hardware != true) {
            record(stages[2], planeSize) { drawLayer(stages[1]) }
        }
        if (stages.size == 4) {
            // Reconstruct the original three input planes inside the existing scene
            // layer. Its unchanged effect still materializes the fourth plane.
            record(stages[3], atlas.size) { drawControlBlurPlanes(this, atlas) }
        }
        recordedControl = atlas
        controlContext = environment
        controlInput = stages[1]
        controlOutput = stages[2]
    }

    fun drawControlBlurAtlas(scope: DrawScope, atlas: NativeGlassMipAtlas) = with(scope) {
        check(retained.size == atlas.composeProducerStages.size && retained.size in 3..4)
        if (retained.size == 4) drawLayer(retained[3])
        else drawControlBlurPlanes(this, atlas)
    }

    private fun drawControlBlurPlanes(scope: DrawScope, atlas: NativeGlassMipAtlas) = with(scope) {
        val plane = requireNotNull(atlas.controlBlurPlane)
        // Compose into the existing consumer or scene input recording. No additional
        // packed offscreen layer or second source callback is introduced.
        clipRect(0f, 0f, atlas.baseSize.width.toFloat(), plane.top.toFloat()) { drawLayer(retained[0]) }
        translate(plane.left.toFloat(), plane.top.toFloat()) {
            clipRect(0f, 0f, plane.width.toFloat(), plane.height.toFloat()) { drawLayer(retained[2]) }
        }
    }

    fun recordPyramid(
        atlas: NativeGlassMipAtlas,
        record: (GraphicsLayer, IntSize, DrawScope.() -> Unit) -> Unit,
        sourceBlock: DrawScope.() -> Unit,
    ): GraphicsLayer {
        recordedControl = null
        val original = base ?: context.createGraphicsLayer().also { base = it }
        original.clip = true
        original.compositingStrategy = CompositingStrategy.Offscreen
        original.renderEffect = atlas.composeTextureSource
        record(original, atlas.baseSize, sourceBlock)
        while (tiles.size > atlas.textureStages.size) context.releaseGraphicsLayer(tiles.removeAt(tiles.lastIndex))
        while (tiles.size < atlas.textureStages.size) tiles += context.createGraphicsLayer()
        var previous = original
        var environment: RecordingContext? = null
        tiles.forEachIndexed { index, tile ->
            val input = previous
            tile.clip = true
            tile.compositingStrategy = CompositingStrategy.Offscreen
            tile.renderEffect = atlas.composeTextureStages[index]
            // HWUI clips a RuntimeShader's input to its layer bounds. Retain the complete
            // preceding mip as input support; only the top-left output tile is evaluated.
            val support = atlas.rects[index]
            if (index == 0) {
                // This direct source draw must run even with a stable atlas: sourceBlock
                // can modify original's public layer state, including outline and Z.
                record(tile, IntSize(support.width, support.height)) {
                    environment = recordingContext()
                    drawLayer(input)
                }
            } else if (recordedPyramid !== atlas || pyramidContext != environment || environment?.hardware != true) {
                // Deeper stages contain only a fixed draw of another private RenderNode.
                // Retaining their display lists preserves every GPU texture barrier while
                // the freshly recorded source propagates through the live child references.
                record(tile, IntSize(support.width, support.height)) { drawLayer(input) }
            }
            previous = tile
        }
        val output = assembled ?: context.createGraphicsLayer().also { assembled = it }
        output.clip = true
        output.compositingStrategy = CompositingStrategy.Offscreen
        output.renderEffect = null
        record(output, atlas.size) {
            drawLayer(original)
            tiles.forEachIndexed { index, tile ->
                val rect = atlas.rects[index + 1]
                translate(rect.left.toFloat(), rect.top.toFloat()) {
                    clipRect(0f, 0f, rect.width.toFloat(), rect.height.toFloat()) { drawLayer(tile) }
                }
            }
        }
        recordedPyramid = atlas
        pyramidContext = environment
        return output
    }

    fun release() {
        retained.forEach(context::releaseGraphicsLayer)
        retained.clear()
        tiles.forEach(context::releaseGraphicsLayer)
        tiles.clear()
        base?.let(context::releaseGraphicsLayer)
        assembled?.let(context::releaseGraphicsLayer)
        controlSource?.let(context::releaseGraphicsLayer)
        sceneInput?.let(context::releaseGraphicsLayer)
        controlSource = null
        sceneInput = null
        base = null
        assembled = null
        recordedPyramid = null
        pyramidContext = null
        recordedControl = null
        controlContext = null
        controlInput = null
        controlOutput = null
    }
}

/**
 * Same-frame, GPU-only reconstruction of the recovered discrete downsample pyramid.
 * Production shrinks each pass with its preceding mip's complete input support and packs the
 * completed tiles once. Explicit center/bilinear sampling preserves the recovered grid
 * without a hardware bitmap snapshot or readback. The original full-extent DAG remains
 * available for numeric fixture comparison. Stable grids retain their programs and layers.
 */
internal class NativeGlassMipPyramid(private val shaders: RuntimeShaderCache) {
    private var initialized = false
    private var source: RenderEffect? = null
    private var sourceSize = IntSize.Zero
    private var kernel: NativeGlassMipKernel? = null
    private var lastLevel = -1
    private var cachedLevels = emptyList<NativeGlassMipLevel>()
    private var atlasSource: RenderEffect? = null
    private var atlasKernel: NativeGlassMipKernel? = null
    private var atlasColorManaged = false
    private var atlasNativeGammaStorage = false
    private var atlasAllowHardwareLinearSampling = false
    private var cachedAtlas: NativeGlassMipAtlas? = null
    private val texturePrograms = mutableMapOf<String, String>()

    /** One unique single-input stage per level; no shared-prefix branch evaluation. */
    fun atlas(
        source: RenderEffect?,
        sourceSize: IntSize,
        maxLod: Float,
        kernel: NativeGlassMipKernel = NativeGlassMipKernel.Compute13Tap,
        colorManaged: Boolean = false,
        nativeGammaStorage: Boolean = colorManaged,
        allowHardwareLinearSampling: Boolean = false,
    ): NativeGlassMipAtlas {
        val extent = IntSize(sourceSize.width.coerceAtLeast(1), sourceSize.height.coerceAtLeast(1))
        val requested = ceil(maxLod.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f).toInt()
        var minimum = extent
        var count = 1
        while (count <= requested && (minimum.width > 1 || minimum.height > 1)) {
            minimum = halfSize(minimum)
            count++
        }
        // A single-level source keeps its existing capture path and does not add a conversion pass.
        val storeNativeGamma = colorManaged && nativeGammaStorage && count > 1
        val retained = cachedAtlas
        if (retained != null && atlasSource === source && atlasKernel == kernel && atlasColorManaged == colorManaged &&
            atlasNativeGammaStorage == storeNativeGamma && atlasAllowHardwareLinearSampling == allowHardwareLinearSampling &&
            retained.baseSize == extent && retained.rects.size == count) return retained
        val continuousRects = ArrayList<IntRect>()
        continuousRects += IntRect(0, 0, extent.width, extent.height)
        var previous = extent
        var continuousX = 0
        while (continuousRects.size <= requested && (previous.width > 1 || previous.height > 1)) {
            previous = halfSize(previous)
            continuousRects += IntRect(continuousX, extent.height,
                continuousX + previous.width, extent.height + previous.height)
            continuousX += previous.width
        }
        fun bounds(rectangles: List<IntRect>) = IntSize(rectangles.maxOf { it.right }, rectangles.maxOf { it.bottom })
        fun area(size: IntSize) = size.width.toLong() * size.height.toLong()
        fun analyticRects(row: Boolean): List<IntRect>? {
            val major = if (row) extent.width else extent.height
            val result = ArrayList<IntRect>(continuousRects.size)
            result += continuousRects[0]
            var previousEnd = 0
            for (index in 1 until continuousRects.size) {
                val remaining = major shr (index - 1)
                if (remaining == 0) return null
                val origin = major - remaining
                val tile = continuousRects[index]
                val length = if (row) tile.width else tile.height
                // The final 1px tile may use the remaining slot after the major axis reaches 1.
                if (origin < previousEnd || length > major - origin) return null
                val x = if (row) origin else extent.width
                val y = if (row) extent.height else origin
                result += IntRect(x, y, x + tile.width, y + tile.height)
                previousEnd = origin + length
            }
            return result
        }
        var selectedRects: List<IntRect> = continuousRects
        var atlasSize = bounds(continuousRects)
        val originalArea = area(atlasSize)
        var regularMipRow: Boolean? = null
        if (maxOf(extent.width, extent.height) <= 65536) {
            for (row in listOf(true, false)) {
                val candidate = analyticRects(row) ?: continue
                val candidateSize = bounds(candidate)
                val candidateArea = area(candidateSize)
                if (candidateArea <= originalArea && (regularMipRow == null || candidateArea < area(atlasSize))) {
                    selectedRects = candidate
                    atlasSize = candidateSize
                    regularMipRow = row
                }
            }
        }
        val rects = selectedRects
        val textureSource = if (storeNativeGamma) {
            val program = shaders.obtainRuntimeShader("Glasmio.NativeMip.nativeGammaStorage", NativeAtlasEncodeStorageShader)
                .asAndroidRuntimeShader()
            val conversion = RenderEffect.createRuntimeShaderEffect(program, "content")
            source?.let { RenderEffect.createChainEffect(conversion, it) } ?: conversion
        } else source
        val compatibilityProducer = {
            var effect = textureSource
            val stages = ArrayList<RenderEffect>()
            textureSource?.let(stages::add)
            for (index in 1 until rects.size) {
                val input = rects[index - 1]
                val output = rects[index]
                val program = shaders.obtainRuntimeShader("Glasmio.NativeMip.atlas.$colorManaged.$storeNativeGamma.$index",
                    if (colorManaged && !storeNativeGamma) NativeMipAtlasManagedDownsampleShader else NativeMipAtlasDownsampleShader)
                    .asAndroidRuntimeShader()
                program.setFloatUniform("nativeMipBaseSize", extent.width.toFloat(), extent.height.toFloat())
                program.setFloatUniform("nativeMipLevelSize", input.width.toFloat(), input.height.toFloat())
                program.setFloatUniform("nativeMipClampZero", if (kernel.clampToZero) 1f else 0f)
                program.setFloatUniform("nativeMipAtlasInputOrigin", input.left.toFloat(), input.top.toFloat())
                program.setFloatUniform("nativeMipAtlasOutputRect", output.left.toFloat(), output.top.toFloat(),
                    output.width.toFloat(), output.height.toFloat())
                program.setFloatUniform("nativeMipCenterScale",
                    if (kernel.oddQuadExpansion && input.width > 1) output.width * 2f / input.width else 1f,
                    if (kernel.oddQuadExpansion && input.height > 1) output.height * 2f / input.height else 1f)
                program.setFloatUniform("nativeMipKernelOffsets", kernel.innerOffset, kernel.outerOffset)
                program.setFloatUniform("nativeMipKernelWeights", kernel.centerWeight, kernel.innerWeight,
                    kernel.diagonalWeight, kernel.outerWeight)
                program.setFloatUniform("nativeMipKernelMode", if (kernel.outputGridOffsets) 1f else 0f,
                    if (kernel.halfArithmetic) 1f else 0f, if (kernel.flushSmallRgb) 1f else 0f)
                program.setFloatUniform("nativeMipAtlasFirstStage", if (index == 1) 1f else 0f)
                val stage = RenderEffect.createRuntimeShaderEffect(program, "content")
                stages += stage
                effect = effect?.let { RenderEffect.createChainEffect(stage, it) } ?: stage
            }
            NativeGlassMipProducer(effect, stages)
        }
        val textureStages = ArrayList<RenderEffect>()
        val textureMode = "${kernel.outputGridOffsets}.${kernel.halfArithmetic}.${kernel.flushSmallRgb}.${kernel.clampToZero}"
        val textureProgramKey = "$colorManaged.$storeNativeGamma.$textureMode"
        for (index in 1 until rects.size) {
            val input = rects[index - 1]
            val output = rects[index]
            val textureProgram = texturePrograms.getOrPut(textureProgramKey) {
                (if (colorManaged && !storeNativeGamma) NativeAtlasManagedColorFunctions else NativeAtlasRawColorFunctions) +
                    nativeMipTextureGridFunctions(kernel.clampToZero) +
                    nativeMipTextureKernelFunctions(kernel.halfArithmetic, kernel.flushSmallRgb) +
                    nativeMipTextureDownsampleMain(kernel.outputGridOffsets)
            }
            val texture = shaders.obtainRuntimeShader("Glasmio.NativeMip.texture.$textureProgramKey.$index", textureProgram)
                .asAndroidRuntimeShader()
            texture.setFloatUniform("nativeMipBaseSize", input.width.toFloat(), input.height.toFloat())
            texture.setFloatUniform("nativeMipLevelSize", input.width.toFloat(), input.height.toFloat())
            texture.setFloatUniform("nativeMipClampZero", if (kernel.clampToZero) 1f else 0f)
            texture.setFloatUniform("nativeMipOutputSize", output.width.toFloat(), output.height.toFloat())
            texture.setFloatUniform("nativeMipCenterScale",
                if (kernel.oddQuadExpansion && input.width > 1) output.width * 2f / input.width else 1f,
                if (kernel.oddQuadExpansion && input.height > 1) output.height * 2f / input.height else 1f)
            texture.setFloatUniform("nativeMipKernelOffsets", kernel.innerOffset, kernel.outerOffset)
            texture.setFloatUniform("nativeMipKernelWeights", kernel.centerWeight, kernel.innerWeight,
                kernel.diagonalWeight, kernel.outerWeight)
            texture.setFloatUniform("nativeMipKernelMode", if (kernel.outputGridOffsets) 1f else 0f,
                if (kernel.halfArithmetic) 1f else 0f, if (kernel.flushSmallRgb) 1f else 0f)
            textureStages += RenderEffect.createRuntimeShaderEffect(texture, "content")
        }
        atlasSource = source
        atlasKernel = kernel
        atlasColorManaged = colorManaged
        atlasNativeGammaStorage = storeNativeGamma
        atlasAllowHardwareLinearSampling = allowHardwareLinearSampling
        return NativeGlassMipAtlas(extent, atlasSize, rects, null, colorManaged, emptyList(),
            textureStages, textureSource, nativeGammaStorage = storeNativeGamma,
            regularMipRow = regularMipRow,
            allowHardwareLinearSampling = allowHardwareLinearSampling,
            compatibilityProducer = compatibilityProducer).also { cachedAtlas = it }
    }

    fun levels(
        source: RenderEffect?,
        sourceSize: IntSize,
        maxLod: Float,
        kernel: NativeGlassMipKernel = NativeGlassMipKernel.Compute13Tap,
    ): List<NativeGlassMipLevel> {
        val extent = IntSize(sourceSize.width.coerceAtLeast(1), sourceSize.height.coerceAtLeast(1))
        val required = ceil(maxLod.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f).toInt()
        var finalSize = extent
        var levelCount = 0
        while (levelCount < required && (finalSize.width > 1 || finalSize.height > 1)) {
            finalSize = halfSize(finalSize)
            levelCount++
        }
        if (initialized && this.source === source && this.sourceSize == extent &&
            this.kernel == kernel && lastLevel == levelCount) return cachedLevels

        val result = ArrayList<NativeGlassMipLevel>(levelCount + 1)
        result += NativeGlassMipLevel(0, extent, source, extent, levelCount)
        var previousSize = extent
        var previousEffect = source
        for (index in 1..levelCount) {
            val size = halfSize(previousSize)
            val program = shaders.obtainRuntimeShader("Glasmio.NativeMip.downsample.$index", NativeMipDownsampleShader)
                .asAndroidRuntimeShader()
            program.setFloatUniform("nativeMipBaseSize", extent.width.toFloat(), extent.height.toFloat())
            program.setFloatUniform("nativeMipLevelSize", previousSize.width.toFloat(), previousSize.height.toFloat())
            program.setFloatUniform("nativeMipClampZero", if (kernel.clampToZero) 1f else 0f)
            program.setFloatUniform("nativeMipOutputSize", size.width.toFloat(), size.height.toFloat())
            // The fragment fallback expands odd-size quads but keeps the original surface
            // projection and source UVs. Its centers are (2 * index + 1) / previousSize.
            program.setFloatUniform("nativeMipCenterScale",
                if (kernel.oddQuadExpansion && previousSize.width > 1) size.width * 2f / previousSize.width else 1f,
                if (kernel.oddQuadExpansion && previousSize.height > 1) size.height * 2f / previousSize.height else 1f)
            program.setFloatUniform("nativeMipKernelOffsets", kernel.innerOffset, kernel.outerOffset)
            program.setFloatUniform("nativeMipKernelWeights", kernel.centerWeight, kernel.innerWeight,
                kernel.diagonalWeight, kernel.outerWeight)
            program.setFloatUniform("nativeMipKernelMode", if (kernel.outputGridOffsets) 1f else 0f,
                if (kernel.halfArithmetic) 1f else 0f, if (kernel.flushSmallRgb) 1f else 0f)
            val downsample = RenderEffect.createRuntimeShaderEffect(program, "content")
            val effect = previousEffect?.let { RenderEffect.createChainEffect(downsample, it) } ?: downsample
            result += NativeGlassMipLevel(index, size, effect, extent, levelCount)
            previousSize = size
            previousEffect = effect
        }
        this.source = source
        this.sourceSize = extent
        this.kernel = kernel
        lastLevel = levelCount
        cachedLevels = result
        initialized = true
        return cachedLevels
    }

    fun reset() {
        initialized = false
        source = null
        sourceSize = IntSize.Zero
        kernel = null
        lastLevel = -1
        cachedLevels = emptyList()
        atlasSource = null
        atlasKernel = null
        atlasColorManaged = false
        atlasNativeGammaStorage = false
        atlasAllowHardwareLinearSampling = false
        cachedAtlas = null
    }

    private fun halfSize(size: IntSize) = IntSize((size.width / 2).coerceAtLeast(1), (size.height / 2).coerceAtLeast(1))
}

/** Common discrete texture reconstruction, independent of the field/ray/material composition. */
private const val NativeMipGridFunctions = """
uniform shader content;
uniform float2 nativeMipBaseSize;
uniform float2 nativeMipLevelSize;
uniform float nativeMipClampZero;
float4 nativeMipTexel(float2 index) {
    if (nativeMipClampZero > .5 && (index.x < 0.0 || index.y < 0.0
        || index.x >= nativeMipLevelSize.x || index.y >= nativeMipLevelSize.y)) return float4(0.0);
    float2 bounded = clamp(index, float2(0.0), nativeMipLevelSize - 1.0);
    return float4(content.eval((bounded + .5) * nativeMipBaseSize / nativeMipLevelSize));
}
float4 nativeMipBilinear(float2 coordinate) {
    float2 pixel = coordinate / nativeMipBaseSize * nativeMipLevelSize - .5;
    if (nativeMipClampZero < .5) pixel = clamp(pixel, float2(0.0), nativeMipLevelSize - 1.0);
    float2 index = floor(pixel);
    float2 fraction = pixel - index;
    float4 upper = mix(nativeMipTexel(index), nativeMipTexel(index + float2(1.0, 0.0)), fraction.x);
    float4 lower = mix(nativeMipTexel(index + float2(0.0, 1.0)), nativeMipTexel(index + 1.0), fraction.x);
    return mix(upper, lower, fraction.y);
}
"""

/**
 * Prepend to a per-level ray/fill program. The caller owns native sampling footprints and
 * adds the branches with PLUS; the normalized triangular weights implement trilinear LOD.
 */
internal const val NativeGlassMipSamplingFunctions = NativeMipGridFunctions + """
uniform float nativeMipLevel;
uniform float nativeMipLastLevel;
float nativeGlassLod(float radius) {
    float scaledRadius = max(radius, 0.0) * 1.6;
    return max(log2(scaledRadius < 2.0 ? 1.0 + scaledRadius * .5 : scaledRadius), 0.0);
}
float nativeMipWeight(float lod) {
    return max(1.0 - abs(clamp(lod, 0.0, nativeMipLastLevel) - nativeMipLevel), 0.0);
}
"""

private const val NativeMipKernelUniforms = """
uniform float4 nativeMipKernelWeights;
uniform float3 nativeMipKernelMode;
"""

private const val NativeMipHalfKernelBody = """
        half4 centerValue = half4(nativeMipBilinear(center));
        half4 axisInner = half4(nativeMipBilinear(center + float2(0.0, -inner.y)))
            + half4(nativeMipBilinear(center + float2(-inner.x, 0.0)));
        axisInner = axisInner + half4(nativeMipBilinear(center + float2(0.0, inner.y)));
        axisInner = axisInner + half4(nativeMipBilinear(center + float2(inner.x, 0.0)));
        half4 diagonal = half4(nativeMipBilinear(center + float2(-inner.x, -inner.y)))
            + half4(nativeMipBilinear(center + float2(-inner.x, inner.y)));
        diagonal = diagonal + half4(nativeMipBilinear(center + float2(inner.x, -inner.y)));
        diagonal = diagonal + half4(nativeMipBilinear(center + inner));
        half4 axisOuter = half4(nativeMipBilinear(center + float2(-outer.x, 0.0)))
            + half4(nativeMipBilinear(center + float2(0.0, -outer.y)));
        axisOuter = axisOuter + half4(nativeMipBilinear(center + float2(0.0, outer.y)));
        axisOuter = axisOuter + half4(nativeMipBilinear(center + float2(outer.x, 0.0)));
        half4 value = axisInner * half(nativeMipKernelWeights.y) + centerValue * half(nativeMipKernelWeights.x);
        value = value + diagonal * half(nativeMipKernelWeights.z);
        value = value + axisOuter * half(nativeMipKernelWeights.w);
"""

private const val NativeMipHalfKernelFlush = """
            value.rgb = half3(abs(float(value.r)) < .00010001659393310547 ? 0.0 : float(value.r),
                abs(float(value.g)) < .00010001659393310547 ? 0.0 : float(value.g),
                abs(float(value.b)) < .00010001659393310547 ? 0.0 : float(value.b));
"""

private const val NativeMipFloatKernelBody = """
    float4 axisInner = nativeMipBilinear(center + float2(-inner.x, 0.0))
        + nativeMipBilinear(center + float2(inner.x, 0.0))
        + nativeMipBilinear(center + float2(0.0, -inner.y))
        + nativeMipBilinear(center + float2(0.0, inner.y));
    float4 diagonal = nativeMipBilinear(center - inner) + nativeMipBilinear(center + inner)
        + nativeMipBilinear(center + float2(-inner.x, inner.y))
        + nativeMipBilinear(center + float2(inner.x, -inner.y));
    float4 axisOuter = nativeMipBilinear(center + float2(-outer.x, 0.0))
        + nativeMipBilinear(center + float2(outer.x, 0.0))
        + nativeMipBilinear(center + float2(0.0, -outer.y))
        + nativeMipBilinear(center + float2(0.0, outer.y));
    return half4(nativeMipBilinear(center) * nativeMipKernelWeights.x
        + axisInner * nativeMipKernelWeights.y + diagonal * nativeMipKernelWeights.z
        + axisOuter * nativeMipKernelWeights.w);
"""

private const val NativeMipKernelFunctions = NativeMipKernelUniforms + """
half4 nativeMipKernel(float2 center, float2 inner, float2 outer) {
    if (nativeMipKernelMode.y > .5) {
""" + NativeMipHalfKernelBody + """
        if (nativeMipKernelMode.z > .5) {
""" + NativeMipHalfKernelFlush + """
        }
        return value;
    }
""" + NativeMipFloatKernelBody + """
}
"""

/** Only the private texture producer has a statically known arithmetic/flush mode. */
private fun nativeMipTextureKernelFunctions(halfArithmetic: Boolean, flushSmallRgb: Boolean): String =
    NativeMipKernelUniforms + """
half4 nativeMipKernel(float2 center, float2 inner, float2 outer) {
""" + (if (halfArithmetic) NativeMipHalfKernelBody +
        (if (flushSmallRgb) NativeMipHalfKernelFlush else "") + "    return value;\n"
    else NativeMipFloatKernelBody) + "}\n"

private const val NativeMipDownsampleShader = NativeMipGridFunctions + NativeMipKernelFunctions + """
uniform float2 nativeMipOutputSize;
uniform float2 nativeMipCenterScale;
uniform float2 nativeMipKernelOffsets;
half4 main(float2 coordinate) {
    float2 index = clamp(floor(coordinate / nativeMipBaseSize * nativeMipOutputSize),
        float2(0.0), nativeMipOutputSize - 1.0);
    float2 center = (index + .5) * nativeMipBaseSize / nativeMipOutputSize * nativeMipCenterScale;
    float2 grid = nativeMipKernelMode.x > .5 ? nativeMipOutputSize : nativeMipLevelSize;
    return nativeMipKernel(center, nativeMipKernelOffsets.x * nativeMipBaseSize / grid,
        nativeMipKernelOffsets.y * nativeMipBaseSize / grid);
}
"""

private const val NativeAtlasRawColorFunctions = """
float4 nativeAtlasDecode(float4 value) { return value; }
float4 nativeAtlasEncode(float4 value) { return value; }
float4 nativeAtlasEncodeRelative(float4 relative, float4 backdrop) { return relative; }
"""

private const val NativeAtlasManagedEncodeFunctions = """
float4 nativeAtlasEncode(float4 value) { return glassNativeGammaToWorkingPremul(value); }
float4 nativeAtlasEncodeRelative(float4 relative, float4 backdrop) {
    if (relative.a == 1.0) return nativeAtlasEncode(relative);
    // A signed backdrop-relative operator is not a color under a nonlinear transform.
    float4 actual = relative + backdrop * (1.0 - relative.a);
    float4 actualWorking = nativeAtlasEncode(actual);
    float4 backdropWorking = nativeAtlasEncode(backdrop);
    return float4(actualWorking.rgb - backdropWorking.rgb * (1.0 - relative.a), relative.a);
}
"""

private const val NativeAtlasManagedColorFunctions = GlassColorSpaceShaderFunctions + """
float4 nativeAtlasDecode(float4 value) { return glassWorkingToNativeGammaPremul(value); }
""" + NativeAtlasManagedEncodeFunctions

private const val NativeAtlasNativeGammaColorFunctions = GlassColorSpaceShaderFunctions + """
float4 nativeAtlasDecode(float4 value) { return value; }
""" + NativeAtlasManagedEncodeFunctions

private const val NativeAtlasEncodeStorageShader = GlassColorSpaceShaderFunctions + """
uniform shader content;
half4 main(float2 p) {
    return half4(glassWorkingToNativeGammaPremul(float4(content.eval(p))));
}
"""

internal fun nativeGlassAtlasColorFunctions(managed: Boolean): String =
    if (managed) NativeAtlasManagedColorFunctions else NativeAtlasRawColorFunctions

private const val NativeMipAtlasGridFunctions = """
uniform shader content;
uniform float2 nativeMipBaseSize;
uniform float2 nativeMipLevelSize;
uniform float nativeMipClampZero;
uniform float2 nativeMipAtlasInputOrigin;
float4 nativeMipTexel(float2 index) {
    if (nativeMipClampZero > .5 && (index.x < 0.0 || index.y < 0.0
        || index.x >= nativeMipLevelSize.x || index.y >= nativeMipLevelSize.y)) return float4(0.0);
    float2 bounded = clamp(index, float2(0.0), nativeMipLevelSize - 1.0);
    float4 sampled = float4(content.eval(nativeMipAtlasInputOrigin + bounded + .5));
    return nativeAtlasDecode(sampled);
}
float4 nativeMipBilinear(float2 coordinate) {
    float2 pixel = coordinate / nativeMipBaseSize * nativeMipLevelSize - .5;
    if (nativeMipClampZero < .5) pixel = clamp(pixel, float2(0.0), nativeMipLevelSize - 1.0);
    float2 index = floor(pixel);
    float2 fraction = pixel - index;
    float4 upper = mix(nativeMipTexel(index), nativeMipTexel(index + float2(1.0, 0.0)), fraction.x);
    float4 lower = mix(nativeMipTexel(index + float2(0.0, 1.0)), nativeMipTexel(index + 1.0), fraction.x);
    return mix(upper, lower, fraction.y);
}
"""

private const val NativeMipAtlasDownsampleMain = """
uniform float4 nativeMipAtlasOutputRect;
uniform float2 nativeMipCenterScale;
uniform float2 nativeMipKernelOffsets;
uniform float nativeMipAtlasFirstStage;
half4 main(float2 coordinate) {
    float2 local = coordinate - nativeMipAtlasOutputRect.xy;
    if (local.x < 0.0 || local.y < 0.0
        || local.x >= nativeMipAtlasOutputRect.z || local.y >= nativeMipAtlasOutputRect.w) {
        if (nativeMipAtlasFirstStage > .5 && (coordinate.x >= nativeMipBaseSize.x
            || coordinate.y >= nativeMipBaseSize.y)) return half4(0.0);
        return content.eval(coordinate);
    }
    float2 index = floor(local);
    float2 center = (index + .5) * nativeMipBaseSize / nativeMipAtlasOutputRect.zw * nativeMipCenterScale;
    float2 grid = nativeMipKernelMode.x > .5 ? nativeMipAtlasOutputRect.zw : nativeMipLevelSize;
    half4 value = nativeMipKernel(center, nativeMipKernelOffsets.x * nativeMipBaseSize / grid,
        nativeMipKernelOffsets.y * nativeMipBaseSize / grid);
    return half4(nativeAtlasEncode(float4(value)));
}
"""

private const val NativeMipAtlasDownsampleShader = NativeAtlasRawColorFunctions + NativeMipAtlasGridFunctions +
    NativeMipKernelFunctions + NativeMipAtlasDownsampleMain
private const val NativeMipAtlasManagedDownsampleShader = NativeAtlasManagedColorFunctions + NativeMipAtlasGridFunctions +
    NativeMipKernelFunctions + NativeMipAtlasDownsampleMain

/** Real-sized intermediate textures keep every recovered tap on its original discrete grid. */
private fun nativeMipTextureGridFunctions(clampToZero: Boolean): String = """
uniform shader content;
uniform float2 nativeMipBaseSize;
uniform float2 nativeMipLevelSize;
uniform float nativeMipClampZero;
float4 nativeMipTexel(float2 index) {
""" + (if (clampToZero) """
    if (index.x < 0.0 || index.y < 0.0
        || index.x >= nativeMipLevelSize.x || index.y >= nativeMipLevelSize.y) return float4(0.0);
""" else "") + """
    float2 bounded = clamp(index, float2(0.0), nativeMipLevelSize - 1.0);
    return nativeAtlasDecode(float4(content.eval(bounded + .5)));
}
float4 nativeMipBilinear(float2 coordinate) {
    float2 pixel = coordinate - .5;
""" + (if (clampToZero) "" else """
    pixel = clamp(pixel, float2(0.0), nativeMipLevelSize - 1.0);
""") + """
    float2 index = floor(pixel);
    float2 fraction = pixel - index;
    float4 upper = mix(nativeMipTexel(index), nativeMipTexel(index + float2(1.0, 0.0)), fraction.x);
    float4 lower = mix(nativeMipTexel(index + float2(0.0, 1.0)), nativeMipTexel(index + 1.0), fraction.x);
    return mix(upper, lower, fraction.y);
}
"""
private fun nativeMipTextureDownsampleMain(outputGridOffsets: Boolean): String = """
uniform float2 nativeMipOutputSize;
uniform float2 nativeMipCenterScale;
uniform float2 nativeMipKernelOffsets;
half4 main(float2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x >= nativeMipOutputSize.x || p.y >= nativeMipOutputSize.y)
        return half4(0.0);
    float2 index = floor(p);
    float2 center = (index + .5) * nativeMipBaseSize / nativeMipOutputSize * nativeMipCenterScale;
    float2 grid = ${if (outputGridOffsets) "nativeMipOutputSize" else "nativeMipLevelSize"};
    half4 value = nativeMipKernel(center, nativeMipKernelOffsets.x * nativeMipBaseSize / grid,
        nativeMipKernelOffsets.y * nativeMipBaseSize / grid);
    return half4(nativeAtlasEncode(float4(value)));
}
"""

/** Native source sampler: each tile clamps independently, then the two integer LODs mix. */
private const val NativeAtlasSamplingUniforms = """
uniform shader content;
uniform float2 nativeAtlasBaseSize;
uniform float4 nativeAtlasRects[32];
uniform float nativeAtlasLastLevel;
uniform float nativeAtlasMipRow;
"""

private const val NativeAtlasRectLookup = """float4 nativeAtlasRect(int level) {
    if (level == 0) return nativeAtlasRects[0];
    if (level == 1) return nativeAtlasRects[1];
    if (level == 2) return nativeAtlasRects[2];
    if (level == 3) return nativeAtlasRects[3];
    if (level == 4) return nativeAtlasRects[4];
    if (level == 5) return nativeAtlasRects[5];
    if (level == 6) return nativeAtlasRects[6];
    if (level == 7) return nativeAtlasRects[7];
    if (level == 8) return nativeAtlasRects[8];
    if (level == 9) return nativeAtlasRects[9];
    if (level == 10) return nativeAtlasRects[10];
    if (level == 11) return nativeAtlasRects[11];
    if (level == 12) return nativeAtlasRects[12];
    if (level == 13) return nativeAtlasRects[13];
    if (level == 14) return nativeAtlasRects[14];
    if (level == 15) return nativeAtlasRects[15];
    if (level == 16) return nativeAtlasRects[16];
    if (level == 17) return nativeAtlasRects[17];
    if (level == 18) return nativeAtlasRects[18];
    if (level == 19) return nativeAtlasRects[19];
    if (level == 20) return nativeAtlasRects[20];
    if (level == 21) return nativeAtlasRects[21];
    if (level == 22) return nativeAtlasRects[22];
    if (level == 23) return nativeAtlasRects[23];
    if (level == 24) return nativeAtlasRects[24];
    if (level == 25) return nativeAtlasRects[25];
    if (level == 26) return nativeAtlasRects[26];
    if (level == 27) return nativeAtlasRects[27];
    if (level == 28) return nativeAtlasRects[28];
    if (level == 29) return nativeAtlasRects[29];
    if (level == 30) return nativeAtlasRects[30];
    return nativeAtlasRects[31];
}
"""

private const val NativeAtlasTexelFunctions = """float4 nativeAtlasTexel(float2 index, float4 rect) {
    float4 sampled = float4(content.eval(rect.xy + clamp(index, float2(0.0), rect.zw - 1.0) + .5));
    return nativeAtlasDecode(sampled);
}
"""

private const val NativeAtlasManualBilinear = """float4 nativeAtlasBilinear(float2 coordinate, int level) {
    float4 rect = nativeAtlasRect(level);
    float2 pixel = clamp(coordinate / nativeAtlasBaseSize * rect.zw - .5, float2(0.0), rect.zw - 1.0);
    float2 index = floor(pixel);
    float2 fraction = pixel - index;
    float4 upper = mix(nativeAtlasTexel(index, rect), nativeAtlasTexel(index + float2(1.0, 0.0), rect), fraction.x);
    float4 lower = mix(nativeAtlasTexel(index + float2(0.0, 1.0), rect), nativeAtlasTexel(index + 1.0, rect), fraction.x);
    return mix(upper, lower, fraction.y);
}
"""

private const val NativeAtlasHardwareLinearBilinear = """float4 nativeAtlasBilinear(float2 coordinate, int level) {
    float4 rect = nativeAtlasRect(level);
    float2 pixel = clamp(coordinate / nativeAtlasBaseSize * rect.zw - .5, float2(0.0), rect.zw - 1.0);
    return float4(content.eval(rect.xy + pixel + .5));
}
"""

private const val NativeAtlasSamplingLodFunctions = """float4 nativeAtlasSample(float2 coordinate, float lod) {
    float bounded = clamp(lod, 0.0, nativeAtlasLastLevel);
    int lower = int(floor(bounded));
    int upper = int(min(float(lower + 1), nativeAtlasLastLevel));
    return mix(nativeAtlasBilinear(coordinate, lower), nativeAtlasBilinear(coordinate, upper), fract(bounded));
}
float nativeGlassLod(float radius) {
    float packedRadius = max(radius, 0.0) * 1.6;
    return max(log2(packedRadius < 2.0 ? 1.0 + packedRadius * .5 : packedRadius), 0.0);
}
"""

private const val NativeAtlasSamplingBody =
    NativeAtlasTexelFunctions + NativeAtlasManualBilinear + NativeAtlasSamplingLodFunctions
private const val NativeAtlasHardwareLinearSamplingBody =
    NativeAtlasTexelFunctions + NativeAtlasHardwareLinearBilinear + NativeAtlasSamplingLodFunctions

private const val NativeAtlasSamplingFunctions =
    NativeAtlasSamplingUniforms + NativeAtlasRectLookup + NativeAtlasSamplingBody

internal const val NativeGlassAtlasSamplingFunctions = NativeAtlasRawColorFunctions + NativeAtlasSamplingFunctions
private const val NativeGlassAtlasManagedSamplingFunctions = NativeAtlasManagedColorFunctions + NativeAtlasSamplingFunctions
private const val NativeGlassAtlasNativeGammaSamplingFunctions = NativeAtlasNativeGammaColorFunctions + NativeAtlasSamplingFunctions

/** Unused packed slots are zero, including slot 31 whenever a smaller bucket is selected. */
private val NativeAtlasSamplingFunctionBuckets: Map<Int, Array<Array<String>>> by lazy {
    listOf(1, 2, 4, 8, 16, 32).associateWith { capacity ->
        val lookup = if (capacity == 32) NativeAtlasRectLookup else buildString {
                append("float4 nativeAtlasRect(int level) {\n")
                repeat(capacity) { level ->
                    append("    if (level == $level) return nativeAtlasRects[$level];\n")
                }
                append("    return nativeAtlasRects[31];\n}\n")
        }
        val manual = NativeAtlasSamplingUniforms + lookup + NativeAtlasSamplingBody
        val linear = NativeAtlasSamplingUniforms + lookup + NativeAtlasHardwareLinearSamplingBody
        arrayOf(
            arrayOf(NativeAtlasRawColorFunctions + manual, NativeAtlasManagedColorFunctions + manual,
                NativeAtlasNativeGammaColorFunctions + manual),
            arrayOf(NativeAtlasRawColorFunctions + linear, NativeAtlasManagedColorFunctions + linear,
                NativeAtlasNativeGammaColorFunctions + linear),
        )
    }
}

/** One program addresses either strip orientation without specializing on mip count or shape. */
private val NativeAtlasRegularSamplingFunctions: Array<Array<String>> by lazy {
    val lookup = """
float4 nativeAtlasRect(int level) {
    if (level == 0) return nativeAtlasRects[0];
    if (level < 0 || float(level) > nativeAtlasLastLevel) return float4(0.0);
    float scale = exp2(-float(level));
    // For integer extents and level >= 1, the half-texel guard preserves the exact floor
    // while keeping an exp2 rounding error away from the lower integer boundary.
    float2 mipSize = max(floor((nativeAtlasBaseSize + .5) * scale), float2(1.0));
    float2 remainder = floor((nativeAtlasBaseSize + .5) * (scale * 2.0));
    float2 origin = nativeAtlasMipRow > .5
        ? float2(nativeAtlasBaseSize.x - remainder.x, nativeAtlasBaseSize.y)
        : float2(nativeAtlasBaseSize.x, nativeAtlasBaseSize.y - remainder.y);
    return float4(origin, mipSize);
}
"""
    val manual = NativeAtlasSamplingUniforms + lookup + NativeAtlasSamplingBody
    val linear = NativeAtlasSamplingUniforms + lookup + NativeAtlasHardwareLinearSamplingBody
    arrayOf(
        arrayOf(NativeAtlasRawColorFunctions + manual, NativeAtlasManagedColorFunctions + manual,
            NativeAtlasNativeGammaColorFunctions + manual),
        arrayOf(NativeAtlasRawColorFunctions + linear, NativeAtlasManagedColorFunctions + linear,
            NativeAtlasNativeGammaColorFunctions + linear),
    )
}
