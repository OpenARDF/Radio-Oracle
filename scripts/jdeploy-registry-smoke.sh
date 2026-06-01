#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PACKAGE_NAME="@openardf/radio-oracle"
PACKAGE_VERSION="${1:-}"
SMOKE_DIR="$(mktemp -d)"
SAMPLE_PROJECT="/tmp/radio-oracle-registry-smoke.rom.json"
APP_PROCESS_PATTERN="Radio-Oracle.app/Contents/MacOS/Client4JLauncher $SAMPLE_PROJECT"
JAR_PROCESS_PATTERN="Radio-Oracle-jdeploy.jar $SAMPLE_PROJECT"
RADIO_ORACLE_PID=""

# shellcheck disable=SC2329
cleanup() {
	osascript -e 'tell application "Radio-Oracle" to quit' >/dev/null 2>&1 || true
	if [[ -n "$RADIO_ORACLE_PID" ]]; then
		kill "$RADIO_ORACLE_PID" >/dev/null 2>&1 || true
	fi
	rm -rf "$SMOKE_DIR"
}

trap cleanup EXIT

if [[ -z "$PACKAGE_VERSION" ]]; then
	PACKAGE_VERSION="$(node -p "require('$REPO_ROOT/package.json').version")"
fi

cp "$REPO_ROOT/samples/desktop-smoke.rom.json" "$SAMPLE_PROJECT"

cd "$SMOKE_DIR"
npm install "${PACKAGE_NAME}@${PACKAGE_VERSION}"
./node_modules/.bin/radio-oracle "$SAMPLE_PROJECT" >/tmp/radio-oracle-registry-smoke.out 2>/tmp/radio-oracle-registry-smoke.err &
RADIO_ORACLE_PID="$!"

for _ in {1..180}; do
	if pgrep -f "$APP_PROCESS_PATTERN" >/dev/null || pgrep -f "$JAR_PROCESS_PATTERN" >/dev/null; then
		echo "Radio-Oracle registry jDeploy smoke OK for ${PACKAGE_NAME}@${PACKAGE_VERSION}"
		exit 0
	fi
	if ! kill -0 "$RADIO_ORACLE_PID" >/dev/null 2>&1; then
		break
	fi
	sleep 1
done

echo "ERROR: Radio-Oracle did not start from registry package ${PACKAGE_NAME}@${PACKAGE_VERSION}." >&2
echo "--- stdout ---" >&2
sed -n '1,80p' /tmp/radio-oracle-registry-smoke.out >&2 || true
echo "--- stderr ---" >&2
sed -n '1,120p' /tmp/radio-oracle-registry-smoke.err >&2 || true
exit 1
