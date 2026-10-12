#!/usr/bin/env node

import assert from "node:assert/strict";
import test from "node:test";

import { describesDirectPushException } from "./release-workflow-policy.mjs";

test("detects direct-push exceptions regardless of phrase order", () => {
  for (const text of [
    "Use a direct-push exception for Development1.",
    "Use a direct push exception for Development1.",
    "Create an exception that permits a direct push to Development1.",
    "Development1 permits an exception for direct pushing during releases.",
    "Allow a direct push to Development1 as an exception.",
  ]) {
    assert.equal(describesDirectPushException(text), true, text);
  }
});

test("allows instructions that prohibit direct pushes and exceptions", () => {
  assert.equal(
    describesDirectPushException(
      "Do not introduce branch-rule exceptions or direct Development1 pushes for release evidence.",
    ),
    false,
  );
});
