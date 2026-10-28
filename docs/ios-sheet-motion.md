# Native sheet implementation reference

This reference records read-only IDA Professional 9.4 / Hex-Rays analysis through
IDA Pro MCP of the local iOS 27.0 simulator shared cache, build `24A434`.
The simulator was not booted. UIKitCore addresses are image virtual addresses;
SpringBoard addresses are relative to its extracted image. Raw decompilation,
ARM64 instructions, selector decoding, and MCP responses are retained locally in
`build/reports/uikit-sheet-native/` and are excluded from Git.

A property that returns multiple floating-point values must be checked in ARM64.
Hex-Rays often prints only the first component of a CGRect, CGSize, insets, or
corner-radii return, and can omit arguments to imported Objective-C stubs. This
particularly affects corner radii, the grabber size, and release target selection.

## Ownership

```text
SBModalViewControllerStack
  -> presentViewController:animated:completion:
  -> UIKit presentation selection
      UISheetPresentationController
        + _UISheetPresentationMetrics
        + SheetLayoutInfo (normal fallback)
        |   or SheetLayoutGraph (SheetAG opt-in)
        + _UISheetInteraction
            + _UIHyperInteractor
            + _UIHyperpoint[] / _UIHyperregionUnion (resting detents)
            + _UIHyperrectangle (continuous dragging region)
            + _UIHyperAsymmetricExtender (independent boundary extents)
            + _UIHyperOutOfProcessViewAnimator
        + _UISheetAnimationController (controller presentation lifecycle)
        + UIDimmingView / confined UIDimmingView
        + background / clipping / content / grabber views
```

`SBModalViewControllerStack` delegates presentation and dismissal to UIKit at
`0x9903e8` and `0x71111c`. These entries do not select custom sheet detents or
replace UIKit's sheet animator. SpringBoard's *CoverSheet* classes implement the
lock screen; their names do not identify the modal sheet being reproduced here.

`UISheetPresentationController.initWithPresentedViewController:presentingViewController:`
(`0x1c4bbedc0`) creates layout information from its metrics and creates separate
ordinary and confined dimming views. The interaction is created lazily at
`0x1c4bc6d7c` and is connected to layout through callbacks for detents, the current
index, offsets, and boundary extents.

`_UISheetLayoutInfoProvider.makeLayoutInfoWithMetrics:` (`0x1c5462d30`) chooses
`SheetLayoutGraph` only when the UIKit defaults key `SheetAG` is present and true
(`0x1c5462c58`). With that key absent it chooses `SheetLayoutInfo`. AttributeGraph
is therefore an alternate native implementation, not an unconditional default.
Both implementations expose the same layout properties. The important portable
boundary is resolved layout -> interaction -> presentation, not copying Apple's
private object or graph runtime into Android.

## Detents and visible geometry

| Entry | Recovered behavior |
| --- | --- |
| `mediumDetent` `0x1c4bca9fc` | Creates type 2, identifier `com.apple.UIKit.medium`, with resolver `0x1c4bbec80`. |
| Medium resolver `0x1c4bbec80` | Unavailable in compact vertical size class. Otherwise multiply `maximumDetentValue` by `0.56` for container height > 568 pt, or `0.63` for height <= 568 pt. Constant pair at `0x1c57218c0`. |
| Maximum detent `0x1c53972c8` | Height of `fullHeightUntransformedFrame`, minus the attached unsafe-edge safe area (bottom for an ordinary downward sheet, top for an inverted sheet). |
| Full frame `0x1c5397088`, stack frame `0x1c53946c0` | Derive layout from container bounds, safe area, scene-bar traits, additional margins, placement, keyboard, and nesting. The portrait top-offset metric is 10 pt (`0x1c48bf3e8`). Solarium expands the stack frame by the difference between additional margins with and without that offset, cancelling the extra 10 pt for a normal phone sheet. |
| Untransformed frame `0x1c5399580` | Resolve the current offset, add relevant attached safe-area content, and round geometry to display scale. |
| Medium inset `0x1c48bf82c` | 8 pt. Applied through a uniform transform of the sheet and its content, not a new narrower independent content layout. |
| Capsule inset `0x1c48bf824` | 28 pt. |
| Capsule height threshold `0x1c48bf834` | 100 pt. |
| Edge-attached minimum radii `0x1c48bf7c8` | `(topLeft, bottomLeft, bottomRight, topRight) = (38, 20, 20, 38)` pt. Hardware-concentric enclosing corners can raise these minima. |
| Floating minimum radii `0x1c48bf7e4` | Four components `(32, 32, 32, 32)` pt. |

