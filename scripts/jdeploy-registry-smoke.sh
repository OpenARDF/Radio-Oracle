#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PACKAGE_NAME="@openardf/radio-oracle"
PACKAGE_VERSION="${1:-}"
SMOKE_DIR="$(mktemp -d)"
SAMPLE_PROJECT="/tmp/radio-oracle-registry-smoke.rom.json"
PROCESS_PATTERN="Radio-Oracle.app/Contents/MacOS/Client4JLauncher $SAMPLE_PROJECT"

# shellcheck disable=SC2329
cleanup() {
	osascript -e 'tell application "Radio-Oracle" to quit' >/dev/null 2>&1 || true
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

for _ in {1..10}; do
	if pgrep -f "$PROCESS_PATTERN" >/dev/null; then
		echo "Radio-Oracle registry jDeploy smoke OK for ${PACKAGE_NAME}@${PACKAGE_VERSION}"
		exit 0
	fi
	sleep 1
done

echo "ERROR: Radio-Oracle did not start from registry package ${PACKAGE_NAME}@${PACKAGE_VERSION}." >&2
echo "--- stdout ---" >&2
sed -n '1,80p' /tmp/radio-oracle-registry-smoke.out >&2 || true
echo "--- stderr ---" >&2
sed -n '1,120p' /tmp/radio-oracle-registry-smoke.err >&2 || true
exit 1
