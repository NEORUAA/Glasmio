# Native navigation motion

The reference is the local iOS 27.0 simulator runtime, build `24A434`, arm64.
UIKitCore was inspected with IDA Professional 9.4 / Hex-Rays through IDA Pro MCP.
Addresses below are image virtual addresses without ASLR. Local research files
are in the ignored workspace directory `.local-research/ios/navigation/ida/`;
no Apple binaries are distributed here.

## Body geometry

`_UINavigationParallaxTransition` constructs separate foreground, underlay and
dimming containers. Its ordinary transition has no gap between pages.

| Native entry | Result |
| --- | --- |
| `parallaxOffset`, `0x1c40fa1a0` | `round(containerWidth * 0.7)` in ordinary navigation. |
| Geometry block, `0x1c40fb640` | The underlay displacement is the remaining `width - parallaxOffset`, approximately 30% of the width. The foreground travels the full width. RTL reverses signs. |
| `gapBetweenViews`, `0x1c40fa188` | Zero outside the alternate custom-container path. |
| `_UIParallaxDimmingView.initWithFrame:overrideDimmingColor:`, `0x1c40fe0dc` | Black with alpha `0.1`, loaded from `0x1c5717f68`. `backgroundDimmingAmount` scales its alpha. |
| `_UIParallaxTransitionCardView.updateProperties`, `0x1c54ab854` | Rounded cards derive all four corners from their enclosing window. The source is not a one-sided MIUI corner clip. |

The independent UIKit probe on a disposable iPhone 18 Pro simulator confirmed
`402x874 pt @3`: foreground `402 -> 0`, underlay `0 -> -121 pt`, with no scale or
size change. Forced RTL reverses both translations. The card's observed corner
radius is zero; its black shadow has opacity `0.04`, radius `12 pt`, and offset
`(0,-3) pt`. On Android, the exposed foreground edge follows the actual device
window: each physical corner radius comes from `WindowInsets.getRoundedCorner`,
converted from pixels to dp. The shadow uses the same continuous corner shape.
Physical corners retain their positions in RTL. This adapts the native enclosing
window behavior to the user's device instead of hardcoding the simulator's zero.

Repeated warm pop measurements take approximately `0.49-0.50 s` from willShow to
didShow, despite the `0.35 s` coordinator declaration. Ordinary motion uses the
physical spring until it settles, without normalizing or truncating it at 350 ms.
The 159-frame capture, warm repeat, RTL repeat and probe sources are retained in
`.local-research/ios/navigation/probe/`. Underlay displacement agrees with the measured
foreground coverage within `0.133 pt`; dimming opacity differs by less than
`0.004` including endpoint snapping. Interactive release was analyzed in IDA;
these runtime captures cover noninteractive push/pop.

For logical forward progress `p`, foreground x is `(1-p)*width` and underlay x is
`-p*(width-round(width*0.7))`. Pop reverses this relationship. The underlay is
dimmed proportionally to its covered state. Headers retain their independently
anchored glass groups and the existing native header morph tracks.

## Springs and coordinator duration

`_UIFluidParallaxTransitionSettings.setDefaultValues` (`0x1c541106c`, implementation
`0x1c5410f44`) configures:

| Channel | Damping ratio | Response |
| --- | ---: | ---: |
| Noninteractive | 1 | 0.3 s |
| Interactive release | 0.85 | 0.3 s |
| Interactive tracking | 0.85 | 0.08 s |

The noninteractive retarget impulse is `0.02`. `tracksImmediatelyWithoutSpring`
defaults to false. Rubber-band extent is `0.5`. The edge-flick boost is `4.25`,
with a normalized velocity threshold of `1.5` widths/second.

The default sliding coordinator duration (`0x1c40fa124`, storage
`0x22e89d9a0`) is `0.35 s`. This is a coordinator parameter, not the response
or a command to truncate every spring at 350 milliseconds. Spring response uses
`omega = 2*pi/response`, stiffness `omega^2` with unit mass.

## Interactive completion

`_UINavigationInteractiveTransitionBase._updateStatistics:firstSample:finalSample:`
(`0x1c4c3b0b0`) samples position and velocity normalized by container width. It
retains a three-sample rolling average of velocity and acceleration. Successive
acceleration changes are limited to 5 normalized units. Samples closer than 1 ms
are ignored; below 10 ms a changed position is required.

`popGesture:withRemainingDuration:shouldPopWithVelocity:` (`0x1c4c3b46c`) projects:

```text
projected = progress + duration*averageVelocity
                     + 0.5*duration^2*averageAcceleration
complete if projected > min(187.5 / widthInPoints, 0.5)
```

This branch requires at least three samples and an active drag. Shorter gestures
fall back to the direction of average velocity. The special boost belongs to an
eligible edge flick, not an arbitrary full-page pan.

## Gesture ownership

The native `_UIParallaxTransitionPanGestureRecognizer` dynamically coordinates
with scroll views, swipe actions and other recognizers (`0x1c40f9ab0`). A page
pan therefore cannot take every horizontal move before child controls see it.

Glasmio supplies a shared exclusion registry in `glasmio-core`. Interactive
controls register live window-space bounds; application-owned pagers, charts and
embedded views can use `Modifier.swipeBackExcluded()`. Ordinary clickable rows
remain valid places to start a page swipe. Ownership is decided for each pointer
sequence and does not transfer back to navigation after a child drag begins.

The page host observes child movement consumption before claiming a pan. A
consumed cancellation release, multiple pointers, a long press, wrong-direction
or vertical intent, and a changed navigation entry cancel that sequence. Normal
release includes the final pointer sample before evaluating completion.
