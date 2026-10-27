# W6 root attempt 1 — 2026-10-24

Goal: get a Magisk-patched `boot_a` onto the W6. Gate: obtain the stock `boot_a` image first (block device is
root-only, so no `adb dd`). Raw captures: `docs/w6/reports/20261024_root/`.

The bench unit no longer boots. When the replacement arrives, follow `docs/w6/REPLACEMENT.md`. That file is the procedure. This file is the record of the dead unit.

## What worked
- Bootloader fastboot is real. `adb reboot bootloader` → device enumerates as "fastboot Gadget" USB **18D1:4EE0**,
  serial `W6-UNIT1-SERIAL`.
- Windows needed the Google USB Driver (not installed). Staged it with
  `sdkmanager "extras;google;usb_driver"` (needs `JAVA_HOME` in Windows form, e.g. the Android Studio `jbr`); its
  `android_winusb.inf` lists `VID_18D1&PID_4EE0` as the bootloader interface. Owner bound it elevated with
  `pnputil /add-driver <inf> /install` (published as `oem79.inf`). After that `fastboot devices` sees the phone.
- `fastboot getvar current-slot` = **a**; `is-userspace` = **no** (true bootloader fastboot, not fastbootd).

## What does NOT work — the blocker
This bootloader can **flash** but cannot **read** partitions, so stock `boot_a` can't be dumped over fastboot:
| Method | Result |
|---|---|
| `fastboot oem pull boot_a` | returns `OKAY` instantly, writes no file — a stub, not implemented |
| `fastboot fetch boot_a` | "Device does not support fetch command" |
| `fastboot getvar all` | USB write timeout (bootloader answers only specific vars) |

## USB instability
After the big `getvar all` the fastboot **write endpoint wedged**: `fastboot devices` still lists the phone, but
`reboot` / `continue` / any sending command fails with `AdbWriteEndpointSync ... semaphore timeout (121)`. Matches the
two USB drops seen in Phase 0. Recovery is physical: long-press power ~10 s (boots to Android), or unplug/replug then
`fastboot reboot`. Not a brick.

## How to get stock boot_a (the real fork) — neither started
- **A. Vendor firmware PAC** for `sl8541e_3h10` / this build (`W6+_V1.0_UHF_JG_FT13_R_202605251847`), from Anysecu or
  the seller; extract `boot.img`. Not found online (searched 2026-10-24). Zero risk, best if obtainable. Also yields the
  FDLs needed for B.
- **B. Unisoc download mode + spd_dump with CVE-2022-38694** (TomKing062 / ilyakurdyukov `spreadtrum_flash`): the BROM
  `exec_addr` no-verify FDL1 can read `boot_a` without a signed PAC. Needs the SL8541E FDL1/FDL2 load addresses; wrong
  FDLs risk a brick. More work, no vendor dependency. Green-light + original-partition backup before touching BROM.

## Next action
Owner: recover the phone (long-press power), then decide A vs B. Flashing later will work (fastboot flash is available
and the phone is unlocked/orange); plan a plain patched-boot flash first, avbtool re-sign only if it bootloops.

## Continuation (same day, phone recovered)
`adb -s W6-UNIT1-SERIAL` is in Android again (`sys.boot.reason=bootloader`, battery 61%, USB charging). The Armor on
wifi adb was listed and was not addressed. Shell still cannot read the boot partition:

| Partition | Node | Size |
|---|---|---|
| `boot_a` (active) | `mmcblk0p38`, mode `0600` root | 64 MiB |
| `boot_b` | `mmcblk0p39` | 64 MiB |
| `vendor_boot_a` | `mmcblk0p40` | 64 MiB |
| `dtb_a` / `dtbo_a` | `mmcblk0p42` / `p44` | 8 MiB each |
| `uboot_a` | `mmcblk0p8` | 1 MiB |
| `vbmeta_a` | `mmcblk0p48` | 1 MiB |

`dd` of one sector of `boot_a` is `Permission denied`. A Magisk patch needs `boot_a` and, on this kernel 5.4 A/B
layout, a look at `vendor_boot_a` before deciding which image actually holds the ramdisk.

### Route B, narrowed
`ro.board.platform=sp9832e` is Unisoc SC9832E / SL8541E. TomKing062's chip table
(`CVE-2022-38694_unlock_bootloader` wiki, AddSupportToModel) gives FDL1 `0x5000`, FDL2 `0x9efffe00`, and the same
BootROM as SC9820E. The signature bypass for that BootROM is `exec_addr 0x4f18` (normal download) or `0x4ee8`
(fallback), blobs `custom_exec_no_verify_4f18.bin` / `custom_exec_no_verify_4ee8.bin` in
`soc/sc9820e_sc9832e`. Tooling is `spd_dump` from TomKing062/spreadtrum_flash, read commands only
(`read_part boot_a`, `vendor_boot_a`, `vbmeta_a`, `dtbo_a`). No `write_part`.

