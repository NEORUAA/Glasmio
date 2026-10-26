# Glasmio

An independent, Android-first Jetpack Compose UI framework inspired by iOS. Glasmio provides Liquid Glass materials, continuous corners, controls, menus, sheets, navigation, page scaffolds, and preferences. The host owns application state, navigation, storage, and fonts.

Version `0.1.0` is in development. Maven Central availability is documented when the release is published.

## Modules

| Artifact | Responsibility |
| --- | --- |
| `glasmio-core` | Theme, color, typography, spacing, continuous shapes, motion tokens |
| `glasmio-glass` | Backdrop recording, refraction, highlights, shadows, contact and menu morph rendering |
| `glasmio-ui` | Stateless controls, toolbars, navigation bars, lists, menus, dialogs, sheets |
| `glasmio-scaffold` | Insets, collapsing titles, lazy pages, overscroll, refresh, sheet state |
| `glasmio-preference` | Caller-owned preference rows and editors |
| `glasmio-icons` | Replaceable symbol resolver and optional host font rendering |
| `sample` | Independent interactive Android component catalog |

## Build

Requires Android SDK 37, JDK 21 or newer, and NDK `27.1.12297006` for the optional native menu field acceleration. Minimum Android version is API 33.

```sh
./gradlew :sample:assembleDebug testDebugUnitTest lintDebug
```

The Gradle wrapper, AGP, Kotlin, and Compose versions match the source application's toolchain. The library has no runtime dependency on MeiloX.

## License

[CPAL-1.0](LICENSE). Third-party dependencies and any host-provided fonts or symbols retain their respective licenses.
