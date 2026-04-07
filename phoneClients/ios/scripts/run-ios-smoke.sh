#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DERIVED_DATA_PATH="${DERIVED_DATA_PATH:-$ROOT_DIR/.build/DerivedData}"
RESULTS_DIR="${RESULTS_DIR:-$ROOT_DIR/.build/TestResults}"
UNIT_RESULT_BUNDLE_PATH="${UNIT_RESULT_BUNDLE_PATH:-$RESULTS_DIR/GPSTracker-unit.xcresult}"
UI_RESULT_BUNDLE_PATH="${UI_RESULT_BUNDLE_PATH:-$RESULTS_DIR/GPSTracker-ui.xcresult}"
DESTINATION="${DESTINATION:-platform=iOS Simulator,name=iPhone 16}"
ENABLE_CODE_COVERAGE="${ENABLE_CODE_COVERAGE:-YES}"
UNIT_TEST_ATTEMPTS="${UNIT_TEST_ATTEMPTS:-3}"
UI_TEST_ATTEMPTS="${UI_TEST_ATTEMPTS:-4}"

mkdir -p "$RESULTS_DIR"
rm -rf "$UNIT_RESULT_BUNDLE_PATH" "$UI_RESULT_BUNDLE_PATH"

common_args=(
  -project "$ROOT_DIR/GpsTracker.xcodeproj"
  -scheme GPSTracker
  -destination "$DESTINATION"
  -derivedDataPath "$DERIVED_DATA_PATH"
  -parallel-testing-enabled NO
  -parallel-testing-worker-count 1
  -maximum-concurrent-test-simulator-destinations 1
)

if [[ "$ENABLE_CODE_COVERAGE" == "YES" ]]; then
  common_args+=(-enableCodeCoverage YES)
fi

prepare_simulator() {
  local destination_name="${DESTINATION##*name=}"
  destination_name="${destination_name%%,*}"

  if [[ -z "$destination_name" || "$destination_name" == "$DESTINATION" ]]; then
    return
  fi

  xcrun simctl boot "$destination_name" >/dev/null 2>&1 || true
  xcrun simctl bootstatus "$destination_name" -b >/dev/null 2>&1 || true
}

run_tests_with_retry() {
  local result_bundle_path="$1"
  local attempts="$2"
  shift 2

  local attempt=1
  while true; do
    rm -rf "$result_bundle_path"
    prepare_simulator

    if xcodebuild \
      "${common_args[@]}" \
      -resultBundlePath "$result_bundle_path" \
      test \
      "$@"; then
      return 0
    fi

    if (( attempt >= attempts )); then
      return 1
    fi

    echo "Retrying xcodebuild test run (${attempt}/${attempts}) after simulator/test runner failure..."
    xcrun simctl shutdown all >/dev/null 2>&1 || true
    sleep 2
    attempt=$((attempt + 1))
  done
}

run_tests_with_retry "$UNIT_RESULT_BUNDLE_PATH" "$UNIT_TEST_ATTEMPTS" \
  -only-testing:GPSTrackerTests

run_tests_with_retry "$UI_RESULT_BUNDLE_PATH" "$UI_TEST_ATTEMPTS" \
  -only-testing:GPSTrackerUITests/GPSTrackerUITests
