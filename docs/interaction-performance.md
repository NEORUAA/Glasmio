# Interaction performance invariants

Performance changes must preserve the perceived motion, live layout, glass depth, refraction, dispersion and highlights. Texture interpolation and intermediate precision may change when the resulting appearance remains intact; pixel identity is not a performance requirement. Do not substitute a scaled fixed-height layer for the sheet's live-height content or skip field updates when geometry changes.

## Shader source lifetime

`NativeControlLens` and `nativeAtlasNavigationFilter` retain immutable shader sources in the existing `BackdropEffectScopeImpl` resource store. RuntimeShader validation, uniform updates and RenderEffect snapshots still run for every effect update. Resources clear with the shader cache when the scope resets.

A reset scope hands its compiled programs to a per-thread `RuntimeShaderPool`, keyed by the complete source. A new scope that needs the same source takes one instead of compiling it again on the main thread; this happens on every push/pop that recreates the tab bar or header buttons. Before reuse every float and int uniform is written to zero, the state of a freshly compiled builder. Programs with a child other than `content`, color uniforms or declarations the linear parser does not recognize are never shared. Parse only when a program is reused, never while a scope detaches. Effects and paints created earlier keep their own uniform snapshots.

Renderers that hosts remember while inactive (`GlassMorphRenderer`, the inner-shadow edge blender and the vibrant highlight blender) compile their programs on first use. `GlassMorphRenderer` creates the shadow-lift shader together with its RenderEffect, so that effect still snapshots the default uniforms. See the [transition traces](../.local-research/perf-20261105/transition/README.md).

A regular material source depends on the **complete field source**, managed-color flag, native-gamma-storage flag and completion mode. Keep full field strings in the source-cache key: the existing runtime-shader key contains only a field hash, and its full source comparison must continue to distinguish collisions. Dimensions, material values and completion parameters are uniforms, not source variants.

Control sources depend on their resting/small/tab/precomposed/color variants and shared-stage capability; generated Gaussian sources also depend on tap limit and plane mode. Each Gaussian axis retains its own shader and uniforms while sharing its immutable source.

Atlas sampling sources are initialized only for the selected layout, lookup capacity, sampler and color mode. Keep the fixed variant set and preserve the complete generated source.

Active Tab scene and consumer programs share their source across the held endpoint. At zero rest, the background transfer returns the original zero-body composition before reading the omitted blur plane. Keep the blur kernel, source uniforms and resting optical endpoint unchanged.

Control scene composition passes the same-coordinate field, raw source and below-glass result through the background and portal stages. Displaced points evaluate their own field and source. Keep raw source and below-glass color distinct: background completion consumes the latter, while relative output uses the former. These are float values within one program, with no new storage or color conversion.

Active built-in Tab consumers sample the already materialized scene instead of reconstructing its below-glass, background and portal colors. The scene producer retains those optical operations. The consumer retains seven spectral samples, relative output and highlights; a geometry-only background alpha preserves the ring and shadow around opaque captures, while transparent captures retain alpha recovery from the composed scene. This intentionally accepts the existing scene texture's precision and interpolation. The source-cache key includes the sampled-scene capability because a modifier can change its backdrop or layer callback without replacing its scope.

Owned SDR active Tabs materialize the original continuous distance and normal field once per geometry into a private data plane. Scene and final consumers sample that plane without repeating the analytic field. Eligibility requires a hardware recording, scalar equal corners, unit sampling scale and the known Capsule or continuous shape. Its support includes displaced portal, spectral and highlight queries; unsupported geometry retains the analytic route. Treat field RGB as data, independently of the scene color codec. See the [field transport probes](../.local-research/performance/first-use-20261102/gpu-field-plane-candidate/probes/avd-result.json).

Tab and segmented-control extraction sources omit the unreachable SmallVariant warp before compilation. The switch and slider retain their warp and mask. Owned SDR extraction uses one clamped hardware-linear lookup; managed extraction retains decode-before-interpolate sampling.

Owned SDR Tabs fuse their unwarped quarter-grid extraction into Gaussian H. Capture raw and portal rectangles once, retain the complete raw logical input for H, then use the quarter-sized H output as V input. Each H tap first clamps on the old quarter grid, maps to the raw image and clamps again at its texel centers. H/V retain the same kernel and use one source with input-scale uniforms; small controls, managed windows and custom scopes retain separate extraction. See the [render-path comparison](../.local-research/performance/first-use-20261102/control-extract-fusion-candidate/README.md).

```text
live capture -> Gaussian H/V -> scene image -> spectral lens output
                                  |                   |
                                  +--- sampled once --+
```

## Capture and effect ownership

