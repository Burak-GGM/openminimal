#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
if [[ -f "$project_dir/local.properties" ]]; then
    while IFS= read -r line; do
        case "$line" in sdk.dir=*) sdk_dir="${line#sdk.dir=}" ;; esac
    done < "$project_dir/local.properties"
fi
avd_name="${1:-Pixel_9a}"
if [[ $# -gt 0 ]]; then shift; fi
if [[ ! -x "$sdk_dir/emulator/emulator" ]]; then
    echo "Android emulator is missing from $sdk_dir/emulator." >&2
    exit 1
fi
# Use a numeric skin: the old Pixel_9a AVD references an absent Studio skin.
# Do not wipe data or reuse snapshots from an older emulator/system image.
exec "$sdk_dir/emulator/emulator" -avd "$avd_name" -no-snapshot -no-boot-anim \
    -gpu swiftshader -no-audio -skin 1080x2424 "$@"
