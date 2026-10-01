#!/usr/bin/env node

import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import { localInstallPath, validateExportFiles } from "./jdeploy-local-smoke.mjs";

test("resolves native jDeploy launcher paths on every supported desktop platform", () => {
  const home = join("", "test-home");
  assert.equal(localInstallPath("darwin", home), join(home, "Applications", "Radio-Oracle.app"));
  assert.equal(
    localInstallPath("win32", home),
    join(home, ".jdeploy", "apps", "@openardf", "radio-oracle", "Radio-Oracle.exe")
  );
  assert.equal(
    localInstallPath("linux", home),
    join(home, ".jdeploy", "apps", "@openardf", "radio-oracle", "Radio-Oracle")
  );
  assert.equal(localInstallPath("freebsd", home), null);
});

test("validates representative installed-package exports", () => {
  const directory = mkdtempSync(join(tmpdir(), "radio-oracle-installed-export-test-"));
  try {
    writeFileSync(join(directory, "results.csv"), "Place,Competitor,Status,Points,Run time\n");
    writeFileSync(join(directory, "final-results.json"), "{\"categories\":[]}\n");
    writeFileSync(join(directory, "start-list.xml"), "<?xml version=\"1.0\"?><StartList/>\n");
    assert.doesNotThrow(() => validateExportFiles(directory));
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

test("rejects incomplete installed-package export evidence", () => {
  const directory = mkdtempSync(join(tmpdir(), "radio-oracle-installed-export-test-"));
  try {
    mkdirSync(join(directory, "unused"));
    assert.throws(() => validateExportFiles(directory), /missing or empty/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
