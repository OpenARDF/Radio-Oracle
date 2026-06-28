#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync } from "node:fs";
import { platform } from "node:os";
import { dirname, resolve } from "node:path";

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

let javaHome = process.env.JAVA_HOME;
if (!javaHome && platform() === "darwin" && existsSync("/usr/libexec/java_home")) {
  javaHome = execFileSync("/usr/libexec/java_home", ["-v", "17"], { encoding: "utf8" }).trim();
}
if (!javaHome) {
  fail("Set JAVA_HOME to a full JDK 17 installation.");
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

const args = process.argv.slice(2);
if (args.length === 0) {
  fail("Pass a jDeploy command to run.");
}

const pathSeparator = platform() === "win32" ? ";" : ":";
const npx = platform() === "win32" ? "npx.cmd" : "npx";
const npxArgs = ["--no-install", "jdeploy", ...args];
const options = {
  stdio: "inherit",
  env: {
    ...process.env,
    PATH: `${resolve(javaHome, "bin")}${pathSeparator}${process.env.PATH || ""}`
  }
};

if (platform() === "win32") {
  execFileSync("cmd.exe", ["/d", "/c", "call", npx, ...npxArgs], options);
} else {
  execFileSync(npx, npxArgs, options);
}

if (args[0] === "package") {
  syncJdeployIcon();
}
