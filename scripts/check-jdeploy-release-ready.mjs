#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync } from "node:fs";
import { platform, tmpdir } from "node:os";
import { join, resolve } from "node:path";

const expectedPackageName = "@openardf/radio-oracle";
const packageJson = JSON.parse(readFileSync("package.json", "utf8"));
const packageLock = JSON.parse(readFileSync("package-lock.json", "utf8"));
const appBuildGradle = readFileSync("app/build.gradle", "utf8");
const rootBuildGradle = readFileSync("build.gradle", "utf8");
const settingsGradle = readFileSync("settings.gradle", "utf8");
const desktopBuildGradle = readFileSync("desktopApp/build.gradle", "utf8");
const versionCatalog = readFileSync("gradle/libs.versions.toml", "utf8");
const readme = readFileSync("README.md", "utf8");
const desktopPrep = readFileSync("docs/desktop-prep.md", "utf8");
const npmPublishWorkflow = readFileSync(".github/workflows/publish-jdeploy.yml", "utf8");
const githubReleaseWorkflow = readFileSync(".github/workflows/jdeploy-github-release.yml", "utf8");
const requiredJdeploySkikoRuntimeArtifacts = [
  "skiko-awt-runtime-linux-arm64",
  "skiko-awt-runtime-linux-x64",
  "skiko-awt-runtime-macos-arm64",
  "skiko-awt-runtime-macos-x64",
  "skiko-awt-runtime-windows-arm64",
  "skiko-awt-runtime-windows-x64"
];

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function requireEqual(label, actual, expected) {
  if (actual !== expected) {
    fail(`${label} expected ${expected} but found ${actual}`);
  }
}

function requireIncludes(label, text, expected) {
  if (!text.includes(expected)) {
    fail(`${label} must include ${expected}`);
  }
}

