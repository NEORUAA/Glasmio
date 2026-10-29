# Native material rendering

Glasmio owns its Android renderer and the public backdrop API under `io.github.neoruaa.glasmio.glass.render`. It does not depend on the Kyant Backdrop artifact or publish a compatibility namespace. Capture/effect infrastructure adapted from AndroidLiquidGlass retains its Apache-2.0 attribution; shape libraries remain separate dependencies.

The material model combines local iOS 27.0 ARM64 analysis, recovered QuartzCore Metal AIR equations, and actual UIKit/SwiftUI layer parameters and screenshots from an iPhone 13 mini running iOS 27.2. Figma supplies component geometry and design terminology. Glasmio translates recovered equations into Android shaders and supplies an Android sampling adapter; it does not execute Apple's Metal binaries.

## Face and tint

These are captured native recipe values before host-density conversion:

| Recipe | Blur | Saturation | Black / white | White fill alpha | Maximum luma |
| --- | --- | --- | --- | --- | --- |
| Regular light | 5 | 1.2 | 0.4 / 1.03 | 0.2 | 1 |
| Regular dark, small | 5 | 1.3 | 0.125 / 1.125 | 0 | 0.6 |
| Regular dark, large | 5 | 1.3 | 0.125 / 1.125 | 0 | 0.35 |
| Clear | 0.75 | 1.06 | 0.125 / 1.08 | 0 | 1 |

QuartzCore's recovered AIR establishes the pre-face maximum-luma transform. For unpremultiplied source RGB `C`, luminance `Y = dot(C, [0.2126, 0.7152, 0.0722])` and recipe `maxLuma`:

```text
gain = saturate(1 - Y * (1 - maxLuma))
adjusted = mix(vec3(gain * Y), gain * C, 1 + 0.3 * (1 - gain))
face = faceMatrix * vec4(adjusted, 1)
result = mix(C, face, faceOpacity)
```

Attenuation and its chroma correction precede the face matrix. This supersedes the earlier measured quadratic-gray approximation, which did not reproduce colored backgrounds. Intermediate RGB is not immediately clamped to `[0,1]`; the native filter has a later clamp/EDR stage.

The recovered composition order is inner refraction → blur fill from the original source coordinate → independent outer-refraction sample/mix → maximum-luma and face matrix → shape coverage, shadow and ring → backdrop-relative stroke completion → tint → upper highlight. Regular captures use a blur-fill radius of 8. Its combined min/max stage is `B = D*min(C,F) + L*max(C,F) + (1-D-L)*C`, followed by `mix(B,F,N)`; lighten, darken and normal are not three sequential blend operations.

Clear's captured `inputRefractionOpacity = 0` disables the outer-ray mix only. Its inner ray remains active.

Prominent and destructive buttons pass their semantic tint into the same regular material pipeline. The default idle opaque tint matrix is reconstructed from DesignLibrary's HSL endpoint mapping and gamma-encoded sRGB luminance. Captured blue and red matrices match that formula to floating-point precision. It replaces a translucent flat tint overlay; other native recipe/state branches are not implied by that result.

## Capture resolution and platform paths

Native regular examples capture 50-point controls at scale 0.5, a 150-point SwiftUI panel near 1/3, and a 200-point UIKit panel at 0.25. Glasmio interpolates its regular geometry and capture scale through those measured sizes. Explicit frost/refraction overrides remain caller-owned.

`NavigationGlassFilter` uses `NativeGlassMipPyramid`, replacing the four-Gaussian approximation. Native CPU packing first scales the radius by **1.6**; the shader selects `max(log2(r < 2 ? 1 + r/2 : r), 0)` from that packed radius. Each level has a discrete floor-half grid. Manual bilinear texel reconstruction and interpolation between adjacent LODs preserve the recovered inner/fill/outer sampling order.

The recovered 13-tap compute and fragment profiles retain their original weights without renormalization. Compute uses destination-grid offsets, half arithmetic and a small-RGB flush; fragment uses its distinct offsets, float weights and odd-size quad projection. Current navigation selects the compute profile. Same-compiler Metal probes reproduce the native sampler payloads exactly: glass reads use clamp-to-edge with linear mip interpolation, compute downsampling uses clamp-to-edge, and the fragment fallback uses fractional clamp-to-zero coverage. These are separate sampling contracts.

