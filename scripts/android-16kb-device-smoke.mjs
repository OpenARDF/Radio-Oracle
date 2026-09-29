#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";
import { join } from "node:path";

const packageName = "org.openardf.radiooracle";
const activityName = `${packageName}/.ui.MainActivity`;
const apkPath = process.argv[2] || "app/build/outputs/apk/debug/app-debug.apk";

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function adbPath() {
  const sdkRoot = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME;
  if (sdkRoot) {
    const candidate = join(sdkRoot, "platform-tools", process.platform === "win32" ? "adb.exe" : "adb");
    if (existsSync(candidate)) return candidate;
  }
  return "adb";
}

function adb(args, options = {}) {
  const output = execFileSync(adbPath(), args, { encoding: "utf8", ...options });
  return typeof output === "string" ? output.trim() : "";
}

function sleep(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

if (!existsSync(apkPath)) fail(`Debug APK does not exist: ${apkPath}`);

const pageSize = adb(["shell", "getconf", "PAGE_SIZE"]);
if (pageSize !== "16384") fail(`Emulator page size is ${pageSize}; expected 16384`);

adb(["install", "-r", apkPath], { stdio: "inherit" });
adb(["shell", "am", "force-stop", packageName]);
adb(["logcat", "-b", "crash", "-c"]);
const launch = adb(["shell", "am", "start", "-W", "-n", activityName]);
if (!/^Status: ok$/m.test(launch)) fail(`Activity launch did not report success:\n${launch}`);

await sleep(5000);
const processId = adb(["shell", "pidof", packageName]);
if (!/^\d+(?:\s+\d+)*$/.test(processId)) fail("Radio-Oracle process is not running after launch");

const crashLog = adb(["logcat", "-b", "crash", "-d"]);
if (crashLog.includes(`Process: ${packageName}`) || crashLog.includes(packageName)) {
  fail(`Radio-Oracle produced a crash-buffer entry:\n${crashLog}`);
}

console.log(`PASS: Radio-Oracle launched and remained running on a ${pageSize}-byte page-size emulator.`);
adb(["shell", "am", "force-stop", packageName]);
