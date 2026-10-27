# W6 handoff — read-only dump on the Ubuntu VM (2026-10-24)

Historical. The dump this file describes was done, and `adb reboot autodloader` from this procedure erased `splloader` on the bench phone. Do not paste it to an agent as the task. The replacement phone follows `docs/w6/REPLACEMENT.md`.

> Read `docs/w6/UBUNTU_DUMP_HANDOFF_2026-10-24.md` only as background. Do not run its dump. Do not `adb reboot autodloader`. The blobs in `temp/w6-root/run/` stay where they are. Do not touch the Armor.

This file is the task. Phase 0 and the fastboot attempt are background in `PHASE0_FINDINGS.md` and `ROOT_ATTEMPT_2026-10-24.md`. Do not repeat them.

The guest mounts this working tree (`D:\Documents\code\personal\SignalDeck` on the host). Find the root by this file. Do not clone a second copy, and do not copy the blobs anywhere. `temp/` is gitignored and is on disk.

## Goal

Read stock images off the Anysecu W6 so a later Magisk patch has something to inspect. This session **reads and then powers off**. It does not flash, erase, repartition, patch, or install Magisk.

Success is files on disk: `splloader.bin`, `splloader_bak.bin`, `boot_a.img`, `vendor_boot_a.img`, `vbmeta_a.img`, `dtbo_a.img`, `dtb_a.img`, plus `parts.xml` and the full spd_dump log. Record the first banner (`SPRD3` or `SPRD4`).

## Phone, and the other phone

| | W6 (this task) | Armor 26 Ultra (not this task) |
|---|---|---|
| How it appears | USB adb serial `W6-UNIT1-SERIAL` | wifi adb `192.168.1.125:5555` |
| Role | Bench phone, analog UHF | Owner's daily phone, runs SignalDeck |

Every `adb` and `fastboot` invocation names `-s W6-UNIT1-SERIAL`. If that serial is absent, do not run the command against "the only device". Never `adb uninstall` (a Magisk grant and app data live on the Armor; SignalDeck was also installed on the W6 for the baseline report).

Do not key the transmitter. Do not read `flyscale_misc/af_en` and do not `cat` a vendor sysfs directory (that read panics this kernel; it has happened twice). Do not run `fastboot getvar all` (it wedges the fastboot write endpoint; recovery is a long-press of power).

## Where the phone was left

Windows got as far as Unisoc download mode and no further. When this handoff was written the W6 was enumerated on the Windows host as `USB\VID_1782&PID_4D00`, Problem `CM_PROB_FAILED_INSTALL` (code 28, no function driver). It was **not** in `adb devices`. The Armor was the only adb device.

Passing `1782:4d00` through to the VM is the intended move. Unplug/replug is fine: autodloader usually re-enumerates. If `lsusb` shows no `1782:4d00`, the phone has dropped back toward Android. Long-press power about 10 seconds, wait until `adb -s W6-UNIT1-SERIAL` shows `device`, then start `spd_dump --wait` **before** `adb reboot autodloader` (BootROM answers for about 20 seconds; autodloader stays up longer).

Last reading before that reboot: battery 61%, USB charging, `sys.boot.reason=bootloader`, `sys.usb.config=mtp,adb`.

## Why this is on Ubuntu

Fastboot on this bootloader can flash and cannot read. `fastboot oem pull boot_a` returns OKAY and writes nothing. `fastboot fetch` is unsupported.

On Windows, `spd_dump` (libusb build, sha1 `2a202404`) reached "Waiting for dl_diag connection" and died with `libusb_open_device failed`. The phone stayed in download mode. UsbDk 1.0.22 was installed and its service was Running, but `CreateFile("\\.\UsbDk")` returns error 2 (`ERROR_FILE_NOT_FOUND`), so `UsbDk_GetDevicesList` fails and libusb never sees the phone. Do not debug that on the VM.

TomKing062's own note: a `libusb_get_config_descriptor failed` on Windows is a reason to use Linux, including a VMware guest.

## Files already in this tree

Working directory for the dump is `temp/w6-root/run/`, relative to the SignalDeck root. These four files are already there. `sha256sum -c` them before the phone is touched. The Windows `spd_dump.exe`, `libusb-1.0.dll`, and `probe.exe` in that directory are the failed Windows attempt. Do not run them.

| File | Bytes | SHA-256 |
|---|---:|---|
| `fdl1-sign.bin` | 35732 | `a54c40c297ad05cbe21e3b105cc58cf52353c29c0e093507c1671978309e4c7f` |
| `fdl2-sign.bin` | 482100 | `a75f93bda3554f6ce275be224f7122f8d549b4c08371577402c8a02c4f2f850a` |
| `custom_exec_no_verify_4f18.bin` | 8 | `0158f15a960f09d1b900cb62ba2a6b52f57557b22c91c59dd9a01a29386b3ff1` |
| `custom_exec_no_verify_4ee8.bin` | 8 | same hash; the two files are the same bytes |

