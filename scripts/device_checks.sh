#!/usr/bin/env bash
set -euo pipefail

adb wait-for-device
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

if ! ./gradlew :sample:connectedDebugAndroidTest :glasmio-ui:connectedDebugAndroidTest --continue --console=plain; then
    mkdir -p sample/build/reports/androidTests
    adb shell dumpsys window > sample/build/reports/androidTests/window-state.txt || true
    adb exec-out screencap -p > sample/build/reports/androidTests/device-failure.png || true
    adb logcat -d -t 10000 > sample/build/reports/androidTests/logcat.txt || true
    # Keep the original test failure even if the emulator disconnected during diagnostics.
    exit 1
fi
