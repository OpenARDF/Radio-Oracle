#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync } from "node:fs";
import { platform } from "node:os";
import { dirname, resolve } from "node:path";

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function syncJdeployIcon() {
  const sourceIcon = resolve("icon.png");
  const bundleIcon = resolve("jdeploy-bundle", "icon.png");
  if (!existsSync(sourceIcon)) {
    fail("icon.png is missing. Run scripts/generate-radio-oracle-icons.py before packaging jDeploy.");
  }
  mkdirSync(dirname(bundleIcon), { recursive: true });
  copyFileSync(sourceIcon, bundleIcon);
}

function gradleCommand() {
  if (platform() === "win32") {
    return resolve("gradlew.bat");
  }
  return resolve("gradlew");
}

function runGradle(args, options) {
  const gradle = gradleCommand();
  if (platform() === "win32") {
    execFileSync("cmd.exe", ["/d", "/c", "call", gradle, ...args], options);
    return;
  }
  execFileSync(gradle, args, options);
}

let javaHome = process.env.JAVA_HOME;
if (!javaHome && platform() === "darwin" && existsSync("/usr/libexec/java_home")) {
  javaHome = execFileSync("/usr/libexec/java_home", ["-v", "17"], { encoding: "utf8" }).trim();
}
if (!javaHome) {
  fail("Set JAVA_HOME to a full JDK 17 installation.");
}

const gradleWrapper = gradleCommand();
if (!existsSync(gradleWrapper)) {
  fail(`Gradle wrapper was not found at ${gradleWrapper}.`);
}

runGradle(
  [":desktopApp:prepareDesktopJdeployBundle", ":desktopApp:verifyDesktopJdeployBundle"],
  {
    stdio: "inherit",
    env: {
      ...process.env,
      PATH: `${resolve(javaHome, "bin")}${platform() === "win32" ? ";" : ":"}${process.env.PATH || ""}`
    }
  }
);
syncJdeployIcon();
