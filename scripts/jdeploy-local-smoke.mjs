#!/usr/bin/env node

import { execFileSync, spawn, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
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

export function localInstallPath(
  runtimePlatform = platform(),
  userHome = homedir(),
  packageSource = null
) {
  if (runtimePlatform === "darwin") {
    return join(userHome, "Applications", "Radio-Oracle.app");
  }
  // GitHub-backed jDeploy installs use a source-qualified directory instead of the npm scope.
  const packageDirectory = packageSource == null
    ? join("@openardf", "radio-oracle")
    : `${createHash("md5").update(packageSource).digest("hex")}.radio-oracle`;
  if (runtimePlatform === "win32") {
    return join(userHome, ".jdeploy", "apps", packageDirectory, "Radio-Oracle.exe");
  }
  if (runtimePlatform === "linux") {
    return join(userHome, ".jdeploy", "apps", packageDirectory, "radio-oracle");
  }
  return null;
}

export function validateVersionEvidence(evidenceText, expectedVersion) {
  if (!evidenceText.includes("Radio-Oracle installed package smoke")) {
    throw new Error("Installed application version evidence has an unexpected header.");
  }
  if (!evidenceText.split(/\r?\n/).includes(`packageVersion=${expectedVersion}`)) {
    throw new Error(`Installed application did not report expected version ${expectedVersion}.`);
  }
  for (const field of ["displayVersion=", "javaVersion=", "osName="]) {
    if (!evidenceText.includes(field)) {
      throw new Error(`Installed application version evidence is missing ${field}`);
    }
  }
}

export function sourcePackageManifestPath(
  userHome,
  packageSource,
  runtimeArchitecture = process.arch
) {
  const packageDirectory = `${createHash("md5").update(packageSource).digest("hex")}.radio-oracle`;
  return join(
    userHome,
    ".jdeploy",
    "manifests",
    runtimeArchitecture,
    packageDirectory,
    "uninstall-manifest.xml"
  );
}

function manifestValue(manifest, field) {
  return manifest.match(new RegExp(`<${field}>([^<]+)</${field}>`))?.[1] ?? null;
}

export function validateSourcePackageManifest(
  manifestPath,
  packageSource,
  expectedVersion,
  runtimeArchitecture = process.arch
) {
  if (!existsSync(manifestPath)) {
    throw new Error(`Installed source package manifest was not created: ${manifestPath}`);
  }
  const manifest = readFileSync(manifestPath, "utf8");
  const qualifiedName = `${createHash("md5").update(packageSource).digest("hex")}.radio-oracle`;
  const expected = new Map([
    ["name", "radio-oracle"],
    ["source", packageSource],
    ["version", expectedVersion],
    ["fullyQualifiedName", qualifiedName],
    ["architecture", runtimeArchitecture]
  ]);
  for (const [field, expectedValue] of expected) {
    const actualValue = manifestValue(manifest, field);
    if (actualValue !== expectedValue) {
      throw new Error(
        `Installed source package manifest reported ${field}=${actualValue}, expected ${expectedValue}.`
      );
    }
  }
  if (!manifest.includes('<uninstallManifest xmlns="http://jdeploy.ca/uninstall-manifest/1.0"')) {
    throw new Error(
      "Installed source package manifest has an unexpected format."
    );
  }
}

function isRadioOracleRunning(installPath) {
  if (platform() === "win32") {
    const result = spawnSync(
      "powershell.exe",
      ["-NoProfile", "-Command", "Get-Process -Name 'Radio-Oracle' -ErrorAction SilentlyContinue | Select-Object -First 1"],
      { encoding: "utf8" }
    );
    return result.status === 0 && result.stdout.includes("Radio-Oracle");
  }

  if (platform() === "linux") {
    const executablePattern = `${installPath} ${sampleProject}`;
    if (spawnSync("pgrep", ["-f", executablePattern]).status === 0) return true;
  }
  const appPattern = `Radio-Oracle.app/Contents/MacOS/Client4JLauncher ${sampleProject}`;
  const jarPattern = `Radio-Oracle-jdeploy.jar ${sampleProject}`;
  return spawnSync("pgrep", ["-f", appPattern]).status === 0 ||
    spawnSync("pgrep", ["-f", jarPattern]).status === 0;
}

function cleanup(installPath) {
  if (platform() === "darwin" || platform() === "linux") {
    // Target only this smoke's unique project argument; another Radio-Oracle instance may contain
    // unsaved user work and must not be closed by release verification.
    if (platform() === "linux" && installPath != null) {
      spawnSync("pkill", ["-f", `${installPath} ${sampleProject}`], { stdio: "ignore" });
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

export function parseSmokeArguments(args) {
  if (args.length === 0) {
    return { probeOnlyVersion: null, packageSource: null };
  }
  if (args.length === 2 && args[0] === "--probe-only" && /^\d+\.\d+\.\d+$/.test(args[1])) {
    return { probeOnlyVersion: args[1], packageSource: null };
  }
  if (
    args.length === 4
    && args[0] === "--probe-only"
    && /^\d+\.\d+\.\d+$/.test(args[1])
    && args[2] === "--source"
  ) {
    const packageSource = new URL(args[3]);
    if (packageSource.protocol !== "https:") {
      throw new Error("The installed package source must use HTTPS.");
    }
    return { probeOnlyVersion: args[1], packageSource: packageSource.href.replace(/\/$/, "") };
  }
  throw new Error(
    "Usage: jdeploy-local-smoke.mjs [--probe-only <major.minor.patch> [--source <https-url>]]"
  );
}

function main() {
  let options;
  try {
    options = parseSmokeArguments(process.argv.slice(2));
  } catch (error) {
    fail(error.message);
  }

  if (options.probeOnlyVersion == null) {
    run(npmCommand(), ["run", "jdeploy:install-local"]);
    run(npmCommand(), ["run", "jdeploy:verify-install"]);
  }

  copyFileSync(resolve("samples", "desktop-smoke.rom.json"), sampleProject);

  const installPath = localInstallPath(platform(), homedir(), options.packageSource);
  process.on("exit", () => cleanup(installPath));
  if (installPath == null) {
    console.log("Radio-Oracle local jDeploy install verified; launch smoke is skipped on this platform.");
    process.exit(0);
  }
  if (!existsSync(installPath)) {
    fail(`Expected local jDeploy install at ${installPath}.`);
  }

  const expectedVersion = options.probeOnlyVersion
    ?? JSON.parse(readFileSync("package.json", "utf8")).version;
  runInstalledExportSmoke(installPath);
  if (platform() === "win32" || platform() === "linux") {
    const versionEvidencePath = join(exportDirectory, "installed-package-evidence.txt");
    try {
      if (existsSync(versionEvidencePath)) {
        validateVersionEvidence(readFileSync(versionEvidencePath, "utf8"), expectedVersion);
      } else if (options.packageSource != null) {
        // v1.0.52 predates versioned smoke output. Its jDeploy uninstall manifest records
        // the exact source-qualified package identity, version, and native architecture.
        validateSourcePackageManifest(
          sourcePackageManifestPath(homedir(), options.packageSource),
          options.packageSource,
          expectedVersion
        );
      } else {
        throw new Error("Installed application did not produce version evidence.");
      }
    } catch (error) {
      fail(error.message);
    }
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

  if (waitFor(() => isRadioOracleRunning(installPath), 30_000)) {
    console.log(`Radio-Oracle installed jDeploy smoke OK for ${packageName} ${expectedVersion}`);
    process.exit(0);
  }

  fail("Radio-Oracle did not start from the installed jDeploy launcher.");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main();
}
