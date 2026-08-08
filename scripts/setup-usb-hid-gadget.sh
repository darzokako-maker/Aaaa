#!/system/bin/sh
# Root HID Remote USB gadget setup for Android/Linux kernels with configfs.
# Run as root on the phone before using /dev/hidg0 and /dev/hidg1.
set -eu
G=/config/usb_gadget/root_hid_remote
UDC_PATH=/sys/class/udc

write_report_desc() {
  path="$1"
  hex="$2"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "$path" "$hex" <<'PY'
import binascii
import sys

with open(sys.argv[1], "wb") as output:
    output.write(binascii.unhexlify(sys.argv[2]))
PY
  elif command -v python >/dev/null 2>&1; then
    python - "$path" "$hex" <<'PY'
from __future__ import print_function
import binascii
import sys

with open(sys.argv[1], "wb") as output:
    output.write(binascii.unhexlify(sys.argv[2]))
PY
  else
    echo "$hex" | sed 's/../\\x&/g' | xargs printf > "$path"
  fi
}

if [ ! -d /config/usb_gadget ]; then
  echo "configfs usb_gadget not found. Kernel must support USB gadget/configfs." >&2
  exit 1
fi

mkdir -p "$G"
cd "$G"
echo 0x1d6b > idVendor
echo 0x0104 > idProduct
echo 0x0100 > bcdDevice
echo 0x0200 > bcdUSB
mkdir -p strings/0x409
echo "RootHidRemote" > strings/0x409/manufacturer
echo "KeyboardMouse" > strings/0x409/product
echo "0000001" > strings/0x409/serialnumber
mkdir -p configs/c.1/strings/0x409
echo "HID keyboard + mouse" > configs/c.1/strings/0x409/configuration
echo 120 > configs/c.1/MaxPower

mkdir -p functions/hid.keyboard
# protocol 1 = keyboard, subclass 1 = boot interface
echo 1 > functions/hid.keyboard/protocol
echo 1 > functions/hid.keyboard/subclass
echo 8 > functions/hid.keyboard/report_length
write_report_desc functions/hid.keyboard/report_desc "05010906a101050719e029e71500250175019508810295017508810195057501050819012905910295017503910195067508150025650507190029658100c0"

mkdir -p functions/hid.mouse
# protocol 2 = mouse, subclass 1 = boot interface
echo 2 > functions/hid.mouse/protocol
echo 1 > functions/hid.mouse/subclass
echo 4 > functions/hid.mouse/report_length
write_report_desc functions/hid.mouse/report_desc "05010902a1010901a1000509190129031500250195037501810295017505810105010930093109381581257f750895038106c0c0"

ln -sf functions/hid.keyboard configs/c.1/
ln -sf functions/hid.mouse configs/c.1/

UDC="${1:-}"
if [ -z "$UDC" ]; then
  UDC=$(find "$UDC_PATH" -mindepth 1 -maxdepth 1 -type l -o -type d | head -n 1 | xargs basename 2>/dev/null || true)
fi
if [ -z "$UDC" ]; then
  echo "No UDC found. Connect USB/device-mode capable cable and retry." >&2
  exit 1
fi
echo "$UDC" > UDC
chmod 666 /dev/hidg0 /dev/hidg1 2>/dev/null || true
echo "Ready: keyboard=/dev/hidg0 mouse=/dev/hidg1"
