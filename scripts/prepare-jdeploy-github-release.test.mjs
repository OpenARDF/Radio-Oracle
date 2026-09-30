#!/usr/bin/env node

import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  validateReleaseContext,
  validateReleaseFiles,
  withGithubReleaseMetadata
} from "./prepare-jdeploy-github-release.mjs";

const environment = {
  GITHUB_REF_TYPE: "tag",
  GITHUB_REF_NAME: "v1.2.3",
  GITHUB_REPOSITORY: "OpenARDF/Radio-Oracle",
  GITHUB_SHA: "0123456789abcdef",
  GH_TOKEN: "test-token"
};
const packageJson = {
  name: "radio-oracle",
  version: "1.2.3",
  devDependencies: { jdeploy: "6.1.3" },
  jdeploy: { javaVersion: "17" }
};

test("validates a matching tag release context", () => {
  assert.equal(validateReleaseContext(environment, packageJson), "v1.2.3");
  assert.throws(
    () => validateReleaseContext({ ...environment, GITHUB_REF_TYPE: "branch" }, packageJson),
    /only for a tag workflow/
  );
  assert.throws(
    () => validateReleaseContext({ ...environment, GITHUB_REF_NAME: "v1.2.4" }, packageJson),
    /does not match package version/
  );
  assert.throws(
    () => validateReleaseContext({ ...environment, GH_TOKEN: "" }, packageJson),
    /requires GITHUB_SHA, GITHUB_REPOSITORY, and GH_TOKEN/
  );
});

test("adds only jDeploy release metadata", () => {
  const prepared = withGithubReleaseMetadata(packageJson, environment);
  assert.equal(prepared.jdeploy.jdeployVersion, "6.1.3");
  assert.equal(prepared.jdeploy.commitHash, environment.GITHUB_SHA);
  assert.equal(prepared.jdeploy.gitTag, environment.GITHUB_REF_NAME);
  assert.equal(prepared.jdeploy.javaVersion, "17");
  assert.equal(packageJson.jdeploy.commitHash, undefined);
  assert.throws(
    () => withGithubReleaseMetadata({ ...packageJson, devDependencies: {} }, environment),
    /must pin the jDeploy dependency/
  );
});

test("requires the release files consumed by publication", () => {
  const directory = mkdtempSync(join(tmpdir(), "radio-oracle-jdeploy-test-"));
  try {
    for (const fileName of ["jdeploy-release-notes.md", "package.json"]) {
      writeFileSync(join(directory, fileName), "{}\n");
    }
    assert.throws(() => validateReleaseFiles(directory), /package-info.json/);
    writeFileSync(join(directory, "package-info.json"), "{}\n");
    assert.doesNotThrow(() => validateReleaseFiles(directory));
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});
