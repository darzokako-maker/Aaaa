#!/system/bin/sh
# Root HID Remote USB gadget setup for Android/Linux kernels with configfs.
# Run as root on the phone before using /dev/hidg0 and /dev/hidg1.
set -eu
G=/config/usb_gadget/root_hid_remote
UDC_PATH=/sys/class/udc

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
printf '\x05\x01\x09\x06\xa1\x01\x05\x07\x19\xe0\x29\xe7\x15\x00\x25\x01\x75\x01\x95\x08\x81\x02\x95\x01\x75\x08\x81\x01\x95\x05\x75\x01\x05\x08\x19\x01\x29\x05\x91\x02\x95\x01\x75\x03\x91\x01\x95\x06\x75\x08\x15\x00\x25\x65\x05\x07\x19\x00\x29\x65\x81\x00\xc0' > functions/hid.keyboard/report_desc

mkdir -p functions/hid.mouse
# protocol 2 = mouse, subclass 1 = boot interface
echo 2 > functions/hid.mouse/protocol
echo 1 > functions/hid.mouse/subclass
echo 4 > functions/hid.mouse/report_length
printf '\x05\x01\x09\x02\xa1\x01\x09\x01\xa1\x00\x05\x09\x19\x01\x29\x03\x15\x00\x25\x01\x95\x03\x75\x01\x81\x02\x95\x01\x75\x05\x81\x01\x05\x01\x09\x30\x09\x31\x09\x38\x15\x81\x25\x7f\x75\x08\x95\x03\x81\x06\xc0\xc0' > functions/hid.mouse/report_desc

ln -sf functions/hid.keyboard configs/c.1/
ln -sf functions/hid.mouse configs/c.1/

UDC="${1:-}"
if [ -z "$UDC" ]; then
  UDC=$(ls "$UDC_PATH" | head -n 1 || true)
fi
if [ -z "$UDC" ]; then
  echo "No UDC found. Connect USB/device-mode capable cable and retry." >&2
  exit 1
fi
echo "$UDC" > UDC
chmod 666 /dev/hidg0 /dev/hidg1 2>/dev/null || true
echo "Ready: keyboard=/dev/hidg0 mouse=/dev/hidg1"
