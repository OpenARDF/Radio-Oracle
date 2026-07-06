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

test:
    JAVA_HOME="{{java_home}}" ./scripts/gradle-sequential.sh :desktopApp:test

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
