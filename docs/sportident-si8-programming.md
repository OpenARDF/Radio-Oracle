# SI-Card8 programming investigation

The desktop product reads SI-Card8 names and previews edits, but does not yet
write names. The SDK bridge is confined to local test tooling and excluded from
the desktop product. The direct Kotlin writer remains an experimental CLI:
several short-name writes verified, but a seven-word attempt stopped with a
partially changed owner word.
Name preparation, word planning, reply validation, and recovery assessment live
in shared Kotlin code for future Android reuse.

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
preview now accepts printable ASCII plus default-character-set Western letters
that round-trip through the SDK's public printer-character conversion, excluding
semicolons. It removes outer spaces explicitly. Two accented names have also
been checked against real SI-Card8 reads below.

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

### Historical optional desktop programming prototype

Earlier desktop builds offered Write Names when launched with privately
configured SDK helper, runtime, and license-file paths. That UI path has been
removed from the product; the following is historical test evidence. No SDK
binaries or license values are included in the Mac bundle or repository.

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

The historical desktop prototype offered Cancel Programming while waiting for a card and
Stop Verification while waiting for read-back. Cancellation was disabled during
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
process/recovery/navigation tests passed in that prototype.

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
particular, equal punch counts cannot establish equal punch values. The existing
Config+ trace ended before a raw post-write read; a later Mac SDK transaction
provided a paired native capture as described below.

`just sportident-owner-verification-check` covers the shared read comparison,
write planner, reply sequence, write rehearsal, and desktop verification
commands, including the desktop fake-serial transport, read preflight, and
read-back verifier. An
end-to-end offline command smoke test
also passes with synthetic files whose paths contain spaces. These fixtures are
test data, not new hardware acceptance. The Mac paired capture below adds
byte-level evidence for one 12-to-11-byte name change. More lengths and
response/error behavior remain to be characterized.

### Paired Mac SDK write and native block reads

On 2026-09-19, native Kotlin readout captured both 128-byte blocks of SI-Card8
2450662 on Mac station 554900 while it held `Donald` / `Duck`. With explicit
approval for possible punch loss, the installed SDK helper changed the names to
`Daisy` / `Duck` in one write. A new serial session and physical reinsertion
confirmed the requested names, 11 control punches, and matching compared punch
values, feedback, and character set. A second native Kotlin readout captured both
blocks after that verification. The raw captures remain outside the repository.

