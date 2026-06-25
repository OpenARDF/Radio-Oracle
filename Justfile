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