The medium height is not `screenHeight / 2`. For a medium detent with maximum
value `M` and a bottom attached safe area `B`, its natural content height includes
`0.56 * M + B` on ordinary phone-height containers. Display-scale rounding and the
uniform inset transform then produce the visible frame. The bottom and side
insets must come from that transform together; setting independent horizontal
padding while keeping the old visible height changes the native proportions.

For a normal phone sheet with no preexisting side or bottom frame insets, the
Solarium transform at `0x1c539cb44` reduces to:

```text
pFull = second component of percentFullHeight
pCap = percentCapsular
inset = lerp(pCap, 8 * (1 - pFull), 28)
scale = clamp01(1 - 2 * inset / naturalWidth)
visibleHeight = naturalHeight * scale
visibleBottom = containerHeight - inset
visibleTop = visibleBottom - visibleHeight
```

This scales content and its chrome together. The general method subtracts
preexisting enclosing-frame edge insets before calculating the extra inset, and
combines this transform with the separate depth/stack transform.

`percentFullHeight` is a pair (`0x1c5392d9c`). For ordinary medium + large detents,
both components equal `clamp01(unlerp(currentOffset, mediumOffset,
fullFrameTop))`. With fewer than two non-dismiss detents, its second component
is 1. If the first active offset is beyond the full-frame edge after display-scale
rounding and the sheet is not functionally full screen, both components are 0.
This distinction matters for a single large detent.

`percentCapsular` (`0x1c5398310`) classifies each adjacent resolved detent as 1
when its untransformed height is at most 100 pt and 0 otherwise, then interpolates
those classifications by the current offset within that adjacent pair. It does
not switch abruptly when the live interpolated height crosses 100 pt. Medium
and large are both non-capsular, so this fraction stays 0 between them.

Corner layout (`0x1c539ade8`) combines the metrics with actual enclosing corner
geometry, attachment, full-height and capsular fractions, and explicit preferred
or dismissal radii. Short sheets at or below the 100 pt capsular threshold use
half-height capsule radii. The generic radius getter returning 10 pt is a legacy
metric and does not describe all modern Solarium corners.
The component ordering above is verified by
`__UICACornerRadiiMakeWithRadiusAndMask` (`0x1c43df5b4`), which maps standard
UIRectCorner bits, and `__UICornerRadiiMakeWithRectCornerRadii`
(`0x1c43e1b9c`), which maps the four scalar components to Core Animation storage.

The concentric candidate is computed by intersecting an enclosing corner's
`R x R` square with the resolved inset sheet rectangle, then taking the smaller
intersection dimension (`0x1c539ade8`). For an ordinary bottom corner with equal
side and bottom inset `m`, this candidate is `max(0, R - m)`, then raised to the
metric minimum. The result remains in the natural layer's coordinates and is
subsequently scaled with the sheet. Dividing it by the uniform scale would not
match the measured native layer values.

## Dragging, projection, and target choice

`_UISheetInteraction.init` (`0x1c3c76d8c`) constructs a two-dimensional
hyperinteractor, a point for each detent, a union of those points, a rectangular
continuum, an asymmetric extender, and an out-of-process view animator. It sets
`SheetDecelerationFactor` to its normal fallback `0.99`. Its preferred frame rate
range is 80...120 Hz, preferred 120 Hz; this is an iOS scheduling preference, not
an Android assumption about available display refresh rates.

During a drag, the region is the continuous min/max rectangle. At release it
becomes the union of resting detent points (`0x1c3c7714c`). Thus the same
interactor can continuously follow the finger and then choose a discrete target.

