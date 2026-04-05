#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

DEFAULT_CLASSES="com.websmithing.gpstracker2.MainActivitySmokeTest,com.websmithing.gpstracker2.service.TrackingBufferStoreContractTest,com.websmithing.gpstracker2.repository.upload.UploadRepositorySocketContractTest"
TEST_CLASSES="${SMOKE_TEST_CLASSES:-$DEFAULT_CLASSES}"
ADB_SERIAL="${ADB_SERIAL:-emulator-5554}"
BOOT_TIMEOUT_SECONDS="${BOOT_TIMEOUT_SECONDS:-120}"

usage() {
  cat <<EOF
Usage: $(basename "$0") [--list-devices] [--prepare-only] [--help]

Runs the same Android connected smoke suite that GitHub Actions uses.

Options:
  --list-devices   Print adb devices and exit.
  --prepare-only   Wait for the emulator, unlock it, and disable animations; do not run Gradle.
  --help           Show this help.

Environment overrides:
  ADB_SERIAL            Target adb serial. Default: ${ADB_SERIAL}
  BOOT_TIMEOUT_SECONDS  Emulator boot wait timeout. Default: ${BOOT_TIMEOUT_SECONDS}
  SMOKE_TEST_CLASSES    Instrumentation classes to run.

Examples:
  $(basename "$0")
  ADB_SERIAL=emulator-5556 $(basename "$0")
  SMOKE_TEST_CLASSES=com.websmithing.gpstracker2.MainActivitySmokeTest $(basename "$0")
EOF
}

resolve_adb() {
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]] && [[ -x "${ANDROID_SDK_ROOT}/platform-tools/adb" ]]; then
    echo "${ANDROID_SDK_ROOT}/platform-tools/adb"
    return
  fi

  if [[ -n "${ANDROID_HOME:-}" ]] && [[ -x "${ANDROID_HOME}/platform-tools/adb" ]]; then
    echo "${ANDROID_HOME}/platform-tools/adb"
    return
  fi

  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return
  fi

  echo "adb was not found. Install Android platform-tools or set ANDROID_SDK_ROOT." >&2
  exit 1
}

ADB_BIN="$(resolve_adb)"

list_devices() {
  "${ADB_BIN}" devices -l
}

wait_for_boot() {
  local started_at now boot_completed
  started_at="$(date +%s)"

  echo "Waiting for ${ADB_SERIAL} to come online..."
  while true; do
    if "${ADB_BIN}" -s "${ADB_SERIAL}" get-state >/dev/null 2>&1; then
      break
    fi

    now="$(date +%s)"
    if (( now - started_at >= BOOT_TIMEOUT_SECONDS )); then
      echo "Timed out waiting for ${ADB_SERIAL} to appear in adb after ${BOOT_TIMEOUT_SECONDS}s." >&2
      echo "Tip: start your dedicated emulator first, or set ADB_SERIAL to the correct device." >&2
      exit 1
    fi
    sleep 2
  done

  while true; do
    if "${ADB_BIN}" -s "${ADB_SERIAL}" get-state >/dev/null 2>&1; then
      boot_completed="$("${ADB_BIN}" -s "${ADB_SERIAL}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
      if [[ "${boot_completed}" == "1" ]]; then
        break
      fi
    fi

    now="$(date +%s)"
    if (( now - started_at >= BOOT_TIMEOUT_SECONDS )); then
      echo "Timed out waiting for ${ADB_SERIAL} to boot after ${BOOT_TIMEOUT_SECONDS}s." >&2
      exit 1
    fi
    sleep 2
  done
}

prepare_device() {
  wait_for_boot

  echo "Unlocking ${ADB_SERIAL} and disabling animations..."
  "${ADB_BIN}" -s "${ADB_SERIAL}" shell input keyevent 82 || true
  "${ADB_BIN}" -s "${ADB_SERIAL}" shell settings put global window_animation_scale 0.0
  "${ADB_BIN}" -s "${ADB_SERIAL}" shell settings put global transition_animation_scale 0.0
  "${ADB_BIN}" -s "${ADB_SERIAL}" shell settings put global animator_duration_scale 0.0
}

run_gradle_smoke() {
  cd "${PROJECT_DIR}"
  ./gradlew --no-daemon :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=${TEST_CLASSES}"
}

PREPARE_ONLY=false

case "${1:-}" in
  --help|-h)
    usage
    exit 0
    ;;
  --list-devices)
    list_devices
    exit 0
    ;;
  --prepare-only)
    PREPARE_ONLY=true
    ;;
  "")
    ;;
  *)
    echo "Unknown option: $1" >&2
    usage
    exit 1
    ;;
esac

echo "Using adb: ${ADB_BIN}"
echo "Target device: ${ADB_SERIAL}"
echo "Smoke classes: ${TEST_CLASSES}"

prepare_device

if [[ "${PREPARE_ONLY}" == "true" ]]; then
  echo "Device preparation completed."
  exit 0
fi

run_gradle_smoke

echo
echo "Connected test report:"
echo "${PROJECT_DIR}/app/build/reports/androidTests/connected/debug/index.html"
