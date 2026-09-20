# SPORTident SDK engineering probe

Optional desktop engineering tool to verify licensed vendor-library station
communication before implementing card writes. It uses documented SDK APIs,
reads station information, checks the expected station number, and closes the
connection. The station read waits at most ten seconds. The station/card-read
commands do not program cards. An explicitly selected write command is
available for controlled SI-Card8 tests; no command configures stations or
updates firmware.

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

## Controlled owner-name write

The optional write command requires a local JSON request. It reads a freshly
inserted SI-Card8, checks its identity and existing names, validates the new
default-character-set names (23 characters combined, with an exact byte
round-trip check), and invokes the documented SDK writer
once with Auto apply disabled and feedback editing unset. After SDK completion,
it closes and reopens the serial connection and verifies the station again.
It then prompts for removal/reinsertion and compares the fresh read's identity,
names, punch values, feedback bytes,
and reported character set. A removal, different card, communication failure,
or timeout invalidates the current session. Removal during the write stops the
transaction; the separate read-back stage explicitly requires reinsertion and
cannot make another write. It never retries the write command automatically.
Each insertion wait is 45 seconds; station reads and write completion each
have a ten-second limit.

Example request, saved outside the repository:

```json
{
  "SchemaVersion": 1,
  "StationNumber": 593927,
  "CardNumber": 2450662,
  "ExpectedFirstName": "Mickey",
  "ExpectedLastName": "Mouse",
  "FirstName": "Minnie",
  "LastName": "Mouse",
  "AcceptPossiblePunchLoss": true
}
```

```sh
just sportident-sdk-card-write /dev/cu.SLAB_USBtoUART 593927 2450662 /private/test-write.json
```

This is an actual card-programming action. The request must explicitly accept
possible punch loss. Successful read-back emits a JSON result including before
and after control-punch counts and `PunchesPreserved`, `FeedbackPreserved`, and
`CharacterSetPreserved`. Exit zero also requires those comparisons to match
and the serial connection to close. A failure after a write attempt leaves
the outcome uncertain; independently read the card before deciding what to do.
No-op requests fail before writing because they cannot demonstrate programming.

Exit code zero requires a matching station read and a successful serial close.
Wrong station, missing license, timeout, or communication failure returns a
nonzero exit code. The probe uses 38400 baud, as verified with the available
BSM8 UART1 USB station. Other station settings have not been characterized.

Verified on macOS ARM64 with .NET runtime 10.0.7 and the privately supplied
SPORTident library 2.59.0 internal test release dated 2024-05-10. The card-read
command returned the expected card 2450662,
stored names `Mickey` / `Mouse`, and 11 control punches on this hardware.
The write command changed the test names to `Minnie` / `Mouse`, verified by a
separate read-only process. An immediate same-session read-back had timed out,
so the transaction now reopens the serial connection and requests reinsertion.
A separately approved return to `Mickey` / `Mouse` passed the complete revised
transaction with exit zero: 11 control punches before/after, matching captured
punch values, feedback bytes, and reported character set. Unexposed card data
was not compared; mid-write removal has not been tested on hardware.
The SDK helper and its .NET runtime are retained only for local protocol
investigation and comparison. The desktop product no longer discovers or invokes
the helper. Build and run this command-line probe using the private SDK reference
and license described above; do not place its published files in a product bundle.

## Historical desktop bridge tests

Earlier private Mac prototypes invoked this helper through a supervised process
and a local `sportident/bridge.json` manifest. Those bridge classes now live in
test-only Kotlin sources, and desktop package checks reject them and SDK/.NET
payloads. The following results document the historical prototype behavior; they
are not instructions for enabling SDK writing in the current desktop app.

The packaged local Mac app has also completed a user-confirmed write from `Mickey`
/ `Mouse` to `Mortimer` / `Mouse` on card 2450662, with independent read-back and
all 11 control punches, captured punch values, feedback bytes, and reported
character set preserved. The Race File was unchanged.

The desktop app offers cancellation during insertion/read-back waits, with the
button disabled during the SDK write. An incomplete attempt leaves a local
recovery reminder that survives app restarts. Read the same card, review its
stored names, and choose Accept Card Read before preparing another write. This
acknowledges the fresh names; it does not verify preservation of earlier punches
or settings. The saved request is never replayed automatically.

A controlled hardware recovery test changed card 2450662 from `Mortimer` / `Mouse`
back to `Mickey` / `Mouse` through a native helper harness, then closed its
supervision pipe at WaitingForReadBack. The helper exited with code 15 after SDK
write completion. The restarted app retained the reminder and blocked writing;
a fresh native read confirmed the requested names, and Accept Card Read cleared
the reminder. Earlier punch/settings preservation was not verified. This test
did not exercise clicking the GUI Stop Verification button or removing the card
during the write.

The corrected GUI Stop Verification flow also passed a separate hardware retest
in desktop version 1.0.49j. Card 2450662 changed from `Mortimer` / `Mouse` to
`Daisy` / `Duck`; Stop Verification was clicked after write completion and before
SDK read-back. The helper exited and the page enabled native Read Card while
blocking another write. A fresh read confirmed the target card and requested
names; Accept Card Read cleared the reminder and restored the editor without an
app restart. Earlier punch/settings preservation was not verified in this test,
and physical interruption during writing remains untested.

Desktop invocation adds `--supervised` after the request-file argument. It keeps
the helper's stdin pipe open; pipe closure or any input terminates the helper
with exit code 15, including after an abrupt app crash. The direct command-line
write recipe retains its existing unsupervised behavior. Run
`just sportident-sdk-supervision-check` for the SDK-free supervision checks;
this separate .NET project has no proprietary references or serial access.

Rebuilding the local Mac bundle changes its ad hoc code signature. macOS can
request Documents-folder access again before reopening the saved Race File;
the app can appear stalled until that system prompt is answered.