The normal minimum effective speed is 250 pt/s (constant pair at
`0x1c57185c0`, loaded by `0x1c3cb8224`). `_effectiveVelocity`
(`0x1c3cb9818`) zeroes a velocity vector whose norm is below this value.
`_projectedPoint` (`0x1c3cb98b0`) computes:

```text
projected = translatedUnconstrainedPoint
          + effectiveVelocity * decelerationRate / (1 - decelerationRate) / 1000
          = translatedUnconstrainedPoint + effectiveVelocity * 0.099
```

Release selection (`0x1c3c78c98`, checked in ARM64) first finds the nearest detent
to the current unconstrained point and to the projected point. It normally uses
the projected nearest detent. The normal `SheetSpeedThreshold` is 1000 pt/s.
When the speed reaches that threshold, both nearest indices are equal, and the
finger is moving away from the current nearest point in the fling direction, it
forces one adjacent detent in that direction, clamped to the detent range.
Array ordering is determined from the first two detent coordinates. Nearest
search uses strictly smaller distance (`0x1c3e31b98`), so equal-distance ties keep
the first point in the native array.

## Resistance and release velocity

`TouchRubberBandingCoefficient` normally falls back to `0.55`
(`0x1c4e3f3f8`). The coefficient for another input source may differ.
`_constrainedFraction` (`0x1c3cb9cd8`) and `_constrainedPoint`
(`0x1c3cb9d70`) give this one-dimensional boundary response for raw excess `x`
and permitted extent `E`:

```text
visibleExcess = sign(x) * E * (1 - 1 / (abs(x) / E * 0.55 + 1))
fraction = visibleExcess / x
```

The boundary extents are resolved from layout, rather than using one global
rubber-band length. For a normal downward-attached sheet:

- Upward beyond the minimum offset: clamp `container.minY + firstActiveDetent.value
  - safeArea.top` to 0...100 pt (`0x1c53992a8`).
- Downward beyond the maximum offset: clamp `fullHeightFrame.height * 0.25` to
  0...200 pt (`0x1c5399428`).

Inversion swaps the relevant sides. The active maximum can include the dismissal
anchor when dismissal is enabled.

The release animator captures `_constrainedVelocity` before changing the region
(`0x1c3c81eb4`). In one-dimensional boundary resistance this is
`effectiveVelocity * fraction`, not the derivative of the resistance curve.
`0x1c3cb9e08` removes the boundary-normal component by `1 - fraction` using
`__UIHyperFractionalProjectionOntoHyperplane` (`0x1c470d2fc`). A zero-distance
spring with nonzero velocity receives a tiny 1 ms displacement so velocity is
preserved instead of discarded. Interrupted animations preserve their current
presentation point before returning control to the interaction.

## Springs and lifecycle

`_UISheetPresentationMetrics.transitionSpringParametersHighSpeed:`
(`0x1c48bf430`) chooses shared parameters initialized at `0x1c48bf490`:

| Defaults key | Normal fallback |
| --- | --- |
| `SheetResponse` | 0.3441442326 s |
| `SheetDampingRatio` | 1.0 |
| `SheetHighSpeedDampingRatio` | 0.8 |

These are passed to `_UISpringParameters.parametersWithDampingRatio:response:`.
The response is a physical spring response parameter, not a fixed completion
interval. The release path chooses the high-speed set at raw absolute vertical
speed >= 1000 pt/s (`0x1c3c78e50`) and installs it on the animator. The generic
hyperanimator's lazy 0.3 s release parameter is replaced by these sheet metrics.
Its generic boundary-crossing spring has response 0.1 s and damping ratio 1.

`_UISheetAnimationController.interruptibleAnimatorForTransition:`
(`0x1c425ff5c`) owns a retained `UIViewPropertyAnimator`, source frame, full frame,
interactive frame, and alongside/completion blocks. Its conventional transition
duration is 0.4 s (`UITransitionView.defaultDurationForTransition:`, transition 8,
`0x1c4e75644`), while its spring timing parameters come from the sheet metrics.
Programmatic controller transitions and interactive detent settling have related
parameters but distinct lifecycle ownership. Dismissal completion is therefore
not equivalent to removing the view as soon as a visibility boolean changes.

