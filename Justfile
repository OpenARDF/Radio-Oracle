set shell := ["bash", "-uc"]

java_home := "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
app_bundle := "desktopApp/build/compose/binaries/main/app/Radio-Oracle.app"
debug_log := "${HOME}/Library/Application Support/Radio-Oracle/logs/debug.log"

default:
    @just --list

status:
    git status --short --branch

diff:
    git -c core.pager=delta diff

gradle +args:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh {{args}}

compile:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:compileKotlin

function-size:
    node ./scripts/check-kotlin-function-size.mjs

test:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:test

# Read-only real-series acceptance; exports are written under desktopApp/build/reports.
classic-route-smoke archive baseline sources="":
    RADIO_ORACLE_ROUTE_SMOKE_ARCHIVE={{quote(archive)}} RADIO_ORACLE_ROUTE_SMOKE_BASELINE={{quote(baseline)}} RADIO_ORACLE_ROUTE_SMOKE_SOURCES={{quote(sources)}} JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:cleanTest :desktopApp:test --tests '*DesktopClassicRouteArchiveSmokeTest' --tests '*DesktopPublishedCourseDiagramArchiveSmokeTest'

android-compile:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :app:compileDebugKotlin

android-test filter="":
    @if [ -n "{{filter}}" ]; then \
        JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :app:testDebugUnitTest --tests "{{filter}}"; \
    else \
        JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :app:testDebugUnitTest; \
    fi

android-check: android-compile android-test

android-si-status serial="":
    @ADB="${ANDROID_ADB:-adb}"; \
    if [ -n {{quote(serial)}} ]; then \
        "$ADB" -s {{quote(serial)}} shell am broadcast -a org.openardf.radiooracle.command.SI_STATUS -n org.openardf.radiooracle/.backend.commands.AppCommandReceiver; \
    else \
        "$ADB" shell am broadcast -a org.openardf.radiooracle.command.SI_STATUS -n org.openardf.radiooracle/.backend.commands.AppCommandReceiver; \
    fi

# Read station information through a privately supplied, licensed SPORTident SDK.
sportident-sdk-probe port expected_station:
    dotnet run --project tools/sportident-sdk-probe/SportIdentSdkProbe.csproj -- {{quote(port)}} {{quote(expected_station)}}

# Read one freshly inserted SI-Card8 through the private SDK; never programs a card.
sportident-sdk-card-read port expected_station expected_card:
    dotnet run --project tools/sportident-sdk-probe/SportIdentSdkProbe.csproj -- {{quote(port)}} {{quote(expected_station)}} {{quote(expected_card)}}

# Program one expected SI-Card8 using an explicit local request, then verify by reading back.
sportident-sdk-card-write port expected_station expected_card request:
    dotnet run --project tools/sportident-sdk-probe/SportIdentSdkProbe.csproj -- {{quote(port)}} {{quote(expected_station)}} {{quote(expected_card)}} --write-request {{quote(request)}}

# BCL-only parent/child supervision check; never loads the SDK or opens a serial port.
sportident-sdk-supervision-check:
    dotnet run --project tools/sportident-sdk-supervision-check/SportIdentSupervisionCheck.csproj

# Build an ignored private bridge including its .NET runtime; never stages SDK binaries.
sportident-sdk-local-publish rid="osx-arm64":
    DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1 dotnet publish tools/sportident-sdk-probe/SportIdentSdkProbe.csproj -c Release -r {{quote(rid)}} --self-contained true -p:PublishTrimmed=false -p:PublishSingleFile=false

# Install the private Mac bridge for normal app launches; the license remains at its source path.
sportident-sdk-local-install license_file rid="osx-arm64":
    python3 scripts/install-sportident-local-bridge.py {{quote("tools/sportident-sdk-probe/bin/Release/net10.0/" + rid + "/publish")}} --license-file {{quote(license_file)}}

sportident-sdk-local-check:
    python3 scripts/test-install-sportident-local-bridge.py
    just gradle :desktopApp:test --tests '*DesktopSportIdentLocalBridgeTest' --tests '*DesktopSportIdentProgrammingClientTest' --tests '*DesktopSportIdentOwnerRecoveryStoreTest'

# Capture a fresh native SI-Card8 read; close competing app/SDK connections first.
sportident-owner-capture expected_station expected_card output:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopSportIdentOwnerVerification -PsiOwnerMode=capture {{quote("-PsiOwnerStation=" + expected_station)}} {{quote("-PsiOwnerCard=" + expected_card)}} {{quote("-PsiOwnerOutput=" + output)}}

