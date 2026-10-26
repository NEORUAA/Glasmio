# Changelog

## 0.1.0 — 2026-10-27

Initial Android Compose framework extraction from MeiloX.

- Introduce Core, Glass, UI, Scaffold, Preference, and Icons modules with one-way dependencies.
- Add light/dark themes, host-configurable colors and typography, and continuous rounded shapes across the component system.
- Preserve backdrop sampling, liquid refraction, press highlights, shadows, navigation contact effects, and menu morph rendering.
- Provide buttons, switches, sliders, segmented controls, text/search fields, steppers, cards, lists, toolbars, navigation bars, menus, alerts, sheets, a color picker, and reorderable groups.
- Add collapsing large-title pages, lazy content, overscroll, pull-refresh rebound, insets, and generic anchor-based sheet state.
- Separate preference presentation from persistence and symbol rendering from application resources.
- Provide one root-level SF symbol font provider, a replaceable icon resolver, and a local debug font option for the sample.
- Add an independent interactive sample and documentation for component use, backdrop hosting, and migration.
- Unify standalone glass under the navigation renderer with Regular Small, Regular Medium, Regular Large, and Clear scene presets from Apple's Materials page.
- Preserve the original calibrated navigation defaults and complete outline, side-shadow, inner-shadow and shadow-lift layers; scene overrides remain opt-in.
- Add a three-tab sample navigation bar with separate search, collapsing titles, progressive edge blur, and Miuix page transitions.
- Use solid catalog backgrounds and the original light/dark iOS 27 wallpaper in glass demonstrations.
- Match the reference text metrics, tracking, control label weights, and continuous corners.
- Configure signed Maven artifacts, sources, API documentation, and automated Central publication.
- Apache-2.0 license with third-party notices included in every published module.

MeiloX remains responsible for music playback, product navigation, application settings, and player-specific rendering.

