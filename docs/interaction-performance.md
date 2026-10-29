# Interaction performance invariants

Performance changes must preserve the existing motion, live layout, contour tolerances and optical sampling. Do not substitute a scaled fixed-height layer for the sheet's live-height content or skip field updates when geometry changes.

## Shader source lifetime

`NativeControlLens` and `nativeAtlasNavigationFilter` retain immutable shader sources in the existing `BackdropEffectScopeImpl` resource store. RuntimeShader validation, uniform updates and RenderEffect snapshots still run for every effect update. Resources clear with the shader cache when the scope resets.

A regular material source depends on the **complete field source**, managed-color flag, native-gamma-storage flag and completion mode. Keep full field strings in the source-cache key: the existing runtime-shader key contains only a field hash, and its full source comparison must continue to distinguish collisions. Dimensions, material values and completion parameters are uniforms, not source variants.

Control sources depend on their resting/small/tab/precomposed/color variants; generated Gaussian sources also depend on tap limit and plane mode. Each Gaussian axis retains its own shader and uniforms while sharing its immutable source.

## Rejected experiments

Draw-only menu press backgrounds and adaptive geometry/running-state caching were evaluated and reverted. Menu opening tails did not improve consistently, and the adaptive experiment encountered a callback regression test failure. Their original composition and drawing behavior remain in place; only immutable shader source reuse is retained.

## Evidence

Local device evidence and reusable probes are retained under [`.local-research/performance/interactions/`](../.local-research/performance/interactions/REPORT.md), ignored by Git. The baseline and candidate passed 180 fixed-clock RGBA frame comparisons exactly, covering control press/release, menu press, material resizing/theme changes, and adaptive collapse/expand. This is sampled visual coverage, not an exhaustive guarantee for every device.

The interaction benchmark captures each active popup window before dismissal and excludes route preparation from measurements. This HyperOS device sets frame flag bit 32 during ordinary rendering; do not discard all nonzero flags or trust its malformed frame-deadline counters. Retain raw frame timestamps and report UI, Draw and RenderThread costs separately.
