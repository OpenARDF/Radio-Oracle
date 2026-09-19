#!/usr/bin/env python3
"""Exercise private installation with placeholders; no SDK, license, or device needed."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import sys
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("bridge_install", Path(__file__).with_name("install-sportident-local-bridge.py"))
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class InstallationTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        root = Path(self.folder.name)
        self.source = root / "publish"
        self.source.mkdir()
        for name in ("SportIdentSdkProbe", "SportIdentSdkProbe.dll", "SportIdentSdkProbe.deps.json",
                     "SPORTident.Communication.dll", "libhostfxr.dylib", "libcoreclr.dylib"):
            (self.source / name).write_text("placeholder")
        (self.source / "SportIdentSdkProbe").chmod(0o700)
        (self.source / "SportIdentSdkProbe.runtimeconfig.json").write_text(json.dumps({
            "runtimeOptions": {"includedFrameworks": [{"name": "Microsoft.NETCore.App", "version": "10.0.7"}]}}))
        self.license = root / "private-placeholder.txt"
        self.license.write_text("license placeholder remains outside the installation")
        self.app_data = root / "app-data"

    def manifest(self):
        return self.app_data / "sportident/bridge.json"

    def test_install_keeps_license_external_and_replaces_only_the_active_pointer(self):
        bridge.install(self.source, self.license, self.app_data)
        first = json.loads(self.manifest().read_text())
        self.assertEqual(str(self.license.resolve()), first["LicenseFile"])
        helper = Path(first["HelperExecutable"])
        self.assertTrue(helper.is_file())
        self.assertEqual(0o600, self.manifest().stat().st_mode & 0o777)
        self.assertFalse((helper.parent / self.license.name).exists())
        bridge.install(self.source, self.license, self.app_data)
        second = json.loads(self.manifest().read_text())
        self.assertNotEqual(first["HelperExecutable"], second["HelperExecutable"])
        self.assertTrue(helper.is_file())

    def test_failed_copy_preserves_the_existing_configuration(self):
        bridge.install(self.source, self.license, self.app_data)
        previous = self.manifest().read_bytes()
        with patch.object(bridge.shutil, "copytree", side_effect=OSError("fixture failure")):
            with self.assertRaises(OSError):
                bridge.install(self.source, self.license, self.app_data)
        self.assertEqual(previous, self.manifest().read_bytes())

    def test_incomplete_runtime_or_embedded_license_cannot_replace_configuration(self):
        bridge.install(self.source, self.license, self.app_data)
        previous = self.manifest().read_bytes()
        embedded = self.source / "license-placeholder.txt"
        embedded.write_text("placeholder")
        with self.assertRaises(ValueError):
            bridge.install(self.source, embedded, self.app_data)
        (self.source / "SportIdentSdkProbe.runtimeconfig.json").write_text('{"runtimeOptions":{"framework":{}}}')
        with self.assertRaises(ValueError):
            bridge.install(self.source, self.license, self.app_data)
        self.assertEqual(previous, self.manifest().read_bytes())


if __name__ == "__main__":
    unittest.main()