# Offline comparison of SDK read JSON with native block evidence; never opens a serial port.
sportident-owner-compare sdk_read native_read:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopSportIdentOwnerVerification -PsiOwnerMode=compare {{quote("-PsiOwnerSdkRead=" + sdk_read)}} {{quote("-PsiOwnerNativeRead=" + native_read)}}

# Offline byte-by-byte comparison of two native SI-Card8 reads; never opens a serial port.
sportident-owner-diff-native before_read after_read:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopSportIdentOwnerVerification -PsiOwnerMode=diff-native {{quote("-PsiOwnerBeforeRead=" + before_read)}} {{quote("-PsiOwnerAfterRead=" + after_read)}}

sportident-owner-verification-check:
    just gradle :shared:desktopTest --tests '*SportIdentOwnerReadVerificationTest' :desktopApp:test --tests '*DesktopSportIdentOwnerVerificationTest'

android-course-workflow-smoke serial:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :app:assembleDebug
    ./scripts/android-course-workflow-smoke.sh {{quote(serial)}}

android-iof-smoke serial="" schema_path="":
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :app:assembleDebug
    ./scripts/android-iof-smoke.sh {{quote(serial)}} {{quote(schema_path)}}

iof-schema-check schema_path="":
    @schema={{quote(schema_path)}}; \
    if [ -z "$schema" ]; then \
        schema="${IOF_SCHEMA_PATH:-{{justfile_directory()}}/../IOF-XML-datastandard-v3/IOF.xsd}"; \
    fi; \
    if [ ! -f "$schema" ]; then \
        echo "IOF XML 3.0 schema not found: $schema" >&2; \
        echo "Set IOF_SCHEMA_PATH=/path/to/IOF.xsd or run: just iof-schema-check /path/to/IOF.xsd" >&2; \
        exit 1; \
    fi; \
    cmp -s "$schema" shared/src/commonMain/resources/iof/IOF.xsd || { \
        echo "Bundled shared IOF XML schema differs from $schema" >&2; \
        exit 1; \
    }; \
    cmp -s "$schema" app/src/main/assets/iof/IOF.xsd || { \
        echo "Bundled Android IOF XML schema differs from $schema" >&2; \
        exit 1; \
    }; \
    xmllint_bin="${XMLLINT:-}"; \
    if [ -z "$xmllint_bin" ]; then \
        if command -v xmllint >/dev/null 2>&1; then \
            xmllint_bin="$(command -v xmllint)"; \
        elif [ -x /opt/local/bin/xmllint ]; then \
            xmllint_bin="/opt/local/bin/xmllint"; \
        else \
            echo "xmllint is required for IOF schema validation. Install libxml2 or set XMLLINT=/path/to/xmllint." >&2; \
            exit 1; \
        fi; \
    fi; \
    "$xmllint_bin" --noout --nonet --schema "$schema" app/src/main/resources/xml/xml_startlist_example.xml; \
    "$xmllint_bin" --noout --nonet --schema "$schema" app/src/main/resources/xml/xml_results_example.xml; \
    "$xmllint_bin" --noout --nonet --schema "$schema" app/src/main/resources/xml/xml_category_valid_example.xml; \
    "$xmllint_bin" --noout --nonet --schema "$schema" app/src/main/resources/xml/xml_category_invalid_example.xml; \
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh -PiofSchemaPath="$schema" :shared:desktopTest --tests org.openardf.radiooracle.shared.files.IofXmlValidatorTest; \
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh -PiofSchemaPath="$schema" :app:testDebugUnitTest --tests org.openardf.radiooracle.files.xml.IofXmlSchemaValidationTests --tests org.openardf.radiooracle.files.xml.StartListXmlTests; \
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh -PiofSchemaPath="$schema" :desktopApp:test --tests org.openardf.radiooracle.desktop.DesktopProjectFilesTest.exportsIofStartListXmlFile --tests org.openardf.radiooracle.desktop.DesktopProjectFilesTest.exportsIofResultListXmlFile

android-series-list serial="":
    @ADB="${ANDROID_ADB:-adb}"; \
    if [ -n {{quote(serial)}} ]; then \
        "$ADB" -s {{quote(serial)}} shell am broadcast -a org.openardf.radiooracle.command.LIST_SERIES; \
    else \
        "$ADB" shell am broadcast -a org.openardf.radiooracle.command.LIST_SERIES; \
    fi

