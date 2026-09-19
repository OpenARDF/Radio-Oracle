# SI-Card8 programming investigation

The desktop inspector and name preview work with SI-Card8. The private Mac SDK
bridge can write names, but a direct Kotlin card writer is not implemented.
Name preparation remains in shared Kotlin code for future Android reuse.

## Verified name limit

On 2026-09-18, a temporary Mac console probe called the documented field-support,
field-length, and personal-data validation APIs in the supplied SPORTident
Communication library, version 2.59.0 (internal test release dated 2024-05-10).
The probe used the default character set and ASCII names. It did not connect
to a station or call a write method.

The validator reports a combined first-name plus last-name maximum of **23
characters** for SI-Card8. Its character count excludes separators. Individual
first-name and last-name limits are also 23, and club is unsupported.

| First-name length | Last-name length | Vendor result | Reported count |
| --- | --- | --- | --- |
| 0 | 0 | Valid | 0 |
| 11 | 11 | Valid | 22 |
| 12 | 11 | Valid | 23 |
| 23 | 0 | Valid | 23 |
| 0 | 23 | Valid | 23 |
| 12 | 12 | Too long | 24 |
| 24 | 0 | Too long | 24 |
| 0 | 24 | Too long | 24 |

This replaces the initial conservative preview rule of 24 bytes including two
separators, which unnecessarily rejected names totaling 23 characters. The
preview still accepts only printable ASCII excluding semicolons and removes
outer spaces explicitly. Accented-name write encoding has not been verified.

The shared planner's encoded text is for round-tripping through the existing
read parser. It is **not a write payload**. Neither the read parser's larger
owner-data region nor the validator's character count establishes a writable
memory range, separator/terminator format, padding, or serial command.

## Write support and remaining questions

### Config+ hardware read verification

On 2026-09-18, SPORTident Config+ 2.12.0 in a Windows ARM64 VM read a
BSM8 UART1 USB station running firmware 657 and an SI-Card8 through its
Config SI-Cards view. Windows used the official SPORTident USB driver released
2026-06-09, which supports ARM64. Config+ showed blank first/last names and
11 existing punch records. Only first and last names were supported; the
other personal-data fields were marked unsupported.

After explicit approval of a test write and possible punch loss, Config+
programmed the fictional first/last names `Mickey` / `Mouse` using its default
MCP7830-compatible character set. Auto apply and feedback editing remained
disabled. The resulting read entry showed the same card number, both desired
names, and 11 punch records. The displayed clear, check, start, and finish
values matched the earlier read. A separate read after physical removal and
reinsertion, with personal-data editing and Auto apply disabled, confirmed the
same card number, `Mickey` / `Mouse`, 11 records, and unchanged displayed clear,
check, start, and finish values. Duplicate entries were enabled in Config+ to
make this identical repeat read visible. Individual control-punch records have
not been compared.

This establishes a working vendor-application write path with the available
station and card, without clearing the reported punch count in this trial.
It does not establish Mac write support, raw write framing, or punch
preservation for every programming operation. Owner editing was disabled
after the test and remained disabled during the separate read-only verification.

### Integration questions

The supplied library's native serial transport was verified on macOS ARM64
using .NET runtime 10.0.7 and Microsoft's System.IO.Ports 10.0.7 dependency.
A licensed, bounded read-only probe opened `/dev/cu.SLAB_USBtoUART` at 38400
baud, read the expected BSM8 UART1 USB station number and product through
`GetSystemData` / `StationConfigRead`, and closed the connection. Radio-Oracle
was closed and the station was disconnected from the Windows VM for exclusive
serial access. No card-programming API was called by the Mac probe.

The documented custom communication handler also opened and closed on macOS
in an offline probe. It required the same System.IO.Ports dependency even
without a native serial handler. The working native transport makes a custom
serial implementation unnecessary for the first desktop integration.

The optional [SDK station probe](../tools/sportident-sdk-probe/README.md) makes
the hardware check repeatable using private SDK/license file paths. Its
repository version was built without warnings and verified against the real
station. A wrong expected station returned failure and closed the serial
connection. Missing-license and invalid-argument cases also returned failure;
none of these trials exposed license values. The desktop app does not yet
invoke or package this tool.

