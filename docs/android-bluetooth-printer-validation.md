# Android Bluetooth Printer Validation

Use this checklist before beta with the paired Bluetooth ESC/POS printer.

## Setup

- Pair the Bluetooth printer in Android system settings before opening
  Radio-Oracle.
- Connect the Android tablet to ADB, preferably wireless debug for live logcat.
- Install a current debug build of Radio-Oracle.
- Watch printer logs:

```shell
adb logcat -v time -s Printer DebugLog
```

## Validation Steps

1. Open Radio-Oracle Settings > Prints.
2. Enable printing.
3. Open Select printer and confirm the paired Bluetooth printer appears.
4. Select the printer and confirm logcat records the selected printer name and
   masked address.
5. Open or create a race with at least one readout.
6. Use readout detail > Print ticket.
7. Confirm a ticket prints and logcat records printer initialization, finish
   ticket print settings, print submission, and successful submission.
8. Enable double printing with a short delay and print again.
9. Confirm two tickets print and the delay matches the setting.
10. Toggle Remove diacritics and print a ticket containing accented text if a
    test race is available.

## Failure Evidence

If printing fails, capture:

- whether the printer is still paired and powered on;
- the selected printer name in Settings;
- relevant `Printer` logcat lines;
- whether the failure occurs during printer initialization or print submission;
- the on-screen toast text.

Bluetooth printer failures should not block SPORTident readout or result
recording.
