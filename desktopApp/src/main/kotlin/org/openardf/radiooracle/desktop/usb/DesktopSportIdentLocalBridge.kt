package org.openardf.radiooracle.desktop.usb

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openardf.radiooracle.desktop.DesktopAppDirectories
import org.openardf.radiooracle.shared.sportident.SportIdentOwnerNameProgramming

/** Discover only a configured local bridge; never execute it during discovery. */
internal fun discoverDesktopSportIdentSdk(
    environment: Map<String, String> = System.getenv(),
    manifest: Path = DesktopAppDirectories.appDataDirectory().resolve("sportident/bridge.json")
): DesktopSportIdentSdkConfiguration? {
    val overrideKeys = listOf("RADIO_ORACLE_SI_SDK_HELPER_DLL", "RADIO_ORACLE_DOTNET", "SPORTIDENT_SDK_LICENSE_FILE")
    return try {
        // An incomplete explicit override must not silently select another installation.
        if (overrideKeys.any(environment::containsKey)) {
            DesktopSportIdentSdkConfiguration.fromEnvironment(environment)
        } else {
            if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) || Files.size(manifest) > 4096) return null
            val installed = SportIdentOwnerNameProgramming.json.parseToJsonElement(Files.readString(manifest)).jsonObject
            if (installed.keys != setOf("SchemaVersion", "HelperExecutable", "LicenseFile")) return null
            val version = installed.getValue("SchemaVersion").jsonPrimitive
            if (version.isString || version.intOrNull != 1) return null
            val helperText = installed.getValue("HelperExecutable").jsonPrimitive
            val licenseText = installed.getValue("LicenseFile").jsonPrimitive
            if (!helperText.isString || !licenseText.isString) return null
            val helper = Path.of(helperText.content)
            val license = Path.of(licenseText.content)
            if (!helper.isAbsolute || !license.isAbsolute || !Files.isRegularFile(helper) ||
                !Files.isExecutable(helper) || !Files.isRegularFile(license) || !Files.isReadable(license)) return null
            DesktopSportIdentSdkConfiguration(listOf(helper.toString()), license)
        }
    } catch (_: Exception) { null }
}
