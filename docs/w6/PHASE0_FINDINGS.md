# W6 Phase 0 findings (2026-10-24, unrooted)

Bench phone: Anysecu W6 UHF, adb serial `W6-UNIT1-SERIAL` (USB only; the owner's Armor is the wifi adb entry and is
never addressed). Every command uses `adb -s W6-UNIT1-SERIAL`. Raw captures: `docs/w6/reports/20261024_phase0/`.

## Platform (from getprop)
| Fact | Value |
|---|---|
| Model / brand | `W6+` / UNISOC, product `sl8541e_3h10_go_32b`, board `sl8541e_3h10_32b` |
| Android | 12 (SDK 31), build `SP1A.210812.016 release-keys`, built 2026-05-25, patch 2023-04-05, user build |
| CPU | 32-bit only (`armeabi-v7a`, zygote32); kernel 5.4.147, clang 12 |
| Bootloader | **already unlocked**: `ro.boot.verifiedbootstate=orange`, `vbmeta.device_state=unlocked`, `flash.locked=0`; A/B slots (`_a` active), `boot_a/b`, `vendor_boot_a/b`, `vbmeta_*`, `super` |
| SELinux | **permissive** (`getenforce` = Permissive; audit lines carry `permissive=1`) |
| Encryption | file-based, metadata encryption on |
| Storage / RAM | ~11 GB free user data; Go edition |
| USB | enumerates as 18D1:4EE8 (adb only) or 1782:4003 (mtp,adb) depending on boot; adb is stable, other interfaces have no Windows driver (harmless) |

## Radio module (stock app evidence, no root needed)
- Stock UHF app: **`com.flyscale.analogpoc`** (`/system/priv-app/FlyscaleAnalog400/FlyscaleAnalog400.apk`, runs as
  **uid 1000 system**, `sharedUserId android.uid.system`, one dex, `lib/armeabi-v7a/libserial_port.so` with only
  `Java_android_serialport_SerialPort_open/close`). The launcher apps `com.corget` (ZhuoZhiDa, PoC over cellular,
  multi-device, no native libs) and `com.loudtalks` (Zello) are not the radio driver.
- Port: **`/dev/ttyS2`**, the only `/dev/tty*` string in the app. ueventd gives it mode 0666 system:system, SELinux
  label `console_device`. Its live termios (read from shell while the stock app held it): **9600 baud 8N1, clocal,
  no flow control**. `/dev/ttySE0` (0666, label `device`) sits at 115200 and is not named by the app.
- Protocol: **SA818-class text commands**: `AT+DMOCONNECT`, `AT+DMOSETGROUP=`, `AT+DMOSETVOLUME=`, `AT+DMOSETMIC=`,
  `AT+DMOSETVOX=`, `AT+DMOAUTOPOWCONTR=`, `AT+DMOVERQ`, replies `+DMOCONNECT:0` etc. Not the `68 … 10` DMR MCU family.
- Control lines the app names: `/sys/class/gpio/gpio{179,181,183,188,189,212,222,223,249,322}/value` (all exported,
  no `direction`/`label` attribute; idle values 181=1, 212=1, 322=1, rest 0), `/sys/kernel/dmr_state/dmr_state`
  (idle 0), `/sys/kernel/ext_pa_en/ext_pa_en`, `/sys/kernel/m6_pa_en/m6_pa_en`, and the flyscale misc nodes
  `dmr_en`, `dmr_ptt`, `dmr_pwd`, `m6_dmr_en` (strings `dmr_ptt`, `dmr_pwd`, `m6_dmr_en` in the dex).
- `/sys/devices/virtual/misc/flyscale_misc/` (driver `misc_flyscale`): `af_en dmr_en dmr_mic_en dmr_ptt dmr_pwd
  m6_dmr_en m6_mic_en m6_spk_en mic_en nr_en nr_pwr poc_spk_en spk_en`; init.rc chmods 0666 `spk_en dmr_en mic_en
  hf_en dmr_spk_en dmr_ptt dmr_pwd audio_pa_m6_en m6_id m6_ptt rfid_pwr` and `/sys/kernel/dmr_state/dmr_state`
  (several of those names do not exist on this board).
- PTT key: input device **`hook_dmr`** (`/dev/input/event12`) reporting `KEY_F2` and `KEY_F3`; the Corget dex has
  `com.creator.android.KEYCODE_F2_DOWN` / `KEYCODE_F3_UP` broadcasts and `flyscale.intent.action.PTT_STATE`.
- A `vendor.sprd.hardware.broadcastradio@2.0-service` runs (FM receiver, unrelated).

