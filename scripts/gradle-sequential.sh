#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
lock_dir="${GRADLE_SEQUENTIAL_LOCK_DIR:-$repo_root/.gradle-sequential.lock}"
pid_file="$lock_dir/pid"

cleanup() {
	if [[ -f "$pid_file" ]] && [[ "$(cat "$pid_file")" == "$$" ]]; then
		rm -rf "$lock_dir"
	fi
}

while ! mkdir "$lock_dir" 2>/dev/null; do
	if [[ -f "$pid_file" ]]; then
		lock_pid="$(cat "$pid_file" 2>/dev/null || true)"
		if [[ -n "$lock_pid" ]] && ! kill -0 "$lock_pid" 2>/dev/null; then
			rm -rf "$lock_dir"
			continue
		fi
	fi
	echo "Waiting for another Gradle command to finish..." >&2
	sleep 1
done

echo "$$" >"$pid_file"
trap cleanup EXIT INT TERM

"$repo_root/gradlew" "$@"
