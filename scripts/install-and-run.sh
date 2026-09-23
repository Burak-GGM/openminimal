#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
apk="app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$apk" ]]; then
    echo "No debug APK. Run scripts/build-and-run.sh first." >&2
    exit 1
fi
if ! command -v adb >/dev/null; then
    echo "APK ready; adb is unavailable. Device deployment skipped."
    exit 0
fi
devices="$(adb devices -l)"
serial=""
emulators=()
while read -r candidate status details; do
    [[ "$status" == "device" ]] || continue
    if [[ "$candidate" == emulator-* ]]; then
        emulators+=("$candidate")
        continue
    fi
    model="$(adb -s "$candidate" shell getprop ro.product.model | tr -d '\r')"
    device="$(adb -s "$candidate" shell getprop ro.product.device | tr -d '\r')"
    if [[ "$model" == "A065" && "$device" == "Pong" ]]; then
        if [[ -n "$serial" ]]; then
            echo "Multiple Nothing Phone (2) devices found; choose one explicitly." >&2
            exit 1
        fi
        serial="$candidate"
    fi
done <<< "$devices"
if [[ -z "$serial" ]]; then
    if [[ ${#emulators[@]} -eq 1 ]]; then
        serial="${emulators[0]}"
        echo "Nothing Phone (2) unavailable; using Android emulator $serial."
    elif [[ ${#emulators[@]} -gt 1 ]]; then
        echo "Multiple emulators available; choose a device explicitly." >&2
        exit 1
    else
        echo "APK ready; no authorized Nothing Phone (2) or running emulator."
        echo "Start the emulator with: bash scripts/start-emulator.sh Pixel_9a"
        exit 0
    fi
fi
adb -s "$serial" install -r "$apk"
adb -s "$serial" shell am start -n org.openminimal.launcher/.MainActivity