Keeping raw L0 and the complete pyramid is mathematically equivalent to the native half-size-base allocation when every requested full-pyramid LOD is at least 1. Native allocation can omit L0 and shift level/radius packing; Glasmio keeps the full coordinate representation. Glasmio packs discrete levels into an atlas and uses one single-input producer stage per level. A separate final consumer samples that atlas and completes the material at device resolution. Its output origin and extent are independent of the atlas grid; only texture addresses use the capture scale, so the final SDF, antialiasing and exterior masks are not enlarged from a low-resolution result. The complete producer backing remains available before the final visible crop. The final draw has a rectangular bound covering that complete consumer, including the atlas and exterior guard; it does not restore a shape clip. This bounds runtime-filter intermediates while retaining their sampled inputs. HWUI still allocates atlas-sized intermediate targets rather than native independently sized mip textures. Half rounding, intermediate color/texture formats and cross-GPU sampling precision prevent a bit-exact claim.

Only the sampled effect texture is reduced. Source callbacks retain logical dimensions and density; foreground text and the final shape, highlight and shadow raster remain at full resolution. Exports retain full logical bounds. Contact contours and custom shapes can use full-resolution sampling when their optical path requires it. The four built-in control lenses use the independent program described below. Explicit custom optics retain their own adapter contract.

Known native materials use this full-resolution `RuntimeShader`/`RenderEffect` completion on all supported Android versions. Arbitrary material-paint callbacks retain their existing clipped, isolated composition path. That fallback uses sampled material effects on API 33–35 and can use destination `RuntimeXfermode` coating on hardware-accelerated API 36+. The color transfers are shared; this does not establish equal GPU pass count or performance across Android and iOS.

## Edges, shadows and touch

The common material path and default shadow DSL use recovered native SDF transfers, replacing the old Figma three-mask/Plus/burn stack. `Shadow.Default` uses the small regular exterior profile: radius 4 pt, Y offset 8 pt and filter opacity 0.04 multiplied by black fill alpha 0.3. `InnerShadow.Default` uses the inner ring: sigma 5 pt, Y offset 8 pt, width 4 pt and black opacity 0.06. Clear has no exterior shadow or inner ring. Larger regular surfaces use the captured size profiles; interpolation between the measured sizes remains a portable calibration. Morph shadows evaluate the actual continuous union field; padded capture bounds are not the shadow contour.

The upper CASDF highlight supplies coverage to the recovered `vibrant_color_matrix_sover` transfer after prominent tint. The matrix processes the sampled destination color, then applies mask alpha and source-over in the native gamma space. It is not a white highlight color evaluated once and painted over every background. The native transfer retains negative channels down to -0.75 and an upper channel bound of 1.2. Explicit custom highlight colors and blend modes retain their own contract.

The thin dark contour is part of the background kernel. Its negative effect offset enables native stroke, highlight extension and negative bias correction. The half-point exterior strip uses the complement of the original SDF coverage; it is not an added black outline or the ordinary inward highlight strip. Unrefracted L0 supplies the uncovered-color completion before tint. Pixels with zero shape coverage can still have a valid ring, shadow or key/fill contribution, so background output retains that exterior domain while foreground content keeps the original shape clip. Coverage and angular edge masks use the geometric shape gradient; an optical-center adjustment changes refraction without rotating those masks.

Menu Gaussian fields cover a bounded skin band. Samples beyond the valid texel centers use the unbounded analytic contour, rather than clamping the exterior distance to the texture edge. This keeps key/fill and shifted-ring probes local to the skin while preserving the Gaussian field inside its domain. See the [menu field notes](../.local-research/validation/menu-motion/FIELD-BOUNDARY.md).

The completion consumer keeps unrefracted color `C` separate from filtered `R`. `C` is reconstructed as straight RGB from raw L0. Native filtering carries raw RGB and alpha independently into the face transfer; it must not be unpremultiplied a second time. Explicit control optics instead supply ordinary premultiplied pixels and use their corresponding adapter. Signed backdrop-relative completion RGB and extended-range channels survive the final encode.

Edge spread uses `t = max(min(H, M) - 1, 0) / (M - 1)` for `M > 1`, otherwise `t = 1`; the default `M` is 1.2. The native threshold is the cosine of the interpolated SDR/EDR angle. The recovered renderer supplies runtime `H` from a context field whose upstream provider was not identified; a portable display-ratio input must remain distinct from that recovered provider.

