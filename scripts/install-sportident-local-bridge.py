#!/usr/bin/env python3
"""Install a privately built, self-contained Mac bridge; never copy license text."""

import argparse
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import uuid


def install(source: Path, license_file: Path, app_data: Path) -> None:
    source = source.resolve(strict=True)
    license_file = license_file.resolve(strict=True)
    required = (
        "SportIdentSdkProbe", "SportIdentSdkProbe.dll", "SportIdentSdkProbe.deps.json",
        "SportIdentSdkProbe.runtimeconfig.json", "SPORTident.Communication.dll",
        "libhostfxr.dylib", "libcoreclr.dylib",
    )
    if any(not (source / name).is_file() for name in required):
        raise ValueError("A complete self-contained Mac publish folder is required.")
    if not os.access(source / "SportIdentSdkProbe", os.X_OK):
        raise ValueError("The published helper must be executable.")
    if not license_file.is_file() or not os.access(license_file, os.R_OK):
        raise ValueError("A readable private license file is required.")
    if license_file.is_relative_to(source):
        raise ValueError("Keep the license outside the publish folder.")
    runtime = json.loads((source / "SportIdentSdkProbe.runtimeconfig.json").read_text())
    if not runtime.get("runtimeOptions", {}).get("includedFrameworks"):
        raise ValueError("The helper must include its runtime.")

    root = app_data.resolve() / "sportident"
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    versions = root / "bridges"
    versions.mkdir(parents=True, exist_ok=True, mode=0o700)
    version = versions / uuid.uuid4().hex
    # The active manifest changes only after every dependency has been copied.
    shutil.copytree(source, version)
    version.chmod(0o700)
    manifest = json.dumps({
        "SchemaVersion": 1,
        "HelperExecutable": str(version / "SportIdentSdkProbe"),
        "LicenseFile": str(license_file),
    })
    if len(manifest.encode("utf-8")) > 4096:
        raise ValueError("The local bridge configuration exceeds its size limit.")
    fd, temporary = tempfile.mkstemp(prefix=".bridge-", dir=root)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as output:
            output.write(manifest)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, root / "bridge.json")
    finally:
        Path(temporary).unlink(missing_ok=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("publish_folder", type=Path)
    parser.add_argument("--license-file", type=Path, required=True)
    arguments = parser.parse_args()
    if sys.platform != "darwin":
        parser.error("This installer currently supports macOS only.")
    try:
        install(arguments.publish_folder, arguments.license_file,
                Path.home() / "Library/Application Support/Radio-Oracle")
    except (OSError, ValueError):
        print("Local bridge installation failed; the active configuration was not replaced.", file=sys.stderr)
        return 1
    print("Private local bridge installed. Restart Radio-Oracle normally to use it.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