android-series-create event_ids name serial="":
    @ADB="${ANDROID_ADB:-adb}"; \
    if [ -n {{quote(serial)}} ]; then \
        "$ADB" -s {{quote(serial)}} shell am broadcast -a org.openardf.radiooracle.command.CREATE_SERIES_FROM_EVENTS --es event_ids {{quote(event_ids)}} --es series_name {{quote(name)}}; \
    else \
        "$ADB" shell am broadcast -a org.openardf.radiooracle.command.CREATE_SERIES_FROM_EVENTS --es event_ids {{quote(event_ids)}} --es series_name {{quote(name)}}; \
    fi

android-series-fingerprint series_id serial="":
    @ADB="${ANDROID_ADB:-adb}"; \
    if [ -n {{quote(serial)}} ]; then \
        "$ADB" -s {{quote(serial)}} shell am broadcast -a org.openardf.radiooracle.command.LOG_SERIES_PACKAGE_FINGERPRINT --es series_id {{quote(series_id)}}; \
    else \
        "$ADB" shell am broadcast -a org.openardf.radiooracle.command.LOG_SERIES_PACKAGE_FINGERPRINT --es series_id {{quote(series_id)}}; \
    fi

android-event-series-fingerprint event_id serial="":
    @ADB="${ANDROID_ADB:-adb}"; \
    if [ -n {{quote(serial)}} ]; then \
        "$ADB" -s {{quote(serial)}} shell am broadcast -a org.openardf.radiooracle.command.LOG_SERIES_PACKAGE_FINGERPRINT --es event_id {{quote(event_id)}}; \
    else \
        "$ADB" shell am broadcast -a org.openardf.radiooracle.command.LOG_SERIES_PACKAGE_FINGERPRINT --es event_id {{quote(event_id)}}; \
    fi

test-nav:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:test --tests org.openardf.radiooracle.desktop.DesktopNavigationTest --tests org.openardf.radiooracle.desktop.DesktopAutomationCliTest

nav-audit:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='nav-audit --require-clean'

nav-tree workflow="":
    @if [ -n "{{workflow}}" ]; then \
        JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='nav-tree --workflow "{{workflow}}"'; \
    else \
        JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='nav-tree'; \
    fi

series-list manifest current="":
    @if [ -n "{{current}}" ]; then \
        JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-list "{{manifest}}" --current-event "{{current}}"'; \
    else \
        JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-list "{{manifest}}"'; \
    fi

series-add-event manifest event:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-add-event "{{manifest}}" "{{event}}"'

series-validate manifest flags="":
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-validate "{{manifest}}" {{flags}}'

series-export manifest target:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-export "{{manifest}}" "{{target}}"'

series-package-fingerprint package:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-package-fingerprint "{{package}}"'

series-match manifest current:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-match "{{manifest}}" "{{current}}"'

series-start-fairness manifest current:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-start-fairness "{{manifest}}" "{{current}}"'

series-optimize-start-fairness manifest current flags="":
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-optimize-start-fairness "{{manifest}}" "{{current}}" {{flags}}'

series-start-fairness-verify manifest current flags="":
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-series-start-fairness-verify "{{manifest}}" "{{current}}" {{flags}}'

event-start-list-verify event flags="":
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='event-start-list-verify "{{event}}" {{flags}}'

route-generator file flags="":
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='route-generator "{{file}}" {{flags}}'

classic-route-lengths input output:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='classic-route-lengths "{{input}}" "{{output}}"'

course-publication-manifest site output:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-publication-manifest "{{site}}" "{{output}}"'

course-publication-verify url inventory:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-publication-verify "{{url}}" "{{inventory}}"'

course-apply-preview input design:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-apply-preview "{{input}}" "{{design}}"'

# Recover fixed fox/SI pairs from a known pre-renumbering catalog into a new Race File.
course-station-repair input reference output:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-station-repair "{{input}}" "{{reference}}" "{{output}}"'

course-export-verify input output:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-export-verify "{{input}}" "{{output}}"'

course-audit input:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-audit "{{input}}"'

# Synthetic workflow only; writes evidence under desktopApp/build/reports/course-workflow.
course-workflow-test:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:test --tests '*DesktopCourseWorkflowTest'
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-workflow-report "{{justfile_directory()}}/desktopApp/build/reports/course-workflow/baseline.json"'