Menu morph anchors retain a `GlassMaterialSource` descriptor for known source materials. It carries the source recipe and original measured size, so a shrinking contour does not reclassify its material. Source and target recipes, tint and default shadows are evaluated once on the shared backing. Arbitrary source-paint callbacks remain on the legacy composition path instead of being interpreted as native metadata.

Explicit custom shadow parameters retain their generic contract. `Shadow.radiusIsSigma = true` identifies an ordinary CALayer Gaussian sigma rather than the older custom blur-radius convention. Built-in control lenses own their captured shadow layers instead of adding the former generic highlight and inner-shadow defaults.

Prominent-button finger presses add two backdrop-aware vibrant color matrices **after material/tint and before content**. The full-area layer uses the native saturation/level matrix; the touch-positioned LittleGlow uses a white circular CALayer shadow and tint-dependent color-dodge gain. These touch filters omit `inputClamp`, whose recovered native default is zero, so their matrices retain extended-range output. The separate upper CASDF highlight keeps its explicit 1.2 clamp. The portable touch mask integrates the circular Gaussian shadow, supported by the physical PNG fit, rather than drawing a guessed radial gradient. Tint luminance enters the recovered gain with exponent 1.25.

Button lift and glow opacity follow native size rules. A 140 × 50 pt button reaches approximately 1.10985× lift, matching the physical capture; unrelated controls retain their own deformation. Appearance uses a 0.1 s critical spring, release uses 0.5 s, and the LittleGlow expands to 4× while fading. Position tracking has a separate 0.15 s spring. The API 36 destination blend and API 33–35 final material effect apply the same two matrix transfers. Programmatic `isHighlighted` alone does not exercise this complete touch path.

## Control lenses

`nativeControlLens` selects a `NativeControlLensStyle` for switch thumbs, slider thumbs, segmented indicators or tab indicators. These roles use UIKit's separate control-lens composition, with their own background, displacement and foreground stages.

The program combines the recovered `sdf_glass_displacement` → `displacement_map` transfer and `glass_foreground` spectral transfer with the role's background, resting body, portal, inner shadow and upper vibrant highlight. The foreground retains its seven native RGB weights and averaged source alpha. When its displacement is zero, one identical source sample is reused without changing that weighted arithmetic.

| Role | UIKit lens style | Lifted foreground aberration | Lifted content source |
| --- | --- | --- | --- |
| Switch thumb | 0, Small | 0 | None |
| Slider thumb | 0, Small | 0 | None |
| Segmented indicator | 1, Large | Height / 19; 2.315789 pt at 44 pt high | Complete transparent label row |
| Tab indicator | 1, Large | Height / 19; 3.684211 pt at 70 pt high | Complete accent selected-content row |

Natural background and lifted content occupy separate recorded planes. The natural plane contains the page, raw track or actual bar backplate; it excludes glyphs subsequently supplied through the portal. The tab's native selected portal hides its original source, while the ordinary row has a separate punchout. Segmented content uses its own lens-region punchout. Resting selected content is unwarped. It remains visible when the active glass is removed; multiplying the entire portal/material stack by lift progress would incorrectly remove it.

UIKit installs the active glass while lifted, transitions it to an identity variant on release, then removes that material and temporary displacement layers. A settled switch or slider therefore has a plain white thumb, and a segmented indicator has its opaque resting fill. A settled tab uses a separate Gaussian-2 layer and captured color matrix on its own quarter-resolution grid, with unwarped selected content. The active spectral foreground, inner shadow and refraction are not permanent resting decorations.

The native identity recipe neutralizes optical amplitudes and the face, highlight, ring and shadow strengths. Glasmio interpolates those individual fields using the measured control spring progress; the identity endpoints are recovered, while that per-field interpolation is an Android adapter rather than a recovered UIKit evaluator.

Transparent Android captures also require a per-filter identity operator adapter. It removes neutral background/foreground coverage as the optical filter approaches identity, preserving its signed RGB delta. Opaque captures and the fully active endpoint retain the original transfer. Resting bodies, selected content and the complete lens are not faded together.

Existing-layer captures establish that the MaterialProvider wrapper has `zPosition = -1`. Its color portal hides and sources the ClearGlassView layer. The background samples the lower scene; the Clear content and inner shadow enter once through that portal before the spectral foreground. Including Clear's selected glyphs in the background capture would refract them twice.