`layerBackdrop(backdrop)` retains independent visible and capture draws, including content that reads `drawContext.graphicsLayer`. The explicit `layerBackdrop(backdrop, reuseContent = true)` overload may record content once and replay that live display list only when the capture uses the default callback **by identity**, its public `GraphicsLayer` has exactly one attached recording owner, and the layer preserves ordinary content drawing. Recheck ownership and properties after recording: a drawing callback can mutate the public layer. Custom captures, shared layers, clipping, transforms, opacity, filters and non-default blending retain direct visible drawing followed by independent capture. Opt in only for content audited to draw identically in both scopes. Preserve ancestor transforms, overflow and destination blending in both paths.

Source-observer dispatch retains a membership snapshot. Rebuild it when observers attach or detach, preserving notification order and safe removal during callbacks.

`NativeGlassMipAtlas` retains Compose wrappers for its immutable Android `RenderEffect` instances. Source-only redraws reuse those wrappers instead of replacing unchanged RenderNode filters. Keep compatibility producers lazy, preserve every pass and texture barrier, and create new wrappers with a new atlas/filter snapshot.

`NativeGlassAtlasPasses` retains only fixed `drawLayer` commands in wholly private successor layers. Every draw still records the live source/base, first mip tile and assembled atlas. Control paths record their live capture and first Gaussian input. Successors reference live child RenderNodes, so every GPU pass and texture barrier remains active. Preserve the source `drawLayer` calls that perform Java-side outline and Z configuration.

The ordinary mip packer uses `Auto` composition: its disjoint children draw into the final filter's input without another atlas-sized copy target. Keep the original capture, every mip stage and the final filter isolated; only the private packing layer has no effect, group opacity or non-default blend. See the [Compose layer audit](../.local-research/performance/first-use-20261102/atlas-pack-copy-before/pass-audit.md).

Successor-recording reuse depends on atlas identity, the raw Float bits of the actual recording density and font scale, layout direction and the hardware recording environment. Invalidate it when the pass mode changes, generic `layers()` access takes ownership, or layers are released. Do not treat source content or the entire mip chain as a retained recording.

Owned materialized inputs use HWUI's linear image sampler when stored channels need no per-texel color decode. This includes control atlases, ordinary mip producers and the private morph renderer. Mip producers retain their thirteen taps, weights and texel-center clamps, replacing each four-read interpolation with one filtered read. Managed working-color interpolation and transparent-border kernels retain manual sampling. The texture program key includes the sampling mode.

Continuous corners and their optical gradients are perceptually important. Tab scene/consumer programs and navigation unions retain the original continuous-corner field; do not replace it with a circular capsule or circular rounded rectangle. Preserve the union's affine axes, gradient, bridge, smoothness and presentation transforms as well as the visible Compose outline.

Material programs omit generic fill operations only when the recipe and its alternate appearance have no fill layers. Source and target recipes have independent topology bits. All declarations and live appearance/tint/opacity updates remain available; custom recipes with fills use the complete implementation.

The fixed tint-mask Bezier uses four clamped Newton iterations instead of twelve bisections. Its x derivative remains above .6429 on [0,1]; the maximum easing difference from the previous finite bisection is below .000182. Control points, coverage and the final y polynomial are unchanged.

## Native field arithmetic

The AArch64 field blur processes independent lanes with NEON while retaining the scalar Float32 tap order. Keep `-ffp-contract=off`, exact square roots and division, the normal-length threshold, scalar remainder handling and the portable fallback. Do not introduce reciprocal estimates, fused operations, fewer taps, coarser fields or changed contour tolerances. This path is architecture-specific arithmetic, not an emulator renderer policy.

Navigation union paths keep the result of the 20-step bisection for every contour root. `RootSearch` reaches it with fewer field evaluations: a hinted window and a safeguarded Illinois iteration enclose the sign change, then the bisection is replayed and only midpoints within the rounding-noise band (`kNavigationFieldNoise`) around that bracket are evaluated. Misaligned capsules, grazing rays near an endpoint and any evaluation that contradicts monotonicity run the plain bisection. On AArch64, four searches share one NEON field evaluation whose lanes repeat the scalar operations, comparisons and NaN rules. The native side returns closed contours in a float array that Kotlin replays into the `Path`. Changes must keep the emitted contours byte-identical to the plain bisection on both the NEON and scalar paths. Raising the noise band only costs evaluations; lowering it below the observed rounding noise breaks the replay. See the [navigation path probes](../.local-research/perf-20261104/navpath/) (`pcompare.sh`, `fuzz.cpp`, `harness_real.cpp`).

## Composition and measurement

Live palettes, semantic foregrounds and material contexts use tracked composition locals. Propagate every value change to actual readers while allowing unrelated application content to remain composed. Keep luminance ownership, callbacks, animation clocks and material interpolation unchanged.

Sheet dialog attributes depend on the window and dark-mode policy; apply them when those keys change. Sheet offset and glass fraction still drive their existing layout and rendering paths.

