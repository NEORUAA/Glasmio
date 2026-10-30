# Interaction performance invariants

Performance changes must preserve the existing motion, live layout, contour tolerances and optical sampling. Do not substitute a scaled fixed-height layer for the sheet's live-height content or skip field updates when geometry changes.

## Shader source lifetime

`NativeControlLens` and `nativeAtlasNavigationFilter` retain immutable shader sources in the existing `BackdropEffectScopeImpl` resource store. RuntimeShader validation, uniform updates and RenderEffect snapshots still run for every effect update. Resources clear with the shader cache when the scope resets.

A regular material source depends on the **complete field source**, managed-color flag, native-gamma-storage flag and completion mode. Keep full field strings in the source-cache key: the existing runtime-shader key contains only a field hash, and its full source comparison must continue to distinguish collisions. Dimensions, material values and completion parameters are uniforms, not source variants.

Control sources depend on their resting/small/tab/precomposed/color variants; generated Gaussian sources also depend on tap limit and plane mode. Each Gaussian axis retains its own shader and uniforms while sharing its immutable source.

## Capture and effect ownership

`layerBackdrop(backdrop)` retains independent visible and capture draws, including content that reads `drawContext.graphicsLayer`. The explicit `layerBackdrop(backdrop, reuseContent = true)` overload may record content once and replay that live display list only when the capture uses the default callback **by identity**, its public `GraphicsLayer` has exactly one attached recording owner, and the layer preserves ordinary content drawing. Recheck ownership and properties after recording: a drawing callback can mutate the public layer. Custom captures, shared layers, clipping, transforms, opacity, filters and non-default blending retain direct visible drawing followed by independent capture. Opt in only for content audited to draw identically in both scopes. Preserve ancestor transforms, overflow and destination blending in both paths.

Source-observer dispatch retains a membership snapshot. Rebuild it when observers attach or detach, preserving notification order and safe removal during callbacks.

`NativeGlassMipAtlas` retains Compose wrappers for its immutable Android `RenderEffect` instances. Source-only redraws reuse those wrappers instead of replacing unchanged RenderNode filters. Keep compatibility producers lazy, preserve every pass and texture barrier, and create new wrappers with a new atlas/filter snapshot.

`NativeGlassAtlasPasses` retains only fixed `drawLayer` commands in wholly private successor layers. Every draw still records the live source/base, first mip tile and assembled atlas; the control-plane path still records stages 0 and 1. Successors reference live child RenderNodes, so every GPU pass and texture barrier remains active. Preserve the source `drawLayer` calls that perform Java-side outline and Z configuration.

Successor-recording reuse depends on atlas identity, the raw Float bits of the actual recording density and font scale, layout direction and the hardware recording environment. Invalidate it when the pass mode changes, generic `layers()` access takes ownership, or layers are released. Do not treat source content or the entire mip chain as a retained recording.

## Native field arithmetic

The AArch64 field blur processes independent lanes with NEON while retaining the scalar Float32 tap order. Keep `-ffp-contract=off`, exact square roots and division, the normal-length threshold, scalar remainder handling and the portable fallback. Do not introduce reciprocal estimates, fused operations, fewer taps, coarser fields or changed contour tolerances. This path is architecture-specific arithmetic, not an emulator renderer policy.

## Composition and measurement

Live palettes, semantic foregrounds and material contexts use tracked composition locals. Propagate every value change to actual readers while allowing unrelated application content to remain composed. Keep luminance ownership, callbacks, animation clocks and material interpolation unchanged.

Sheet dialog attributes depend on the window and dark-mode policy; apply them when those keys change. Sheet offset and glass fraction still drive their existing layout and rendering paths.

Cascading-menu width remains measured during composition with the current collections and text styles. Let `TextMeasurer` revalidate its own cache when asynchronously resolved fonts change; title/style keys alone do not cover that invalidation. Preserve the original measurement constraints and width formula.

## Rejected experiments

Draw-only menu press backgrounds and adaptive geometry/running-state caching were evaluated and reverted. Menu opening tails did not improve consistently, and the adaptive experiment encountered a callback regression test failure. Neither experiment is part of the implementation; immutable shader source reuse from that investigation is retained.

## Evidence

Cold-process comparisons and supporting probes for capture replay, native arithmetic and reader invalidation are retained under [`.local-research/performance/cold-20261030/`](../.local-research/performance/cold-20261030/REPORT.md). Use runs with cleared shader caches as primary performance evidence, and report repeated interactions separately.

The interaction benchmark excludes route preparation and retains popup-dismissal frames through Perfetto after their windows are destroyed. Keep app SurfaceFrame latency separate from UI and RenderThread scope durations. This HyperOS device sets frame flag bit 32 during ordinary rendering; do not discard all nonzero flags or trust its malformed frame-deadline counters. Retain raw frame timestamps and report UI, Draw and RenderThread costs separately.

Supplemental pre-launch traces include the first app draw and shared-shader compilation. Treat nonzero Perfetto buffer-overwrite statistics as data loss even when the service labels them informational. The current cold AVD results are mixed: reduced recording and reader work does not establish consistently lower interaction latency. Keep algorithm microbenchmarks, scheduled CPU time, frame latency and physical-device acceptance separate.
