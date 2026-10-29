# Backdrop hosting

Glass reads pixels from a recorded source. `GlasmioBackdropHost` makes that relationship explicit through two slots:

- `sampledContent` records the background or ordinary page content.
- `overlayContent` draws glass controls above that source.

Both slots receive the same `LayerBackdrop`. The host provides it through `LocalGlassBackdrop` for controls and `LocalBlurBackdrop` for modal surfaces. Glass controls require a host or `GlasmioBackdropProvider`, including when transparency is reduced.

```kotlin
GlasmioBackdropHost(
    modifier = Modifier.fillMaxSize(),
    sampledContent = {
        Box(Modifier.fillMaxSize().background(GlasmioTheme.colors.groupedBackground))
    },
    overlayContent = {
        GlasmioButton(onClick = onContinue) { Text("Continue") }
    },
)
```

Place glass outside the layer it samples. Recording a control into its own backdrop creates a feedback loop. For scrolling pages, `GlasmioPinnedListPage` already records the body separately from its fixed toolbar.

Standalone surfaces use the shared navigation material, calibrated from native UIKit recipes. Explicit `GlassSurface(material = NavigationGlassMaterial.RegularSmall)` selects a scene; Medium serves menus and alerts, Large serves sheets, and Clear retains more background. The sampled face, shape coverage, thin exterior contour, default shadow and upper highlight complete together at device resolution. Foreground content keeps its shape clip. Switch, slider, segmented and tab indicators use their separate native control-lens program. See [Native material rendering](native-materials.md) for the recipes and platform boundaries.

## Bottom navigation and floating search

`GlasmioNavigationBarWithTrailingItem` can detach any caller-owned navigation item;
the adjacent item is not restricted to search. Detached presentation shows its icon,
while integrated expanded presentation uses the normal icon and label. Both
configurations retain an icon-only trailing button when the bar becomes compact.

Keep a navigation bar and its floating search control inside one
`BottomNavigationContact`. The container owns their glass material while they
are separated as well as while their outlines merge. Both controls sample the
same page backdrop, and press deformation does not change the container's
optical reference. Each separated control retains its own capsule edge.
During fusion, refraction follows the exposed contour, including the upper and
lower neck. The former facing edges no longer contribute a second refractive
rim inside the joined material.

The built-in container uses the native 6-point angle-weighted CASDF union. One
analytic distance field supplies its contour, refraction and edge effects;
connection state follows that field rather than rectangle overlap.

`BottomControlContactMember.connected` describes contour contact;
`sharesSurface` describes container material ownership. Do not use `connected`
to switch between individual and shared material backgrounds. The sample's
bottom navigation shows the complete arrangement.

The container's stable material backdrop records its actual visible background before glyphs and the selection lens. The tab lens reads that backplate together with the page; it does not recreate a hidden special material or sample its own completed foreground.

