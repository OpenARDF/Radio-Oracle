#!/usr/bin/env node

import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const args = new Set(process.argv.slice(2));
const strict = args.has("--strict");
const maxArg = process.argv.slice(2).find((arg) => arg.startsWith("--max="));
const maxLines = Number(maxArg?.slice("--max=".length) || 150);
const roots = ["app/src", "desktopApp/src", "shared/src"];
const functionPattern =
  /^(?:public |private |internal |protected |actual |expect |suspend |inline |tailrec |operator |infix |external )*fun\b/;
const declarationBoundaryPattern =
  /^(?:public |private |internal |protected |actual |expect |suspend |inline |tailrec |operator |infix |external |data |sealed |enum |open |abstract )*(?:fun|class|object|interface)\b/;

function ktFiles(directory) {
  const entries = readdirSync(directory, { withFileTypes: true });
  return entries.flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return ktFiles(path);
    }
    return entry.isFile() && entry.name.endsWith(".kt") ? [path] : [];
  });
}

function declarationsIn(path) {
  const lines = readFileSync(path, "utf8").split(/\r?\n/);
  const declarations = [];
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (!functionPattern.test(line.trim())) {
      continue;
    }
    let end = index;
    let depth = 0;
    let foundBody = false;
    for (let bodyIndex = index; bodyIndex < lines.length; bodyIndex += 1) {
      if (!foundBody && bodyIndex > index && declarationBoundaryPattern.test(lines[bodyIndex].trim())) {
        end = bodyIndex - 1;
        break;
      }
      const bodyLine = lines[bodyIndex];
      for (const char of bodyLine) {
        if (char === "{") {
          depth += 1;
          foundBody = true;
        } else if (char === "}") {
          depth -= 1;
        }
      }
      end = bodyIndex;
      if (foundBody && depth <= 0) {
        break;
      }
    }
    declarations.push({
      path,
      start: index + 1,
      end: end + 1,
      label: line.trim(),
      lines: end - index + 1
    });
  }
  return declarations;
}

const files = roots
  .filter((root) => {
    try {
      return statSync(root).isDirectory();
    } catch {
      return false;
    }
  })
  .flatMap(ktFiles);

const oversized = files
  .flatMap(declarationsIn)
  .filter((declaration) => declaration.lines > maxLines)
  .sort((left, right) => right.lines - left.lines);

if (oversized.length === 0) {
  console.log(`No Kotlin declarations exceed ${maxLines} lines.`);
  process.exit(0);
}

console.log(`Kotlin declarations over ${maxLines} lines:`);
for (const declaration of oversized) {
  console.log(`${declaration.path}:${declaration.start} ${declaration.lines} lines ${declaration.label}`);
}

if (strict) {
  process.exit(1);
}