The next read-only slice was verified on the same Mac and station. The
`sportident-sdk-card-read` command verified the station, waited for a fresh
insertion, checked the expected SI-Card8 number and family, and called the
documented `ReadCurrentSiCard(CardsReadMode.ReadCards)` API. Its completion
reported card 2450662, first name `Mickey`, last name `Mouse`, and 11 control
punches, matching the Config+ trial. The connection closed successfully. The
probe emits a versioned JSON read result for a future desktop bridge; the
card-read command has no programming or station-configuration action.
The updated probe built without warnings. Invalid/zero card arguments,
missing license, a real station-number mismatch, and the 45-second wait
without reinsertion all failed as expected; the hardware failures closed
the connection without emitting a card result. Different-card and
mid-read removal guards were implemented but had not been exercised on
hardware at that earlier read-only slice.

### Mac SDK write hardware verification

The optional `sportident-sdk-card-write` command now accepts an explicit local
request tied to a station number, SI-Card8 number, existing first/last names,
replacement names, and acceptance of possible punch loss. It starts with a
fresh card read, validates the replacement through the SDK, disables Auto
apply, leaves feedback editing unset, and calls
`SetSiCardPersonalDataAndFeedback` once. It waits for SDK completion, then
reopens the serial connection, checks the station again, and prompts for a
fresh insertion to check the card identity and stored names. Immutable
before/after snapshots compare control-punch values and clear/check/start/finish
records, including their reserve values, plus the reported clear counter,
control-punch pointer, feedback bytes, and character set. The helper runs as a
separate process; the optional desktop prototype now invokes it. Shared Kotlin
name preparation and transaction validation remain reusable by Android.

The writer built without warnings. Eight malformed-request cases were rejected
before license configuration or serial access. On real hardware, a request for
card 2450663 was refused when the available card 2450662 was inserted, and the
serial connection closed before any write invocation.

After explicit approval, the Mac SDK programmed `Mickey` / `Mouse` to `Minnie` /
`Mouse` on card 2450662. The SDK reported write completion, but an immediate
same-session read-back request timed out. No write retry was made. A separate
read-only process, after physical removal/reinsertion, confirmed the same card,
`Minnie` / `Mouse`, and 11 control punches. Individual before/after punch values
could not be compared in this attempt because its original snapshot was in
the terminated writer process.

The helper was adjusted to use a separate serial session and fresh insertion
for read-back while retaining immutable snapshots in memory. After separate
explicit approval of possible punch loss and a return from `Minnie` / `Mouse`
to `Mickey` / `Mouse`, the full transaction succeeded with exit code zero.
The read-back confirmed card 2450662 and the desired names, 11 control punches
before and after, and matching immutable punch snapshots, feedback bytes, and
reported character set. Thus the compared individual control-punch values,
clear/check/start/finish and reserve records, clear counter, and control-punch
pointer were preserved in this trial. Unexposed card data was not compared.

This proves the Mac SDK write and independent read-back path on the available
SI-Card8/BSM8 hardware. The helper does not automatically retry a write after
timeout, removal, or failure; a write attempt with uncertain outcome requires
an independent card read before deciding whether to proceed. Mid-write removal
and interruption recovery still need hardware validation before production use.

The supplied library's API documentation exposes personal-data programming,
validation, progress, and completion facilities. The Mac SDK transaction now
establishes a tested integration route on the available hardware. The documentation inspected does not explain
punch preservation guarantees or the raw write
transaction. The supplied deprecated PC Programmer's Guide describes card
readout, but does not specify a personal-data write command.

