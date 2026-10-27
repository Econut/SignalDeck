# W6 replacement — dump and root

Follow this file when the replacement phone arrives. The dead unit stays unplugged. Nothing in this file
sends `adb reboot autodloader`, writes the watch SPL, or flashes the dead phone.

The dead phone's stock images are already in gitignored `temp/w6-root/dump-20261024/`. Checksums:
`docs/w6/reports/20261024_root/dump/SHA256SUMS`. How they were read, and why that phone cannot boot, is in
`ROOT_ATTEMPT_2026-10-24.md`. `UBUNTU_DUMP_HANDOFF_2026-10-24.md` and the command block in
`UBUNTU_DUMP_RESUME_2026-10-24.md` are history. Do not run them.

## What this phone is

| Fact | Dead unit (2026-10-24) |
|---|---|
| Display id | `W6+_V1.0_UHF_JG_FT13_R_202605251847` |
| Fingerprint | `UNISOC/sl8541e_1h10_32b_Natv/sl8541e_1h10_32b:12/SP1A.210812.016/58:user/release-keys` |
| Board / hardware | `sl8541e_3h10_32b` |
| Product | `sl8541e_3h10_go_32b` |
| Platform | `sp9832e` (SL8541E / SC9832E) |
| u-boot board string | `Spreadtrum sl8541e_3h10_32b Board`, build `jenkins-NV6-FT13-FT13-ANALOG-JIGUANG-3.0-58` |
| Android | 12, SDK 31, 32-bit (`armeabi-v7a`) |
| Slot | `_a` |
| Bootloader | unlocked, `ro.boot.verifiedbootstate=orange`, `flash.locked=0` |
| SELinux | permissive. Shell is uid 2000. `su` is absent until Magisk is installed. |
| Fastboot | USB `18d1:4ee0`. `fastboot flash` works. `fastboot oem pull` and `fastboot fetch` do not read. `fastboot getvar all` wedges the write endpoint. |
| Stock `boot_a` | `temp/w6-root/dump-20261024/boot_a.img`, 67108864 bytes, sha256 `3693852f8b6a32b2e9e24dc497c1d26ee7f8fcf40552ae18dc36f2c85704ae0d`. Header v4, lz4 newc-cpio ramdisk. This is the Magisk input. |
| Stock `vbmeta_a` | same directory, `vbmeta_a.img`, sha256 `3f13e968717f771601ad8f7112401c39b18277698d44cb566bd47d22f39b24d4`. Flags are 0. `boot` is a signed chain. |

The fingerprint says `1h10`. u-boot says `3h10`. Trust the board property and the display id, not the fingerprint's board token.

## Do not

- Do not run `adb reboot autodloader`. On this uboot that command erases `splloader` and `splloader_bak`. The dead phone is the result: cold boot is mask BootROM `SPRD3`, both copies are 256 KiB of zeros, and Android never starts. A long-press of power does not bring that phone back.
- Do not address the Armor (`192.168.1.125`, wifi adb). Every `adb` and `fastboot` line names the replacement's serial.
- Do not `adb uninstall`.
- Do not run `fastboot getvar all`.
- Do not read `flyscale_misc/af_en` or `cat` a vendor sysfs directory. That read panics this kernel.
- Do not key the transmitter. A transmit test, if one is ever needed, is the owner's Faraday-cage setup.
- Do not write `temp/w6-root/dw99_head.pac` or its `u-boot-spl.bin`. That SPL is a DW99 watch (`sl8541e_1h10`), its DHTB hash is zero, and this BootROM did not run the watch's signed FDL until the check was patched in RAM. The patch does not survive a power cycle.
- Do not flash the dead phone in this session. Unplug it before starting so a `1782:4d00` device cannot be mistaken for the replacement.

## 1. Identity, before any reboot

Unplug the dead phone. Plug in only the replacement. `adb devices` must show one USB serial that is not the Armor.

```bash
export W6=<serial from adb devices>
adb -s "$W6" shell getprop ro.build.display.id
adb -s "$W6" shell getprop ro.build.fingerprint
adb -s "$W6" shell getprop ro.product.board
adb -s "$W6" shell getprop ro.product.name
adb -s "$W6" shell getprop ro.boot.slot_suffix
adb -s "$W6" shell getprop ro.boot.verifiedbootstate
adb -s "$W6" shell getprop ro.boot.flash.locked
adb -s "$W6" shell getprop ro.boot.vbmeta.device_state
```

Write the eight lines into `temp/w6-root/replacement/identity.txt` (the directory is gitignored).

Gate:

