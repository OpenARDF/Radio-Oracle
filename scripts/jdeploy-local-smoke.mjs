#!/usr/bin/env node

import { execFileSync, spawn, spawnSync } from "node:child_process";
import { copyFileSync, existsSync } from "node:fs";
import { homedir, platform, tmpdir } from "node:os";
import { join, resolve } from "node:path";

const packageName = "@openardf/radio-oracle";
const sampleProject = join(tmpdir(), "radio-oracle-desktop-smoke.rom.json");
let launchedProcess;

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function run(command, args, options = {}) {
  if (platform() === "win32" && command.endsWith(".cmd")) {
    execFileSync("cmd.exe", ["/d", "/c", "call", command, ...args], { stdio: "inherit", ...options });
    return;
  }
  execFileSync(command, args, { stdio: "inherit", ...options });
}

function npmCommand() {
  return platform() === "win32" ? "npm.cmd" : "npm";
}

function localInstallPath() {
  if (platform() === "darwin") {
    return join(homedir(), "Applications", "Radio-Oracle.app");
  }
  if (platform() === "win32") {
    return join(homedir(), ".jdeploy", "apps", "@openardf", "radio-oracle", "Radio-Oracle.exe");
  }
  return null;
}

function isRadioOracleRunning() {
  if (platform() === "win32") {
    const result = spawnSync(
      "powershell.exe",
      ["-NoProfile", "-Command", "Get-Process -Name 'Radio-Oracle' -ErrorAction SilentlyContinue | Select-Object -First 1"],
      { encoding: "utf8" }
    );
    return result.status === 0 && result.stdout.includes("Radio-Oracle");
  }

  const appPattern = `Radio-Oracle.app/Contents/MacOS/Client4JLauncher ${sampleProject}`;
  const jarPattern = `Radio-Oracle-jdeploy.jar ${sampleProject}`;
  return spawnSync("pgrep", ["-f", appPattern]).status === 0 ||
    spawnSync("pgrep", ["-f", jarPattern]).status === 0;
}

function cleanup() {
  if (platform() === "darwin") {
    spawnSync("osascript", ["-e", 'tell application "Radio-Oracle" to quit'], { stdio: "ignore" });
  } else if (platform() === "win32") {
    spawnSync("taskkill.exe", ["/IM", "Radio-Oracle.exe", "/F"], { stdio: "ignore" });
  }
  if (launchedProcess && !launchedProcess.killed) {
    launchedProcess.kill();
  }
}

process.on("exit", cleanup);

run(npmCommand(), ["run", "jdeploy:install-local"]);
run(npmCommand(), ["run", "jdeploy:verify-install"]);

copyFileSync(resolve("samples", "desktop-smoke.rom.json"), sampleProject);

const installPath = localInstallPath();
if (installPath == null) {
  console.log("Radio-Oracle local jDeploy install verified; launch smoke is skipped on this platform.");
  process.exit(0);
}
if (!existsSync(installPath)) {
  fail(`Expected local jDeploy install at ${installPath}.`);
}

if (platform() === "darwin") {
  run("open", ["-n", installPath, "--args", sampleProject]);
} else {
  launchedProcess = spawn(installPath, [sampleProject], {
    detached: true,
    stdio: "ignore"
  });
  launchedProcess.unref();
}

const deadline = Date.now() + 30_000;
while (Date.now() < deadline) {
  if (isRadioOracleRunning()) {
    console.log(`Radio-Oracle local jDeploy smoke OK for ${packageName}`);
    process.exit(0);
  }
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000);
}

fail("Radio-Oracle did not start from local jDeploy install.");