During the lift transition, SmallVariant's below-glass order is displacement followed by Gaussian blur. Switch interpolates map amount 50 / blur 6 pt toward 9 / 0 while lifted. Slider is a measured override: its below-glass map stays 0. LargeVariant interpolates below-glass map 0 to 9 without that blur. These transition endpoints do not imply that the active layers remain installed after unlift cleanup. Background, portal displacement, foreground correction and top highlight retain their native layer order and separate state controls.

The background's signed inner refraction amount is `-.3 × height` for SmallVariant and `-.15 × height` for LargeVariant. Its height parameter is `.18 × height` and `.1 × height`, respectively. QuartzCore forwards the signed amount directly to the AIR transfer; taking its absolute value reverses the sampling direction.

Lift changes the actual measured lens bounds rather than scaling the complete optical program. Captured switch/slider idle thumbs are 37 × 24 pt; their lifted representatives are 58 × 38.333333 and 57.35 × 37.2 pt. A 303 × 32 pt three-segment control has a 97 × 28 pt resting indicator inside each 101 pt slot, expanding to 121 × 44 pt. The tab lens is 54 pt high and adds 16 pt to both dimensions when lifted. Its 62 dp navigation container gives the resting indicator 4 dp vertical insets.

A matching four-tab configuration with a prominent trailing role has a 263 × 62 pt main platter, a separate 62 pt button and an 8 pt gap on the 375 pt iPhone. Its selected lens changes locally from 78.75 × 54 to 94.75 × 70 pt, and the selected content source scales to 1.16. A separate parent presentation transform carries the glass, ordinary glyphs, selected source and lens together. Its native 14.137931 pt lift gives this main platter a 1.053756392 scale, making the held lens approximately 99.84 × 73.76 pt on screen. Model UIView transforms alone do not expose that animation.

Tab glyphs use 18 pt Medium with the SF font's `ss16` **Large Symbols** substitution. Their 28 dp layout slot does not force the glyph's ink into a square. Labels use 10 pt Medium in the ordinary row and Semibold in the selected source; segmented labels use 13 pt Regular/Medium. The host's requested letter spacing remains in effect.

Four and five short vertical items use the source-derived phone allocation, including its inset and spacing branches. For example, platter width 263 with four items gives a 78.75 pt local lens; width 333 gives 94 pt with four items and 81 pt with five. These widths include native content outsets and are not universal minimums. Long titles, other counts and unavailable SF metrics retain the responsive fallback.

The default compact material is 48 pt inside the 62 pt allocation, with a 7 pt inset and the same 28 pt symbol slot. It keeps its native 18 pt Medium Large-Symbol configuration. The compact circle and detached button use the UltraSmall Flex recipe (16 pt lift, tracking .625/.262 s and release .375/.4 s); the wide platter's dynamic recipe uses its own size-dependent lift and spring.

SmallVariant lift uses damping ratio .625 / response .27 s and unlift .7 / .5 s. LargeVariant uses a critical 1 / .25 s spring in both directions, with epsilon .001. Small spring overshoot is retained. The measured .22 s hang time is a **minimum lifted lifetime** from lift start: short taps finish that dwell, while a longer hold releases immediately. A new press cancels an outstanding unlift.

The slider's resting ordinary shadow uses sigma 5 pt, Y offset 1 pt and opacity .14. The captured ClearGlass inner shadow uses radius 3 pt, Y offset 7 pt and opacity .06. Gaussian sampling remains an Android adapter: SDR uses HWUI Gaussian blur, while wide/HDR convolution averages decoded native-gamma premultiplied texels. This is distinct from recovering Apple's Gaussian kernel.

Style-0 shadow fill uses `HSL(h, s * .25, .45)` with alpha 1, independently of tint alpha and the shadow's coverage. The recovered DesignLibrary SliderSpecModifier calls the HSL conversion helpers with these constants; 102 controlled UIKit samples also match this transfer for resolved sRGB tints in both appearances. Native input-color conversion for P3 or extended-range tints has not been established.

## Navigation fusion and scroll motion

The navigation container uses the recovered CASDF union with 6 pt smoothness, zero Gaussian radius and zero gradient ovalization. Its polynomial smooth union weights the blend radius by the two field normals' angle. Contour coverage, refraction, highlights and shadows consume that same analytic field, including its unnormalized mixed gradient. A bounded foreground path approximates the zero field for Android content clipping; explicit unsupported custom contacts retain their previous contour contract.

