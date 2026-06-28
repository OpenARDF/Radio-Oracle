#!/usr/bin/env node

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const packageJsonPath = join(repoRoot, "package.json");
const iconPath = join(repoRoot, "icon.png");
const jarPath = join(repoRoot, "desktopApp", "build", "jdeploy", "Radio-Oracle-jdeploy.jar");

if (!existsSync(packageJsonPath)) {
  console.error("package.json is missing. Recreate the jDeploy scaffold before publishing.");
  process.exit(1);
}

const packageJson = JSON.parse(readFileSync(packageJsonPath, "utf8"));

if (!existsSync(iconPath)) {
  console.error("icon.png is missing. Restore the baseline jDeploy icon before publishing.");
  process.exit(1);
}

if (!packageJson.files?.includes("icon.png")) {
  console.error("package.json must include icon.png in files so jDeploy downloads show the app icon.");
  process.exit(1);
}

if (!existsSync(jarPath)) {
  console.error("desktopApp/build/jdeploy/Radio-Oracle-jdeploy.jar is missing. Run ./gradlew :desktopApp:prepareDesktopJdeployBundle :desktopApp:verifyDesktopJdeployBundle before publishing.");
  process.exit(1);
}

if (process.env.RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH !== "1") {
  console.error("Refusing to publish Radio-Oracle jDeploy package.");
  console.error("Set RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH=1 when performing an intentional release.");
  process.exit(1);
}