| Result | What to do |
|---|---|
| Display id is exactly `W6+_V1.0_UHF_JG_FT13_R_202605251847`, board `sl8541e_3h10_32b`, slot `_a`, verified boot `orange`, `flash.locked` `0` | Continue at step 2. The dead unit's `boot_a.img` is the image to patch. |
| Display id differs, or the board is not `sl8541e_3h10_32b` | Stop. Do not flash `boot_a.img`. Do not enter download mode. Say what the eight lines were. Fastboot cannot read this phone's own boot, and shell cannot either (mode `0600`). |
| Verified boot is not `orange`, or `flash.locked` is not `0` | Stop before any flash. An unlocked bootloader was the condition the patched image relies on. Record the values. |
| `adb devices` is empty or the only extra device is `192.168.1.125` | The replacement is not the target yet. Do not fall back to another serial. |

## 2. Patch the stock boot

Host-side stock file, unchanged:

```bash
mkdir -p temp/w6-root/replacement
cp -a temp/w6-root/dump-20261024/boot_a.img temp/w6-root/replacement/boot_a.img
cp -a temp/w6-root/dump-20261024/vbmeta_a.img temp/w6-root/replacement/vbmeta_a.img
sha256sum temp/w6-root/replacement/boot_a.img
# 3693852f8b6a32b2e9e24dc497c1d26ee7f8fcf40552ae18dc36f2c85704ae0d  boot_a.img
adb -s "$W6" push temp/w6-root/replacement/boot_a.img /sdcard/Download/boot_a.img
```

On the phone, install a current Magisk app (header v4 has been supported since Magisk 24; the phone is 32-bit). Magisk → Install → "Select and Patch a File" → `/sdcard/Download/boot_a.img`. It writes `magisk_patched-*.img` under `/sdcard/Download/`.

```bash
adb -s "$W6" shell ls -l /sdcard/Download/magisk_patched-*.img
adb -s "$W6" pull /sdcard/Download/magisk_patched-*.img temp/w6-root/replacement/
```

Keep the stock `boot_a.img` beside the patched file. Do not overwrite it.

## 3. Flash the patched boot

`adb reboot bootloader` is the fastboot path. It is not autodloader. On the dead unit this command enumerated `18d1:4ee0` and the phone could return to Android.

```bash
adb -s "$W6" reboot bootloader
fastboot -s "$W6" devices
fastboot -s "$W6" getvar current-slot
fastboot -s "$W6" getvar unlocked
fastboot -s "$W6" getvar is-userspace
```

Expect `current-slot: a`, `unlocked: yes`, `is-userspace: no`. One variable at a time. Never `getvar all`.

If `is-userspace` is `yes`, this is fastbootd, not the bootloader. `fastboot reboot bootloader` and check again.

```bash
fastboot -s "$W6" flash boot_a temp/w6-root/replacement/magisk_patched-*.img
fastboot -s "$W6" reboot
```

If `flash boot_a` reports an unknown partition, use `fastboot -s "$W6" flash boot <patched>` (the active slot). Do not flash `vendor_boot`. The generic ramdisk is in `boot_a`.

Wait for Android. `adb -s "$W6" shell su -c id` should print `uid=0`. The Magisk app should show installed. Then go to step 5 and pull `splloader` from the running system. That is the dump. Download mode is not required.

## 4. If the patched boot does not start

`vbmeta_a` flags are 0 and `boot_a` carries a signed hash. This bootloader has only been seen booting the stock signed image, so a patched boot can be rejected. Recovery stays in fastboot as long as `splloader` was not wiped.

Get the serial back as a fastboot device (it often sits there when AVB fails). If the screen is stuck elsewhere, a long-press of power is the recovery that returned this model to Android when the boot image was still stock. Once `fastboot -s "$W6" devices` lists it:

First restore stock, so a bad patch is not the thing left installed:

```bash
fastboot -s "$W6" flash boot_a temp/w6-root/replacement/boot_a.img
```

Confirm the phone boots again before the second try. The second try is the same patched boot plus a vbmeta flash that asks the bootloader to skip verification. This is a write. Do it only after stock boot has just been shown to work, and keep the stock `vbmeta_a.img` as the way back.

```bash
fastboot -s "$W6" --disable-verity --disable-verification flash vbmeta_a temp/w6-root/replacement/vbmeta_a.img
fastboot -s "$W6" flash boot_a temp/w6-root/replacement/magisk_patched-*.img
fastboot -s "$W6" reboot
```

If `--disable-verity` is rejected by this fastboot, stop. Do not hand-edit `vbmeta`. Flash stock `boot_a` again if the patched image is what is installed.

Way back to the images that were dumped:

```bash
fastboot -s "$W6" flash boot_a temp/w6-root/replacement/boot_a.img
fastboot -s "$W6" flash vbmeta_a temp/w6-root/replacement/vbmeta_a.img
fastboot -s "$W6" reboot
```

The plain `vbmeta_a` flash writes the original file, flags still 0.

## 5. Dump splloader from the rooted phone

Do this after `su` works. Do not reboot to download mode to get these bytes.

`splloader` is the eMMC boot area, not a `by-name` link. The dead unit's copy was 256 KiB of zeros; a live one starts with `DHTB` and the hash bytes are not zero.