function requireNotIncludes(label, text, unexpected) {
  if (text.includes(unexpected)) {
    fail(`${label} must not include ${unexpected}`);
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

if (!existsSync("icon.png")) {
  fail("icon.png is missing. Restore the baseline jDeploy icon before releasing.");
}
if (!packageJson.files?.includes("icon.png")) {
  fail("package.json must include icon.png in files so jDeploy downloads show the app icon");
}

const expectedBundledDependencies = ["node-fetch", "shelljs", "tar", "yauzl"];
const bundledDependencies = packageJson.bundledDependencies || packageJson.bundleDependencies || [];
for (const dependency of expectedBundledDependencies) {
  if (!bundledDependencies.includes(dependency)) {
    fail(`package.json must bundle ${dependency} so GitHub-release jDeploy tarballs can run without npm install`);
  }
}

if (!appBuildGradle.includes("versionName = rootProject.ext.radioOracleVersion")) {
  fail("Android versionName must use rootProject.ext.radioOracleVersion");
}

if (!rootBuildGradle.includes(`ext.radioOracleVersion = "${packageJson.version}"`)) {
  fail("root radioOracleVersion must match package.json version");
}

requireIncludes("Gradle repositories", settingsGradle, "https://maven.pkg.jetbrains.space/public/p/compose/dev");

if (!desktopBuildGradle.includes("packageVersion = rootProject.ext.radioOracleVersion")) {
  fail("desktop native packageVersion must use rootProject.ext.radioOracleVersion");
}
requireIncludes("desktop native packaging icon configuration", desktopBuildGradle, "Radio-Oracle.icns");
requireIncludes("desktop native packaging icon configuration", desktopBuildGradle, "Radio-Oracle.ico");
requireIncludes("desktop native packaging icon configuration", desktopBuildGradle, "Radio-Oracle.png");
for (const iconPath of [
  "desktopApp/packaging/icons/Radio-Oracle.icns",
  "desktopApp/packaging/icons/Radio-Oracle.ico",
  "desktopApp/packaging/icons/Radio-Oracle.png"
]) {
  if (!existsSync(iconPath)) {
    fail(`desktop packaging icon is missing: ${iconPath}`);
  }
}
requireIncludes("desktop jDeploy runtime configuration", desktopBuildGradle, "desktopJdeployRuntimeClasspath");
for (const artifact of requiredJdeploySkikoRuntimeArtifacts) {
  requireIncludes("desktop jDeploy native runtime configuration", desktopBuildGradle, artifact);
}

requireIncludes("README desktop install guidance", readme, "https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle");
requireIncludes("desktop-prep install guidance", desktopPrep, "GitHub Release Installers");
requireIncludes("desktop-prep install guidance", desktopPrep, "https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle");
requireIncludes("desktop-prep deployment guidance", desktopPrep, "GitHub-release jDeploy page");
requireIncludes("desktop-prep deployment guidance", desktopPrep, "public end-user install method");
requireIncludes("desktop-prep deployment guidance", desktopPrep, "registry/provenance/automation path");

requireIncludes("npm publish workflow", npmPublishWorkflow, "workflow_dispatch:");
requireIncludes("npm publish workflow", npmPublishWorkflow, "id-token: write");
requireIncludes("npm publish workflow", npmPublishWorkflow, "node-version: \"24\"");
requireIncludes("npm publish workflow", npmPublishWorkflow, "RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH: \"1\"");
requireIncludes("npm publish workflow", npmPublishWorkflow, "RADIO_ORACLE_RELEASE_BUILD: \"1\"");
requireIncludes("npm publish workflow", npmPublishWorkflow, "node ./scripts/publish-jdeploy-trusted.mjs");
requireNotIncludes("npm publish workflow", npmPublishWorkflow, "registry-url: https://registry.npmjs.org");
const trustedPublishScript = readFileSync("scripts/publish-jdeploy-trusted.mjs", "utf8");
requireIncludes("trusted npm publish script", trustedPublishScript, "delete publishEnv.NODE_AUTH_TOKEN");
requireIncludes("trusted npm publish script", trustedPublishScript, "NPM_CONFIG_USERCONFIG");
requireIncludes("trusted npm publish script", trustedPublishScript, "\"--provenance\"");
requireIncludes("trusted npm publish script", trustedPublishScript, "dist?.attestations?.provenance");
if (!existsSync("scripts/patch-jdeploy-macos-installer-icons.mjs")) {
  fail("macOS jDeploy installer icon patcher is missing");
}

requireIncludes("GitHub release workflow", githubReleaseWorkflow, "tags:");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "- \"v*\"");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "node-version: \"24\"");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "RADIO_ORACLE_RELEASE_BUILD: \"1\"");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "Use GitHub-safe jDeploy package identity");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "const githubPackageName = \"radio-oracle\"");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "deploy_target: github");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "Patch macOS installer icons");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "patch-jdeploy-macos-installer-icons.mjs");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "gh release upload");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "radio-oracle-*.tgz");
requireIncludes("GitHub release workflow", githubReleaseWorkflow, "gh release edit");

const releaseBuildEnv = { RADIO_ORACLE_RELEASE_BUILD: "1" };
runGradle([":desktopApp:verifyDesktopJdeployBundle"], releaseBuildEnv);

const skikoVersion = versionCatalog.match(/^skiko = "([^"]+)"$/m)?.[1];
if (!skikoVersion) {
  fail("gradle/libs.versions.toml must define a skiko version for jDeploy native runtime validation");
}
const jdeployLibsDir = join(process.cwd(), "desktopApp", "build", "jdeploy", "libs");
const stagedLibs = new Set(readdirSync(jdeployLibsDir));
for (const artifact of ["skiko-awt", ...requiredJdeploySkikoRuntimeArtifacts]) {
  const jarName = `${artifact}-${skikoVersion}.jar`;
  if (!stagedLibs.has(jarName)) {
    fail(`jDeploy staged libs are missing ${jarName}`);
  }
}

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