## Hazard found: a sysfs read that panics the kernel
Reading **`/sys/devices/virtual/misc/flyscale_misc/af_en`** kills the phone: `af_en_show` → `gpiod_get_value_cansleep`
on a missing GPIO → "Kernel panic - not syncing: Fatal exception" (from `SYSTEM_LAST_KMSG`). It happened twice, each
time the first file in a `cat *` loop. Consequences for the device report:
1. Never `cat` every attribute of a vendor misc/sysfs directory. Read only names the stock app or init.rc use, one
   file per call, and write a "reading X" marker to the report before each read so a reboot names the culprit.
2. Add `af_en` to a deny list; treat any `*_show` crash in `SYSTEM_LAST_KMSG` as a report finding.
3. `dumpsys dropbox --print SYSTEM_LAST_KMSG` is readable from shell without root and gives the panic trace; the
   report should include the last kernel panic trace when the previous boot reason was `kernel_panic`.

## Other report lessons already visible
- `pm list packages -3` misses the stock app: it is a priv-app with uid 1000. Scan all packages; rank by holding a
  serial port, shipping a serial library, naming `/dev/tty*` in its dex, or sharing the system uid.
- APK clue scan must read `lib/armeabi-v7a` on 32-bit phones (the current scan reads `lib/arm64` only).
- Strings worth scanning in the dex, not only the native libs: `/dev/tty*`, `/sys/...` paths, `AT+...` command
  names. The protocol family and every control node of this phone came from dex strings alone.
- `stty -a < /dev/ttyS2` from the shell user read the live baud without root because the node is 0666; the report
  should try that on every `/dev/tty*` it can open, read-only, with a timeout.
- SELinux permissive and an unlocked bootloader are discoverable unrooted and should be stated in the summary.

## Root route (decided)
Bootloader unlocked and `fastboot` present in platform-tools. Route: pull `boot_a` (needs root) or, failing that,
extract it from the vendor's firmware package; patch with the Magisk app on the W6; `fastboot flash boot_a`. Do the
vendor_boot/dtbo check first (kernel 5.4 GKI-style A/B). Recorded here so tier 3 of the reset in the plan can repeat it.

## Open items
- Baseline report A with beta.19 (installed, 2026-10-24) — see the dated report folder once captured.
- Capture the PTT key with `getevent -lt /dev/input/event12` when someone can press the key.
- The flyscale nodes' idle values (one per call, never `af_en`).

## Baseline report A (beta.19, unrooted, folder `UNISOC_W6__20260627_112623`; the phone's clock is wrong, it says June)
Captured with the first-run "Help us support this phone" → RUN CHECKS flow driven over adb (`input tap`, `uiautomator
dump`). Raw copy: `docs/w6/reports/20261024_phase0/baselineA/`. What it got right without root: SELinux permissive and
verifiedbootstate orange in the platform table, `/dev/ttyS2` as the one readable+writable port, the ranked candidate
list picked `com.flyscale.analogpoc` on its own (name, icon, serial library, `/dev/tty` string) and copied the APK, the
dex path scan listed `/dev/ttyS2` and the ten `/sys/class/gpio/gpioNNN/value` paths, the exported gpio names.

What it missed, each one found by hand today without root, so each is a report improvement:
| # | Gap | Where the fact was |
|---|---|---|
| 1 | No baud ("serial speeds: -") | `stty -a < /dev/ttyS2` from shell: 9600 8N1 (node is 0666) |
| 2 | No protocol family | dex strings `AT+DMOCONNECT`, `AT+DMOSETGROUP=`… → SA818-class; the clue scan looks for paths and numbers only |
| 3 | No `flyscale_misc` nodes, no `/sys/kernel/dmr_state` | report scans `/sys/devices/platform` only; the misc device lives in `/sys/devices/virtual/misc`, the kernel node in `/sys/kernel`; both names carry "dmr" |
| 4 | No input device list ("root getevent: n/a") | `getevent -pl` works as shell here (shell is in the `input` group) and names `hook_dmr` KEY_F2/KEY_F3 |
| 5 | No init.rc / ueventd lines | `/vendor/etc/init/hw/*.rc` and `/vendor/ueventd.rc` are world-readable and name every node (chmod 0666 lines) and port mode |
| 6 | No last-panic / boot-reason section | `dumpsys dropbox --print SYSTEM_LAST_KMSG` and `getprop sys.boot.reason` readable as shell |
| 7 | Phone clock wrong, folder name misleading | compare `date` with `ro.build.date`; stamp the folder with host time when driven from adb |
| 8 | `lib/arm64` only in the clue scan | this phone is 32-bit; scan `lib/armeabi-v7a` too (its `libserial_port.so` only has open/close JNI) |
| 9 | Platform-device scan reads every attribute | on this phone a blind `cat` of `af_en` panics the kernel; read only app-referenced names, one per step, marker first |

Not possible unrooted, as expected: which process holds `/dev/ttyS2`, the stock app's termios changes over time, strace
of the command bytes, module version query, power/PTT sequencing. Those are Phase 1.
