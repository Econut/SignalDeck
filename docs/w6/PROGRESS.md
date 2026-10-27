# W6 progress log

Newest first. Phone: ANYSECU W6+ replacement unit, adb `-s W6202601000904` (USB). Plan: `docs/W6_DEVICE_REPORT_PLAN.md`.

## 2026-10-27 17:25 CDT — Serial sniff + start-up watch (B3, B4; plan items 1, 2)

- Branch `w6/serial-sniff`, stacked on `w6/probe-at`.
- Wrote `sdtrace` (our own ptrace sniffer, no strace/LGPL): waits for the process, attaches to every thread, logs
  open/TCSETS/read/write on `/dev/tty*`. The watch stops the stock app first and starts it under the sniffer.
- **The W6's start-up, from the report:** `/dev/ttyS2` 9600 8N1 raw; `AT+DMOCONNECT\r` → `+DMOCONNECT:0`;
  `AT+DMOVERQ\r` → `+DMOVERQ:PB202EU05`; `AT+DMOSETGROUP=1,435.025,435.025,240,240,3,1\r` → `+DMOSETGROUP:0`;
  `AT+DMOSETVOLUME=1\r` → `+DMOSETVOLUME:0`; one write per command, ~100 ms apart; then `FIONREAD` every 150 ms.
  Its log adds the power step: `SerialHandle: patch == /sys/class/gpio/gpio322/value; value == 1` right after the
  port opens (`openModule()`).
- So the earlier "lost byte" was not the module: commands end in `\r` only, and the probe's first command after its
  speed changes loses its first byte. The engine stays at 9600 and sends `\r`.
- Report kept in `reports/20261027_serial_sniff/` (with `stock_serial.txt`).
- Next: 32-bit native code in the clue scan (gap 8), then safe node discovery (gaps 3, 5, 6, 9, B11). After that I
  have enough for `ENGINE_DESIGN.md`.

## 2026-10-27 16:55 CDT — Probe: the stock app's port and the AT probe (B1, B2, B5)

- Branch `w6/probe-at`, stacked on `w6/bench-driver`.
- The probe takes the ports the stock app or the profile name (W6: `/dev/ttyS2`) and skips the rest (`ttySE0`); the
  sepolicy line is now `console_device`. AT probe added (`AT+DMOCONNECT`, `AT+DMOVERQ`).
- **W6 facts from it:** the module answers at 9600 on `/dev/ttyS2` with version **`PB202EU05`** (`+DMOVERQ:PB202EU05`).
  The handshake came back three times as an error echo with one byte lost (`T+DMOCONNECT
:1`, `AT+DMOCONNEC:1`), while
  the shorter version query answered cleanly: the engine design has to check how the stock app writes (pacing, one
  write per command) before it relies on long commands. The module also echoes unknown binary input wrapped in `:1`,
  which the old probe took for a DMR answer (fixed).
- Report kept in `reports/20261027_probe_at/`.
- Next: serial sniff of the stock app plus a real start-up watch (B3, B4; plan item 1).

## 2026-10-27 16:35 CDT — Bench driver (plan item 10, gap 7)

- Branch `w6/bench-driver`, stacked on `w6/phase1-baseline`.
- `DiagReceiver checks` runs Run all checks with no screen (`--es watch auto|none|<pkg>`, `--ez trial`, `--el hosttime`);
  progress and the result go to log tag `BenchChecks`. `scripts/w6/run_checks.sh -s <serial>` drives it and pulls the
  folder without the APK.
- On the W6: one command, ~110 s, no taps; `watch auto` picked `com.flyscale.analogpoc`; folder and header now carry the
  host's time, and the report says `the phone is 155 d 10 h 22 min behind`. Report kept in
  `reports/20261027_bench_driver/`.
- Seen while testing: the stock-app watch runs `logcat -c`, which wipes the earlier `BenchChecks` lines (harmless for the
  script, which waits for the last line). Since the watch now stops the stock app at the end and it is not restarted, the
  next run sees a real start-up (`Starting: Intent …` instead of "brought to the front"), so B4 is half fixed by the
  order of runs, not by code: the code fix stays on the list.
- Next: the probe's port choice and the AT probe (B1, B2, B5).

## 2026-10-27 16:15 CDT — Phase 1: rooted baseline B

- Worktree `SignalDeck-w6`, branch `w6/phase1-baseline` from `origin/main` `eb2797c0`. Copied the untracked
  `docs/w6/` notes from the main checkout (APKs, vendor partition heads and the third-party PoC app's dex strings left
  out; the dead unit's serial masked as `W6-UNIT1-SERIAL`).
- Built `1.0.0-beta.20` release (the APK carries `lib/armeabi-v7a` for all three native libs), `install -r` on the W6,
  root granted through Magisk's policy table (uid 10113).
- Ran **Run all checks** through screen taps over adb. Report B is in `reports/20261027_phase1/baselineB/`. A location
  prompt got a mis-tap ("while using"); the grant was revoked with `pm revoke` before the run.
- Result and gap list: `PHASE1_BASELINE_B.md`. Most important: the probe skipped `/dev/ttyS2` (label `console_device`)
  and wrote DMR queries to the Unisoc engineering port `/dev/ttySE0` instead; no AT probe; no serial bytes; the stock
  app was already running so its start-up was not seen.
- Next: the bench driver (`DiagReceiver checks` + `scripts/w6/run_checks.sh`), then the probe port choice and AT probe.
