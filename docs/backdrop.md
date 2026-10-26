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

Standalone surfaces use the shared navigation material. Choose the scene through `GlassSurface(material = NavigationGlassMaterial.RegularSmall)`: Small is suited to controls and bars, Medium to menus and alerts, and Large to sheets. `Clear` retains more of the background. The component defaults already select their scene; Switch, Slider and Segmented Control retain their internal lenses.

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

`rememberLayerBackdrop` is from `com.kyant.backdrop.backdrops`. Recording and tracking must refer to the same layer. Keep its lifetime tied to the composition.

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
            if (showAlert) {
                GlasmioAlertDialog(
                    title = "Ready",
                    message = "The alert samples the page below.",
                    onDismissRequest = onDismissAlert,
                )
            }
        }
    },
)
```

The alert stays outside the page recording. For nested sheets or menus, choose the layer representing the visible content underneath the new surface, then pass it explicitly through the component's `backdrop` parameter or the appropriate local.

## Appearance and fallbacks

`GlassAppearance` has two independent controls:

| Setting | Result |
| --- | --- |
| `liquidGlassEnabled = true` | Enables the refraction channel when transparency is available |
| `liquidGlassEnabled = false` | Keeps backdrop blur while removing liquid refraction |
| `reduceTransparency = true` | Uses readable opaque surfaces and skips backdrop/refraction effects |

```kotlin
GlasmioTheme(
    appearance = GlassAppearance(reduceTransparency = true),
) {
    // Install the same backdrop host and component hierarchy.
}
```

The first release requires Android API 33. Runtime-shader support is checked by the rendering implementation; progressive blur uses its uniform-blur fallback when the shader path is unavailable. Reduced transparency is also available as an explicit host preference.

## Lifetime and cost

Remember backdrop sources instead of recreating them during animation. Glasmio's shared/rasterized backdrop helpers cache recorded regions for multiple glass consumers. Animation state belongs in drawing and layer blocks where possible, so a changing highlight does not rebuild an entire page.

`rememberSharedGlassBackdrop` shares page regions between consumers. `rememberRasterizedBackdrop` creates a live flattened source and removes its pre-draw listener when composition ends. Use these helpers only when the source hierarchy calls for them; the standard host is sufficient for a simple screen.