# Runs dependent platform phases in order; no private archive or device is used.
course-workflow-transfer-test:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:test --tests '*DesktopCourseWorkflowTest'
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :app:testDebugUnitTest --tests '*CourseWorkflowArchiveTransferTest' -PcourseWorkflowTransferDirectory="{{justfile_directory()}}/desktopApp/build/reports/course-workflow"
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:test --tests '*DesktopCourseWorkflowTransferReturnTest' -PcourseWorkflowTransferDirectory="{{justfile_directory()}}/desktopApp/build/reports/course-workflow"
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-workflow-report "{{justfile_directory()}}/desktopApp/build/reports/course-workflow/round-trip.json"'

course-workflow-check:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :shared:desktopTest :desktopApp:test --tests '*Course*' --tests '*DesktopClassicRoute*' --tests '*DesktopProjectSessionTest' --tests '*DesktopProjectFilesTest' --tests '*DesktopPublicResultsSiteMirrorTest' --tests '*DesktopCloudflarePagesPublisherTest' :app:testDebugUnitTest --tests '*CourseWorkflow*Test' --tests '*AndroidCourseProtectionTest' --tests '*AndroidPublicResults*Test'
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='course-workflow-report "{{justfile_directory()}}/desktopApp/build/reports/course-workflow/baseline.json" "{{justfile_directory()}}/app/build/reports/course-workflow/android-transfer.json"'
    just course-workflow-transfer-test

event-category-remove event category flags="":
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:desktopAutomation --args='remove-category "{{event}}" "{{category}}" {{flags}}'

desktop-check: compile test

desktop-package:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:checkRuntime :desktopApp:createDistributable :desktopApp:verifyDesktopDistributable

desktop-close:
    pkill -x Radio-Oracle 2>/dev/null || true
    sleep 2
    pgrep -fl Radio-Oracle || true

desktop-launch:
    open "{{justfile_directory()}}/{{app_bundle}}"
    sleep 3
    pgrep -fl Radio-Oracle

# Launch the local desktop prototype with privately configured SDK helper paths.
desktop-sdk-launch:
    @test -n "${RADIO_ORACLE_SI_SDK_HELPER_DLL:-}" && test -n "${SPORTIDENT_SDK_LICENSE_FILE:-}" && test -n "${RADIO_ORACLE_DOTNET:-}" || { echo "Configure the private SDK helper, license file, and dotnet paths first." >&2; exit 1; }
    @if pgrep -x Radio-Oracle >/dev/null; then echo "Save and close Radio-Oracle before launching with SDK support." >&2; exit 1; fi
    open "{{justfile_directory()}}/{{app_bundle}}" --env "RADIO_ORACLE_SI_SDK_HELPER_DLL=$RADIO_ORACLE_SI_SDK_HELPER_DLL" --env "SPORTIDENT_SDK_LICENSE_FILE=$SPORTIDENT_SDK_LICENSE_FILE" --env "RADIO_ORACLE_DOTNET=$RADIO_ORACLE_DOTNET"
    sleep 3
    pgrep -x Radio-Oracle

# Reuse the app's startup-file hook for repeatable UI tests; never closes an existing app.
desktop-launch-file file:
    @test -f {{quote(file)}} || { echo "Race/series file does not exist." >&2; exit 1; }
    @if pgrep -x Radio-Oracle >/dev/null; then echo "Save and close Radio-Oracle before launching a test file." >&2; exit 1; fi
    open "{{justfile_directory()}}/{{app_bundle}}" --args {{quote(file)}}
    sleep 3
    pgrep -x Radio-Oracle

desktop-relaunch: desktop-close desktop-package desktop-launch

# Commit pending changes, push the current branch, and relaunch the packaged app.
commit-push-launch message:
    just commit-push {{quote(message)}}
    just desktop-relaunch

# Commit pending changes and push the current branch.
commit-push message:
    git diff --check
    if [ -n "$(git status --porcelain)" ]; then \
        git add -A; \
        git diff --cached --check; \
        git status --short; \
        git commit -m {{quote(message)}}; \
    else \
        echo "No changes to commit"; \
    fi
    git push origin "$(git branch --show-current)"

# Run the desktop test suite before committing, pushing, and relaunching.
test-commit-push-launch message:
    just test
    just commit-push-launch {{quote(message)}}

desktop-log:
    tail -n 120 "{{debug_log}}"

desktop-log-follow:
    tail -f "{{debug_log}}"

jdeploy-prepare:
    npm run jdeploy:prepare

jdeploy-smoke:
    npm run jdeploy:local-smoke

jdeploy-preflight:
    npm run jdeploy:release-preflight