Request current programming documentation or a supported SI-Card8 write example
from [SPORTident developer support](https://www.sportident.com/support/developers)
covering:

- SI-Card8 personal-data command framing, addressing, acknowledgements, and
  compatible download stations and firmware.
- Exact first/last-name representation, separators, terminators, padding,
  encoding, and reserved bytes.
- Whether programming clears punches, and how to preserve card identity and
  unrelated settings.
- Card removal, timeout, interruption, and retry behavior; a write must never
  silently move to a different card.

If an SDK bridge is used instead of a documented serial command, obtain a
supported release version and verify interruption behavior. Account for packaging
the .NET runtime/library and injecting a licensed key without adding secrets
to public source. A desktop bridge would be separate from the shared Kotlin
planner and would require a separate Android integration later.

### Optional desktop programming prototype

The desktop SI Card page can now offer Write Names when launched with privately
configured SDK helper, runtime, and license-file paths. Normal launches retain
the native owner-information reader and name preview. No SDK binaries or license
values are included in the Mac bundle or repository.

A native read captures the card number, station number, and port. The confirmation
shows the replacement names and requires acceptance of possible punch loss. The
helper reads that same card again and checks its existing names before writing.
The app holds its existing SI transport mutex until the helper exits and cleanup
finishes, preventing station polling or another read from opening the same port.
It requests reinsertion for independent verification and accepts success only
after matching identity, names, punch counts, and all preservation flags.

Timeout, cancellation, malformed output, or helper failure clears the editor's
snapshot and requires a fresh native read. The child process is stopped and its
temporary request deleted before releasing the mutex. No automatic write retry
occurs. Shared Kotlin contains the request/result models, name/consent rules,
progress sequence, and read-back validation; desktop contains process management
and UI. Android transport integration remains separate.

Five shared transaction tests and seven desktop child-process tests pass,
including wrong identity, missing/reordered progress, nonzero exit, timeout,
cancellation during startup and writing, process termination, and request cleanup.
The local Mac distributable builds and verifies successfully. In the running Mac
app, Charles entered `Mortimer` / `Mouse` and confirmed the possible-punch-loss
dialog for card 2450662. After both requested reinsertions, the app reported
"Names written and verified. 11 control punches preserved." Its owner details
showed `Mortimer` / `Mouse`; the result also passed feedback, reported character
set, and captured punch-value preservation checks. The Race File remained clean
(Save Race disabled). This verifies the app-to-helper transaction on the available
SI-Card8/BSM8 hardware, without claiming hardware interruption/recovery coverage.

### Cancellation and recovery slice

The desktop prototype now offers Cancel Programming while waiting for a card and
Stop Verification while waiting for read-back. Cancellation is disabled during
the SDK write itself. Page changes and app shutdown also stop the helper; they
do not trigger another write.

Before starting the SDK process, the app atomically saves the intended station,
card, original names, and replacement names in local application data. Failure to
save prevents process startup. A fully verified result clears the record. Any
incomplete attempt retains it across page changes and app restarts and prevents
another write until recovery finishes. Corrupt or unreadable records block
programming rather than disappearing silently.

Recovery uses the existing native Read Card action. Shared Kotlin distinguishes
the requested names, original names, other stored names, a different card, and an
incomplete owner read. Only a complete read of the target SI-Card8 enables
Accept Card Read. This explicit acknowledgement clears the reminder; it does not claim
preservation of earlier punches or other settings, because the SDK's original
immutable snapshot is unavailable after an interrupted process. The saved
request is never replayed, and every later write requires a new confirmation.

The programming/recovery gate now includes 23 tests: eight shared
programming/recovery tests, eight desktop child-process tests, and seven
persistent recovery-store/cleanup tests. These cover
recreating the store after an incomplete attempt, refusing another write,
identity/readiness checks, corrupt records, failed persistence, stale
acknowledgements, and cancellation while waiting for insertion or read-back.
The desktop also passes `--supervised` to the helper and keeps its stdin pipe
open. The helper exits on EOF, unexpected input, or pipe failure, releasing its
serial handles even if the app terminates without running shutdown hooks. Three
SDK-free .NET supervision checks pass: an open pipe keeps the child alive, pipe
closure/input stops it, and abrupt termination of a fixture parent stops its
child. The private helper rebuild passes with zero warnings/errors.

The approved controlled stop-after-write test passed on SI-Card8 2450662 and
BSM8 station 593927. A native helper harness changed `Mortimer` / `Mouse` to
`Mickey` / `Mouse`, then closed the supervision pipe at WaitingForReadBack,
after SDK write completion. The helper exited with code 15. The intended
request remained in the recovery store across app restart; programming stayed
blocked until a fresh native card read showed `Mickey` / `Mouse` and
Accept Card Read was selected. The acknowledgement removed the saved reminder
and restored the editor. This validates helper shutdown after writing and app
recovery, not a manual click on Stop Verification. Earlier punch/settings
preservation was not verified for this interrupted transaction. An earlier
attempt that did not complete also retained its reminder; a fresh read still
showed the original names. Physical interruption during the write remains untested.

The compact desktop UI shows the read/edit/write steps, stored names, both name
fields side by side, and Write Names without scrolling at the tested 1436 × 768
window size. Status instructions explain the fresh insertion before writing,
reinsertion for independent verification, and the distinction between writing
completion and verified success. The Mac package build and focused desktop
process/recovery/navigation tests pass; the rebuilt app is running locally.

An approved GUI test changed card 2450662 back to `Mortimer` / `Mouse` and clicked
Stop Verification after the app reported write completion. The SDK helper exited,
but the page stayed on Stopping with Read Card disabled. Cancellation interrupted
the return from the disk reload before the UI flags could reset. The fix keeps
the reload and UI reset together inside a non-cancellable context; a regression
test cancels a suspended transaction and checks that its completion callback
restores the UI while retaining the pending request. Focused desktop tests and
Mac packaging pass. The rebuilt app, version 1.0.49j, retained the reminder after
restart; a fresh native read confirmed `Mortimer` / `Mouse`, and Accept Card Read
cleared the record and restored the editor. The Race File remained unchanged.
No further write was attempted during this fix, and earlier punch/settings
preservation was not verified.

The separately approved corrected GUI retest passed on the same SI-Card8/BSM8
hardware in version 1.0.49j. The app changed `Mortimer` / `Mouse` to `Daisy` / `Duck`.
After Write completed appeared, Stop Verification was clicked before reinsertion
for SDK read-back. The helper exited, the page immediately entered recovery,
Read Card became enabled, and writing stayed unavailable. A fresh native read
confirmed card 2450662 with `Daisy` / `Duck` and the requested-name assessment.
Accept Card Read cleared the persistent record and restored both fields and the
editor without restarting the app. Save Race stayed disabled. Earlier
punch/settings preservation was not verified for this deliberately interrupted
transaction. This completes the GUI stop/recovery hardware acceptance for the
available desktop prototype, without claiming physical mid-write interruption coverage.

The SI Card page now starts listening when it opens with a connected, idle
station. It prompts for insertion without a Read Card click, shows the read
immediately, waits for removal before accepting another insertion, and releases
the station when the page closes or writing begins. It also arms automatically
after interrupted-write recovery; a reader failure offers Retry Reader. The
write confirmation and independent read-back remain separate. On 2026-09-19,
the packaged Mac app read SI-Card8 2450662 (stored names Donald Duck) on two
separate insert/remove cycles with station 554900, without a Read Card click.
Each cycle produced a successful read in the SPORTident log. Leaving the page
released the Mac serial port. This was a read-only check; automatic listening
around a real write and its interrupted-write recovery still need hardware
validation.

Next, characterize physical interruption and resolve supported SDK, runtime,
and licensed distribution packaging. This workflow primarily targets new
cards. SDK completion alone is insufficient evidence that the desired names were stored.

### Kotlin replacement verification: read parity slice

Development proceeds without requiring a vendor response. The existing SDK
remains a reference through its ordinary read API; the direct Kotlin write
transaction still needs a sufficiently characterized and permitted protocol.
No SDK binary was decompiled or instrumented. A later Config+ serial-traffic
capture is described below.

`SportIdentOwnerReadVerification` in shared Kotlin replays native block evidence
through the existing card-readout and owner-inspection parsers. It compares the
SDK probe's existing read JSON against station/card identity, first and last
names, and control punch count. It rejects unsupported versions/families,
incomplete or duplicate blocks, malformed hex, and out-of-range counts. Empty
owner fields are valid reads. The report always identifies its limited scope:
matching counts do **not** prove matching punch values, feedback, character set,
or other card settings, and matching reads do not prove a write protocol.

To collect paired reads, close the desktop app and every other station
connection first, then run these commands sequentially, reinserting the same
card for each reader. Neither command programs a card. The SDK output file must
contain the single JSON read result, without build messages.

```sh
just sportident-sdk-card-read /dev/cu.SLAB_USBtoUART 593927 2450662 > /tmp/si-sdk-read.json
just sportident-owner-capture 593927 2450662 /tmp/si-native-read.json
just sportident-owner-compare /tmp/si-sdk-read.json /tmp/si-native-read.json
```

Native capture verifies the station before starting card readout, verifies the
inserted/downloaded card, validates the completed blocks, and saves an immutable
hex snapshot to a new file. Existing evidence files are never overwritten.
Offline comparison opens no station connection and starts no SDK process. Its
CLI exit codes are 0 for a match, 2 for differences, and 1 for invalid evidence
or failed capture; Gradle/just surface nonzero exits as task failures.

Two saved native reads can also be compared byte for byte without a reader:

```sh
just sportident-owner-diff-native /tmp/si-before.json /tmp/si-after.json
```

The JSON report includes the parsed card/station identities, owner names, punch
counts, every changed block byte and offset, and a separate list of changes
outside the SI-Card8 owner region in block 0. Exit code 0 means both snapshots
were valid and a diff was produced; it does not certify a safe write. In
particular, equal punch counts cannot establish equal punch values. This command
is ready for a future raw post-write capture; the existing Config+ trace ended
before that read.

`just sportident-owner-verification-check` passes six shared comparison/replay
tests and three desktop command tests. An end-to-end offline command smoke test
also passes with synthetic files whose paths contain spaces. These fixtures are
test data, not new hardware acceptance. Paired hardware captures and richer
punch-value/settings comparisons remain the next verification slices.

### Config+ serial write observation

On 2026-09-19, an OS-level COM4 trace captured `WriteFile` calls and completed
`ReadFile` calls from SPORTident Config+ 2.12.0 running in the Windows ARM VM.
This observed the station's serial exchange without inspecting Config+ or SDK
binaries. Station 593927 and SI-Card8 2450662 were selected, Auto apply was off,
and the approved change was `Daisy` / `Duck` to `Donald` / `Duck`. The raw trace
stays outside the repository.

Immediately before writing, Config+ requested SI-Card8 blocks 0 and 1 with the
existing `0xEF` read command. Block 0 still contained `Daisy;Duck;` beginning at
offset `0x20`; block 1 contained the 11 control punches. It then sent three
`0xEA` extended frames. Each payload contained a one-byte word address followed
by four bytes of `Donald;Duck;`:

| Word address | Block-0 offsets | Four data bytes | Full transmitted frame |
| --- | --- | --- | --- |
| `0x08` | `0x20`–`0x23` | `Dona` | `ff 02 ea 05 08 44 6f 6e 61 96 4e 03` |
| `0x09` | `0x24`–`0x27` | `ld;D` | `ff 02 ea 05 09 6c 64 3b 44 9e 90 03` |
| `0x0a` | `0x28`–`0x2b` | `uck;` | `ff 02 ea 05 0a 75 63 6b 3b cf 84 03` |

Each frame's CRC matches the existing shared `SportIdentProtocol.calculateCrc`
implementation. Config+ received one CRC-valid `0xEA` response after each word:
`02 ea 03 00 0a 08 00 2e 03`,
`02 ea 03 00 0a 09 01 2e 03`, and
`02 ea 03 00 0a 0a 02 2e 03`. The response's final data byte echoes the word
address; the meaning of the preceding two data bytes is not yet established.
No write retry or card-memory erase command appeared in this transaction.

In Config+'s read-only SI-card view, a separate read at 9:10:56 AM displayed
card 2450662 as `Donald` / `Duck`, with all 11 control-punch codes and times
matching the 8:54:51 AM pre-write read. Duplicate readouts were temporarily
enabled to make the independent entry visible, then disabled again. The trace
ended before that fresh read, so it does not contain raw post-write card blocks.

This single transaction establishes the observed address-to-block mapping,
four-byte word framing, CRC vectors, and a successful name change on the tested
card and station. It does not establish handling for shorter or longer names,
empty fields, character sets beyond the tested ASCII, trailing-byte cleanup,
response error codes, interruption/retry safety, or other card families and
stations. The pre-write block contained older residual text after `Daisy;Duck;`;
this transaction wrote only the three words above. A direct Kotlin writer needs
those boundaries characterized and fresh read-back verification before it is
enabled on hardware. The observed Config+ frames also do not resolve any
licensing or distribution question for a replacement implementation.

### Shared offline write plan

`SportIdentSi8OwnerWordWritePlanner` now uses the existing shared card-read
fixture and name/consent rules to produce an offline plan. It requires a complete
SI-Card8 read, the requested station and card, exact existing first/last names,
and raw owner bytes that agree with those parsed names. For now it accepts only
a 12-byte ASCII `first;last;` string when the existing text is no longer than
12 bytes, the three-word shape observed above.
It builds the three `0xEA` frames through the shared CRC encoder. A test checks
all three frames against the independent Config+ capture and replays their word
bytes into a copy of the synthetic pre-write block; the normal Kotlin parser
then reads the requested names while the punch block remains unchanged.

The planner has no serial transport call and is not wired into desktop or
Android programming. The fixture itself carries no freshness or card-presence
guarantee; a future sender must obtain a fresh read and check card identity.
In particular, the planner refuses a shorter string whose final word would
require an unverified padding rule, or a longer existing string that could
leave trailing bytes. A second controlled capture with
a shorter name, a raw post-write block read, and response/error characterization
are needed before relaxing this limit or attempting a direct hardware write.

### SDK provenance and licensing

The library used here came from the private archive
`sportident_communication_core_2.59.0_internal_with_example.zip`, already present
in the local SPORTident reference collection. It contains the vendor DLL, API
documentation, console example, and `LICENSE.txt`. The original delivery of that
archive has not been established; version 2.59.0 is marked as an internal test
release. The public [SPORTident developer page](https://www.sportident.com/support/developers)
offers the Communication library upon request.

The agreement included in that archive permits unlimited copies bundled with a
product using a license key issued to the developer or their organisation. It
prohibits redistributing the library as a standalone product, circumventing its
licensing mechanism, reverse engineering its binaries, and suggesting SPORTident
endorsement through its trademarks. Its FAQ describes non-commercial keys as
free and perpetual. Charging third parties for the product, including pay-per-use,
requires a commercial license; charging only for services while personally using
the product, such as timekeeping, does not.

The agreement forbids publishing license keys or including them in public source.
For a packaged product it allows the key only inside a compiled executable, with
public source using a placeholder replaced at build time. The private local
prototype instead reads the existing private key file at runtime and has not
been distributed. These terms establish bundled-library permission for this
archived release; the current key's commercial status, any separate agreement,
a supported release, and compiled-key provisioning still need verification
before public product packaging. The licensing-form URL referenced by the
archive could not be loaded during this investigation.

### Private local desktop bridge installation

The Mac prototype can now discover `sportident/bridge.json` under its existing
application-data directory during normal launches. The versioned manifest stores
an absolute helper executable path and the existing private license-file path,
never license text. Explicit SDK environment settings take precedence; incomplete
or invalid overrides do not fall back to a different installation. Missing,
malformed, oversized, unsupported, or symlinked manifests disable programming.
Discovery never starts a helper or opens a station connection.

`just sportident-sdk-local-publish` builds a self-contained `osx-arm64` bridge in
ignored build output, with trimming and single-file publishing disabled.
`just sportident-sdk-local-install <private-license-file>` copies the complete
publish folder into a new private local version directory, then atomically
replaces its manifest with file permissions 0600. An incomplete package or failed
copy leaves the active manifest unchanged. The license remains at its original
path, and previous version directories remain available to already-running apps.

The build produced an approximately 79 MB ARM64 bridge including .NET 10.0.7.
Its installed executable verified station 593927 with global runtime paths
disabled and no `dotnet` executable in the probe PATH. The focused gate passes
21 desktop tests (six discovery, eight process, seven recovery/cleanup) and
three SDK-free installer tests. The Mac package build also passes. This is a
private local prototype installation, not a publicly distributed SDK package.
Version 1.0.49k was launched normally with all SDK environment overrides removed.
A fresh native read of card 2450662 showed `Daisy` / `Duck`, and the programming
editor was available through discovery of the private installation. No new
card write was performed for this packaging acceptance; the self-contained
SDK station probe and the app's native card read are separate checks.

The built and hardware-validated bridge targets Mac ARM64 only. Intel Macs,
Windows, and Linux need their own runtime/architecture builds and SDK, driver,
serial, and hardware acceptance; selecting another publish RID does not establish
compatibility. The installer currently supports macOS only. Android needs an
Android-compatible SDK/transport integration; shared Kotlin request validation,
progress/result verification, and recovery assessment remain reusable, while
this Mac executable and its installation workflow do not.

Vendor archives, extracted API documentation, binaries, credentials, and the
raw COM4 trace stay outside this repository. No vendor binary was inspected or
decompiled; the write frames above came from observed serial traffic.