Both exec files are `00 52 00 00 00 00 00 00`. `spd_dump` loads whichever name matches `exec_addr`: it opens `custom_exec_no_verify_%x.bin` from the **process working directory**. Start it in the directory that contains `custom_exec_no_verify_4f18.bin`.

Both FDL binaries start with `DHTB`. They were cut out of the DW99 watch PAC `sl8541e_1h10_DW99_lineage_v1.4.pac` (SourceForge project `dw-samrtwatch-flash-file`, full file 1989894339 bytes, md5 `bd2948f9a23ebff7b54117cf819b5091`). PAC load addresses: FDL1 `0x5000`, FDL2 `0x9efffe00` (uint32 2667576832). Those match TomKing062's SC9832E / SL8541E chip table (same BootROM as SC9820E).

This is **not** an Anysecu W6 PAC. The watch is `sl8541e_1h10`. This phone's board is `sl8541e_3h10`. The owner accepted that for one read-only attempt: FDL2 runs the watch board's pin init. A foreign FDL2 that times out at `EXEC FDL1` should not have written flash. Recover with a long-press of power. Do not fetch a different phone's FDL and try again in this session.

## Build spd_dump

Match the Windows binary's revision so the command words below mean the same thing.

Build on the guest's own disk (`$HOME` or `/tmp`), not on the shared tree. A host share is often `noexec` and a poor place to compile.

```bash
sudo apt-get install build-essential libusb-1.0-0-dev git
git clone https://github.com/TomKing062/spreadtrum_flash.git
cd spreadtrum_flash
git checkout 2a202404a7512026d146b62624f089aceb408520
make
```

`spd_dump` prints `branch:stable, sha1:2a202404…` at startup. Confirm that before it touches the phone. `libusb-1.0-0` is the runtime library; the `-dev` package is for the build. Invoke the binary by its absolute path. Its working directory is still `temp/w6-root/run/` on the share, so the exec blob name resolves and the images land back on the host.

Stop ModemManager before the dump if it is installed. It probes Unisoc `1782:4d00` and steals the interface:

```bash
sudo systemctl stop ModemManager
```

## See the phone before any FDL traffic

```bash
lsusb -d 1782:4d00
```

Expect `ID 1782:4d00`. If the VM does not show it, the controller still has the device on the Windows side. Pass that USB device into the guest (VirtualBox USB filter, VMware removable-device menu, or QEMU usb-host). Do not install a Windows driver while the guest is supposed to own it.

No `1782:4d00` and no `W6-UNIT1-SERIAL` in `adb devices` means the phone is off or on a port the VM cannot see. Stop. Ask for a long-press of power. Do not address `192.168.1.125`.

## The dump

`cd` to `temp/w6-root/run/` first. Replace `./spd_dump` with the absolute path of the binary built on the guest.

```bash
./spd_dump --wait 60 --verbose 1 \
  exec_addr 0x4f18 \
  fdl fdl1-sign.bin 0x5000 \
  fdl fdl2-sign.bin 0x9efffe00 \
  exec \
  timeout 300000 \
  disable_transcode \
  blk_size 0xf000 \
  partition_list parts.xml \
  read_part splloader 0 1M splloader.bin \
  read_part splloader_bak 0 1M splloader_bak.bin \
  read_part boot_a 0 64M boot_a.img \
  read_part vendor_boot_a 0 64M vendor_boot_a.img \
  read_part vbmeta_a 0 1M vbmeta_a.img \
  read_part dtbo_a 0 8M dtbo_a.img \
  read_part dtb_a 0 8M dtb_a.img \
  poweroff
```

`blk_size 0xf000` is 61440, under the tool's 65535 maximum. Log stdout and stderr (`tee`). About 147 MiB of images; several minutes is normal.

`0x4f18` is the normal-download bypass. Use `0x4ee8` only if the banner is BootROM (`SPRD3`) and the failure is the exec blob itself, before FDL1 is executed. One such retry, still read-only. A timeout on `EXEC FDL1` is an FDL mismatch, not a reason to change `exec_addr`.

## Hard stops

- No `write_part`, `w`, `erase_part`, `e`, `erase_all`, `repartition`, `set_active`, verity disable, or `reset` that is not `poweroff`.
- No `r all` and no `r all_lite`. `r all` walks the GPT and would pull userdata.
- Do not erase `splloader` to force BootROM. `adb reboot autodloader` can wipe `splloader` when uboot has no autodloader handler. `SPRD4:AutoD` means the handler is present (spl was not wiped). `SPRD3` means BootROM. Write down which one the log shows. This 2026 uboot is likely to print `SPRD4`; that was not proven on Windows because spd_dump never opened the device.
- If `partition_list` fails or a name is wrong, spd_dump may sit in FDL2. One `--reconnect` in FDL2 is documented as unstable. Prefer a long-press of power over a second session.
- A failed `splloader` read is not followed by a write. `splloader` is a special name, not a `/dev/block/by-name` link. 1 MiB is the size the tool documents; the real partition may differ. Keep whatever was read and stop if the command errors.
- Do not try another PAC's FDL in this session.

