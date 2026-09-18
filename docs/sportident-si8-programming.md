# SI-Card8 programming investigation

The desktop inspector and name preview work with SI-Card8. Card writes are
not implemented. Name preparation remains in shared Kotlin code for future
Android reuse.

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
control-punch pointer, feedback bytes, and character set. The helper is separate
from the desktop app; shared Kotlin name preparation remains reusable by Android.

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
and interruption recovery still need hardware validation before app integration.

The supplied library's API documentation exposes personal-data programming,
validation, progress, and completion facilities. The Mac SDK transaction now
establishes a tested integration route on the available hardware; Radio-Oracle
does not yet invoke the writer. The documentation inspected does not explain
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

Next, integrate an explicit desktop app action targeting the
freshly read card, followed by read-back verification of its number and names.
This workflow primarily targets new cards. If writing clears existing punches,
disclose that before writing. SDK completion alone is insufficient evidence
that the desired names were stored.

Vendor archives, extracted API documentation, binaries, and credentials stay
outside this repository. No binary inspection, decompilation, or serial command
guessing was used in this investigation.