Specifically, the controller factory at `0x1c4bc74ac` only creates this interactive
sheet animator while `_isDraggingAndTransitioning` is true. Ordinary programmatic
presentation delegates to `UIPresentationController` and its visual style, then
the built-in controller transition when no custom animator is returned. The
interactive sheet animator's duration must not be assumed to be the ordinary
programmatic presentation duration. `UISpringTimingParameters` converts response
to `mass = 1`, `stiffness = (2*pi/response)^2`, and
`damping = 2*dampingRatio*sqrt(stiffness)` (`0x1c3c8fb28`).

### Native normalized spring completion

The hyperanimator (`0x1c3c82320`) creates `UIViewPropertyAnimator` with duration
zero and implicit `UISpringTimingParameters`. The latter's `settlingDuration`
(`0x1c3c8fa24`) calls the UIView duration solver (`0x1c5531da0`) for each velocity
axis and keeps the maximum. Completion depends on a normalized **0.001**
displacement tolerance, rather than an absolute screen-pixel threshold.

The release velocity passed to this solver is
`constrainedVelocity / (target - presentationPoint)`. If this displacement is
zero but velocity is not, the release path (`0x1c3c81eb4`) first moves the
presentation point by `velocity * 0.001`, yielding normalized velocity -1000.
The spring retains the physical point velocity after this normalization.

For `omega = sqrt(stiffness/mass)`,
`zeta = max(damping/(2*sqrt(mass*stiffness)), 0)`, and normalized velocity `v`,
the duration solver uses the following branches. The critical branch uses the
native Lambert-W approximation, whose constants were checked in ARM64 rather
than inferred from a screenshot:

```text
zeta == 0: duration = infinity
0 < zeta < 1:
  decay = zeta * omega
  A = abs((decay - v) / (omega * sqrt(1 - zeta*zeta)))
  duration = -log(0.001) / decay                  if A < 2^-23
  duration = max(-log(0.001/(A+1)) / decay, 0)    otherwise
zeta >= 1:
  b = omega - v
  L = -1 - log(abs(omega * exp(-omega/b) * 0.001 / b))
  W = -1 - L - 5.950609937518595 *
      (1 - 1/(1 + 0.3361*sqrt(0.5*L) /
                     (1 - 0.0042*L*exp(-0.0201*sqrt(L)))))
  duration = -(omega + b*W) / (omega*b)
```

With zero velocity and the ordinary sheet response this gives
0.5058237871186482 s, matching the physical
0.5058237871186482 s Core Animation duration. The
Core Animation `settlingDuration` getter reports the separate rounded value
0.6 s. The source requested animator duration, actual animation duration, and
rounded settling metadata must not be substituted for one another. The Android
port includes a finite fallback near the critical solver's singular
`omega - v = 0` case. UIKit's inspected ARM64 path has no corresponding guard;
this fallback is port robustness, not a recovered native branch.

## Materials, dimming, and grabber

Modern non-large background (`0x1c539e884`) creates `_UIViewGlass` with variant 0,
`flexible = true`, and subvariant `sheet`. Large background (`0x1c539e8ec`) is
`UIColor.systemBackgroundColor`. The selector (`0x1c53a351c`) first honors an
explicit background effect and per-detent overrides. Otherwise it selects large
background when the relevant full-height fraction exceeds 0.5 and dimming is
enabled or the sheet is edge attached; non-large uses the glass background.
The relevant fraction is the second `percentFullHeight` component.
The background and content views retain separate ownership, and glass receives a
backdrop group name. When that background owns a glass shadow, the ordinary
presentation shadow is zeroed (`0x1c539e420`).

