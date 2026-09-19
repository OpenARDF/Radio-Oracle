package org.openardf.radiooracle.desktop.usb

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopSportIdentLocalBridgeTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun discoversAnInstalledExecutableWithoutStartingItOrRequiringDotnet() {
        val fixture = fixture()
        val configuration = discoverDesktopSportIdentSdk(emptyMap(), fixture.manifest)
        assertEquals(listOf(fixture.helper.toString()), configuration?.commandPrefix)
        assertEquals(fixture.license, configuration?.licenseFile)
        assertFalse(Files.exists(fixture.started))
    }

    @Test fun missingMalformedOversizedAndUnsupportedManifestsDisableProgramming() {
        val fixture = fixture()
        Files.delete(fixture.manifest)
        assertNull(discoverDesktopSportIdentSdk(emptyMap(), fixture.manifest))
        for (text in listOf("not JSON", "x".repeat(4097), fixture.json(version = 2),
                fixture.json().dropLast(1) + ",\"UnknownField\":1}")) {
            Files.writeString(fixture.manifest, text)
            assertNull(discoverDesktopSportIdentSdk(emptyMap(), fixture.manifest))
        }
    }

    @Test fun missingLicenseAndNonExecutableHelpersDisableProgramming() {
        val fixture = fixture()
        assertTrue(fixture.helper.toFile().setExecutable(false, false))
        assertNull(discoverDesktopSportIdentSdk(emptyMap(), fixture.manifest))
        assertTrue(fixture.helper.toFile().setExecutable(true, true))
        Files.delete(fixture.license)
        assertNull(discoverDesktopSportIdentSdk(emptyMap(), fixture.manifest))
    }

    @Test fun relativePathsAndSymlinkedManifestsAreRejected() {
        val fixture = fixture()
        Files.writeString(fixture.manifest, fixture.json(helper = "relative-helper"))
        assertNull(discoverDesktopSportIdentSdk(emptyMap(), fixture.manifest))
        Files.writeString(fixture.manifest, fixture.json())
        val link = fixture.manifest.resolveSibling("linked.json")
        Files.createSymbolicLink(link, fixture.manifest)
        assertNull(discoverDesktopSportIdentSdk(emptyMap(), link))
    }

    @Test fun completeExplicitEnvironmentOverridesTheInstalledBridge() {
        val fixture = fixture()
        val dll = temporary.newFile("override.dll").toPath()
        val env = mapOf("RADIO_ORACLE_SI_SDK_HELPER_DLL" to dll.toString(),
            "RADIO_ORACLE_DOTNET" to fixture.helper.toString(), "SPORTIDENT_SDK_LICENSE_FILE" to fixture.license.toString())
        assertEquals(listOf(fixture.helper.toString(), dll.toString()),
            discoverDesktopSportIdentSdk(env, fixture.manifest)?.commandPrefix)
    }

    @Test fun incompleteOrBrokenExplicitEnvironmentNeverFallsBackToAnInstallation() {
        val fixture = fixture()
        for (environment in listOf(mapOf("SPORTIDENT_SDK_LICENSE_FILE" to fixture.license.toString()),
                mapOf("RADIO_ORACLE_DOTNET" to "\u0000"), mapOf("RADIO_ORACLE_SI_SDK_HELPER_DLL" to ""))) {
            assertNull(discoverDesktopSportIdentSdk(environment, fixture.manifest))
        }
    }

    private fun fixture(): Fixture {
        val root = temporary.newFolder("private bridge").toPath()
        val helper = root.resolve("helper")
        val started = root.resolve("started")
        Files.writeString(helper, "#!/bin/sh\ntouch '${started}'\n")
        assertTrue(helper.toFile().setExecutable(true, true))
        val fixture = Fixture(helper, temporary.newFile("license-placeholder.txt").toPath(), root.resolve("bridge.json"), started)
        Files.writeString(fixture.manifest, fixture.json())
        return fixture
    }

    private data class Fixture(val helper: Path, val license: Path, val manifest: Path, val started: Path) {
        fun json(version: Int = 1, helper: String = this.helper.toString()) = buildJsonObject {
            put("SchemaVersion", version)
            put("HelperExecutable", helper)
            put("LicenseFile", license.toString())
        }.toString()
    }
}
