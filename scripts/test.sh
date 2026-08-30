#!/usr/bin/env bash
# Run every test suite the release gate cares about.
#   ./scripts/test.sh            # backend (always) + android (if the SDK is present)
#   ./scripts/test.sh backend    # backend only
#   ./scripts/test.sh android    # android only
set -euo pipefail
cd "$(dirname "$0")/.."

what="${1:-all}"
fail=0

run() { echo; echo "━━ $1"; shift; "$@" || { echo "  ✗ FAILED"; fail=1; }; }

if [[ "$what" == all || "$what" == backend ]]; then
  # stdlib domain suite — must pass with a bare interpreter
  run "backend · stdlib pytest"  python3 -m pytest -q
  # full stack — needs fastapi + httpx (the venv, or an install with [test])
  if [[ -x .venv/bin/python ]]; then
    run "backend · venv pytest"  .venv/bin/python -m pytest -q
  else
    run "backend · pytest [test]" python3 -m pytest -q
  fi
fi

if [[ "$what" == all || "$what" == android ]]; then
  if [[ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" && -x android/gradlew ]]; then
    run "android · unit"            android/gradlew -p android :app:testDebugUnitTest -q
    run "android · lintVitalRelease" android/gradlew -p android :app:lintVitalRelease -q
  elif [[ "$what" == android ]]; then
    echo "  ! ANDROID_HOME unset or android/gradlew missing — cannot run android tests"; fail=1
  else
    echo; echo "━━ android · skipped (no SDK)"
  fi
fi

echo
[[ $fail -eq 0 ]] && echo "✓ all requested suites passed" || { echo "✗ one or more suites failed"; exit 1; }
