#!/usr/bin/env bash
set -euo pipefail

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard

mkdir -p sample/build/reports/androidTests
text_trace_report=sample/build/reports/androidTests/text-selection-logcat.txt
adb logcat -v threadtime -s GlasmioTextTrace:D '*:S' > "$text_trace_report" 2>&1 &
text_trace_pid=$!
trap 'kill "$text_trace_pid" 2>/dev/null || true' EXIT

if ! ./gradlew :sample:connectedDebugAndroidTest :glasmio-ui:connectedDebugAndroidTest --console=plain; then
    adb shell dumpsys window > sample/build/reports/androidTests/window-state.txt
    adb exec-out screencap -p > sample/build/reports/androidTests/device-failure.png
    adb logcat -d -t 500 > sample/build/reports/androidTests/logcat.txt
    kill "$text_trace_pid" 2>/dev/null || true
    wait "$text_trace_pid" 2>/dev/null || true
    cat "$text_trace_report"
    exit 1
fi