Top toolbar action groups have their own UIKit recipe: the `.toolbar` default geometry spring uses duration .416 / bounce .25, and `NavigationBarPlatterContent` explicitly supplies 12 pt container smoothness. `GlasmioToolbarActionGroup` preserves spring velocity when its style changes mid-transition and retains the shared material through settlement. All native material stages use the same multi-element CASDF union instead of treating the moving outline as a rounded rectangle. Glasmio interpolates backing rectangles between its fixed action slots; this layout/identity mapping is an Android adapter. The old sin-squared pull is removed, and a grouping-only toggle does not add an unproven pulse or delay. Independent per-action custom separation retains its existing contour adapter. See the [top-toolbar source receipt](../.local-research/ios/toolbar-action-separation/FINDINGS.md).

The native scroll-away driver uses a 64 pt active distance and 96 pt minimum content range. Its independent hint applies a maximum five-percent shrink to the common bottom wrapper, including both controls and their glyphs. The target morph uses the ordinary AnimationKit .8/.3 s spring without an intermediate-shape delay. Hint tracking, deceleration and reset have separate .8/.1 s, .8/.5 s and .8/.3 s springs. Android uses the recovered spring family with an explicit normalized precision threshold; conditional AnimationKit projection and impulse integration remain an adapter boundary.

UIKit retains its auxiliary item's identity even when its expanded representation is merged into the main platter. The expanded source uses a dummy/portal, while the minimized target includes the auxiliary item's minimized platter and suppresses its label. The layout manager applies one liquid morph to the source and target collections with `usingIntermediateShapes = false`; no separate detach timer was recovered. Glasmio preserves the caller-supplied trailing role through both endpoint layouts and drives separation and compaction from the existing progress. Its Compose placement, source-to-target interpolation and contact-smoothness ramp from one contour to two are Android adapters, not the recovered UIKit identity-matching algorithm or an additional native material parameter. This explicit host role also differs from UIKit's automatic auxiliary-candidate selection. See the [merged-to-compact source receipt](../.local-research/ios/navigation-merged-compact/FINDINGS.md).

## Color space and HDR output

Native matrices operate in fixed extended gamma-sRGB. Material, highlight and press entry points convert straight working RGB through Android's `toLinearSrgb` / `fromLinearSrgb` adapters; alpha and geometric data remain unchanged. Native gains survive until output conversion instead of being clipped to SDR white in the material shader. Backdrop-relative operators are converted by reconstructing their captured composition, converting both the composition and backdrop, and subtracting the working-space backdrop contribution. Nonlinear color conversion must not be applied directly to signed relative RGB.

Hosts opt into display output with `configureGlassDynamicRange(window, hdrEnabled)`. The helper uses the Activity's display context before decor attachment, allowing a host to select its output before creating material layers. The Demo's persistent HDR Effects setting defaults to off. It requests HDR only when enabled, transparency reduction is off, and the display reports HDR support. Changing its effective output recreates the material host while preserving saveable navigation state, so layers created for SDR are not reused as HDR layers. The default headroom request is Android's automatic policy. A host can supply an explicit linear HDR/SDR ratio; native source clamping at 1.2 is not a bound on the final highlight, whose coverage can exceed one before source-over. `readGlassDynamicRange` distinguishes the request from the display's reported headroom. Actual render formats and headroom remain platform-controlled. An FP16 offscreen readback can verify retained energy; SDR screenshots and SDR-only AVD displays cannot verify emitted HDR brightness.

The mip producer uses progressively smaller intermediate textures with an offscreen barrier per pass, retaining each preceding grid's complete input support, then assembles the atlas once. It preserves the complete downsample kernel while avoiding repeated full-atlas copies. Wide/HDR mip textures carry native gamma-space numbers internally: the base capture converts once, successive mip samples retain that space, and final composition converts the resolved color back to the destination space. The compatibility filter graph is built only when explicitly requested, rather than compiling unused programs for each material owner. Single-level sources retain their existing path. Wide/HDR control blur similarly separates extraction and the two gamma-space Gaussian axes, with bounded shader loop variants that retain the complete sampled kernel. Small control thumbs keep raw and portal pixels in the extract texture; the two Gaussian passes record only the blur plane, which the existing consumer combines without an extra packing pass. Per-sigma Gaussian weights and their total are cached outside the pixel loop. Resting thumbs without lifted content need only their ordinary body and shadow buffer. Resting control bodies compile a separate endpoint program rather than a lifted spectral program with a uniform set to zero. These are Android render-graph adaptations; they retain HDR color processing and do not change the recovered optical endpoints.