Cascading-menu width remains measured during composition with the current collections and text styles. Let `TextMeasurer` revalidate its own cache when asynchronously resolved fonts change; title/style keys alone do not cover that invalidation. Preserve the original measurement constraints and width formula.

## Rejected experiments

Role-specific highlight arithmetic was evaluated with the original continuous corners retained and reverted. The [candidate record](../.local-research/performance/first-use-20261102/highlight-candidate-source.json) retains its source identity and outcome: cold compilation and frame tails did not improve consistently across Demo and MeiloX.

Draw-only menu press backgrounds and adaptive geometry/running-state caching were evaluated and reverted. Menu opening tails did not improve consistently, and the adaptive experiment encountered a callback regression test failure. Neither experiment is part of the implementation; immutable shader source reuse from that investigation is retained.

Explicit `noinline` boundaries on atlas samplers or control-scene functions did not reduce first-use compilation on the physical device and were removed. Enlarging the scene input to the full consumer atlas also lacked a clear benefit. Preserve normal compiler specialization and the original input extents; image bounds and target state can still require distinct Skia pipelines for the shared source.

Do not defer all effect invalidation to drawing: `nativeMaterialCompleted` changes the owning layout layer's clip and compositing strategy. A path clip may already be recorded before the draw callback runs, so a draw-time update can leave the first frame using the previous material's clipping.

## Device measurement

Use ADB for device control and Perfetto FrameTimeline as the primary frame measurement. Query traces through the official Python `TraceProcessor` API and keep intermediate rows in memory. Android Studio is an optional view for thread and memory investigations. Run extra collectors separately from cold comparisons.

Verify the target package, UID, PID and window before attributing frames. MeiloX uses the `com.netease.cloudmusic` process alias; a process-name match alone can select another installed app. The unmodified androidperf 0.2.1 collector cannot handle this safely and attempts a heap dump after recording, so it is not part of the formal benchmark. Its gfxinfo-derived FPS and automatic jank summary do not replace actual presentation measurements.

Report all-frame long-latency shares, p95/p99, deadline misses and actual presentation gaps. Keep app SurfaceFrame latency, display presentation intervals and scheduled CPU time distinct. A layer's low buffer production rate can include stationary or obscured content; call it animation FPS only when continued visual motion is established. Include cold startup and route preparation in the whole sequence, with interaction phases also reported separately. Preserve per-window attribution for sheets and menus.

## Evidence

Cold-process comparisons and supporting probes for capture replay, native arithmetic and reader invalidation are retained under [`.local-research/performance/cold-20261030/`](../.local-research/performance/cold-20261030/REPORT.md). Use runs with cleared shader caches as primary performance evidence, and report repeated interactions separately.

The physical-device follow-up for lazy atlas sources and unified Tab programs retains [source and APK identities](../.local-research/performance/device-20261101/accepted-source.json), the [Demo comparison](../.local-research/performance/device-20261101/analysis/accepted-demo-summary.md) and the [MeiloX comparison](../.local-research/performance/device-20261101/analysis/accepted-mei-comparison.md). The measured benefit is concentrated in first-use navigation dragging; it does not establish a general menu, sheet or whole-app improvement.

The [AndroidLiquidGlass shader review](../.local-research/performance/kyant-20261101/kyant-shader-review.md) identifies small reachable shader graphs and fixed source families as useful organizational patterns. Glasmio retains its own optical kernels and color math. The [shared-scene comparison](../.local-research/performance/kyant-20261101/shared-scene-summary.md) measures explicit reuse of identical intermediate values against `7a484a6`, separately from the earlier gains.

The [Compose/HWUI boundary audit](../.local-research/performance/render-architecture-20261101/production-graph.md) grounds the shared Tab program in the existing materialized passes. Its [Demo confirmation](../.local-research/performance/render-architecture-20261101/shared-tab-confirmation-comparison.md) and [MeiloX comparison](../.local-research/performance/render-architecture-20261101/shared-tab-program-mei-comparison.md) retain complete cold interaction sequences, including compilation that moves between actions and startup or frame-tail regressions. First-use stalls remain; the improvement is specific to navigation.

Retain popup-dismissal frames through Perfetto after their windows are destroyed. Historical interaction-only samples exclude route preparation; current full-sequence captures include it. Keep app SurfaceFrame latency separate from UI and RenderThread scope durations. This HyperOS device sets frame flag bit 32 during ordinary rendering; do not discard all nonzero flags or trust its malformed frame-deadline counters. Retain raw frame timestamps and report UI, Draw and RenderThread costs separately.

Supplemental pre-launch traces include the first app draw and shared-shader compilation. Treat nonzero Perfetto buffer-overwrite statistics as data loss even when the service labels them informational. Keep algorithm microbenchmarks, scheduled CPU time, frame latency and physical-device acceptance separate.
