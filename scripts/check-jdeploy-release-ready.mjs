#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { platform, tmpdir } from "node:os";
import { join, resolve } from "node:path";

const expectedPackageName = "@openardf/radio-oracle";
const packageJson = JSON.parse(readFileSync("package.json", "utf8"));
const packageLock = JSON.parse(readFileSync("package-lock.json", "utf8"));
const appBuildGradle = readFileSync("app/build.gradle", "utf8");
const rootBuildGradle = readFileSync("build.gradle", "utf8");
const desktopBuildGradle = readFileSync("desktopApp/build.gradle", "utf8");

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function requireEqual(label, actual, expected) {
  if (actual !== expected) {
    fail(`${label} expected ${expected} but found ${actual}`);
  }
}

function gradleCommand() {
  return platform() === "win32" ? resolve("gradlew.bat") : resolve("gradlew");
}

requireEqual("package name", packageJson.name, expectedPackageName);
requireEqual("package-lock name", packageLock.name, expectedPackageName);
requireEqual("package-lock version", packageLock.version, packageJson.version);

if (!appBuildGradle.includes("versionName = rootProject.ext.radioOracleVersion")) {
  fail("Android versionName must use rootProject.ext.radioOracleVersion");
}

if (!rootBuildGradle.includes(`ext.radioOracleVersion = "${packageJson.version}"`)) {
  fail("root radioOracleVersion must match package.json version");
}

if (!desktopBuildGradle.includes("packageVersion = rootProject.ext.radioOracleVersion")) {
  fail("desktop native packageVersion must use rootProject.ext.radioOracleVersion");
}

execFileSync(gradleCommand(), [":desktopApp:verifyDesktopJdeployBundle"], { stdio: "inherit" });

const tempDir = mkdtempSync(join(tmpdir(), "radio-oracle-manifest-"));
try {
  execFileSync("jar", ["xf", `${process.cwd()}/desktopApp/build/jdeploy/Radio-Oracle-jdeploy.jar`, "META-INF/MANIFEST.MF"], {
    cwd: tempDir,
    stdio: "ignore"
  });
  const manifest = readFileSync(join(tempDir, "META-INF/MANIFEST.MF"), "utf8");
  const implementationVersion = manifest.match(/^Implementation-Version: (.+)$/m)?.[1]?.trim();
  requireEqual("jDeploy jar Implementation-Version", implementationVersion, packageJson.version);
} finally {
  rmSync(tempDir, { recursive: true, force: true });
}

console.log(`Radio-Oracle jDeploy release preflight OK for ${packageJson.name}@${packageJson.version}`);
