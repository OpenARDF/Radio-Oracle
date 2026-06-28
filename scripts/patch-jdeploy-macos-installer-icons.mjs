#!/usr/bin/env node

import { execFileSync } from "node:child_process";
import {
  copyFileSync,
  existsSync,
  mkdtempSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync
} from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

const repoRoot = fileURLToPath(new URL("..", import.meta.url));
const installerIconPath = join(repoRoot, "desktopApp", "packaging", "icons", "Radio-Oracle.icns");
const pngIconPath = join(repoRoot, "icon.png");
const targetDir = resolve(process.argv[2] || ".");

function fail(message) {
  console.error(`ERROR: ${message}`);
  process.exit(1);
}

function requireFile(path, label) {
  if (!existsSync(path) || !statSync(path).isFile()) {
    fail(`${label} not found: ${path}`);
  }
}

function walkFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...walkFiles(path));
    } else if (entry.isFile()) {
      out.push(path);
    }
  }
  return out;
}

function findMacInstallerArchives(dir) {
  return walkFiles(dir)
    .filter((path) => /^Radio-Oracle\.Installer-mac-.*\.tgz$/.test(basename(path)))
    .sort();
}

function replaceAppXmlIcon(appXmlPath, iconDataUri) {
  const original = readFileSync(appXmlPath, "utf8");
  const updated = original.replace(
    /icon=(['"])data:image\/png;base64,[^'"]*\1/,
    `icon='${iconDataUri}'`
  );
  if (updated === original) {
    fail(`No embedded icon data URI found in ${appXmlPath}`);
  }
  writeFileSync(appXmlPath, updated);
}

function patchArchive(archivePath, iconDataUri) {
  const tempDir = mkdtempSync(join(tmpdir(), "radio-oracle-mac-installer-"));
  try {
    execFileSync("tar", ["-xzf", archivePath, "-C", tempDir], { stdio: "inherit" });
    const files = walkFiles(tempDir);
    const icnsFiles = files.filter((path) => path.endsWith(".app/Contents/Resources/icon.icns"));
    const appXmlFiles = files.filter((path) => path.endsWith(".app/Contents/app.xml"));
    if (icnsFiles.length === 0) {
      fail(`No macOS app icon.icns found in ${archivePath}`);
    }
    if (appXmlFiles.length === 0) {
      fail(`No macOS app app.xml found in ${archivePath}`);
    }
    for (const icnsFile of icnsFiles) {
      copyFileSync(installerIconPath, icnsFile);
    }
    for (const appXmlFile of appXmlFiles) {
      replaceAppXmlIcon(appXmlFile, iconDataUri);
    }
    const topLevelEntries = readdirSync(tempDir);
    if (topLevelEntries.length !== 1) {
      fail(`Expected one top-level installer directory in ${archivePath}, found ${topLevelEntries.length}`);
    }
    const tempArchive = join(dirname(archivePath), `.${basename(archivePath)}.tmp`);
    execFileSync("tar", ["-czf", tempArchive, "-C", tempDir, topLevelEntries[0]], { stdio: "inherit" });
    copyFileSync(tempArchive, archivePath);
    rmSync(tempArchive, { force: true });
    console.log(`Patched macOS installer icon in ${archivePath}`);
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
}

requireFile(installerIconPath, "Radio-Oracle macOS ICNS");
requireFile(pngIconPath, "Radio-Oracle PNG icon");

if (!existsSync(targetDir) || !statSync(targetDir).isDirectory()) {
  fail(`Installer asset directory not found: ${targetDir}`);
}

const archives = findMacInstallerArchives(targetDir);
if (archives.length === 0) {
  fail(`No Radio-Oracle macOS installer .tgz files found in ${targetDir}`);
}

const iconDataUri = `data:image/png;base64,${readFileSync(pngIconPath).toString("base64")}`;
for (const archive of archives) {
  patchArchive(archive, iconDataUri);
}
