# Message balloons and context menus

## Native naming and geometry

iOS 27 ChatKit uses `CKBalloonView`, `CKBalloonLayer`, and `CKBubblePath`. Its path factory
delegates to private `BubbleKit.BubblePath`. These are private Messages components, not
public UIKit controls. Glasmio exposes `GlasmioMessageBubble` and `MessageBubbleShape`.

The Messages refresh flag selects `roundTailed`. The fitted radius is
`min(requestedRadius, width / 2, height / 2.33925)`. The tail occupies `radius * 0.33925`
at the bottom and adds no horizontal inset. A tailless balloon uses the full pill frame.
Continuous corners blend toward circular corners as the body approaches a capsule.
The seven tail cubics and the per-axis corner parameters come from BubbleKit's path
factory; the tail and body form one contour.

Native text insets are 14pt horizontally and 10pt vertically. Native radius is half the
minimum text pill height. Glasmio derives that height from the host body line box and
these insets, including Dynamic Type. Color, content, grouping, and message width remain
host responsibilities.

## Context presentation

Messages supplies a `UIContextMenuConfiguration` with `UITargetedPreview`; ChatKit adds
safe-area reservations for `CKTapbackPickerViewController`. UIKit's rapid presentation
assistant uses response 0.32 / damping 0.8 on entry, 0.35 / 0.85 for action dismissal,
and 0.4 / 0.85 for outside dismissal. The preview lift is capped at 1.15 and 26pt growth
on its longest edge, then fitted to the available viewport. The default Glass metrics
dim the background without enabling the legacy backdrop blur filter.
The semantic dim color is black at 0.20 alpha in the default/light trait and 0.48
in the dark trait. Native UIKit's Glass action list uses a 250pt default width, 32pt corners,
10pt vertical section insets, and a default 42pt single-line row. Its baselines are
27pt from the top, 19pt apart, and 15pt from the bottom (14pt for subtitles).

Glasmio renders the action list with `GlasmioContextMenu`, `GlasmioMenuItem`, and the
shared menu separator and height calculation. Its resting dimensions, material,
typography, icon gutter, and pressed highlight therefore follow the standard Menu
components. The message wrapper owns only preview placement and presentation motion.

The Tapback pill is 64.24pt high at the normal text size, with 44pt cells and a 5pt
gap above the preview. Its 44pt emoji lobe, 15.84pt medium dot, and 7.92pt small dot
hang beside the preview. Each position/size/opacity channel has its own native spring
and delay. `MessageContextMotion` retains those independent channels. The picker and
action list use Glasmio's shared navigation glass material. Native ChatKit selects the
private `messagesTapback` glass subvariant and adapts its BubbleKit filters to transcript
background luminosity; those separate filter coefficients are not part of this mapping.
The accessory's material field is a `NavigationGlassUnionShape`, not the fallback
rounded box of its full layout allocation. Its four primitives retain empty lower
corners and detached dots, while optics are calibrated to the main 64.24pt pill.

The Demo's `Messages` page supplies local editable messages and reaction state. MeiloX
supplies its existing copy action and resource-card callbacks. The six classic reactions
use native ChatKit animation frames, losslessly encoded as animated WebP at 60 FPS. They
play once when the picker appears, retain their final frame, and continue through the
dismissal until the picker is disposed. Custom reactions display the host's emoji.

## Supporting evidence

Research artifacts remain outside source control:

- `.local-research/ios/message-bubble/FINDINGS.md`, `source-receipt.json`, and
  `shape-oracle.json`: native flags, text insets, factory addresses, and six ARM64 path
  fixtures. The Double coordinate comparison precedes Compose's Float conversion.
- `.local-research/ios/message-context/README.md`, `layout-metrics.json`, and `resolved/`:
  ChatKit layout and Tapback spring stages.
- `.local-research/ios/message-context/uikit/`: UIKit preview sizing, Glass metrics,
  and rapid context presentation profiles.
