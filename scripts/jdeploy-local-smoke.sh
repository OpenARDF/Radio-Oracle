#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_BUNDLE="$HOME/Applications/Radio-Oracle.app"
SAMPLE_PROJECT="/tmp/radio-oracle-desktop-smoke.rom.json"
PROCESS_PATTERN="Radio-Oracle.app/Contents/MacOS/Client4JLauncher $SAMPLE_PROJECT"

# shellcheck disable=SC2329
cleanup() {
	osascript -e 'tell application "Radio-Oracle" to quit' >/dev/null 2>&1 || true
}

trap cleanup EXIT

cd "$REPO_ROOT"

npm run jdeploy:install-local
npm run jdeploy:verify-install

cp samples/desktop-smoke.rom.json "$SAMPLE_PROJECT"

open -n "$APP_BUNDLE" --args "$SAMPLE_PROJECT"

for _ in {1..10}; do
	if pgrep -f "$PROCESS_PATTERN" >/dev/null; then
		echo "Radio-Oracle local jDeploy smoke OK"
		exit 0
	fi
	sleep 1
done

echo "ERROR: Radio-Oracle did not start from local jDeploy install." >&2
exit 1
