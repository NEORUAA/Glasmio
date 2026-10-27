# Native menu motion reference

The menu branches are based on IDA Professional 9.4 and Hex-Rays analysis of the
iOS 27.0 simulator image, build 24A434. The original images are read-only. Extracted
UIKitCore and AnimationKit retain their virtual addresses; SpringBoard.framework
uses image base zero.

| Owning implementation | Entry | Behavior |
| --- | --- | --- |
| SpringBoard `SBMenuBarMainMenuView` | `0x632774` | Obtains the superclass menu style, then sets `shouldMenuOverlapSourcePreview` to false at `0x63281c`. |
| UIKit `_UIContextMenuLiquidMorphPresentationAnimation` | `0x1c54ea49c` | Tests `sourcePreviewMorphsToMenu` at `0x1c54eb090`. The overlap path uses `morph(from:to:through:)`; the separate menu path uses `morph(to:parameters:alongsideAnimations:completion:)` at `0x1c54eb3c4`. |
| UIKit attachment preview | `0x1c54ec21c` | The separate menu path can create a 10 × 10 point preview with radius 5, positioned in the menu attachment coordinate space. |
| UIKit dismissed menu layout | `0x1c54ec6b0` | Outside Reduce Motion, preserves the compact menu bounds and position, sets transform scale to 0.2 and alpha to zero. |
| AnimationKit `LiquidMorphAnimation.morph(to:)` | `0x1de4095d4` | Uses the shared item engine at `0x1de410324`. UIKit passes no kick, disables intermediate shapes, and enables position match-move for dismissal. |
| AnimationKit `MorphAnimationSettings.setDefaultValues` | `0x1de43594c` | Sets the regular content spring to damping ratio 0.8 and response 0.3 seconds, then copies it to width, height, position X/Y and transform. |
| AnimationKit content application | `0x1de41fe9c` → `0x1de41a03c` | Runs content morphing and writes alpha at `0x1de41a1b0`; the alpha protocol setter is confirmed by `0x1de432180` using slot `0x60`. |

Response is converted to a unit-mass spring as `stiffness = (2π / response)²`.
Popup lifetime follows physical completion so interrupted dismissal can retain its
current geometry and velocity. The foreground recorder retains the source content
until the return animation finishes.

The SpringBoard menu-bar style is one caller's explicit choice. It does not
establish the default overlap behavior of every UIButton Pop-up Button. Material,
source geometry and the selected animation branch must be checked together.

## Pop-up Button branch

Real taps on a connected iPhone 13 mini running iOS 27.2, build 24B119, were checked
with stock gray and plain UIButton configurations. Both used
`showsMenuAsPrimaryAction` and `changesSelectionAsPrimaryAction`. Both reported
`sourcePreviewMorphsToMenu = true`, no attachment preview, V2 enabled and speed 0.7.
The live settings were eject 0.75/0.5, absorb 0.8/0.7, blur-in 1/0.3 and blur-out
0.8/0.45 (damping ratio/response). These are the existing V2 physical channels.

The closing 0.2 transform and zero alpha belong to the menu parent. Its root list
keeps its resting bounds, identity transform and alpha 1. They must not be applied
again to the list contents. The measured source-preview flag selects the overlap
branch in the analyzed UIKit code; the private Swift overload was not directly
intercepted on the device.

Glasmio therefore defaults both Pop-up Buttons and Cascading Menus to the shared V2
source-preview implementation. A standalone trigger uses itself as its through
preview; a toolbar trigger retains its grouped source and tracking behavior. Row
anchors record their foreground into a GPU layer, while glass anchors record only
their foreground after the material passes. Explicit `morphFromAnchor = false`
uses the separate compact-menu spring and fixed anchor.


Plain Pop-up labels transfer only their foreground pixels. Their source shape is not
included in the glass contour; the incoming menu material follows the native content
progress, leaving no outline or shadow around the restored text. Glass buttons keep
their source material. Selection changes refresh the retained foreground and retarget
the closing source bounds without restarting the physical channels.


The physical contour uses geometric field tangents at its certified boundary samples.
Uniform polar sampling gives unequal physical segment lengths on tall menus; fitting
uniform Catmull–Rom controls can skew a corner's tangent despite subpixel position
error. Each cubic therefore follows the local zero-field tangent and its own chord.
The scalar field, optical normals, native spring channels and target radii are retained.

Plain-source material opacity records into an explicit graphics layer that includes
the material's shadow support, rather than a menu-sized canvas alpha layer. The
transparent Popup shell preserves that backing outside the visible menu; placement
constrains the visible menu and compensates the shell separately for each direction.

Glass anchors retain their source material and any explicit frost override. During
the morph, the source exterior shadow fades into the menu shadow and returns before
the live anchor is revealed. The source pass uses the static renderer's full resolution;
default navigation retains its 15 dp / 2% shadow, while an Edit Menu toolbar retains
Regular Medium. Backdrop frost follows the same material progress between the source
radius and the menu's 16 dp radius without recreating the source capture. The native
source, foreground and portal blur channels remain independent.

Source anchors also retain their actual ordered material-paint operations. Their tint
contributes with `1 - contentProgress` while the menu tint contributes with
`contentProgress`. Each fill keeps its original blend mode against the sampled
backing, including Plus, Luminosity, custom tints and Prominent overlays; no
transparent alpha layer changes those operations at the handoff.

The source also declares whether its static surface uses shadow lifting and inner
shadows. Their coverage interpolates to the menu rather than switching at the last
frame. Prominent and custom surfaces therefore retain their own optical backing
instead of inheriting navigation-only treatment before the live anchor returns.
