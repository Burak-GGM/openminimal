#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug "$@"
bash scripts/install-and-run.sh