No PAC for `W6+_V1.0_UHF_JG_FT13_R_202605251847` or `sl8541e_3h10` showed up (searched 2026-10-24). The FDL pair
in hand is borrowed from a DW99 `sl8541e_1h10` watch PAC (same 1h10 name as this phone's fingerprint, not this
3h10 board). A borrowed FDL2 initialises that board's pins. `adb reboot autodloader` wipes `splloader` when uboot
has no autodloader handler; `SPRD4:AutoD` means the handler ran.

### Download mode entered on Windows; nothing was read
The owner green-lit one read-only dump with that borrowed FDL pair. `adb -s W6-UNIT1-SERIAL reboot autodloader`
put the phone in download mode (`1782:4d00`). `spd_dump` never opened it: Windows had no function driver (code 28),
and the installed UsbDk driver exposes no `\\.\UsbDk` node, so libusb cannot see the device. No FDL was sent.
No partition was read or written. The phone was still in that download mode when the session moved to an Ubuntu VM.

The procedure, file hashes, and stop rules for the VM are in `UBUNTU_DUMP_HANDOFF_2026-10-24.md`. The VM mounts
this working tree. The four blobs are gitignored and already at `temp/w6-root/run/` (`fdl1-sign.bin`,
`fdl2-sign.bin`, and the two `custom_exec_no_verify_*.bin`).

## Ubuntu dump, halted 2026-10-24 17:56 CDT (resume from `UBUNTU_DUMP_RESUME_2026-10-24.md`)

`spd_dump` built on the guest at `/home/joshua/w6-dump/spreadtrum_flash/spd_dump` (`2a202404`). ModemManager
stopped. `1782:4d00` was in the guest. Nothing written. No images.

- `exec_addr 0x4f18`: `CMD_CONNECT bootrom`, FDL1 sent, `CHECK_BAUD FDL1` timed out. Log:
  `docs/w6/reports/20261024_root/dump/spd_dump_ubuntu_4f18.log`.
- `exec_addr 0x4ee8` (allowed BootROM retry): **FDL1 came up** (`CHECK_BAUD FDL1`, `CMD_CONNECT FDL1`,
  `KEEP_CHARGE FDL1`). FDL2 was mid-upload when the owner halted. Log:
  `docs/w6/reports/20261024_root/dump/spd_dump_ubuntu_4ee8.log`.

No `SPRD3`/`SPRD4` banner line (VERIFY_ERROR then `CMD_CONNECT bootrom`). Phone left in `1782:4d00` with FDL1
loaded and a partial FDL2. Next: long-press power, then one clean read-only dump with `0x4ee8` as root.

## BootROM after the power cut (same evening)

A long-press and a battery pull with the cable out both cut USB. The next plug enumerated `1782:4d00` again.
A connect-only handshake (no FDL) answered `CHECK_BAUD bootrom` / `BSL_REP_VER: "SPRD3"`. That is the mask
BootROM, so Android is not in the boot path. `poweroff` from BootROM returns `FDL NOT READY`.

FDL1 at `exec_addr 0x4ee8` still comes up (`Spreadtrum Boot Block version 1.1`). `reset` (`BSL_CMD_NORMAL_RESET`,
type `0x05`) is not answered by this old FDL1: the bulk read times out and the control endpoint then wedges.
A guest USB port reset drops the passthrough device; it comes back as `1782:4d00` about a minute later, still
`SPRD3`. Do not send `reset` to this FDL1.

The watch FDL2 was loaded and executed. It answered `FDL2: incompatible partition` (type `0x96`). The GPT
partition list fails (`0xfe`, "Partition table not available"). Named reads still return data. Nothing was
written.

| Read | Result |
|---|---|
| `splloader`, `splloader_bak`, 256 KiB each | all `0x00` (sha256 `8a39d2abd3999ab73c34db2476849cddf303ce389b35826850f9a700589b4a90`) |
| `boot_a` first 4 KiB | `ANDROID!` |
| `uboot_a` | `DHTB` with a non-zero hash |
| `vbmeta_a` | `AVB0` |
| `sml_a`, `teecfg_a`, `trustos_a` | `DHTB` with non-zero hashes |

Heads are in `docs/w6/reports/20261024_root/dump/heads/`. Milestones: `recovery_milestones.txt` in that dump
directory. The eMMC user area is intact. The BootROM stage that should hand off to `splloader` is empty, which
matches a cold boot that stops at `SPRD3`.

