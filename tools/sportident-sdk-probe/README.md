# SPORTident SDK read-only probe

Optional desktop engineering tool to verify licensed vendor-library station
communication before implementing card writes. It uses documented SDK APIs,
reads station information, checks the expected station number, and closes the
connection. The read waits at most ten seconds. It has no card-programming,
station-configuration, or firmware-update action.

The optional card-read command first verifies the station, then waits up to
45 seconds for a fresh insertion of the specified SI-Card8. It checks the
detected card number and family before requesting a read, then checks them
again on completion. A different card, removal before completion, timeout,
or communication failure returns a nonzero exit code. A card already seated
when the probe starts must be removed and reinserted after the prompt.

Requirements:

- .NET 10 SDK and Microsoft's System.IO.Ports 10.0.7 NuGet package.
- A privately supplied SPORTident.Communication.dll and a license issued to you.
- The vendor-issued license text containing the C# `License.Type`,
  `License.Name`, and `License.Key` assignments.
- Exclusive serial access: close Radio-Oracle and other station software, and
  connect the USB device to the host rather than a VM before running.

Keep the DLL and license file outside this repository. Supply their file paths
through environment variables; never put the license values on the command
line. Build output copies the DLL into ignored `bin/` directories. License
values are read into memory at run time and are not embedded in source or
printed. Vendor exception messages are omitted to avoid accidental disclosure.

```sh
export SPORTIDENT_SDK_DLL="/private/sdk/SPORTident.Communication.dll"
export SPORTIDENT_SDK_LICENSE_FILE="/private/sdk/issued-license.txt"
just sportident-sdk-probe /dev/cu.SLAB_USBtoUART 593927
# Optional read of one expected SI-Card8; remove and reinsert after the prompt.
just sportident-sdk-card-read /dev/cu.SLAB_USBtoUART 593927 2450662
```

Card-read success emits one JSON object on standard output with `SchemaVersion`
(currently 1), `StationNumber`, `CardNumber`, `CardType`, `FirstName`, `LastName`,
and `ControlPunchCount`. Status messages and failures go to standard error.
The punch count covers the SDK's control-punch list. Treat the result as
successful only when the process exits zero, including serial close.

Exit code zero requires a matching station read and a successful serial close.
Wrong station, missing license, timeout, or communication failure returns a
nonzero exit code. The probe uses 38400 baud, as verified with the available
BSM8 UART1 USB station. Other station settings have not been characterized.

Verified on macOS ARM64 with .NET runtime 10.0.7 and the privately supplied
SPORTident library 2.59.0 internal test release dated 2024-05-10. The card-read
command returned the expected card 2450662,
stored names `Mickey` / `Mouse`, and 11 control punches on this hardware.
This is an investigation tool, separate from the packaged Radio-Oracle application. A
supported vendor release, runtime packaging, and license injection into a
distributed product still need to be resolved.
