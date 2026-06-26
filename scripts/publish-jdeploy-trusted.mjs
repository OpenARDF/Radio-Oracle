#!/usr/bin/env node

import { spawnSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const packageJson = JSON.parse(readFileSync("package.json", "utf8"));
const packageName = packageJson.name;
const packageVersion = packageJson.version;
const expectedGitHead = process.env.GITHUB_SHA || "";

function log(message) {
  console.log(`[publish-jdeploy] ${message}`);
}

function fail(message, exitCode = 1) {
  console.error(`[publish-jdeploy] ERROR: ${message}`);
  process.exit(exitCode);
}

function publishedPackage() {
  const result = spawnSync("npm", ["view", `${packageName}@${packageVersion}`, "--json"], {
    encoding: "utf8",
    env: process.env
  });
  if (result.status !== 0) {
    return null;
  }
  try {
    return JSON.parse(result.stdout);
  } catch {
    return null;
  }
}

function verifyPublishedPackage(context) {
  const published = publishedPackage();
  if (!published) {
    return false;
  }
  if (published.name !== packageName || published.version !== packageVersion) {
    fail(`${context}: npm registry returned ${published.name}@${published.version}, expected ${packageName}@${packageVersion}`);
  }
  if (expectedGitHead && published.gitHead && published.gitHead !== expectedGitHead) {
    fail(`${context}: published gitHead ${published.gitHead} does not match workflow commit ${expectedGitHead}`);
  }
  if (!published.dist?.tarball || !published.dist?.integrity) {
    fail(`${context}: published package is missing dist tarball or integrity metadata`);
  }
  if (!published.dist?.attestations?.provenance) {
    fail(`${context}: published package is missing npm provenance attestation metadata`);
  }
  log(`${context}: verified ${packageName}@${packageVersion} is published with provenance.`);
  return true;
}

if (process.env.RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH !== "1") {
  fail("Refusing to publish. Set RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH=1 for an intentional release.");
}

if (verifyPublishedPackage("preflight")) {
  process.exit(0);
}

const tempDir = mkdtempSync(join(tmpdir(), "radio-oracle-npm-publish-"));
const npmrcPath = join(tempDir, ".npmrc");
writeFileSync(npmrcPath, "registry=https://registry.npmjs.org/\n", "utf8");

try {
  const publishEnv = {
    ...process.env,
    NPM_CONFIG_USERCONFIG: npmrcPath
  };
  delete publishEnv.NODE_AUTH_TOKEN;

  log(`Publishing ${packageName}@${packageVersion} with GitHub OIDC trusted publishing.`);
  const result = spawnSync("npm", ["publish", "--access", "public", "--provenance"], {
    env: publishEnv,
    stdio: "inherit"
  });

  if (result.status === 0) {
    verifyPublishedPackage("post-publish");
    process.exit(0);
  }

  log(`npm publish exited ${result.status}; checking whether the package was published before npm failed.`);
  if (verifyPublishedPackage("post-failure")) {
    log("Treating npm publish failure as success because the exact package version is live with provenance.");
    process.exit(0);
  }

  process.exit(result.status ?? 1);
} finally {
  rmSync(tempDir, { recursive: true, force: true });
}
