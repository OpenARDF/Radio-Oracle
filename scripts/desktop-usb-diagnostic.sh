#!/usr/bin/env bash
set -euo pipefail

REQUIRE_SI=false
if [[ "${1:-}" == "--require-si" ]]; then
	REQUIRE_SI=true
fi

SI_VENDOR_ID_DEC=4292
SI_PRODUCT_ID_DEC=32778
SI_VENDOR_ID_HEX="10c4"
SI_PRODUCT_ID_HEX="800a"

if [[ "$(uname -s)" != "Darwin" ]]; then
	echo "Desktop USB diagnostic currently supports macOS only."
	echo "Expected SPORTident USB bridge VID:PID: ${SI_VENDOR_ID_DEC}:${SI_PRODUCT_ID_DEC} (0x${SI_VENDOR_ID_HEX}:0x${SI_PRODUCT_ID_HEX})"
	exit 0
fi

echo "Radio-Oracle desktop USB diagnostic"
echo "Expected SPORTident USB bridge VID:PID: ${SI_VENDOR_ID_DEC}:${SI_PRODUCT_ID_DEC} (0x${SI_VENDOR_ID_HEX}:0x${SI_PRODUCT_ID_HEX})"
echo

USB_REPORT="$(ioreg -p IOUSB -l -w 0 2>/dev/null || true)"

SI_USB_PRESENT=false
if [[ "$USB_REPORT" == *"\"idVendor\" = ${SI_VENDOR_ID_DEC}"* && "$USB_REPORT" == *"\"idProduct\" = ${SI_PRODUCT_ID_DEC}"* ]]; then
	SI_USB_PRESENT=true
	echo "SPORTident USB bridge: detected in macOS IORegistry."
else
	echo "SPORTident USB bridge: not detected by VID/PID in macOS IORegistry."
fi

echo
echo "USB serial device nodes:"
SERIAL_DEVICES=()
for pattern in /dev/cu.usbserial* /dev/cu.SLAB_USBtoUART* /dev/cu.usbmodem* /dev/cu.wchusbserial*; do
	for device in $pattern; do
		[[ -e "$device" ]] || continue
		SERIAL_DEVICES+=("$device")
	done
done

if ((${#SERIAL_DEVICES[@]} == 0)); then
	echo "- none found"
else
	for device in "${SERIAL_DEVICES[@]}"; do
		echo "- $device"
	done
fi

echo
echo "Relevant USB inventory lines:"
if [[ -n "$USB_REPORT" ]]; then
	printf '%s\n' "$USB_REPORT" | awk '
		BEGIN {
			RS = "\n[ |]*\\+-o "
			ORS = ""
		}
		NR > 1 && /SPORTident|CP210|Silicon|UART|FTDI/ {
			print "---\n" $0 "\n"
		}
	' || true
else
	echo "- ioreg returned no USB report"
fi

echo
if [[ "$SI_USB_PRESENT" == true ]]; then
	echo "Desktop USB feasibility signal: SI USB bridge is visible to macOS."
	exit 0
fi

if ((${#SERIAL_DEVICES[@]} > 0)); then
	echo "Desktop USB feasibility signal: serial devices are visible, but the exact SPORTident VID/PID is not attached."
else
	echo "Desktop USB feasibility signal: no USB serial device is visible."
fi

if [[ "$REQUIRE_SI" == true ]]; then
	exit 1
fi
