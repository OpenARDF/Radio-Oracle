# Radio-Oracle 1.0.52 ARM64 Retrospective Verification

Date: 2026-10-01

This record supplements, but does not rewrite, the completed v1.0.52 release. The public tag and assets were not moved, replaced, or modified for these retrospective checks.

## Identity

- Public release: [v1.0.52](https://github.com/OpenARDF/Radio-Oracle/releases/tag/v1.0.52)
- Immutable release tag commit: `1c017b9931da009d38e4eef0c2a308a648f9eaea`
- Hosted ARM64 smoke implementation tested at: `bd22b8dc87cb98f432458ddeeaf687b7ec3ffb2d`
- Runners: native GitHub-hosted `ubuntu-24.04-arm` and `windows-11-arm`
- Linux ARM64 asset: `Radio-Oracle.Installer-linux-arm64-1.0.52_26CY.tar.gz`, SHA-256 `f48d410f5d85e945bdb4583669cbee739a61a08a595389809f70bfc6423b1bd5`
- Windows ARM64 asset: `Radio-Oracle.Installer-win-arm64-1.0.52_26CY.exe`, SHA-256 `7cb24ab47638d2e82f5e66d012623dcb3f60eb8098766e4795b4553aef5f4fbd`

## Results

| Check | Result | Evidence |
| --- | --- | --- |
| Linux ARM64 candidate package install, exports, version 1.0.53, and launch | Passed | [Run 36958667822](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36958667822) |
| Windows ARM64 candidate package install, exports, version 1.0.53, and launch | Passed | [Run 36958667821](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36958667821) |
| Published v1.0.52 Linux ARM64 installer bootstrap, install, exports, version, and launch | Passed | [Run 36959015205](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36959015205) |
| Published v1.0.52 Windows ARM64 installer bootstrap | Failed before application installation | [Run 36959013821](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36959013821) |

Both candidate runs asserted an ARM64 Node process before preparing or installing the candidate. The Windows candidate additionally verified Microsoft Java 17 running as `aarch64`. The existing Linux x64 and Windows x64 installed-package jobs also passed in those runs.

The Linux release run downloaded exactly one matching published ARM64 archive. Its required `--jdeploy:update` upgraded the bootstrap to jDeploy Installer 6.1.7, `--jdeploy:command=install` reported `Installing Radio-Oracle 1.0.52` and completed, and the source-qualified installed launcher passed the existing representative exports and launch checks. The installed jDeploy manifest independently matched the GitHub source, `radio-oracle` package identity, version 1.0.52, source-qualified package name, and ARM64 architecture.

The Windows release run downloaded exactly one matching published ARM64 executable. The exact executable exited with status 1 during `--jdeploy:update`, before `--jdeploy:command=install`, application launch, or candidate-package preparation. The workflow did not fall back to candidate evidence.

## Interpretation

Radio-Oracle's candidate package passes on native Windows ARM64, while the exact published Windows ARM64 v1.0.52 installer fails at the same required bootstrap stage previously observed for SerialSlinger. Linux ARM64 passes for both the candidate and exact published installer. Together with the matching published Windows ARM64 bootstrap size and digest already observed across the two applications, this is strong evidence of a shared jDeploy Windows ARM64 bootstrap defect rather than a Radio-Oracle application/runtime incompatibility. It remains an evidence-based inference, not a confirmed jDeploy root cause.

The published Windows ARM64 v1.0.52 installer remains a failed release verification gate and must not be represented as passed or skipped. A future jDeploy fix should be verified with a newly generated installer in a later release; the immutable v1.0.52 tag and assets should remain unchanged.

## Remaining Warning

Candidate `npm ci` logs retain deprecation warnings for `glob@7.2.3` and `inflight@1.0.6` through the pinned `jdeploy@6.1.7 -> shelljs@0.8.5` dependency chain. They are pre-existing upstream packaging-tool warnings, not compiler or application warnings, and were not converted into passed checks. Updating or overriding that transitive chain is outside this conservative verification change because it could alter jDeploy packaging behavior; it remains an explicit upstream-dependency exception.
