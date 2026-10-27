#!/usr/bin/env bash
set -euo pipefail

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

if ! ./gradlew :sample:connectedDebugAndroidTest :glasmio-ui:connectedDebugAndroidTest --continue --console=plain; then
    mkdir -p sample/build/reports/androidTests
    adb shell dumpsys window > sample/build/reports/androidTests/window-state.txt
    adb exec-out screencap -p > sample/build/reports/androidTests/device-failure.png
    adb logcat -d -t 500 > sample/build/reports/androidTests/logcat.txt
    exit 1
fi