The shared byte diff found changes only in block 0 at offsets `0x21`–`0x2b`.
The original 12-byte `Donald;Duck;` became the 11-byte `Daisy;Duck;` followed
by `0xEE` at offset `0x2b`. Older residual bytes beginning at `0x2c` were
unchanged, as were the rest of block 0 and all of block 1. This is evidence for
the resulting card bytes and preservation in this particular transaction; it
does not reveal the SDK's transmitted frames or prove that another station,
card, or name length behaves the same way.

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
address. The preceding two data bytes are the station code (`00 0a` for this
Config+ station), consistent with the other extended replies in the capture
and the independent [`sportident-python` frame parser](https://github.com/OpenARDF/sportident-python/blob/e92c32bb800a2c39ed39db085d834f07150eb650/sireader2.py#L1618-L1624).
Their presence does not establish whether the word write succeeded.
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

### Shared offline word-reply sequence

`SportIdentSi8OwnerWordWriteReplySequence` matches the three captured Config+
reply shapes above using the connected station's code. It requires an extended,
CRC-valid `0xEA` frame with three data bytes: the two-byte station code and the
next expected word address (`08`, `09`, or `0a`). Wrong order, duplicates, a
different station code, wrong command or length, and invalid CRC do not advance
the sequence. The checker has no serial transport, timeout, or retry behavior
and is shared with Android.

Matching all three replies means only that the observed reply pattern was seen
from the connected station. It does not confirm that a card was written. The
sender must stop on unknown replies or timeouts, avoid an
automatic write retry, and verify card identity, stored names, and preservation
through a fresh independent read.

### Shared offline write plan

`SportIdentSi8OwnerWordWritePlanner` now uses the existing shared card-read
fixture and name/consent rules to produce an offline plan. It requires a complete
SI-Card8 read, the requested station and card, exact existing first/last names,
and raw owner bytes that agree with those parsed names. For now it accepts only
11- or 12-byte ASCII `first;last;` strings when the existing text is no longer
than 12 bytes. The 11-byte form is limited to an existing 12-byte name and adds
the observed `0xEE` twelfth byte. Both produce three-word plans through the
shared CRC encoder. One test checks all
three 12-byte frames against the independent Config+ capture. Another replays
the 11-byte plan into a synthetic pre-write block containing the observed
residual owner bytes and checks the resulting owner bytes against the paired
Mac capture's byte pattern; the synthetic punch block remains unchanged.

The planner has no serial transport call. The experimental desktop CLI now
uses it; Android programming is not wired. The fixture itself carries no
freshness or card-presence guarantee; the sender obtains a fresh read and
checks card identity.
The Mac capture supports only one-byte `0xEE` padding in the third word; the
planner still refuses 10-byte and other unobserved lengths, or a longer existing
string that could leave trailing bytes. Response/error characterization,
interruption safety, and further lengths are needed before attempting a direct
Kotlin hardware write.

The shared planner can now replay its proposed word frames into a copy of a
complete before-read snapshot and compare all 256 predicted card bytes with an
independent after-read snapshot. The offline desktop command takes the exact
write request and those two native-read files:

```sh
just sportident-owner-compare-plan /tmp/si-request.json /tmp/si-before.json /tmp/si-after.json
```

The JSON report includes the planned frame hex, predicted and observed card
identities and owner names, and every differing byte. Exit code 0 means the
predicted and observed card images match, 2 means a mismatch, and 1 means
invalid evidence or an unsupported plan. The command opens no reader and sends
no write. Replaying the approved `Donald` / `Duck` to `Daisy` / `Duck` request
against the paired Mac native captures returned a match: station 554900, card
2450662, 11 control punches, and zero differing bytes across both blocks. This
confirms the planned card image for that one transaction, including unchanged
punch bytes; it does not establish which frames the SDK sent or validate a
Kotlin hardware write.

### Shared offline write rehearsal

`SportIdentSi8OwnerWriteRehearsal` combines the planner and observed reply
checker without opening a serial port. It hands out one planned frame at a time
and will not hand out the next until the expected CRC-valid reply is supplied.
No reply, a negative acknowledgement, or an unfamiliar reply permanently stops
the rehearsal, with no automatic resend. Even after all three observed replies,
its state requires an independent card read. Only a complete read with the
expected station, card, names, and every predicted byte can mark the rehearsal
verified. A mismatch or invalid read leaves it stopped. Tests replay the
captured Config+ exchange and exercise timeout, negative acknowledgement,
corrupt or out-of-order replies, cancellation, and changed punch bytes.

This is a transport-free sequence check, not an enabled writer. The fixture
cannot prove read freshness, and the reply has no proven write-success status.
A future desktop transport must verify the card just before writing,
send each frame at most once, stop on ambiguous outcomes, and obtain a fresh
independent read before reporting success.

### Internal desktop word transport

An internal desktop adapter now consumes the shared rehearsal using the existing
serial port and buffered frame reader. It writes each exact planned frame once,
examines the first reply even when its CRC is invalid, and stops on a short
write, serial exception, timeout, NAK, unfamiliar reply, or extra bytes already
buffered after a reply. Keeping one frame reader across the three words prevents
coalesced replies from being mistaken for replies to later writes. Fake-port
tests cover the captured exchange and these failure paths. At that stage, no
UI, CLI, or live card path constructed this adapter; later sections describe
its integration with preflight, read-back, and the experimental hardware CLI.

### Same-connection read preflight

An internal desktop preflight now opens the selected station, requires its exact
serial number, extended mode, and download-capable mode, then waits for a newly
inserted SI-Card8 on that same port. It checks the insert event, parsed readout,
both complete raw blocks, requested card number, punch count, current names,
and explicit punch-loss consent before handing a validated shared rehearsal
and immutable before-read snapshot to a callback. The port stays open through
the callback and closes on every exit path. Fake-port tests reject mismatched
stations, cards, names, and incomplete evidence before the callback runs.

At this stage, no application or CLI path invoked this preflight or the word
transport. The reader's completed download does not establish continued card
presence; a live
writer must handle removal between reading and writing and obtain an independent
post-write read. Later sections describe the experimental CLI and live trials.

### Independent Kotlin read-back boundary

An internal desktop verifier now waits for a removal event for the target card,
then uses the existing block reader to wait for a new insert and complete card
download on the open port. It checks the insert event, parsed readout, raw card
number and punch count against the target owned by the shared rehearsal. The
shared comparison then checks both 128-byte blocks against the planned card
image. Missing removal or reinsertion, a different card, incomplete blocks, or
changed punch bytes cannot mark the rehearsal verified. The word transport also
rejects extra bytes buffered after its third reply so they cannot disappear
when the event reader starts. Fake-port tests cover these boundaries.

### Internal desktop transaction composition

An internal transaction now composes the same-port preflight, one-shot word
exchange, and independent read-back. Its terminal outcome includes the shared
rehearsal state, stop reason, and full-block comparison when available. It only
starts read-back after all three expected replies; a missing reply stops after
one attempted word, and a missing removal event or changed non-owner byte
cannot report success. Fake-port integration tests exercise the complete order,
no automatic retry, and port cleanup on preflight rejection or transport failure.

The experimental CLI now invokes this transaction; the application UI does not.
Its fake-port tests alone do not establish continued card presence during a
write, post-write removal/reinsertion events, or that an `0xEA` reply proves
success. The live trials below add evidence for one station/card combination.

### Read-only pre-write presence recheck

A read-only diagnostic now re-requests SI-Card8 block 0 on the same open port
after a complete download, then repeats that request after the card's removal
event. On 2026-09-19, Mac station 554900 returned an exact block-0 match for
SI-Card8 2450662 while it was seated and a negative acknowledgement after it
was removed. No owner-write command was sent during this trial.

The internal transaction now makes that same exact-block recheck immediately
before its first owner word. A negative acknowledgement, timeout, invalid reply,
or changed block stops the shared rehearsal before any owner-write frame. The
check can establish that the expected card answered at that instant; it cannot
lock the card in place between the recheck and subsequent words. A removal
during an attempted word still has an uncertain outcome, and independent
read-back plus physical interruption validation remain required before live use.

### Durable native-attempt gate

The internal Kotlin transaction now reuses the desktop SDK prototype's recovery
record. It refuses to open the station when a pending or unreadable record
exists. After a fresh read and matching block-0 recheck, it saves the intended
write atomically before sending the first owner word. If any word reply or
independent read-back is missing or mismatched, or serial I/O fails, that record
remains across process restarts and prevents another write. A complete native
read-back that matches the predicted two-block image, target card, station,
and requested names clears it. Failure to clear the record remains an error;
the original disk error is not hidden by port cleanup.

The existing native Read Card and Accept Card Read recovery flow can assess a
retained request, but it does not prove punch preservation after an interruption.
The new transaction has no UI caller. Physical mid-write interruption, the timing
gap after the presence recheck, and the station's post-write event sequence
remain to be tested before enabling it.

### Read-only native-write readiness

`just sportident-owner-readiness <request-json>` runs the same recovery-state,
station, fresh-insertion, complete SI-Card8 read, current-name, supported
word-plan, and seated block-0 checks used before the internal Kotlin write.
It closes the port before any recovery intent is saved or owner-word frame is
sent. A missing or unreadable recovery record is required; an earlier pending
attempt blocks readiness. A negative acknowledgement or changed block cannot
produce a ready result.

On 2026-09-19, the command passed on Mac station 554900 with SI-Card8 2450662
and an exact hypothetical `Daisy` / `Duck` to `Donald` / `Duck` request. It read
the stored `Daisy` / `Duck` names, 11 control punches, and an identical block 0
while the card was seated. No card programming or recovery-record write occurred.
This accepts the pre-write sequence on the available hardware; it does not
validate the Kotlin word exchange or post-write event/read-back behavior.

After the SDK repair, a second read-only check passed for the proposed
`Donald` / `Duck` to `Daisy` / `Duck` request on the same Mac station and card.
It read the stored `Donald` / `Duck` name and 11 punches, matched seated block 0,
and made no owner write or recovery-record change. The frame trace showed
station code `00 0e` in the probe, insertion event, and card-block reply.

### Experimental native-write command

`just sportident-owner-native-experiment <request-json>` is an explicit desktop
CLI entry point for one native Kotlin SI-Card8 owner-write attempt. It accepts
only a valid exact-request JSON file and checks the recovery state, station,
freshly inserted card, stored names, supported word plan, and seated block 0
before persisting an intent and sending owner words. It requests card removal
after the three expected replies, then a new insertion for full two-block
comparison. It reports success only when the predicted image matches that
independent read and the recovery record is cleared. It never retries. An
uncertain outcome retains the recovery record and requires a fresh read and
explicit recovery before any further attempt.

The expected reply shape came from one observed Config+ transaction; its
success/error meaning and behavior under physical interruption are
still unknown. Run this command only as a controlled hardware experiment with
the exact target card and names reviewed beforehand.

On 2026-09-19, the first approved direct Kotlin attempt targeted station
554900 and SI-Card8 2450662, `Daisy` / `Duck` to `Donald` / `Duck`. The fresh
preflight read and seated block-0 recheck passed, and the recovery intent was
saved. The first word produced an `UNEXPECTED_REPLY`, so the sender stopped
without sending words two or three. A separate read-only native capture found
`Donay` / `Duck`: the first word had changed offsets `0x20`–`0x23` to `Dona`,
while the rest of the original name remained. Comparing both complete blocks
with the preceding `Daisy` capture found only the three differing name bytes;
all 11 punches and all non-owner bytes matched. No Kotlin retry was attempted.
This demonstrates that a word can take effect even when the observed reply is
unfamiliar.

An explicitly approved SDK repair then changed the partial `Donay` / `Duck`
to `Donald` / `Duck`. Its independent read-back verified the target names,
unchanged 11 punch values, feedback, and character set. A further native
two-block read matched the earlier known-good `Donald` / `Duck` capture byte
for byte. The offline `sportident-owner-native-recovery` command checked that
fresh complete read against the pending station/card and explicitly observed
names, then used the existing recovery-store acknowledgement to clear the
reminder. It neither opens a serial port nor writes a card.

```sh
just sportident-owner-native-recovery /private/tmp/fresh-native-read.json Donald Duck
```

This offline acknowledgement requires the existing pending record, the same
station and card, complete parseable SI-Card8 blocks, and the observed first
and last names given on the command line. As in the UI, accepting the read
resolves the reminder; it does not by itself prove preservation from the
earlier interrupted attempt.

The first attempt did not record the raw reply bytes. A read-only probe of Mac
station 554900 returned station code 14 (`00 0e`), whereas the Config+ station
used code 10 (`00 0a`). The shared reply gate now expects the connected
station's verified code. The CLI also prints each complete word reply before
checking it. At this point the mismatch was a hypothesis because the first Mac
reply had not been recorded. Do not treat a
matching reply as proof of a write; independent full-card read-back remains
required.

A separately approved second Kotlin attempt changed the same card from
`Donald` / `Duck` to `Daisy` / `Duck` on Mac station 554900. Its three raw,
CRC-valid `0xEA` replies were `02 ea 03 00 0e 08 80 35 03`,
`02 ea 03 00 0e 09 81 35 03`, and `02 ea 03 00 0e 0a 82 35 03`.
The code-14 reply gate accepted them in order. After observed removal and
reinsertion, a fresh two-block read matched the predicted card image byte for
byte: the new name was present and the 11 punches and all other bytes were
preserved. The transaction reported verified and cleared its recovery record.
This confirms the station-code mismatch as the cause of the first reply
rejection. It establishes one complete Kotlin exchange and read-back on this
station/card combination; interruption behavior, other stations, and card
families remain unverified.

### Between-word queued-input guard

The desktop serial adapter now checks the driver's already-queued input before
each owner word, including the first. Any queued byte or failure to inspect the
queue stops the one-shot attempt without sending the next word. Because intent
was persisted before the exchange, the recovery record remains pending even
when this check stops before the first word; read-back is not started.
Fake-port tests cover input before the first and second words, queue-check
failure, and transaction-level recovery persistence. A read-only probe on Mac
station 554900 found an empty queue in 0 ms, then received the complete
SI-Card8 2450662 insertion event through this nonblocking path. It sent no
owner word. This guard catches card events already waiting between replies, but
cannot prevent removal immediately after the check. Physical interruption of
an owner-word exchange remains an open hardware gate.

### Complete 11-to-12-byte Kotlin write

A second approved direct Kotlin transaction changed SI-Card8 2450662 from
`Daisy` / `Duck` (11 owner-text bytes plus `0xEE` padding) to `Donald` / `Duck`
(12 owner-text bytes) on Mac station 554900. A fresh read-only preflight matched
the stored names, 11 punches, and seated block 0. The sender then received all
three code-14 `0xEA` replies in order. Observed removal and reinsertion led to
a full two-block read matching the planned result, and the recovery record
cleared.

Separate read-only captures before and after the write were compared offline.
The three planned frames predicted the after-image exactly. The direct diff
found 11 changed bytes, all within block 0's owner-text region at offsets
`0x21`–`0x2b`; block 1 and every non-owner byte were unchanged, including the
11 recorded punches. These results establish one successful write in each
observed 11/12-byte direction on this card and station. Other name lengths,
other readers/cards, and interruption points beyond the one-word test below
still need validation before the Kotlin path is offered in the app UI.

The next interruption gate now has a durable native recovery record. Before
the first `0xEA` word, the desktop CLI atomically saves both complete pre-write
SI-Card8 blocks and the exact request. Immediately before each serial word it
atomically advances an attempted-word count. The count is an upper bound: a
crash may occur after saving it but before transmission. A failed save prevents
that word from being sent. Older SDK reminder records remain readable.

`just sportident-owner-native-assess <fresh-native-read-json>` compares a
fresh two-block read with the saved baseline and all zero-to-three-word prefix
images. It reports matching prefixes and every byte changed outside the 12
owner bytes, and retains the pending record. The read-only native capture now
saves valid full-card blocks by card identity even when partial owner bytes do
not parse cleanly; assessment compares those raw blocks. This is an offline
assessment, not proof that every attempted word reached the card. An explicit
`just sportident-owner-native-recovery` acknowledgement still requires the
observed names and clears the reminder only after a complete parseable read.
For a native attempt, it also requires a plausible word-prefix image with no
changes outside the 12 owner bytes. The desktop editor cannot clear a native
attempt from displayed names alone; its earlier name-only acknowledgement is
still available for SDK-originated reminders.

Shared Kotlin can also propose, without transmitting, three owner-word frames
that restore the saved pre-write 12 bytes when a fresh full-card image matches
a possible prefix and all other bytes match the baseline. It returns no frames
when the fresh image already equals the baseline and refuses a mismatched
station/card, impossible prefix, or non-owner byte change. This proposal uses
raw bytes, so misleading intermediate names do not become repair authority.
`just sportident-owner-native-restore-proposal <fresh-native-read-json>` prints
those frames offline and retains the recovery record. No CLI or UI path sends
the proposed restore frames; actual interruption repair still requires a
separately verified write transaction.

For the spare-card interruption test,
`just sportident-owner-native-stop-after-one <request-json>` uses the same
preflight and recovery persistence as the normal experimental writer, then
deliberately stops after the first valid word reply. It never sends words two
or three, never retries, and leaves the recovery record for independent
read-only capture and assessment. The controlled-stop mode has passed simulated
transport tests. On 2026-09-19 it was also exercised on station 554900 and
spare SI-Card8 2450662. A fresh baseline matched the prior `Donald` / `Duck`
image byte for byte with 11 punches. The command sent only word 1 of the
`Donald` / `Duck` to `Daisy` / `Duck` plan, received the expected station-code-14
reply, and stopped intentionally with a persisted upper bound of one attempted
word. After removal and reinsertion, a fresh read showed `Daisld` / `Duck`.
The offline assessment matched exactly the one-word prefix: three bytes in
block 0 changed, with zero changes outside the 12 owner bytes. The 11 punch
records matched the baseline.

After explicitly acknowledging that partial read, a separate read-only
preflight confirmed the exact `Daisld` / `Duck` to `Donald` / `Duck` repair
request. One normal Kotlin write received all three expected replies, then
verified a fresh two-block read after observed removal and reinsertion. A
further independent native capture matched the original `Donald` / `Duck`
baseline byte for byte across both blocks, including all 11 punches. This
demonstrates recovery from one deliberately interrupted word sequence on this
station and spare card.

Fake-port checks cover a deliberate stop after
two acknowledged words, a lost reply or negative/wrong-station reply at each of
the three word positions, and a simulated process failure after recording each
attempt but before sending that word. Each case leaves an upper-bound recovery
record and sends no subsequent word. The shared assessment marks a fresh image
as unexpected if it requires more words than the record permits or if bytes
outside the owner words changed. The serial adapter accepts captured code-10
and code-14 replies only when the connected station code agrees. Lost replies,
negative replies, process failure, and cross-station replies remain simulated.

On 2026-09-20, `just sportident-owner-native-stop-after-two` passed on the
same spare card and Mac station. A fresh two-block `Donald` / `Duck` baseline
was byte-identical to the earlier known-good read, including all 11 punches.
The command rechecked the seated card, received the expected code-14 replies
for words 1 and 2, and deliberately stopped without sending word 3. The
persisted recovery record bounded the attempt at two words. After removal and
reinsertion, an independent read showed `Daisy` / `Duuck` (`Daisy;Duuck;` in
the 12 owner bytes). The shared offline assessment matched exactly the
two-word prefix: seven changed owner bytes, zero changes elsewhere. The
offline baseline-restore proposal produced three frames but transmitted none.
The accepted fresh read cleared the reminder; a separate read-only preflight
then confirmed the exact `Daisy` / `Duuck` to `Donald` / `Duck` repair request.
One normal Kotlin write received all three expected replies and passed fresh
two-block readback after observed removal and reinsertion. A further separate
capture was byte-identical to the day's original baseline across both blocks,
including all 11 punches. The intentional-stop command's nonzero process exit
was expected because it retained the recovery record pending independent read.

`just sportident-owner-native-stop-after-three <request-json>` now has a
separate deliberate stop after all three valid word replies but before the
in-process removal/reinsertion readback. It retains the native recovery record
with an upper bound of three attempted words and requires an independent fresh
capture and prefix assessment. Fake-port and shared-state tests pass, including
an extra-byte case after the third reply. On 2026-09-20, the physical stop on
the spare SI-Card8 received all three expected code-14 replies, then exited
with a pending three-word recovery record before readback. After removal and
reinsertion, a separate two-block capture read `Daisy` / `Duck` with all 11
punches. The offline assessment matched only prefix 3: 11 changed bytes within
the 12 owner bytes and none elsewhere. The accepted fresh read cleared the
recovery reminder. A separate read-only preflight confirmed the exact
`Daisy` / `Duck` to `Donald` / `Duck` request; a normal Kotlin write received all three
replies and verified on fresh removal/reinsertion readback. A further
independent capture matched the original two-block `Donald` / `Duck` baseline
byte for byte, including all 11 punches. The intentional-stop command's
nonzero exit was expected because it retained the pending recovery record.

A second SPORTident reader, serial 593927, is attached to the Windows VM
rather than available as a Mac serial port. A read-only Config+ v2.12.0 direct
station read on 2026-09-19 confirmed it as BSM8 UART1 on COM4, code 10,
firmware 657, in SI-card readout mode. On 2026-09-20, Config+ added a fresh
timestamped read of SI-Card8 2450662 on this station: `Donald` / `Duck` with
11 punches. Its duplicate-readout option had to be enabled because older reads
of the same SIID were already listed. After handing its USB connection from
the VM to macOS, a Kotlin two-block capture on station 593927 matched the
station-554900 baseline byte for byte while both readers were attached. The
internal pinned-station commands now select a unique USB serial matching the
requested station and still verify the connected station before reading.

The first Kotlin owner-write attempt on station 593927 stopped after word 1:
the station returned the CRC-valid code-10 reply `00 0a 08`, but the shared
system-info parser reported code 32. A separate fresh capture read `Daisld` /
`Duck`, matching only the saved one-word prefix, with three owner bytes changed
and all 11 punches intact. Read-only station diagnostics showed this BSM8's
primary code byte was 10 while an unrelated byte at the BSF7 direct-code
offset was 32. The parser now applies that alternate offset only to BSF7
models. Shared parser and desktop compatibility tests passed, and the live
diagnostic then reported code 10. After explicit recovery acknowledgement, a
new exact `Daisld` / `Duck` to `Daisy` / `Duck` Kotlin write received all three
code-10 replies and verified on fresh removal/reinsertion readback. A separate
capture found only the expected 11 owner-byte changes from the original
`Donald` / `Duck` baseline, with no punch or other byte changes. A further
exact Kotlin write restored `Donald` / `Duck`; its fresh readback verified, and
an independent final capture matched the original station-593927 two-block
baseline byte for byte, including all 11 punches. The recovery record is clear.

### Desktop owner-name UI and variable-length comparison

The SI Card owner-name page keeps the complete raw two-block SI-Card8 read
alongside the displayed names. Its confirmation dialog no longer exposes a
backend choice or asks users to accept likely punch erasure. On this Mac, the
installed private SDK bridge handles writes. It compares the card number and
stored names before writing, makes one write, requires physical reinsertion,
and compares the new names, individual punches, feedback, and character set.
An interrupted write leaves a durable recovery reminder. The dialog supports
printable ASCII and round-trippable default-character-set Western letters
totaling up to 23 characters, excluding semicolons. SDK availability on this
Mac is not Android support or public distribution permission.

On Mac station 554900, the SDK wrote `José;Duck;` and `Bjørn;Duck;` to spare
SI-Card8 2450662 in separate one-write sessions. Each independent SDK read
returned the exact spelling and preserved all 11 individual punches, feedback,
and the reported character set. Separate Kotlin two-block reads found `é` as
`0x82` and `ø` as `0xF8`, matching
`CardPersonalData.ReplacePrinterCharsetBytes` with the default character set.
Shared Kotlin now applies that public conversion table for SI-Card8 owner
reads and write previews and rejects characters that do not round-trip or
would produce a card terminator byte. The spare was returned to `Donald;Duck;`
with another independent SDK read confirming the 11 punches. Other character
sets and Unicode outside the verified default mapping remain unsupported.

The shared Kotlin planner now handles 2- through 25-byte `first;last;` owner
text in one to seven four-byte owner words. An independent Mac SDK write of
`Donald;Duck;` to `Huey;Duck;` matched its three-word plan byte for byte, with
`0xEE` padding in the final word. `Huey;Duck;` to the 23-character
`Penny;Popandrolopoulos-J;` matched all seven planned words, including final
padding. The SDK's return from that maximum-length name to `Donald;Duck;`
matched the planner's three-word proposal: older trailing owner bytes remained
untouched. Each separate two-block capture showed the same 11 punches and no
changes outside the planned owner words.

The first direct Kotlin seven-word trial stopped at the second word after an
acknowledged first word and a negative acknowledgement for the second. A fresh
read found the first word changed as planned and owner word `0x09` containing
`EA EA EA EA`, rather than any planned whole-word prefix. The shared recovery
assessment correctly rejected it; block 1 and all 11 punches were unchanged.
The SDK read the partial names as `Pennêêêêuck` / `rolopoulos-J`, then an exact
SDK repair restored `Donald` / `Duck`. A separate native two-block read matched
the saved pretrial image byte for byte, and only then was the recovery record
cleared. No automatic retry occurred. Until the direct transport failure is
understood and retested, the real direct transaction rejects unverified lengths.
Variable-length planning remains offline and is not selected by the desktop UI.

### Config+ seven-word serial trace on station 593927

On 2026-09-20, a controlled Config+ 2.12.0 write changed spare SI-Card8
2450662 from `Donald` / `Duck` to `Penny` / `Popandrolopoulos-J` through the
VM-attached station 593927. A COM4 API trace recorded seven `WRITE_SI_CARD_WORD`
frames at addresses `0x08`–`0x0E`, each followed by a CRC-valid reply for
station code 10 and the matching address. The seven transmitted frames match
the shared Kotlin planner byte for byte, including CRCs and `0xEE` padding in
the final word. Config+ sent `FF 06` after the seventh reply; its role in this
transaction is not yet established.

The trace includes two complete prewrite reads and an independent complete
postwrite read of both 128-byte card blocks. The two prewrite images agree.
The postwrite image differs at 11 bytes, all within owner block 0 offsets
`0x20`–`0x2B`; block 1 is byte-identical, including its punch data. Config+'s
fresh card row showed the full requested name and all 11 punches. The card
was left with `Penny` / `Popandrolopoulos-J` after this test.

This establishes that the seven command frames and reply sequence are valid on
station 593927. It does not explain the earlier direct Kotlin second-word NAK
on Mac station 554900. The vendor trace advanced from each reply to the next
write quickly. The direct Kotlin transaction now durably reserves the full word
count before transmission, removing its former per-word disk sync, and buffers
per-word CLI output until the sequence ends. It records reply-to-next-write
and write-to-reply intervals for the next comparison. The saved
count is a conservative upper bound: after interruption, a fresh two-block
read must still match an actual planned prefix with no changes outside owner
words. The queued-input guard remains. Measure the direct reply-to-next-word
interval and compare station behavior before another controlled seven-word
trial. Keep the live Kotlin length gate and the product write UI disabled until
the transaction and fresh two-block verification succeed on the intended
hardware combinations.

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

### Historical private local desktop bridge prototype

This prototype is retained only for local investigation. The current desktop
product never discovers or launches its SDK helper, and its package checks reject
the bridge classes and SDK/.NET payloads. The following records how the earlier
prototype was installed and tested; it is not a product setup procedure.

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