This follows Apple's shared-container organization, described in
[GlassEffectContainer](https://developer.apple.com/documentation/swiftui/glasseffectcontainer)
and [WWDC25's shared sampling explanation](https://developer.apple.com/videos/play/wwdc2025/323/?time=1200).
The Android shader and its stable optical reference are Glasmio's implementation.

## Control lens sources

Built-in controls wire the native lens sources automatically. Their natural background contains the page plus an unwarped track or actual bar backplate. A segmented or tab lens also receives an independent transparent glyph source through `nativeControlLens(..., liftedContent = source)`. Switch and slider thumbs have no lifted glyph source.

Keep the glyph recording separate from the natural plane. The lens owns its selected-content output at rest and while lifted, including the role's source suppression and punchout relationship. Flattening the ordinary and selected glyph rows into the sampled background causes duplicate content and changes the refraction. A custom recording must keep its real position and per-item transforms; do not apply an extra whole-row scale to approximate a portal.

The four roles have distinct background, displacement and foreground recipes. Switch/slider foreground aberration is zero; segmented/tab selection uses nonzero spectral sampling. Ordinary `GlassSurface` presets are not interchangeable with these lens programs.

## Existing recorded sources

Use `GlasmioBackdropProvider(backdrop) { ... }` when the application already owns a backdrop. Custom `LayerBackdrop` sources need both recording and position tracking:

```kotlin
val pageBackdrop = rememberLayerBackdrop()
Box(
    Modifier
        .fillMaxSize()
        .glassBackdropSource(pageBackdrop)
        .trackBackdropPosition(pageBackdrop),
) {
    Text("Recorded page content")
}
```

`rememberLayerBackdrop` is from `io.github.neoruaa.glasmio.glass.render.backdrops`. Recording and tracking must refer to the same layer. Keep its lifetime tied to the composition.

The low-level `Backdrop`, draw modifiers, effects, highlights and shadows live under `io.github.neoruaa.glasmio.glass.render`. Glasmio no longer depends on the Kyant Backdrop artifact and provides no `com.kyant.backdrop` compatibility namespace. Migrate direct imports and remove the old dependency; retained third-party source portions keep their attribution.

## Alerts, sheets, and separate windows

Android dialogs and sheets have separate Compose owners. Their coordinates cannot be mapped with an ordinary parent-child layout transform. Glasmio's modal components use `rememberCrossWindowBackdrop` to map the source through screen coordinates, including ancestor transforms.

The standard host registers its source position automatically. If a modal must sample the rendered page, including its controls, record a second page layer and provide it only to `LocalBlurBackdrop`. The page controls continue to sample the original background:

```kotlin
GlasmioBackdropHost(
    modifier = Modifier.fillMaxSize(),
    sampledContent = {
        Box(Modifier.fillMaxSize().background(GlasmioTheme.colors.groupedBackground))
    },
    overlayContent = {
        val pageBackdrop = rememberLayerBackdrop()
        CompositionLocalProvider(LocalBlurBackdrop provides pageBackdrop) {
            Box(
                Modifier.fillMaxSize()
                    .glassBackdropSource(pageBackdrop)
                    .trackBackdropPosition(pageBackdrop),
            ) {
                GlasmioButton(onClick = onShowAlert) { Text("Show alert") }
            }
            GlasmioAlertDialog(
                visible = showAlert,
                title = "Ready",
                message = "The alert samples the page below.",
                onDismissRequest = onDismissAlert,
            )
        }
    },
)
```

The alert stays outside the page recording. For nested sheets or menus, choose the layer representing the visible content underneath the new surface, then pass it explicitly through the component's `backdrop` parameter or the appropriate local.

## Appearance and fallbacks

`GlassAppearance` exposes these controls:

| Setting | Result |
| --- | --- |
| `liquidGlassEnabled = true` | Enables the refraction channel when transparency is available |
| `liquidGlassEnabled = false` | Keeps backdrop blur while removing liquid refraction |
| `reduceTransparency = true` | Uses readable opaque surfaces and skips backdrop/refraction effects |
| `adaptiveColorScheme = true` | Resolves each sampled material's light/dark recipe from its local backdrop, with hysteresis; default is `false` |

```kotlin
GlasmioTheme(
    appearance = GlassAppearance(adaptiveColorScheme = true),
) {
    // Install the same backdrop host and component hierarchy.
}
```

Adaptive sampling is disabled by reduced transparency or a surface with `sampleBackdrop = false`. A missing sample preserves the last material scheme; before the first sample it follows the host appearance. This changes the material and its default semantic content colors, not the application's theme or explicit child colors. Raw samples publish every valid change, including small threshold crossings. Readbacks are bounded to 30 Hz with one snapshot in flight. The retained light/dark target drives one shared, retargetable presentation spring for the material and semantic foregrounds; nested surfaces sharing its probe also share its presentation.

Custom material hosts use `Modifier.sampleBackdropLuminance` for an invisible probe, with `sampleBounds` in current host-local pixels. The probe does not add a layout layer, clip its children, or draw a material. Do not emulate it with an empty `drawPlainBackdrop` pass, which still owns visible clipping. Recorded sources notify subscribed probes after changes, allowing a stationary surface to refresh its small luminance sample without replaying the complete material. Custom recorded providers can forward `Backdrop.observeSourceChanges`; unsubscribe when the consumer detaches. Non-sampling material contexts let nested sampling surfaces own their own probes. The Demo enables this effect only for the draggable surface on Materials → Adaptive Glass, with a fixed full-page iOS wallpaper crop split at the horizontal center: light above and dark below. Other Demo pages follow their selected environment appearance.

Glasmio requires Android API 33. Known native materials use a full-resolution `RuntimeShader`/`RenderEffect` completion over the sampled texture. Arbitrary material-paint callbacks retain their clipped, isolated path, with sampled material effects on API 33–35 and optional destination `RuntimeXfermode` coating on hardware-accelerated API 36+. Progressive blur retains its uniform-blur fallback. Reduced transparency uses the same host and component hierarchy.

## Lifetime and cost

Remember backdrop sources instead of recreating them during animation. Glasmio's shared/rasterized backdrop helpers cache recorded regions for multiple glass consumers. Animation state belongs in drawing and layer blocks where possible, so a changing highlight does not rebuild an entire page.

`rememberSharedGlassBackdrop` shares page regions between consumers. `rememberRasterizedBackdrop` creates a live flattened source and removes its pre-draw listener when composition ends. Use these helpers only when the source hierarchy calls for them; the standard host is sufficient for a simple screen.

Navigation materials reduce the backdrop texture's capture resolution according to surface size. Source callbacks keep their logical size and density. A separate final consumer evaluates shape coverage, exterior contours, highlights and shadows at device resolution; foreground text and clipping retain their original resolution. The low-level `drawBackdrop` default remains full resolution, and merged contact shapes retain their own sampling policy. Keep foreground content outside a backdrop-only recording rather than downsampling the complete control.

Final native draws are bounded by the complete recorded consumer rectangle, including the atlas or auxiliary planes and exterior guard. Keep that rectangular bound even when a shader returns transparent pixels outside its shape; a shape clip would remove the contour and shadow, while an unbounded runtime filter can allocate a viewport-sized intermediate.

The material's native-derived mip pyramid uses discrete texel grids and manual LOD reconstruction. Each HWUI mip pass retains the preceding grid's bounds as input support and evaluates the next smaller grid, then the completed tiles are assembled once. These Android texture barriers preserve sampling but do not imply identical native allocation or bandwidth cost. See [Native material rendering](native-materials.md) for the sampling and precision boundaries.