When the selected background changes during dragging,
`_containerViewLayoutSubviews` (`0x1c4bc5af8`) wraps `UIView._setBackground:` in
`_UISheetAnimateWithCompletion`. Outside dragging it sets the background in the
existing animation context. Thus the background role switches at the midpoint,
but its visual transition uses time-based sheet animation. A linear
`glassOpacity = 1 - pFull` is not the native transition rule.

Dimming is a resolved layout output derived from the current offset between
active dimming detents (`0x1c539dd20`), with separate confined dimming for nested
presentations (`0x1c539dfe8`). UIKit does not equate dimming to expansion to large.
A first medium presentation can already have its normal dimming output.

`_UIGrabber._intrinsicSizeWithinSize:` (`0x1c4154da0`, both ARM64 floating-point
return registers checked) returns **60 x 4 pt** for Solarium and 36 x 5 pt for the
legacy path. It tracks tertiary label color, can use a 30 pt luma blur, and
expands touch insets to a minimum 44 pt target (`0x1c4154e04`).
`UIDropShadowView` initializes grabber spacing to 6 pt (`0x1c4e614dc`) and lays
out the top grabber at spacing plus half its height (`0x1c4e619f8`). For the
modern 4 pt grabber this is a natural-frame top inset of 6 pt and center at 8 pt.

## Physical-device cross-check

The connected iPhone 13 mini running iOS 27.2 was inspected with a temporary native
UIKit probe through `pymobiledevice3`. Its live controller was
`_UIPageSheetPresentationController`, with `SheetLayoutInfo`. A 375 x 812 pt window
reported top safe area 50 pt and bottom safe area 34 pt:

```text
maximumDetentValue = 728
medium natural frame = (0, 370.3333333, 375, 441.6666667)
medium transform = scale(0.9573333333), tx 0, ty 1.4222222
medium visible frame = (8, 381.1777778, 359, 422.8222222)
bottom gap = 8
percentMaximum = 0
percentCapsular = 0
```

The medium background was a flexible regular `_UIViewGlass`, subvariant `sheet`,
automatic frost, and no tint override. Both `UIDropShadowView` and its clipping
view had Core Animation corner-radii components
`[36,36,36,36,38,38,38,38]`: natural top corners 38 pt and bottom corners 36 pt.
Those radii and all content receive the same sheet scale.

The opening position animation was a retained additive `_UIMultiLayer`
`CASpringAnimation` with duration 0.5 s, mass 3, stiffness 1000, damping 500,
`allowsOverdamping = false`, fill mode `both`, and `removedOnCompletion = false`.
With overdamping disabled this runs as a critically damped spring at
`sqrt(1000/3)`, matching the frequency `2*pi/0.3441442326`. The measured 0.5 s
programmatic opening interval is recorded separately from the older cache's
interactive transition convention and free-settling gesture animation.
The presentation-layer samples for opening medium, dismissing, and opening large
fit this critical response with a maximum position difference below 0.061 pt.
The source samples and fit are recorded in
`build/reports/uikit-sheet-native/iphone/presentation-analysis.json`; its inferred
Core Animation commit epoch is an observation, not an additional prescribed delay.

Large rests at `(0, 50, 375, 762)`, with identity transform and natural corner
radii top 38 / bottom 44 pt. The presenting view remains full-window with an
identity transform in both states. Modern page-sheet presentation does not use
the older presenting-page retreat transform in this configuration.

Programmatic Large dismissal retains its 375 x 762 pt viewport and identity
transform while translating down. It does not resize through Medium on its way
out. Its opaque white background animates toward nil on the same 500 ms spring,
revealing the glass backdrop during dismissal. Position, background, and dimming
therefore have distinct values even when they share a controller timeline.

## Hosted table backgrounds and material inheritance

A grouped row does not become translucent by reducing the row's alpha. The
native path retains its semantic system color and resolves that color through
the sheet's inherited material context:

```text
sheet glass background
  -> UIView._GlassBackgroundStyle
  -> rendering-mode 2 + vibrancy 1 trait overrides
  -> UIColor._resolvedMaterialWithTraitCollection:
  -> DesignLibrary background style, level 1, fill role
  -> UIVibrancyEffect / vibrantColorMatrix
```

