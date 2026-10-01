#!/usr/bin/env node

import { execFileSync, spawn, spawnSync } from "node:child_process";
import { copyFileSync, existsSync, readFileSync, rmSync } from "node:fs";
import { homedir, platform, tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const packageName = "@openardf/radio-oracle";
const sampleProject = join(tmpdir(), "radio-oracle-desktop-smoke.rom.json");
const exportDirectory = resolve("desktopApp", "build", "reports", "jdeploy-installed-smoke", platform());
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

export function localInstallPath(runtimePlatform = platform(), userHome = homedir()) {
  if (runtimePlatform === "darwin") {
    return join(userHome, "Applications", "Radio-Oracle.app");
  }
  if (runtimePlatform === "win32") {
    return join(userHome, ".jdeploy", "apps", "@openardf", "radio-oracle", "Radio-Oracle.exe");
  }
  if (runtimePlatform === "linux") {
    return join(userHome, ".jdeploy", "apps", "@openardf", "radio-oracle", "radio-oracle");
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

  if (platform() === "linux") {
    const executablePattern = `${localInstallPath()} ${sampleProject}`;
    if (spawnSync("pgrep", ["-f", executablePattern]).status === 0) return true;
  }
  const appPattern = `Radio-Oracle.app/Contents/MacOS/Client4JLauncher ${sampleProject}`;
  const jarPattern = `Radio-Oracle-jdeploy.jar ${sampleProject}`;
  return spawnSync("pgrep", ["-f", appPattern]).status === 0 ||
    spawnSync("pgrep", ["-f", jarPattern]).status === 0;
}

function cleanup() {
  if (platform() === "darwin" || platform() === "linux") {
    // Target only this smoke's unique project argument; another Radio-Oracle instance may contain
    // unsaved user work and must not be closed by release verification.
    if (platform() === "linux") {
      spawnSync("pkill", ["-f", `${localInstallPath()} ${sampleProject}`], { stdio: "ignore" });
    }
    spawnSync("pkill", ["-f", `Radio-Oracle.app/Contents/MacOS/Client4JLauncher ${sampleProject}`], { stdio: "ignore" });
    spawnSync("pkill", ["-f", `Radio-Oracle-jdeploy.jar ${sampleProject}`], { stdio: "ignore" });
  } else if (platform() === "win32") {
    spawnSync("taskkill.exe", ["/IM", "Radio-Oracle.exe", "/F"], { stdio: "ignore" });
  }
  if (launchedProcess && !launchedProcess.killed) {
    launchedProcess.kill();
  }
}

function waitFor(predicate, timeoutMilliseconds) {
  const deadline = Date.now() + timeoutMilliseconds;
  while (Date.now() < deadline) {
    if (predicate()) return true;
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1000);
  }
  return false;
}

export function validateExportFiles(directory) {
  const resultsCsv = join(directory, "results.csv");
  const finalResultsJson = join(directory, "final-results.json");
  const startListXml = join(directory, "start-list.xml");
  for (const path of [resultsCsv, finalResultsJson, startListXml]) {
    if (!existsSync(path) || readFileSync(path).length === 0) {
      throw new Error(`Installed-package export is missing or empty: ${path}`);
    }
  }

  const csvHeader = readFileSync(resultsCsv, "utf8").split(/\r?\n/, 1)[0];
  if (csvHeader !== "Place,Competitor,Status,Points,Run time") {
    throw new Error(`Installed-package CSV has an unexpected header: ${csvHeader}`);
  }
  JSON.parse(readFileSync(finalResultsJson, "utf8"));
  const xml = readFileSync(startListXml, "utf8");
  if (!/<(?:[A-Za-z][\w.-]*:)?StartList\b/.test(xml)) {
    throw new Error("Installed-package XML does not contain an IOF StartList root.");
  }
}

function runInstalledExportSmoke(installPath) {
  // macOS's installed path is an application bundle; Windows and Linux expose executable launchers.
  if (platform() !== "win32" && platform() !== "linux") return;

  rmSync(exportDirectory, { recursive: true, force: true });
  const result = spawnSync(
    installPath,
    ["--installed-package-smoke", sampleProject, exportDirectory],
    { encoding: "utf8", timeout: 30_000 }
  );
  if (result.error) fail(`Installed-package export command failed: ${result.error.message}`);
  if (result.status !== 0) {
    fail(`Installed-package export command exited ${result.status}: ${(result.stderr || "").trim()}`);
  }

  const expected = ["results.csv", "final-results.json", "start-list.xml"];
  if (!waitFor(() => expected.every(fileName => existsSync(join(exportDirectory, fileName))), 30_000)) {
    fail(`Installed-package exports were not created in ${exportDirectory}.`);
  }
  try {
    validateExportFiles(exportDirectory);
  } catch (failure) {
    fail(failure.message);
  }
}

function main() {
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

  runInstalledExportSmoke(installPath);

  if (platform() === "darwin") {
    run("open", ["-n", installPath, "--args", sampleProject]);
  } else {
    launchedProcess = spawn(installPath, [sampleProject], {
      detached: true,
      stdio: "ignore"
    });
    launchedProcess.unref();
  }

  if (waitFor(isRadioOracleRunning, 30_000)) {
    console.log(`Radio-Oracle local jDeploy smoke OK for ${packageName}`);
    process.exit(0);
  }

  fail("Radio-Oracle did not start from local jDeploy install.");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
