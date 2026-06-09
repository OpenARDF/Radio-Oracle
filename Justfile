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

compile:
    JAVA_HOME="{{java_home}}" ./gradlew :desktopApp:compileKotlin

test:
    JAVA_HOME="{{java_home}}" ./gradlew :desktopApp:test

desktop-check: compile test

desktop-package:
    JAVA_HOME="{{java_home}}" ./gradlew :desktopApp:checkRuntime :desktopApp:createDistributable :desktopApp:verifyDesktopDistributable

desktop-close:
    pkill -x Radio-Oracle 2>/dev/null || true
    sleep 2
    pgrep -fl Radio-Oracle || true

desktop-launch:
    open -a "{{app_bundle}}"
    sleep 3
    pgrep -fl Radio-Oracle

desktop-relaunch: desktop-close desktop-package desktop-launch

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