`UITableConstants_IOS.defaultCellBackgroundPropertiesForTableViewStyle:state:
traitCollection:` (`0x1c4aef2d4`) selects `tableCellGroupedBackgroundColor` for
ordinary grouped cells. `UIBackgroundConfiguration.listGroupedCellConfiguration`
(`0x1c3ca23f8`, helper `0x1c3ca1b70`) carries this semantic color into the
configuration. `_UISystemBackgroundView._dynamicUserInterfaceTraitDidChange`
(`0x1c3e3a25c`) resolves the color again, rather than applying a sheet fraction to
its alpha.

The glass application helpers (`0x1c51c1fa0`, `0x1c51c25c0`) install
`_GlassBackgroundStyle` and explicitly override
`UITraitUserInterfaceRenderingMode` to 2 and `UITraitVibrancy` to 1. The opaque
branch resets these to their default values. Consequently public traits such as
Light/Dark, Elevated, and ListEnvironment can remain unchanged while descendant
colors change their rendering. `UIColor._resolvedMaterialWithTraitCollection:`
(`0x1c43c7720`) requires rendering mode 2; a vibrant material also requires
vibrancy 1. `UIView._updateDynamicBackgroundColor` (`0x1c4e904e0`) applies the
resolved background/content effects while retaining the semantic color.

The Solarium resolver (`0x1c5282a48`, helper `0x1c5283b9c`) maps
`tableCellGroupedBackgroundColor` to
`DesignLibrary.GlassMaterialProvider.ResolvedStyleProvider.resolveBackgroundStyle`
with **level 1** and **fill** role. The imported provider entry is
`0x1caddf5b4`; its symbol was verified from the DesignLibrary export trie.
Helper `0x1c52834a0` converts the returned material's color matrix into
`_UIVibrantColorMatrixConfiguration`, then wraps light and dark configurations
in `UIVibrancyEffect` and `_UIMaterial`.

The physical iPhone probe verified the following default inset-grouped table
behavior in light and dark modes. Cell and fill-view alpha remain 1 in every
state, with no custom background view, color transformer, or configuration
visual effect:

| State | Table background | Configured cell color | Actual cell fill layer |
| --- | --- | --- | --- |
| Medium, light | Clear | White, alpha 1 | White carrier + vibrant matrix |
| Large, light | `#F2F2F7` | White, alpha 1 | Opaque white, no filter |
| Medium, dark | Clear | `#2C2C2E`, alpha 1 | White carrier + vibrant matrix |
| Large, dark | `#1C1C1E` | `#2C2C2E`, alpha 1 | Opaque `#2C2C2E`, no filter |

The live medium cell filter is `vibrantColorMatrix`. Its 4 x 5 matrix is
identity with RGB bias **-0.05999999865889549 in light mode**, and
**+0.05999999865889549 in dark mode**; alpha is unchanged:

```text
1  0  0  0  +/-0.06
0  1  0  0  +/-0.06
0  0  1  0  +/-0.06
0  0  0  1       0
```

This is a backdrop-aware compositing effect, not an ordinary transform of the
solid-white carrier. Independent native captures with the table hidden verify
that rows subtract approximately 15.4 sRGB byte values in light mode and add
approximately 15.4 in dark mode over the sheet backing. Replacing this with a
translucent white or gray SrcOver fill changes contrast and saturation.
The source captures and exact NSValue matrix readbacks are retained in
`build/reports/uikit-sheet-native/iphone/filters-cells-*.jsonl`; independent
backing references are `iphone/empty-{light,dark}-{baseline,medium,large}.png`.

