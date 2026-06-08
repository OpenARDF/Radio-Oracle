#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
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

function javaEnv(extra = {}) {
  let javaHome = process.env.JAVA_HOME;
  if (!javaHome && platform() === "darwin" && existsSync("/usr/libexec/java_home")) {
    javaHome = execFileSync("/usr/libexec/java_home", ["-v", "17"], { encoding: "utf8" }).trim();
  }
  if (!javaHome) {
    fail("Set JAVA_HOME to a full JDK 17 installation.");
  }
  return {
    ...process.env,
    ...extra,
    PATH: `${resolve(javaHome, "bin")}${platform() === "win32" ? ";" : ":"}${process.env.PATH || ""}`
  };
}

function runGradle(args, extraEnv = {}) {
  const gradle = gradleCommand();
  const env = javaEnv(extraEnv);
  if (platform() === "win32") {
    execFileSync("cmd.exe", ["/d", "/c", "call", gradle, ...args], { stdio: "inherit", env });
    return;
  }
  execFileSync(gradle, args, { stdio: "inherit", env });
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

const releaseBuildEnv = { RADIO_ORACLE_RELEASE_BUILD: "1" };
runGradle([":desktopApp:verifyDesktopJdeployBundle"], releaseBuildEnv);

const tempDir = mkdtempSync(join(tmpdir(), "radio-oracle-manifest-"));
try {
  execFileSync("jar", ["xf", `${process.cwd()}/desktopApp/build/jdeploy/Radio-Oracle-jdeploy.jar`, "META-INF/MANIFEST.MF"], {
    cwd: tempDir,
    env: javaEnv(),
    stdio: "ignore"
  });
  const manifest = readFileSync(join(tempDir, "META-INF/MANIFEST.MF"), "utf8");
  const implementationVersion = manifest.match(/^Implementation-Version: (.+)$/m)?.[1]?.trim();
  requireEqual("jDeploy jar Implementation-Version", implementationVersion, packageJson.version);

  const classInfo = execFileSync(
    "javap",
    [
      "-classpath",
      `${process.cwd()}/desktopApp/build/jdeploy/Radio-Oracle-jdeploy.jar`,
      "-verbose",
      "org.openardf.radiooracle.desktop.DesktopBuildInfo"
    ],
    { env: javaEnv(releaseBuildEnv), encoding: "utf8" }
  );
  if (!classInfo.includes(`ConstantValue: String ${packageJson.version}`)) {
    fail(`desktop release build must include an unsuffixed display version ${packageJson.version}`);
  }
  if (classInfo.includes(`Radio-Oracle Desktop ${packageJson.version}-`) ||
      /\bRadio-Oracle Desktop \d+\.\d+\.\d+[a-z]/.test(classInfo)) {
    fail("desktop release build must not include an iterative desktop build suffix");
  }
} finally {
  rmSync(tempDir, { recursive: true, force: true });
}

console.log(`Radio-Oracle jDeploy release preflight OK for ${packageJson.name}@${packageJson.version}`);
