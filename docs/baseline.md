# MeiloX migration baseline

Captured on 2026-10-27 before replacing the installed MeiloX application.

## Environment

- Device: `emulator-5580`, HyperOS Phone, model `M610BB`, Android 17 / API 37.
- Main display: 1120 × 2436. SurfaceFlinger display ID `4619827259835644672`.
- Application: `com.neoruaa.meilox`, version 1.55.1 (16).
- Application process: `com.netease.cloudmusic` (the manifest deliberately uses this process name).
- Installed APK SHA-256: `985803684f384a2239bbbfb58644d18f7e8196500112ff9b6870be7c13ae99ca`.
- Source checkout before migration: `ca6fdd7017ec8abdbb46dffc3f480d0466db213a`, clean. The installed APK does not contain the newer `mei_build_id` metadata, so its exact source revision is unavailable.
- All interaction and collection used ADB. Screen recording was performed separately from performance collection.

## Evidence

Local evidence is kept under `build/reports/baseline/` and is intentionally excluded from Git. It includes original-resolution PNG captures for home, library list, settings, popup menu, alert, and color-picker sheet in light and dark modes. `navigation-light.mp4` records navigation between the main tabs. UI hierarchy XML, device fingerprint, package metadata, raw frame statistics, and memory reports accompany the captures.

For repeatable image capture, specify the main display explicitly:

```sh
adb -s emulator-5580 exec-out screencap -d 4619827259835644672 -p > capture.png
```

The device has a second display. Omitting `-d` can prepend a display-selection warning to stdout, which corrupts a redirected PNG.

## Navigation workload

Start on the home tab at the top of its list, with playback paused and the same account data loaded. At the physical display coordinates, tap Library `(550,2256)`, Settings `(731,2256)`, and Home `(185,2256)` three times, waiting 700 ms after each tap. Then perform three pairs of 350 ms vertical swipes between `(600,1750)` and `(600,850)`, waiting 400 ms after each swipe. Collect light and dark separately using `cmd uimode night no` / `yes`.

Before each workload, reset `dumpsys gfxinfo com.netease.cloudmusic reset`. Afterwards save `dumpsys gfxinfo com.netease.cloudmusic framestats` and `dumpsys meminfo com.netease.cloudmusic`.

| Mode | Frames | Janky frames | P50 | P90 | P95 | P99 | Total PSS |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Light | 1324 | 2 (0.15%) | 20 ms | 36 ms | 42 ms | 65 ms | 440,908 KB |
| Dark | 1246 | 2 (0.16%) | 21 ms | 42 ms | 48 ms | 69 ms | 413,645 KB |

These measurements describe this AVD and this workload. Network artwork loading and host load can affect the results. Compare the same workload on the same AVD after migration, keeping recording and mirroring disabled during measurement.