The material role changes during a direct drag at the previously described
full-height midpoint, before the selected detent identifier changes. Its
`_UISheetAnimateWithCompletion` factory (`0x1c3fdea40`) uses a
`UIViewPropertyAnimator` with zero initial velocity and the ordinary sheet
spring (response 0.3441442326, damping ratio 1). The requested property-animator
duration is **0.4 s**:
`transitionDuration` (`0x1c48bf420`) delegates to
`UITransitionView.defaultDurationForTransition:` (`0x1c4e75644`) with kind 8,
whose value is `dbl_1c5722590[8]`. This duration is separate from the measured
0.5 s programmatic presentation animation. The physical container's resulting
`backgroundColor` spring is mass 1, stiffness 333.3333332805039, damping
36.51483716411749, initial velocity 0, and Core Animation duration
0.5058237871186482 s. This effective animation duration is recorded independently
from the factory's requested duration. Its dark color is `#1C1C1E`, identifying
it as container backing rather than the `#2C2C2E` cell fill. Programmatic Large
dismissal reintroduces the vibrant context while retaining Large geometry.

Stable native layer identifiers distinguish descendant replacement from the
container spring. At the drag threshold, the vibrant carrier is replaced by a
plain carrier; neither carrier receives color, opacity, or filter animations
(dark mode), and light mode adds only layout position/size animations. The
private cell traits change from rendering/vibrancy **2/1** to **0/-1** at that
point. Cell appearance therefore switches with the material context instead of
following the container's animated opacity. On programmatic Large dismissal the
traits return to **2/1** and newly created vibrant carriers receive the
controller's 0.5 s spring for `backgroundColor`, `inputColorMatrix`, and clamp
parameters, while the prior plain carrier fades out. The serialized matrix's
initial and target values are identical (RGB identity and the same +/-0.06
bias). Its gain does not animate from zero to one. The new carrier's background
animates from opaque black to opaque white; the prior plain carrier animates
from white or `#2C2C2E` to nil. Direct Large presentation instead starts with
opaque final row color, without an observed alpha ramp. These paths need
distinct handling. The stable-identifier evidence is retained in
`iphone/cell-trait-transition-summary.json` and `traits-cells-*-trace.jsonl`.

### Grouped-list separators

Separators resolve their semantic color independently from the row background.
`UIColor.tableSeparatorColor` (`0x1c43d5cb0`) selects
the modern `separatorColor` palette (`0x1c43cea70`) when Solarium is enabled.
For the normal phone palette this is `#3C3C43` at alpha **0.12** in light mode
and `#545458` at alpha **0.5** in dark mode, including elevated appearance.
An opaque `#E6E6E6` line does not represent this color.

The generic fallback system-material map (`0x1c43ca064`) includes
`separatorColor` and `opaqueSeparatorColor` as vibrancy role **7**, blur style
**9**. That mapping does not establish that a table separator uses this effect.
The physical separators in both Medium and Large are painted into layer
contents with these resolved semantic colors. Their own layers and five
ancestors have no color filters, and background color is nil. This active table path therefore uses
ordinary semantic separator painting, independently of the grouped cell's
level-1 RGB-bias material. `_addSeparatorWithFrame:` (`0x1c4ac2ed8`) creates a
`_UITableViewCellSeparatorView` with its separate backdrop-overlay/effect flags.

`UITableViewCell._separatorHeight` (`0x1c4ac2ac4`) delegates to helper
`0x1c497b604`. The modern Solarium branch returns **1.0 pt**; only the legacy
branch resolves `1/displayScale` subject to its platform condition. The physical
probe confirms `_UITableViewCellSeparatorView` frame height 1.0 pt, such as
`(20, 72.3333359, 303, 1)`. This becomes 3 physical pixels at Large on the
3x iPhone and 2.872 pixels after Medium's uniform sheet scale.

### Hosted labels

The Solarium color-material resolver (`0x1c5283b9c`) maps `labelColor` and
`secondaryLabelColor` to provider foreground levels 0 and 1. The physical
`UILabel.textColor` remains its semantic system color across detents; the
material rendering difference is applied by a parent `_UIMultiLayer` filter.
Medium uses RGB-identity vibrant color matrices with these RGB biases:

| Appearance | Primary | Secondary |
| --- | --- | --- |
| Light | -1 | -0.400000006 |
| Dark | +0.949999988 | +0.300000012 |

