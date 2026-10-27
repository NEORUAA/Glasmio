# Native Alert motion reference

Glasmio's Alert motion follows the iOS 27.0 simulator image, build 24A434, analyzed
read-only with IDA Professional 9.4 and Hex-Rays. No simulator was started for this
analysis. Addresses below are image virtual addresses, except SpringBoard's
zero-based image addresses.

| Owning implementation | Entry | Behavior |
| --- | --- | --- |
| SpringBoard `SBSharedModalAlertItemPresenter` | `0x521fa4`, `0x522170` | Uses standard animated controller presentation and dismissal. Cleanup runs after the dismissal completion. |
| SpringBoard `SBModalViewControllerStack` | `0x9903e8`, `0x71111c` | Calls `presentViewController:animated:completion:` and `dismissViewControllerAnimated:completion:` without replacing the animator. |
| SpringBoardUI `SBAlertItem` | `0x21f9d76bc` | Creates `_SBAlertController` and selects `UIAlertControllerStyleAlert` (1). |
| SpringBoardUI style provider | `0x21f9d79fc` | Returns `nil`, selecting UIKit's default style. `_SBAlertController` and `SBAlertControllerVisualStyle` have no animation overrides or category overrides. |
| UIKit `UIAlertControllerVisualStyleAlert` duration | `0x1c4871420` | Returns 0.404 seconds for either direction. |
| UIKit Alert animation | `0x1c487142c` | Presentation starts at scale 1.2 and alpha 0, ending at identity and alpha 1. Dismissal uses identity and fades alpha 1 to 0. The dimming view uses the same alpha timeline. Reduce Motion keeps the presentation transform at identity. |
| UIKit Alert animation blocks | `0x1c487169c`, `0x1c4871740` | Apply the final transform/alpha, restore glass visibility suppression, and complete the transition. |
| UIKit `UIView` duration spring | `0x1c4eaa504` | Receives duration 0.404, delay 0, damping ratio 600, initial velocity 0 and options 6 (allow interaction and begin from current state). |
| UIKit `UIViewSpringAnimationState` generator | `0x1c4ebbd48` | Generates unit-mass spring properties with epsilon 0.001. |
| UIKit physical solver | `0x1c4ebb518` | Clamps damping ratio to 1, producing a critically damped response. |
| UIKit critical equation / derivative / Newton solver | `0x1c4ebbaf8`, `0x1c4ebbb68`, `0x1c4ebbba4` | Solves `(1 + omega * T) * exp(-omega * T) = 0.001`, starting at `5 / T`. |

For `T = 0.404`, the recovered solver gives `omega = 22.8549844663`,
`mass = 1`, `stiffness = 522.350314957` and physical damping `45.709968933`.
The normalized response at elapsed time `t` is:

```text
progress(t) = 1 - (1 + omega * t) * exp(-omega * t)
```

It reaches approximately 0.999 at 404 ms, when Core Animation completes and
removes the animation. Glasmio samples this response on the same fixed interval
and completes at the exact target. It does not reinterpret 404 ms as the response
of a different spring or wait for Compose's independent settling threshold.

The panel and dimming view animate separately. `GlasmioAlertDialog(visible = …)`
keeps the window, cross-window backdrop and editing host alive until dismissal
finishes. Changing `visible` during an animation cancels removal and continues
from the current presentation values. Android's animation duration scale is
respected by Compose. Removing the composable itself still removes its window,
so callers should drive `visible` rather than conditional composition.

The shared-cache selector and relative method-list decoding follows Apple's
[ObjC visitor](https://github.com/apple-oss-distributions/dyld/blob/main/common/ObjCVisitor.h)
and [cache optimization layout](https://github.com/apple-oss-distributions/dyld/blob/main/common/DyldSharedCache.h).
