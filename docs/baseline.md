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

## Source-controlled extraction comparison

A second comparison used a clean build of MeiloX `ca6fdd7017ec8abdbb46dffc3f480d0466db213a` rather than the initially installed APK. Its manifest records `dirty=false`. The temporary detached checkout was removed after preserving the APK and build log. The migrated snapshot contains MeiloX commits `0c2f7493` (framework integration) and `614205fe` (remaining continuous corners), before the subsequent requested Navigation-material visual change.

| Snapshot | Retained APK | SHA-256 |
| --- | --- | --- |
| Clean source baseline | `/tmp/glasmio-source-baseline-ca6fdd.apk` | `70952fdc1f19e580bb3d8726c3ff8fa614a02d94acb058d692075e0d5bf4b8ea` |
| Migrated snapshot | `/tmp/glasmio-migration-614205.apk` | `1814c92a06cdc547692c849d4314343fa4733b41ad9ecc22ec20c5fe9d0680a0` |

Both APKs are debuggable. The migrated APK was compiled immediately before the continuous-corners commit, so its manifest identifies `0c2f7493` with `dirty=true`; its code includes the changes committed as `614205fe`. These APKs are retained benchmark fixtures, not release packages.

The application was force-stopped before each `adb install -r`. Each mode ran one full navigation workload as warmup, followed by a graphics-statistics reset and an identical measured workload. No Gradle build, mirroring, screen recording, or screenshot capture ran during measurement. Raw reports and artifact hashes are in `build/reports/migration/controlled/`.

| Snapshot | Mode | Frames | P50 | P90 | P95 | P99 | Total PSS |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Clean source | Dark | 1028 | 27 ms | 61 ms | 89 ms | 200 ms | 364,385 KB |
| Migrated | Dark | 942 | 28 ms | 77 ms | 109 ms | 200 ms | 382,168 KB |
| Clean source | Light | 1244 | 21 ms | 46 ms | 57 ms | 85 ms | 426,553 KB |
| Migrated | Light | 1026 | 22 ms | 69 ms | 105 ms | 150 ms | 410,563 KB |

Median latency differs by 1 ms in both modes; the migrated run has higher tail latency in this pair. This first pair left performance acceptance open; the bounded audit below supersedes its warmup/reset method. This HyperOS AVD records `FrameDeadline` 7–19 seconds after `IntendedVsync` in the raw frame data, so its reported zero deadline misses are excluded from acceptance. Host memory pressure also varied substantially during the session. A stable device or a further deterministic profile is needed to attribute the tail difference before making a parity claim.

The migration passed all 793 existing JVM tests and 11 representative device interaction cases, including exact SF symbol optical geometry, native menu-field rendering, menu reversal, toggle gestures, overscroll, tab navigation, and compact expansion. The navigation-only pixel parity case was moved into Glasmio UI and passed there. Production home, list, settings, menu, alert, and sheet captures cover both modes; inspected pairs preserve layout, labels, glyphs, tint inheritance, and sampling.


## Bounded extraction audit with equal compilation setup

One final old/new pair used the same saved APKs, two complete warmup workloads per mode, and a deterministic Home reset before each measured workload. Repeated downward swipes and two consecutive hierarchy reads confirmed the large title at exactly `(48, 344, 256, 479)` for both modes and both APKs. All source installation, hierarchy reads, and compilation finished before statistics were reset.

For both APKs, `cmd package compile -m speed -f com.neoruaa.meilox` returned `Success`. The recorded `pm art dump` output nevertheless reports `[status=verify] [reason=cmdline]` for both. The requested speed AOT was not applied by this ROM; this comparison uses the same observed verification state and equal warmup, rather than claiming an AOT comparison.

| Snapshot | Mode | Frames | P50 | P95 | P99 | Total PSS |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Clean source | Dark | 1170 | 21 ms | 53 ms | 81 ms | 333,353 KB |
| Migrated | Dark | 1146 | 21 ms | 57 ms | 81 ms | 327,278 KB |
| Clean source | Light | 1130 | 21 ms | 53 ms | 77 ms | 357,890 KB |
| Migrated | Light | 1246 | 21 ms | 57 ms | 81 ms | 368,437 KB |

The full-workload median is equal in both modes. P95 differs by 4 ms in each mode, P99 by 0–4 ms, and PSS by −1.8% / +2.9% for dark / light. This bounded result narrows the large tail difference in the earlier run. It does not include the subsequently requested Navigation-material visual change.

The raw rolling frame buffer contains the most recent 120 frames. Its CPU, drawing, and render-thread portions are reported separately below; they cover the final scrolling portion, rather than the entire workload.

| Snapshot / mode | CPU P50 / P95 | Draw P50 / P95 | Render P50 / P95 |
| --- | ---: | ---: | ---: |
| Clean source / Dark | 8.422 / 15.577 ms | 4.025 / 8.912 ms | 16.881 / 28.258 ms |
| Migrated / Dark | 2.359 / 15.088 ms | 0.780 / 9.628 ms | 15.569 / 31.004 ms |
| Clean source / Light | 2.893 / 12.954 ms | 1.280 / 7.446 ms | 16.901 / 25.234 ms |
| Migrated / Light | 4.352 / 17.582 ms | 0.664 / 10.964 ms | 16.666 / 33.395 ms |

CPU uses `SyncQueued − FrameStartTime`, Draw uses `SyncQueued − DrawStart`, and Render uses `SwapBuffers − SyncStart`. The recent-frame distributions vary by mode, including higher migrated light-mode tails. Raw data, compilation output, actual ART state, stable-heading evidence, and parsed P50/P90/P95/P99 values are retained in `build/reports/migration/aot-controlled/`. Deadline-miss counts remain excluded because the raw deadlines are many seconds in the future. No renderer parameters or device-wide ART properties were changed, and the migrated APK and light mode were restored after the pair.

## Supplementary source recording

`build/reports/migration/navigation-final/source-baseline-ca6fdd.mp4` records the clean source baseline in light mode, including Settings, the accent menu, the color-picker sheet, and player expansion/collapse. The 21.39-second H.264 clip is 560 × 1218 and was captured separately from performance collection. An unmodified Home PNG and hierarchy accompany it as `source-baseline-ca6fdd-home-light.*`. The current application was restored with `adb install -r` afterwards, with the same account, paused song, settings, and light mode preserved.

The adjacent `navigation-final.mp4` and `pre-stack-fix-functional.json` document the first Navigation-preset candidate. That candidate passed functional search, player, menu, and sheet checks, but its visual acceptance was superseded by the requested restoration of the original Navigation outline and fine shadows. These files are retained as intermediate evidence rather than the final visual result.
