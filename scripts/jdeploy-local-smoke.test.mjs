#!/usr/bin/env node

import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  localInstallPath,
  parseSmokeArguments,
  sourcePackageMetadataPath,
  validateExportFiles,
  validateSourcePackageMetadata,
  validateVersionEvidence
} from "./jdeploy-local-smoke.mjs";

test("resolves native jDeploy launcher paths on every supported desktop platform", () => {
  const home = join("", "test-home");
  assert.equal(localInstallPath("darwin", home), join(home, "Applications", "Radio-Oracle.app"));
  assert.equal(
    localInstallPath("win32", home),
    join(home, ".jdeploy", "apps", "@openardf", "radio-oracle", "Radio-Oracle.exe")
  );
  assert.equal(
    localInstallPath("linux", home),
    join(home, ".jdeploy", "apps", "@openardf", "radio-oracle", "radio-oracle")
  );
  assert.equal(localInstallPath("freebsd", home), null);
  assert.equal(
    localInstallPath("linux", home, "https://github.com/OpenARDF/Radio-Oracle"),
    join(
      home,
      ".jdeploy",
      "apps",
      "1190fb0d3f779f0fba709b0dec2020e8.radio-oracle",
      "radio-oracle"
    )
  );
  assert.equal(
    localInstallPath("win32", home, "https://github.com/OpenARDF/Radio-Oracle"),
    join(
      home,
      ".jdeploy",
      "apps",
      "1190fb0d3f779f0fba709b0dec2020e8.radio-oracle",
      "Radio-Oracle.exe"
    )
  );
});

test("resolves source-qualified package metadata on native ARM64", () => {
  const home = join("", "test-home");
  assert.equal(
    sourcePackageMetadataPath(
      home,
      "https://github.com/OpenARDF/Radio-Oracle",
      "1.0.52",
      "arm64"
    ),
    join(
      home,
      ".jdeploy",
      "gh-packages-arm64",
      "1190fb0d3f779f0fba709b0dec2020e8.radio-oracle",
      "1.0.52",
      "package.json"
    )
  );
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

test("parses the release-installed probe-only mode conservatively", () => {
  assert.deepEqual(parseSmokeArguments([]), { probeOnlyVersion: null, packageSource: null });
  assert.deepEqual(parseSmokeArguments(["--probe-only", "1.0.52"]), {
    probeOnlyVersion: "1.0.52",
    packageSource: null
  });
  assert.deepEqual(
    parseSmokeArguments([
      "--probe-only",
      "1.0.52",
      "--source",
      "https://github.com/OpenARDF/Radio-Oracle/"
    ]),
    {
      probeOnlyVersion: "1.0.52",
      packageSource: "https://github.com/OpenARDF/Radio-Oracle"
    }
  );
  assert.throws(
    () => parseSmokeArguments(["--probe-only", "1.0.52", "--source", "http://example.test"]),
    /must use HTTPS/
  );
  assert.throws(
    () => parseSmokeArguments(["--probe-only", "1.0.52", "--source", "not-a-url"]),
    /Invalid URL/
  );
  assert.throws(() => parseSmokeArguments(["--probe-only"]), /Usage:/);
  assert.throws(() => parseSmokeArguments(["--probe-only", "v1.0.52"]), /Usage:/);
  assert.throws(() => parseSmokeArguments(["--unexpected", "1.0.52"]), /Usage:/);
});

test("requires complete exact version evidence from the installed application", () => {
  const evidence = [
    "Radio-Oracle installed package smoke",
    "packageVersion=1.0.53",
    "displayVersion=1.0.53a",
    "javaVersion=17",
    "osName=Test",
    ""
  ].join("\n");
  assert.doesNotThrow(() => validateVersionEvidence(evidence, "1.0.53"));
  assert.throws(() => validateVersionEvidence(evidence, "1.0.52"), /expected version/);
  assert.throws(
    () => validateVersionEvidence("Radio-Oracle installed package smoke\npackageVersion=1.0.53\n", "1.0.53"),
    /missing displayVersion=/
  );
});

test("validates exact source package identity for legacy published probes", () => {
  const directory = mkdtempSync(join(tmpdir(), "radio-oracle-source-package-test-"));
  const metadata = join(directory, "package.json");
  try {
    writeFileSync(metadata, "{\"name\":\"radio-oracle\",\"version\":\"1.0.52\"}\n");
    assert.doesNotThrow(() => validateSourcePackageMetadata(metadata, "1.0.52"));
    assert.throws(() => validateSourcePackageMetadata(metadata, "1.0.51"), /expected radio-oracle/);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
