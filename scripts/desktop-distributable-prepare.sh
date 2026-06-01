#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ -x /usr/libexec/java_home ]]; then
	JAVA_HOME="$(/usr/libexec/java_home -v 17)"
	export JAVA_HOME
elif [[ -z "${JAVA_HOME:-}" ]]; then
	echo "ERROR: Set JAVA_HOME to a full JDK 17 installation." >&2
	exit 1
fi

export PATH="$JAVA_HOME/bin:$PATH"

cd "$REPO_ROOT"

./gradlew :desktopApp:checkRuntime :desktopApp:createDistributable :desktopApp:verifyDesktopDistributable
