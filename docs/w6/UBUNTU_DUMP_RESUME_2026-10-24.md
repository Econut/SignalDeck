# W6 dump resume — Ubuntu VM (2026-10-24, BootROM, splloader empty)

Paste to the next agent (run Grok as root/`sudo` so libusb can reopen after FDL1):

> The dead W6 cold-boots to BootROM (`SPRD3`). `splloader` reads as zeros. Its boot images are in `temp/w6-root/dump-20261024/`. Do not write the watch SPL. Do not touch the Armor. The replacement phone is `docs/w6/REPLACEMENT.md`. Do not run the dump command later in this file.

This file is the task. Phase 0, the Windows UsbDk dead end, and the original procedure are in
`PHASE0_FINDINGS.md`, `ROOT_ATTEMPT_2026-10-24.md`, and `UBUNTU_DUMP_HANDOFF_2026-10-24.md`. Do not repeat them.

## Where it stands (dump finished, still BootROM)

The read-only dump is done. Images and `SHA256SUMS` are in gitignored `temp/w6-root/dump-20261024/`.
A copy of the checksums is `docs/w6/reports/20261024_root/dump/SHA256SUMS`. The assessment is in
`ROOT_ATTEMPT_2026-10-24.md` under "Full read" and "Can this unit be rooted?". Nothing was written.
The phone was still `1782:4d00` after the last read.

`splloader` and `splloader_bak` are still 256 KiB of zeros, so this unit does not boot and Magisk cannot be
installed on it. `boot_a` is a full Android 12 header v4 image with an lz4 newc cpio ramdisk. `vbmeta_a`
flags are 0 and `boot_a` has a signed AVB hash. The watch `u-boot-spl.bin` in `temp/w6-root/dw99_head.pac`
is the wrong board and its DHTB hash is zero; leave it.

Do not send `reset` to this FDL1. The old boot block does not answer it, and the following USB wedge needs a
guest port reset that drops the VMware device for about a minute. FDL2 from the watch reports
`incompatible partition` and will not return a GPT. Named `read_part` works. One `--reconnect` after the
main session finished `trustos_a`; do not count on a second one.

Android does not start until a signed `splloader` for `W6+_V1.0_UHF_JG_FT13_R_202605251847` /
`sl8541e_3h10` is written. There is no copy of that image in this tree. On a replacement of this same
build, reach fastboot with `adb reboot bootloader` and do not run `adb reboot autodloader`.

## Where it was left (halted on request — historical, superseded by the section above)

The long-press does not return this phone to Android. Do not follow the dump command later in this file until a signed `splloader` is restored.

- W6 still enumerated as `1782:4d00` (Bus 001 Device 002). No `spd_dump` process. ModemManager inactive.
- **Nothing was written to flash.** No `boot_a.img` / `parts.xml` / other images. Success is still those files plus a log.
- Armor (`192.168.1.125`) was never addressed.

| Attempt | Result |
|---|---|
| `exec_addr 0x4f18` | `CMD_CONNECT bootrom`. FDL1 + exec blob ACK'd. `CHECK_BAUD FDL1` timed out ×5 → "wrong command or wrong mode". Log: `docs/w6/reports/20261024_root/dump/spd_dump_ubuntu_4f18.log` |
| `exec_addr 0x4ee8` (the one allowed BootROM retry) | **FDL1 came up:** `CHECK_BAUD FDL1`, `CMD_CONNECT FDL1`, `KEEP_CHARGE FDL1`. Then FDL2 (`fdl2-sign.bin` @ `0x9efffe00`) was mid-`0x02` upload when the owner said halt. No `EXEC FDL2`. Log: `docs/w6/reports/20261024_root/dump/spd_dump_ubuntu_4ee8.log` |

No `BSL_REP_VER` / `SPRD3` / `SPRD4` line in either log. Classification was `CMD_CONNECT bootrom` (VERIFY_ERROR then ACK), not autodloader `SPRD4`.

The 4ee8 session left the phone in **FDL1 with a partial FDL2 download**. Do not `--reconnect` that session. Long-press power ~10 s back to Android, then one clean dump.

## Already on this VM (do not rebuild unless missing)

| What | Where |
|---|---|
| `spd_dump` | `/home/joshua/w6-dump/spreadtrum_flash/spd_dump` — `sha1:2a202404a7512026d146b62624f089aceb408520` (prints `branch:HEAD, sha1:2a202404…`) |
| Blobs | `temp/w6-root/run/` — hashes in the original handoff; `sha256sum -c` before touching the phone |
| udev | `/etc/udev/rules.d/99-unisoc-dl.rules` — `1782:4d00` MODE 0666 GROUP plugdev |
| Packages | `libusb-1.0-0-dev`, `libxml2-dev`, `pkg-config` installed |

`sudo systemctl stop ModemManager` before the dump (it was inactive at halt).

CWD for `spd_dump` is still `temp/w6-root/run/` (exec blob name is resolved from cwd). Invoke the binary by absolute path.

## After the long-press — do not run

This block is how the dead phone lost `splloader`. `adb reboot autodloader` on this uboot erases it. The replacement procedure is `docs/w6/REPLACEMENT.md` (fastboot, then `dd` of `mmcblk0boot0` once Magisk is in). The command below is kept so the old `exec_addr` and the `poweroff` mistake stay visible. Do not type it.

1. Historical only. The long-press does not return the dead phone to Android.
2. Historical only. `adb reboot autodloader` is the wipe.
3. `exec_addr 0x4ee8` is the address that worked, if a later read-only BootROM session is actually required. `0x4f18` timed out.
4. Historical command, including a `poweroff` this FDL1 does not answer:

```bash
cd /mnt/hgfs/Documents/code/personal/SignalDeck/temp/w6-root/run
systemctl stop ModemManager
/home/joshua/w6-dump/spreadtrum_flash/spd_dump --wait 60 --verbose 1 \
  exec_addr 0x4ee8 \
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
  poweroff \
  2>&1 | tee /home/joshua/w6-dump/spd_dump_ubuntu.log
```

That command is not the next step. The reads it was written for are already in `temp/w6-root/dump-20261024/`, and `poweroff` on this FDL1 wedges USB. The next phone is `docs/w6/REPLACEMENT.md`.

## Hard stops (unchanged)

No `write_part` / `w` / `erase_*` / `repartition` / `r all` / `r all_lite`. No other PAC. No `fastboot getvar all`. Do not read `flyscale_misc/af_en`. Do not key the transmitter.

If `EXEC FDL2` times out: stop, keep the log, long-press power. That is an FDL2 board mismatch, not a reason to change `exec_addr` again.