The only SPL image on disk is the DW99 watch `u-boot-spl.bin` inside `temp/w6-root/dw99_head.pac` (offset
2337596, 38548 bytes, load address `0x80000000`). Its DHTB hash field is zero. It was not written. This phone's
own images are signed, and that watch SPL is for `sl8541e_1h10`, not this `sl8541e_3h10` board. BootROM will
not chain it into Android. The phone stays recoverable from `SPRD3` until a signed `splloader` for this build
is written.

## Full read (same evening, still nothing written)

A fresh FDL1 `0x4ee8` plus the watch FDL2 read the images below into `temp/w6-root/dump-20261024/`
(gitignored). Checksums: `docs/w6/reports/20261024_root/dump/SHA256SUMS`. Transfer was about 3.8 MB/s.
`trustos_a` was finished with one `--reconnect` after the first session stopped at the FDL2 prompt.
No `reset`, no `poweroff`, no `write_part`. The phone was still `1782:4d00` when the reads finished.

| Image | Bytes read | What it is |
|---|---:|---|
| `boot_a.img` | 67108864 | Android 12 boot header v4. Kernel 16461360 bytes. Ramdisk 2472083 bytes, lz4 legacy frame, newc cpio (`070701`). AVB footer at the end: original size `0x1211000`, embedded vbmeta algorithm SHA256_RSA2048, flags 0, hash descriptor for `boot`. |
| `vendor_boot_a.img` | 67108864 | Vendor boot header v4, page 4096, vendor ramdisk 17365941 bytes, also lz4 legacy. Cmdline is `console=ttyS1,115200n8 buildvariant=user`. |
| `vbmeta_a.img` | 1048576 | AVB 1.0, algorithm 2, rollback 0, flags 0 (hashtree and verification both still requested). Chain descriptors: `boot`, `vendor_boot`, `dtbo`, `vbmeta_system`, `vbmeta_system_ext`, `vbmeta_vendor`, `vbmeta_product`, `l_modem`, `l_ldsp`, `l_gdsp`, `pm_sys`. |
| `vbmeta_system_a`, `vbmeta_vendor_a` | 4096 each | Hashtree descriptors for `system`, and for `vendor` / `vendor_dlkm`. Fingerprint matches the build that was running. |
| `dtbo_a.img` | 8388608 | DTBO table magic `0xd7b7ab1e`. |
| `dtb_a.img` | 8388608 | No FDT magic. A gzip member starts at `0x74a48`. |
| `uboot_a.img` | 1048576 | One DHTB, payload 787424, then `SIMGHDR`. |
| `sml_a.img` | 1048576 | DHTB payload 38192, then `SIMGHDR`. |
| `teecfg_a.img` | 1048576 | DHTB payload 122880, then `SIMGHDR`. |
| `trustos_a.img` | 2228224 | DHTB payload 2109440 and the following `SIMGHDR`. The partition continues past this read (the last byte is nonzero). |
| `misc.img` | 1048576 | BCB command `bootonce-bootloader`. Slot control at `0x800`: suffix `_a`, magic `BCAB`, two slots, slot A successful. |
| `boot_b`, `vendor_boot_b`, `vbmeta_b` | 4096 each | Same first 4 KiB as slot A. |

`splloader` was not read again. The earlier 256 KiB zero dumps still stand.

## Can this unit be rooted?

Not while it is in BootROM. Magisk needs a booting Android, or at least fastboot, and this phone has neither until a signed `splloader` for this build is written. That write was not done.

The dump is a normal Magisk target once a phone of this build is running:

- Patch `boot_a.img`, not `vendor_boot_a.img`. The generic ramdisk is in `boot_a` (header v4, ramdisk size nonzero). Current Magisk supports this header.
- `vbmeta_a` flags are 0 and the boot image hash is signed, so the patched image will not match stock AVB. This phone's bootloader was unlocked (`orange`, `flash.locked=0`) before the wipe. On a phone that still boots, the first try is a plain `fastboot flash boot` of the patched image. If AVB rejects it, fastboot is still there and vbmeta flags are the next step.
- `misc` already asks for one boot into the bootloader. After a signed `splloader` is restored on this unit, the first start should enter fastboot once. Slot A is the active slot.

On the replacement, compare the display id to `W6+_V1.0_UHF_JG_FT13_R_202605251847` before flashing this `boot_a`. Use `adb reboot bootloader` to reach fastboot. Do not run `adb reboot autodloader`: that is the command that left `splloader` empty on this unit.
