#!/usr/bin/env node

if (process.env.RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH !== "1") {
  console.error("Refusing to publish Radio-Oracle jDeploy package.");
  console.error("Set RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH=1 when performing an intentional release.");
  process.exit(1);
}
