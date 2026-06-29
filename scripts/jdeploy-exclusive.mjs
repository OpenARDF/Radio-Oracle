#!/usr/bin/env node

import { spawn } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { platform } from "node:os";

const [, , label, separator, command, ...commandArgs] = process.argv;

if (!label || separator !== "--" || !command) {
  console.error("Usage: node ./scripts/jdeploy-exclusive.mjs <label> -- <command> [args...]");
  process.exit(2);
}

const lockDir = resolve(".gradle", "jdeploy-release-command.lock");
const lockInfoPath = resolve(lockDir, "owner.json");
const staleAfterMs = 2 * 60 * 60 * 1000;
const timeoutMs = Number.parseInt(process.env.RADIO_ORACLE_JDEPLOY_LOCK_TIMEOUT_MS || "", 10) || 45 * 60 * 1000;
const pollMs = 1000;
const statusEveryMs = 30 * 1000;
const startedAt = Date.now();
let lockHeld = false;
let lastStatusAt = 0;

function sleep(ms) {
  return new Promise((resolveSleep) => setTimeout(resolveSleep, ms));
}

function readLockInfo() {
  if (!existsSync(lockInfoPath)) {
    return null;
  }
  try {
    return JSON.parse(readFileSync(lockInfoPath, "utf8"));
  } catch {
    return null;
  }
}

function removeLockIfStale() {
  const info = readLockInfo();
  const createdAt = Date.parse(info?.createdAt || "");
  if (!Number.isFinite(createdAt) || Date.now() - createdAt <= staleAfterMs) {
    return false;
  }
  rmSync(lockDir, { recursive: true, force: true });
  console.warn(`[jdeploy-exclusive] Removed stale jDeploy release lock from ${info?.label || "unknown command"}.`);
  return true;
}

async function acquireLock() {
  mkdirSync(dirname(lockDir), { recursive: true });
  while (true) {
    try {
      mkdirSync(lockDir);
      writeFileSync(lockInfoPath, JSON.stringify({
        label,
        command: [command, ...commandArgs],
        pid: process.pid,
        createdAt: new Date().toISOString()
      }, null, 2));
      lockHeld = true;
      return;
    } catch (error) {
      if (error?.code !== "EEXIST") {
        throw error;
      }
      if (removeLockIfStale()) {
        continue;
      }
      const elapsedMs = Date.now() - startedAt;
      if (elapsedMs > timeoutMs) {
        const info = readLockInfo();
        console.error(`[jdeploy-exclusive] Timed out waiting for jDeploy release lock held by ${info?.label || "unknown command"}.`);
        process.exit(1);
      }
      if (Date.now() - lastStatusAt >= statusEveryMs) {
        const info = readLockInfo();
        console.log(`[jdeploy-exclusive] Waiting for ${info?.label || "another jDeploy release command"} to finish before running ${label}.`);
        lastStatusAt = Date.now();
      }
      await sleep(pollMs);
    }
  }
}

function releaseLock() {
  if (!lockHeld) {
    return;
  }
  rmSync(lockDir, { recursive: true, force: true });
  lockHeld = false;
}

function runCommand() {
  return new Promise((resolveRun) => {
    const child = spawn(command, commandArgs, {
      stdio: "inherit",
      shell: platform() === "win32"
    });
    child.on("exit", (code, signal) => {
      resolveRun({ code, signal });
    });
  });
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    releaseLock();
    process.exit(signal === "SIGINT" ? 130 : 143);
  });
}

await acquireLock();
const result = await runCommand();
releaseLock();

if (result.signal) {
  process.exit(result.signal === "SIGINT" ? 130 : 143);
} else {
  process.exit(result.code ?? 1);
}