Large removes the label filters. Its primary color is ordinary black or white;
secondary is `#3C3C43` at alpha 0.6 in light mode and `#EBEBF5` at alpha 0.6 in
dark mode. A fixed foreground gray does not reproduce Medium's
backdrop-dependent secondary label material. The separator and label readbacks
are retained in `iphone/separator-text-material-summary.json`.

## Android implementation and optical mapping

`GlasmioSheetState` owns detent selection and cancellation. `IosSheetLayout`
resolves unscaled bounds, floating transforms and capsular geometry;
`IosSheetMotion` owns gesture projection, resistance and settling.
`IosSheetPresentationMotion` separately owns the measured 500 ms controller
response. `GlasmioBottomSheet` owns window lifetime, insets, scroll arbitration
and background-role transitions. `GlasmioSheetSurface` owns the shared optical
renderer; the custom-player Scaffold state remains independent.

Programmatic presentation freezes its selected viewport and keeps independent
dimming position and velocity. A dismissal reversal retains those presentation
values. Direct dragging returns to the detent continuum. Upward content motion
expands before scrolling, and an upward fling at Large passes into content.
Downward motion at the content's top transfers to the sheet, including resisted
input. A real drag cancellation returns to its origin detent. Both sheet corners
and grabber use continuous curves; bottom radii adapt to Android's reported
hardware corner radius using the native concentric rule.

The optional `topBar` slot adopts Glasmio's navigation-header treatment: a separate
body-only backdrop feeds the shared untinted 10dp progressive blur, extending 34dp below
the measured header. The handle and toolbar are overlaid after recording, avoiding
feedback. A foreground-only mask matches the Reduce Transparency header block:
alpha rises linearly from 0.25 at its top to 1 at its bottom. The blur still extends
34dp below the header. The native backplate and table canvas remain separate from
this mask. The full original progressive blur samples
the already-faded content, with no extra spatial attenuation or dim/tint paint.
Reduce Transparency uses an opaque canvas instead. The body extends to
the sheet's bottom edge; `LocalGlasmioSheetContentPadding` supplies scroll-content
and footer safe spacing without reserving a differently colored navigation strip.
This chrome treatment is a Glasmio integration choice, not a recovered UIKit filter.
The header starts clear and uses the supplied `topBarBlurProgress` scroll-edge
value to reveal its material, independently of compact/large-title state.

The Android backdrop retains the shared Navigation blur, lens, outline and
shadow stack, with sheet-specific paint and preserved source colors. It exports
a content-free backplate for descendant materials, corresponding to UIKit's
backdrop provider. Grouped-cell matrices sample that backplate; they never
transform solid white pixels or sample their own foreground. Label materials
use the same provider through glyph masks. Separators retain ordinary semantic
SrcOver painting rather than cell vibrancy.

Bare-sheet captures and table-cell captures are calibrated independently after
converting native Display P3 PNGs to sRGB. The Android light backing uses a white
SrcOver pass at 0.232 and a white Plus pass at 0.065; dark uses `#111111` at 0.43.
These renderer mappings are measured approximations to the private regular/sheet
filter, not recovered Apple GPU coefficients. The table and cell semantic colors
and their vibrancy matrices, in contrast, follow the read-back UIKit values.

On the isolated low-frequency body region, input-normalized RGB RMS errors are
0.81/255 light and 0.71/255 dark for the bare sheet, and 1.04/255 light and
0.99/255 dark for the cell. The source-normalized comparison excludes text,
separators, progressive header effects, optical edges, shadows and different
hardware bottom corners. It is not a whole-image pixel-parity measurement.
The reproducible local evidence is
`build/reports/uikit-sheet-native/separated-avd-optical-comparison.json`.

## Mapping limits

The analyzed simulator image supplies implementation structure, fallback
parameters, and formulas. A connected physical-device probe is used separately
to verify its active branch, frames, colors, and presentation values. UIKit
private glass rendering, Core Animation scheduling, hardware corner geometry,
scene embedding, and platform input integration are not Android primitives;
the Compose implementation must make these mappings explicit. An unverified
parameter must not be described as recovered solely because its visual result
looks similar.
