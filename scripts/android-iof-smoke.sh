#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
adb_bin="${ANDROID_ADB:-adb}"
package_name="org.openardf.radiooracle"
apk_path="$repo_root/app/build/outputs/apk/debug/app-debug.apk"
schema_path="${2:-${IOF_SCHEMA_PATH:-$repo_root/../IOF-XML-datastandard-v3/IOF.xsd}}"
official_examples_dir="$repo_root/../IOF-XML-datastandard-v3/examples"
generated_fixture_dir="$repo_root/build/iof-smoke-fixtures"
legacy_start_number_fixture="$generated_fixture_dir/legacy-start-number.xml"

if [[ ! -f "$apk_path" ]]; then
	echo "Debug APK not found: $apk_path" >&2
	echo "Run ./gradlew :app:assembleDebug first, or use: just android-iof-smoke" >&2
	exit 1
fi

if ! command -v "$adb_bin" >/dev/null 2>&1 && [[ ! -x "$adb_bin" ]]; then
	echo "adb not found: $adb_bin" >&2
	echo "Set ANDROID_ADB=/path/to/adb or add adb to PATH." >&2
	exit 1
fi

serial_arg="${1:-}"
devices=()
if [[ -n "$serial_arg" ]]; then
	devices+=("$serial_arg")
else
	while IFS=$'\t' read -r serial state; do
		if [[ "$state" == "device" ]]; then
			devices+=("$serial")
		fi
	done < <("$adb_bin" devices | awk 'NR > 1 && NF >= 2 { print $1 "\t" $2 }')
fi

if [[ "${#devices[@]}" -eq 0 ]]; then
	echo "No authorized Android devices found." >&2
	exit 1
fi

xmllint_bin=""
if [[ -f "$schema_path" ]]; then
	if [[ -n "${XMLLINT:-}" ]]; then
		xmllint_bin="$XMLLINT"
	elif command -v xmllint >/dev/null 2>&1; then
		xmllint_bin="$(command -v xmllint)"
	fi
fi

stage_file() {
	local serial="$1"
	local src="$2"
	local name="$3"
	local app_input_dir="$4"
	local download_dir="$5"
	local tmp="/data/local/tmp/radiooracle-iof-smoke-$name"

	"$adb_bin" -s "$serial" push "$src" "$tmp" >/dev/null
	"$adb_bin" -s "$serial" shell run-as "$package_name" cp "$tmp" "$app_input_dir/$name"
	"$adb_bin" -s "$serial" shell rm -f "$tmp"
	"$adb_bin" -s "$serial" push "$src" "$download_dir/$name" >/dev/null
}

for serial in "${devices[@]}"; do
	echo "== $serial =="
	"$adb_bin" -s "$serial" install -r "$apk_path" >/dev/null

	app_input_arg="iof-smoke-input"
	app_output_arg="iof-smoke-output"
	app_input_dir="files/$app_input_arg"
	app_output_dir="files/$app_output_arg"
	download_dir="/sdcard/Download/RadioOracleIofSmoke"
	"$adb_bin" -s "$serial" shell run-as "$package_name" rm -rf "$app_input_dir" "$app_output_dir"
	"$adb_bin" -s "$serial" shell run-as "$package_name" mkdir -p "$app_input_dir" "$app_output_dir"
	"$adb_bin" -s "$serial" shell "mkdir -p '$download_dir'"

	stage_file "$serial" "$repo_root/app/src/main/resources/xml/xml_category_valid_example.xml" "course.xml" "$app_input_dir" "$download_dir"
	stage_file "$serial" "$repo_root/app/src/main/resources/xml/xml_startlist_example.xml" "start.xml" "$app_input_dir" "$download_dir"
	stage_file "$serial" "$repo_root/app/src/main/resources/xml/xml_results_example.xml" "results.xml" "$app_input_dir" "$download_dir"

	if [[ -f "$schema_path" ]]; then
		mkdir -p "$generated_fixture_dir"
		sed 's/<BibNumber>1001<\/BibNumber>/<StartNumber>1001<\/StartNumber>/' \
			"$repo_root/app/src/main/resources/xml/xml_startlist_example.xml" >"$legacy_start_number_fixture"
		stage_file "$serial" "$schema_path" "IOF.xsd" "$app_input_dir" "$download_dir"
		stage_file "$serial" "$legacy_start_number_fixture" "legacy-start-number.xml" "$app_input_dir" "$download_dir"
	fi

	if [[ -d "$official_examples_dir" ]]; then
		stage_file "$serial" "$official_examples_dir/CourseData_Individual_Step2.xml" "official-CourseData_Individual_Step2.xml" "$app_input_dir" "$download_dir"
		stage_file "$serial" "$official_examples_dir/StartList_Individual_Step3.xml" "official-StartList_Individual_Step3.xml" "$app_input_dir" "$download_dir"
		stage_file "$serial" "$official_examples_dir/ResultList1.xml" "official-ResultList1.xml" "$app_input_dir" "$download_dir"
	fi

	broadcast_args=(
		-n "$package_name/.backend.commands.AppCommandReceiver"
		-a org.openardf.radiooracle.command.RUN_IOF_XML_SMOKE
		--es fixture_dir "$app_input_arg"
		--es output_dir "$app_output_arg"
		--ez keep_event true
	)
	# Android's platform XML stack may not provide W3C XSD validation. Keep this opt-in
	# until Radio-Oracle ships an Android-compatible schema validator.
	if [[ -f "$schema_path" && "${ANDROID_IOF_APP_SCHEMA_VALIDATION:-}" == "1" ]]; then
		broadcast_args+=(--es iof_schema_file IOF.xsd)
	fi

	"$adb_bin" -s "$serial" shell am broadcast "${broadcast_args[@]}" >/dev/null

	for _ in {1..20}; do
		if "$adb_bin" -s "$serial" shell run-as "$package_name" test -f "$app_output_dir/smoke-summary.txt"; then
			break
		fi
		sleep 1
	done

	host_dir="$repo_root/build/iof-smoke/$serial"
	rm -rf "$host_dir"
	mkdir -p "$host_dir/output"
	for name in smoke-summary.txt exported-start-list.xml exported-result-list.xml; do
		"$adb_bin" -s "$serial" exec-out run-as "$package_name" cat "$app_output_dir/$name" >"$host_dir/output/$name"
	done

	cat "$host_dir/output/smoke-summary.txt"
	echo "staged manual picker files: $download_dir"
	echo "pulled smoke outputs: $host_dir/output"

	if [[ -n "$xmllint_bin" ]]; then
		"$xmllint_bin" --noout --schema "$schema_path" "$host_dir/output/exported-start-list.xml"
		"$xmllint_bin" --noout --schema "$schema_path" "$host_dir/output/exported-result-list.xml"
	elif [[ -f "$schema_path" ]]; then
		echo "xmllint not found; skipped pulled export schema validation." >&2
	else
		echo "IOF schema not found at $schema_path; skipped pulled export schema validation." >&2
	fi

	"$adb_bin" -s "$serial" push "$host_dir/output/exported-start-list.xml" "$download_dir/automated-exported-start-list.xml" >/dev/null
	"$adb_bin" -s "$serial" push "$host_dir/output/exported-result-list.xml" "$download_dir/automated-exported-result-list.xml" >/dev/null
done
