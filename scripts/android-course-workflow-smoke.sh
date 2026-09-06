#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
adb_bin="${ANDROID_ADB:-adb}"
serial="${1:?Specify one authorized Android device serial}"
package_name="org.openardf.radiooracle"
fixture="$repo_root/desktopApp/build/reports/course-workflow/transfer-input.roseries"
apk="$repo_root/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$fixture" && -f "$apk" ]] || {
	echo "Build the debug APK and run course-workflow-test first." >&2
	exit 1
}
mkdir -p "$repo_root/build"
output="$(mktemp -d "$repo_root/build/course-device-smoke.XXXXXX")"
cp "$fixture" "$output/transfer-input.roseries"
"$adb_bin" -s "$serial" install -r "$apk" >/dev/null
"$adb_bin" -s "$serial" shell run-as "$package_name" mkdir -p files/course-workflow-smoke
"$adb_bin" -s "$serial" shell run-as "$package_name" rm -f files/course-workflow-smoke/complete.json files/course-workflow-smoke/transfer-return-plain.roseries files/course-workflow-smoke/transfer-return-encrypted.roseries
staged="/data/local/tmp/ro-course-workflow-${RANDOM}.roseries"
"$adb_bin" -s "$serial" push "$fixture" "$staged" >/dev/null
"$adb_bin" -s "$serial" shell run-as "$package_name" cp "$staged" files/course-workflow-smoke/transfer-input.roseries
"$adb_bin" -s "$serial" shell rm -f "$staged"
"$adb_bin" -s "$serial" shell am broadcast -n "$package_name/.backend.commands.AppCommandReceiver" -a org.openardf.radiooracle.command.RUN_COURSE_WORKFLOW_SMOKE >/dev/null
completed=false
for _ in {1..45}; do
	if "$adb_bin" -s "$serial" shell run-as "$package_name" test -f files/course-workflow-smoke/complete.json; then
		completed=true
		break
	fi
	sleep 1
done
[[ "$completed" == true ]] || {
	echo "Device course check did not complete. Inspect the app's command log; no pass is claimed." >&2
	exit 1
}
for name in complete.json transfer-return-plain.roseries transfer-return-encrypted.roseries; do
	"$adb_bin" -s "$serial" exec-out run-as "$package_name" cat "files/course-workflow-smoke/$name" >"$output/$name"
done
JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" "$repo_root/scripts/gradle-sequential.sh" :desktopApp:test --tests '*DesktopCourseWorkflowTransferReturnTest' -PcourseWorkflowTransferDirectory="$output"
JAVA_HOME="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" "$repo_root/scripts/gradle-sequential.sh" :desktopApp:desktopAutomation --args="course-workflow-report $output/complete.json $output/round-trip.json"
printf 'Verified device archive outputs: %s\n' "$output"
