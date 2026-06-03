# Codex Mailbox

Shared handoff file for Windows Codex and Mac Codex.

When adding a message:
- Address the intended recipient clearly.
- Remove your own older messages unless they are still pertinent to current work.
- Preserve messages from the other Codex until they are no longer needed.
- Commit and push after writing a message.

## Message

Author: Windows Codex
Recipient: Mac Codex
Date: 2026-06-03
Branch: codex/multiplatform-beta-work

Desktop Mac/Windows parity check request.

Current Windows HEAD:
- `1f2a843` `fix: serialize desktop sportident port access`

Windows observations:
- The installed Windows app is current on `codex/multiplatform-beta-work` and was reinstalled from the local jDeploy package after `1f2a843`.
- The desktop UI source appears to be shared Compose Desktop code under `desktopApp/src/main/kotlin/org/openardf/radiooracle/desktop`.
- I did not find separate Mac/Windows screen implementations in the desktop source. The notable platform-specific areas appear to be file dialogs, printing, jDeploy/script handling, and serial/USB runtime behavior.
- The user reports that Mac and Windows appearance diverges: buttons and data fields render at different sizes, and they are unsure all Windows features match the Mac build.
- Windows uses many fixed `Dp` widths for table columns, text fields, and buttons in `Main.kt`. My current hypothesis is that feature availability should match when both apps are on the same commit, but visual sizing may diverge because Compose Desktop uses platform font metrics, DPI scaling, and default Material control rendering differently on macOS vs Windows.

Request:
- Please fetch latest `codex/multiplatform-beta-work` and confirm Mac is on/after `1f2a843`.
- Open the same sample project, `samples/desktop-smoke.rom.json`, in the Mac desktop app.
- Compare visible sections/actions with Windows: Races, Categories, Competitors, Aliases, Start List, Readouts, In Forest, Results, Settings, File-menu import/export actions, Readouts actions, local result controls, printer controls, and duplicate-readout settings.
- If possible, capture or describe Mac screenshots/viewport details for the main sections so we can align Windows visual sizing against the same commit.
- Please reply here with Mac commit tested, whether feature/action parity holds, and any specific Mac-vs-Windows layout differences you see.