## What the images should be

From `/proc/partitions` while the phone was in Android (1 KiB blocks):

| Name | Node | Size |
|---|---|---|
| `boot_a` (active slot) | `mmcblk0p38` | 65536 → 64 MiB, mode `0600` root |
| `boot_b` | `mmcblk0p39` | 64 MiB |
| `vendor_boot_a` | `mmcblk0p40` | 64 MiB |
| `vendor_boot_b` | `mmcblk0p41` | 64 MiB |
| `dtb_a` / `dtb_b` | `p42` / `p43` | 8 MiB |
| `dtbo_a` / `dtbo_b` | `p44` / `p45` | 8 MiB |
| `uboot_a` | `mmcblk0p8` | 1 MiB |
| `vbmeta_a` | `mmcblk0p48` | 1 MiB |
| `super` | `p46` | 2913280 |
| `userdata` | `p66` | 11913216 |

`mmcblk0` is 15388672 blocks of 1 KiB. Slot suffix is `_a`.

After a clean run, check sizes before calling it done:

| File | Expected bytes |
|---|---:|
| `boot_a.img`, `vendor_boot_a.img` | 67108864 |
| `dtbo_a.img`, `dtb_a.img` | 8388608 |
| `vbmeta_a.img` | 1048576 |
| `splloader.bin`, `splloader_bak.bin` | 1048576, or shorter if the tool stopped at the real end; say which |

`boot_a.img` should start with `ANDROID!`. `vendor_boot_a.img` should start with `VNDRBOOT` or `ANDROID!`. Record the first 8 bytes of each. A short file is a truncated read, not a stock image. Do not flash either one.

`sha256sum` the seven images and `parts.xml`. They already land in `temp/w6-root/run/` on the host. Do not commit them (64 MiB each; keep userdata and any callsign out of git). Copy the log and the checksum list to `docs/w6/reports/20261024_root/dump/`. Leave the images where they were written.

## After a good read

Stop. Update `docs/w6/ROOT_ATTEMPT_2026-10-24.md` and add a `CHANGELOG.md` row: download mode was entered, what the banner was, what was read, that nothing was written, device-tested on the W6. Magisk comes later, in a different session, after the images are inspected (`boot` vs `vendor_boot` for the ramdisk; kernel 5.4 GKI-style A/B). Plan a plain patched-boot flash first. `avbtool` re-sign only if a later flash bootloops. The bootloader is already unlocked (`ro.boot.verifiedbootstate=orange`, `flash.locked=0`).

## If it fails

| What you see | What to do |
|---|---|
| No `1782:4d00` | USB is not in the guest. Fix passthrough. Do not reboot the Armor. |
| `libusb` cannot open, or ModemManager holds the interface | `systemctl stop ModemManager`, unplug, replug, one retry. |
| Timeout at `EXEC FDL1` | Stop. No second FDL. Long-press power ~10 s back to Android. The watch FDL2 does not match this board. |
| `SPRD3` and the exec blob is rejected before FDL1 runs | One retry with `exec_addr 0x4ee8` and `custom_exec_no_verify_4ee8.bin` in the working directory. Still read-only. |
| FDL2 comes up, `partition_list` or a `read_part` errors | Keep the log and any partial file. Long-press power. Do not write. |
| Phone vanishes | Long-press power. It is the same recovery as the fastboot wedge. Not a brick by itself. |

Bring the log back either way. The banner line matters even when the read fails.

## Phone facts already measured (do not re-derive)

Unisoc, brand string `UNISOC`. Model string `W6+`. Product `sl8541e_3h10_go_32b`. Board and hardware `sl8541e_3h10_32b`. `ro.board.platform=sp9832e` (SC9832E / SL8541E). Android 12, SDK 31, 32-bit only (`armeabi-v7a`, zygote32). Kernel 5.4.147. A/B, current slot a. User build, release-keys. Build 2026-05-25. Security patch 2023-04-05. Fingerprint `UNISOC/sl8541e_1h10_32b_Natv/sl8541e_1h10_32b:12/SP1A.210812.016/58:user/release-keys`. Display id contains `W6+_V1.0_UHF_JG_FT13_R_202605251847`. SELinux permissive. Bootloader unlocked, orange. Shell is uid 2000; `su` is absent, so permissive SELinux does not let shell read `boot_a`.

Radio, for context only: stock app `com.flyscale.analogpoc` (`/system/priv-app/FlyscaleAnalog400/`), uid 1000. Port `/dev/ttyS2`, 9600 8N1. Protocol is SA818-class `AT+DMO…`, not the DMR `68…10` MCU. Leave the radio alone during this dump.

## Out of scope

The nine unrooted device-report gaps in `PHASE0_FINDINGS.md`. Installing SignalDeck. Patching or flashing boot. Any transmit test.
