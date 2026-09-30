#!/usr/bin/env node

import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { chmodSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import test from "node:test";

const publishScript = resolve("scripts/publish-jdeploy-github-release.sh");

function makeFixture() {
  const directory = mkdtempSync(join(tmpdir(), "radio-oracle-release-publish-test-"));
  const releaseDirectory = join(directory, "jdeploy/github-release-files");
  const fakeBin = join(directory, "bin");
  mkdirSync(releaseDirectory, { recursive: true });
  mkdirSync(fakeBin);
  writeFileSync(join(releaseDirectory, "package-info.json"), '{"versions":["1.2.3"]}\n');
  writeFileSync(join(releaseDirectory, "jdeploy-release-notes.md"), "## Installers\n");
  writeFileSync(join(releaseDirectory, "Radio-Oracle.Installer-mac-arm64.tgz"), "installer\n");

  const fakeGh = join(fakeBin, "gh");
  writeFileSync(fakeGh, `#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >>"$FAKE_GH_LOG"
if [[ "$1 $2" == "api repos/OpenARDF/Radio-Oracle/releases/tags/jdeploy" ]]; then
  printf '%s\\n' '{"id":123,"assets":[{"id":11,"name":"package-info.json","digest":"sha256:old"},{"id":22,"name":"package-info-2.json","digest":"sha256:old"}]}'
elif [[ "$1 $2" == "api repos/OpenARDF/Radio-Oracle/releases/123" ]]; then
  printf '%s\\n' 'sha256:old'
elif [[ "$1 $2" == "release view" ]]; then
  exit 1
fi
`);
  chmodSync(fakeGh, 0o755);
  return { directory, fakeBin, logPath: join(directory, "gh.log") };
}

test("publishes metadata defensively and creates a missing draft release", () => {
  const fixture = makeFixture();
  try {
    execFileSync("bash", [publishScript], {
      cwd: fixture.directory,
      env: {
        ...process.env,
        PATH: `${fixture.fakeBin}:${process.env.PATH}`,
        FAKE_GH_LOG: fixture.logPath,
        GH_TOKEN: "test-token",
        GITHUB_REF_NAME: "v1.2.3",
        GITHUB_REF_TYPE: "tag",
        GITHUB_REPOSITORY: "OpenARDF/Radio-Oracle",
        RADIO_ORACLE_ALLOW_GITHUB_RELEASE_PUBLISH: "1"
      },
      stdio: "pipe"
    });
    const calls = readFileSync(fixture.logPath, "utf8");
    assert.match(calls, /api --method DELETE repos\/OpenARDF\/Radio-Oracle\/releases\/assets\/11/);
    assert.match(calls, /release upload jdeploy jdeploy\/github-release-files\/package-info.json/);
    assert.match(calls, /api --method DELETE repos\/OpenARDF\/Radio-Oracle\/releases\/assets\/22/);
    assert.match(calls, /release create v1\.2\.3 .*--draft/);
    assert.match(calls, /release upload v1\.2\.3 .*--clobber/);
    assert.match(calls, /release edit v1\.2\.3 .*--notes-file/);
  } finally {
    rmSync(fixture.directory, { recursive: true, force: true });
  }
});

test("refuses publication without the workflow-only opt-in", () => {
  const fixture = makeFixture();
  try {
    const result = spawnSync("bash", [publishScript], {
      cwd: fixture.directory,
      env: {
        ...process.env,
        PATH: `${fixture.fakeBin}:${process.env.PATH}`,
        FAKE_GH_LOG: fixture.logPath,
        GH_TOKEN: "test-token",
        GITHUB_REF_NAME: "v1.2.3",
        GITHUB_REF_TYPE: "tag",
        GITHUB_REPOSITORY: "OpenARDF/Radio-Oracle"
      },
      encoding: "utf8"
    });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /RADIO_ORACLE_ALLOW_GITHUB_RELEASE_PUBLISH/);
  } finally {
    rmSync(fixture.directory, { recursive: true, force: true });
  }
});
