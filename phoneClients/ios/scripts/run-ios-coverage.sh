#!/bin/zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RESULTS_DIR="${RESULTS_DIR:-$ROOT_DIR/.build/TestResults}"
UNIT_RESULT_BUNDLE_PATH="${UNIT_RESULT_BUNDLE_PATH:-$RESULTS_DIR/GPSTracker-unit.xcresult}"
UI_RESULT_BUNDLE_PATH="${UI_RESULT_BUNDLE_PATH:-$RESULTS_DIR/GPSTracker-ui.xcresult}"
DESTINATION="${DESTINATION:-platform=iOS Simulator,name=iPhone 16}"
RUN_SMOKE="${RUN_SMOKE:-1}"
SUMMARY_PATH="${SUMMARY_PATH:-$RESULTS_DIR/coverage-summary.md}"
UNIT_REPORT_PATH="${UNIT_REPORT_PATH:-$RESULTS_DIR/coverage-unit-report.txt}"
UI_REPORT_PATH="${UI_REPORT_PATH:-$RESULTS_DIR/coverage-ui-report.txt}"

typeset -a unit_checks=(
  "GPSTracker/ViewModel/TrackingViewModel.swift|80"
  "GPSTracker/Model/TrackingRuntimeModels.swift|80"
  "GPSTracker/Service/TrackingBufferStore.swift|80"
  "GPSTracker/Repository/SettingsRepository.swift|90"
  "GPSTracker/Service/UploadEndpoint.swift|95"
  "GPSTracker/Service/WialonIpsService.swift|85"
  "GPSTracker/Utils/SettingsValidation.swift|90"
)

typeset -a ui_checks=(
  "GPSTracker/View/ContentView.swift|80"
  "GPSTracker/View/SettingsView.swift|85"
  "GPSTracker/View/StatsView.swift|60"
)

mkdir -p "$RESULTS_DIR"

if [[ "$RUN_SMOKE" != "0" ]]; then
  ENABLE_CODE_COVERAGE=YES \
  DESTINATION="$DESTINATION" \
  UNIT_RESULT_BUNDLE_PATH="$UNIT_RESULT_BUNDLE_PATH" \
  UI_RESULT_BUNDLE_PATH="$UI_RESULT_BUNDLE_PATH" \
  "$ROOT_DIR/scripts/run-ios-smoke.sh"
fi

xcrun xccov view --report "$UNIT_RESULT_BUNDLE_PATH" > "$UNIT_REPORT_PATH"
xcrun xccov view --report "$UI_RESULT_BUNDLE_PATH" > "$UI_REPORT_PATH"

extract_coverage() {
  local report_path="$1"
  local absolute_target="$2"

  awk -v target="$absolute_target" '
    $1 == target {
      if (match($0, /[0-9]+\.[0-9]+%/)) {
        value = substr($0, RSTART, RLENGTH - 1)
        print value
        found = 1
        exit
      }
    }
    END {
      if (!found) {
        exit 1
      }
    }
  ' "$report_path"
}

bundle_coverage() {
  local report_path="$1"
  local bundle_name="$2"

  awk -v target="$bundle_name" '
    $1 == target {
      if (match($0, /[0-9]+\.[0-9]+%/)) {
        print substr($0, RSTART, RLENGTH - 1)
        exit
      }
    }
  ' "$report_path"
}

passes_threshold() {
  local coverage="$1"
  local threshold="$2"

  awk -v coverage="$coverage" -v threshold="$threshold" 'BEGIN { exit !((coverage + 0) >= (threshold + 0)) }'
}

cat > "$SUMMARY_PATH" <<EOF
# iOS Coverage Gate

| Suite | File | Coverage | Threshold | Result |
| --- | --- | ---: | ---: | --- |
EOF

append_bundle_summary() {
  local label="$1"
  local report_path="$2"
  local app_coverage tests_coverage
  app_coverage="$(bundle_coverage "$report_path" "GPSTracker.app")"
  tests_coverage="$(bundle_coverage "$report_path" "${label}.xctest")"
  if [[ -n "$app_coverage" ]]; then
    printf '\n- %s app coverage: %s%%\n' "$label" "$app_coverage" >> "$SUMMARY_PATH"
  fi
  if [[ -n "$tests_coverage" ]]; then
    printf -- '- %s test target coverage: %s%%\n' "$label" "$tests_coverage" >> "$SUMMARY_PATH"
  fi
}

append_bundle_summary "GPSTrackerTests" "$UNIT_REPORT_PATH"
append_bundle_summary "GPSTrackerUITests" "$UI_REPORT_PATH"
printf '\n' >> "$SUMMARY_PATH"

overall_status=0

run_checks() {
  local suite_name="$1"
  local report_path="$2"
  shift 2
  local check relative_path threshold absolute_path coverage result_mark

  for check in "$@"; do
    IFS='|' read -r relative_path threshold <<< "$check"
    absolute_path="$ROOT_DIR/$relative_path"

    if coverage="$(extract_coverage "$report_path" "$absolute_path")"; then
      if passes_threshold "$coverage" "$threshold"; then
        result_mark="PASS"
      else
        result_mark="FAIL"
        overall_status=1
      fi
    else
      coverage="missing"
      result_mark="FAIL"
      overall_status=1
    fi

    printf '| %s | `%s` | %s%s | %.2f%% | %s |\n' \
      "$suite_name" \
      "$relative_path" \
      "$coverage" \
      "$( [[ "$coverage" == "missing" ]] && print "" || print "%" )" \
      "$threshold" \
      "$result_mark" >> "$SUMMARY_PATH"
  done
}

run_checks "unit" "$UNIT_REPORT_PATH" "${unit_checks[@]}"
run_checks "ui" "$UI_REPORT_PATH" "${ui_checks[@]}"

cat "$SUMMARY_PATH"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$SUMMARY_PATH" >> "$GITHUB_STEP_SUMMARY"
fi

exit "$overall_status"