```bash
mkdir -p temp/w6-root/replacement
adb -s "$W6" shell su -c 'dd if=/dev/block/mmcblk0boot0 of=/sdcard/Download/splloader.bin'
adb -s "$W6" shell su -c 'dd if=/dev/block/mmcblk0boot1 of=/sdcard/Download/splloader_bak.bin'
adb -s "$W6" pull /sdcard/Download/splloader.bin temp/w6-root/replacement/splloader.bin
adb -s "$W6" pull /sdcard/Download/splloader_bak.bin temp/w6-root/replacement/splloader_bak.bin
adb -s "$W6" shell rm /sdcard/Download/splloader.bin /sdcard/Download/splloader_bak.bin
python3 - << 'PY'
import pathlib
for name in ("splloader.bin", "splloader_bak.bin"):
    b = pathlib.Path("temp/w6-root/replacement", name).read_bytes()
    print(name, len(b), b[:4], "zero" if b == bytes(len(b)) else "nonzero", "hash0" if b[8:40] == bytes(32) else "hash-set")
PY
sha256sum temp/w6-root/replacement/splloader.bin temp/w6-root/replacement/splloader_bak.bin | tee temp/w6-root/replacement/SHA256SUMS
```

A good dump is `DHTB`, not all zeros, and the 32 bytes at offset 8 are not zero. Copy `SHA256SUMS` to `docs/w6/reports/` under a new date directory. Leave the binaries in `temp/`. Do not commit them.

Also record `adb -s "$W6" shell su -c id` and `getprop ro.build.display.id` in the changelog row for that session.

## 6. Only if the replacement is already in download mode

Skip this section when the phone is in Android. Rooting does not need it. Use it only when the phone is already `1782:4d00` and nobody has run `adb reboot autodloader` on it.

`spd_dump` is `/home/joshua/w6-dump/spreadtrum_flash/spd_dump` (sha1 `2a202404`). Working directory is `temp/w6-root/run/` so `custom_exec_no_verify_4ee8.bin` resolves. Stop ModemManager first. Run as root.

Connect once with no FDL and no reboot command. The first banner line is the result that matters.

- `SPRD4` / `AutoD` means uboot's autodloader handler ran. `splloader` should still be present.
- `SPRD3` with a non-zero `splloader` means mask BootROM and the boot area is still intact.
- `SPRD3` and a `splloader` of zeros means the wipe already happened. Stop. Do not write the watch SPL.

Read `splloader` before the big images. `exec_addr` is `0x4ee8` ( `0x4f18` times out on this ROM). Do not send `reset` or `poweroff`: this FDL1 ignores `reset` and the USB link wedges. Do not run `partition_list` (the watch FDL2 answers `incompatible partition` and the GPT read fails; named `read_part` still works). There is no quit command; the process sits at `FDL2 >` after the reads. Stop it after `Read Part Done` for `splloader_bak`. One `--reconnect` can finish a later read. A second one is not reliable.

```bash
cd /mnt/hgfs/Documents/code/personal/SignalDeck/temp/w6-root/run
systemctl stop ModemManager
mkdir -p /tmp/w6-replacement
/home/joshua/w6-dump/spreadtrum_flash/spd_dump --wait 30 --verbose 0 \
  exec_addr 0x4ee8 \
  fdl fdl1-sign.bin 0x5000 \
  fdl fdl2-sign.bin 0x9efffe00 \
  exec \
  timeout 60000 \
  disable_transcode \
  blk_size 0xf000 \
  path /tmp/w6-replacement \
  read_part splloader 0 256K splloader.bin \
  read_part splloader_bak 0 256K splloader_bak.bin
```

Check the two files are `DHTB` before any other read. Copy them into `temp/w6-root/replacement/` and hash them. Leave download mode with a long-press of power, about 10 seconds, cable unplugged, then plug back in and wait for adb. Do not send `poweroff` from spd_dump.

## 7. Dead unit, later

Not this session. After step 5 has a non-zero `DHTB` `splloader`, the dead phone can take that image in `splloader` and `splloader_bak` only. It is still `1782:4d00` / `SPRD3`. Its `misc` asks for one boot into fastboot (`bootonce-bootloader`) and slot `_a` is active, so the first successful start should land in fastboot. Writing it is a separate decision. The watch SPL is not a substitute.

## Tools left from the dead unit

| What | Where |
|---|---|
| Read-only images | `temp/w6-root/dump-20261024/` |
| FDL blobs and the 8-byte bypass | `temp/w6-root/run/` (`fdl1-sign.bin`, `fdl2-sign.bin`, `custom_exec_no_verify_4ee8.bin`) |
| `spd_dump` | `/home/joshua/w6-dump/spreadtrum_flash/spd_dump` |
| udev | `/etc/udev/rules.d/99-unisoc-dl.rules` for `1782:4d00` |