## Automatic appearance

`GlassAppearance(adaptiveColorScheme = true)` opts sampled materials into local light/dark resolution. Its default is `false`. Reduced transparency and `sampleBackdrop = false` disable sampling. The resolved material keeps its state while luminance remains in the hysteresis range, retains the last successful sample when a new one is unavailable, and starts from the host appearance.

Glasmio uses code-derived default hysteresis ranges: `[0.2, 0.5]` in a light environment and `[0.6, 0.9]` in a dark environment. The environment selects the range; the material's resolved scheme supplies the retained state. These thresholds were recovered from native code, not physically verified as active UIKit thresholds. Public regular UIKit and SwiftUI probes, including inherited appearance, kept `tracksLuma = false` and followed their effective traits. No public invocation enabling UIKit's optional adaptive-scheme gate was established by those probes.

A separate iPhone 13 mini probe enabled raw `CABackdropLayer` luminance tracking: sRGB gray values `0.25`, `0.5`, and `0.75` returned those same luminance values, rather than their linear-light equivalents. Glasmio retains the alpha-weighted, gamma-encoded Rec.709 sample and the recovered regular-material ranges. Native HDR normalization corrects the sampled surface's encoding scale; physical display headroom must not divide an already unscaled SDR sample. The native `[0.5, 0.5]` range belongs to an explicit override or selected clear-material roles, rather than the ordinary adaptive regular default.

The native adaptive transaction uses the modern `Animation.default`: a fluid spring with response `0.5`, damping fraction `1`, blend duration `0`, and delay `0` (unit-mass stiffness `157.91367`). Glasmio applies that retargetable presentation to independently resolved light/dark material contributions and semantic foregrounds. The native transaction's `1/30` animation-frame interval is not an animation duration. The Android snapshot budget is an independent implementation choice. Private Core Animation filter internals and emitted HDR brightness are not established by this adaptation.

## Local implementation evidence

Reusable raw evidence is ignored by Git. Contributors with the local research workspace can inspect:

- [Adaptive material transaction](../.local-research/ios/adaptive-transition/FINDINGS.md).
- [Adaptive luminance input and physical gray-sweep probe](../.local-research/ios/adaptive-input-luminance/FINDINGS.md).
- [UIKit material path](../.local-research/ios/prominent-buttons/FINDINGS.md) and [tint formula](../.local-research/ios/prominent-buttons/TINT-MATH.md).
- [Recovered AIR composition and face transform](../.local-research/ios/prominent-buttons/gpu/all-filter-composition-audit.md) and [refraction transfer](../.local-research/ios/prominent-buttons/gpu/refraction-findings.md).
- [Native mip grids and 13-tap profiles](../.local-research/ios/prominent-buttons/gpu/mip/FINDINGS.md) and [same-compiler sampler receipt](../.local-research/ios/prominent-buttons/gpu/sampler/receipt.json).
- [Native background completion](../.local-research/ios/prominent-buttons/gpu/native-completion.md), [vibrant edge and HDR transfers](../.local-research/ios/prominent-buttons/edges/vibrancy-hdr/FINDINGS.md), [native SDF edges, shadows and touch CPU path](../.local-research/ios/prominent-buttons/edges/FINDINGS.md), [actual finger captures](../.local-research/ios/prominent-buttons/probe/touch-capture/ACTUAL-TOUCH-FINDINGS.md), and [static control profiles](../.local-research/ios/prominent-buttons/controls-probe/FINDINGS.md).
- [Native adaptation and capture scale](../.local-research/ios/prominent-buttons/ADAPTATION-AND-SCALE.md).
- [Active control-lens recipes, source ownership, typography and timing](../.local-research/ios/control-lenses/FINDINGS.md).
- [Merged auxiliary navigation and compact morph](../.local-research/ios/navigation-merged-compact/FINDINGS.md).
- [iPhone automatic-appearance probe](../.local-research/ios/prominent-buttons/auto-probe/FINDINGS.md) and its complete [material profiles](../.local-research/ios/prominent-buttons/auto-probe/native-material-profiles.json).
- [Owned backend review](../.local-research/ios/prominent-buttons/owned-backend-review.md).
