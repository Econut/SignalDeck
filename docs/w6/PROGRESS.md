# W6 progress log

Newest first. Phone: ANYSECU W6+ replacement unit, adb `-s W6202601000904` (USB). Plan: `docs/W6_DEVICE_REPORT_PLAN.md`.

## 2026-10-27 22:10 CDT: SignalDeck drives the W6 (receive)

- Owner approved the design. SignalDeck #38: `ModuleEngine` interface (on main). #40 on top: `AtModuleEngine`,
  `Sa818Tones`, `CamcorderAudio`, the W6 profile (unverified), stock channel import.
- On the W6 (Take over): 128 channels imported, VERQ/SETGROUP/SETVOLUME answered `:0`; HackRF tone on CH 1 (405.025,
  62.5 Hz) → receiving shown, SignalDeck's own recording −44 dBFS pure 1 kHz (module volume 4).
- `RSSI?` gets no answer (owner OK'd the query): no S-meter on the W6.
- Handed back at the end: the stock app owns the radio, latest #40 build installed.
- Next: hide DMR controls when `engine.dmr()` is false; transmit waits for the owner in the cage.

## 2026-10-27 21:15 CDT: Receive-audio test (ENGINE_DESIGN test 1): passed

- HackRF in the cage, 435.025 MHz, 1 kHz + 62.5 Hz CTCSS, `-x 20`; the stock app's squelch opened (tone code 240 = 62.5 Hz
  confirmed). New bench command `audioprobe`.
- **`CAMCORDER` carries the module's receive audio** (−65 dBFS at the stock volume 1/8, nearly pure tone, unchanged
  with the speaker amplifier off). Raw ALSA capture around the HAL gets nothing. Details: `RX_AUDIO_TEST.md`.
- The W6's temporary files are removed; the HackRF is idle; the stock app owns the radio.
- Next: the engine (ModuleEngine interface PR, then AtModuleEngine with the CAMCORDER audio backend), once the owner
  approves the design (#35). The RSSI query and the power-line recovery still wait for the owner's OK.

## 2026-10-27 20:10 CDT: Compact intercom (layout B)

- Branch `w6/compact-intercom`, stacked on `w6/engine-design`. Owner picked layout B with today's button columns, and
  asked that changes to the Armor's page reach the compact page by themselves: one build path, one size table, the
  regular sizes locked by a host test to the previous numbers.
- W6: everything fits; `screenHeightDp` is 545 (status bar excluded), the rule is < 700.
- Next: the HackRF is mine after this (owner): receive-audio test (ENGINE_DESIGN test 1) first.

## 2026-10-27 18:50 CDT: Engine design written (docs/w6/ENGINE_DESIGN.md)

- Branch `w6/engine-design`, stacked on `w6/clues-32bit`. Docs only.
- Read the stock app with the SDK's `dexdump` and `aapt2` (the downloaded jadx stays unused: running it was blocked).
  Found: the GPIO map (power `gpio322`, PTT `gpio188`, mic `gpio222`, receive detect `gpio189` as an input, audio
  routes `gpio183`/`gpio249`, amplifiers in `/sys/kernel/*_pa_en`), receive start/end as system broadcasts
  (`android.flyscale.rece.dmr.start/end`), the side key as `fly.privkey.poc.down/up`, the full AT command set and the
  SETGROUP field order (tx tone before rx tone), the tone code table (0 none, 1-38 CTCSS, 240-252 extra tones, 39-121
  DCS). Every factory channel uses 62.5 Hz (code 240) both ways.
- Received audio is switched in hardware to the speaker amplifier, the microphone in hardware to the module; whether
  Android can hear or feed audio is the open question that decides the feature set (design section 6, test 1).
- Next: the owner reviews the design and the tests in section 7.

## 2026-10-27 18:15 CDT — 32-bit clues (gaps 2, 8, B12); a second kernel panic; a silent module

- Branch `w6/clues-32bit`, stacked on `w6/serial-sniff`. Native libraries of every ABI, `AT+…` names and a protocol
  hint from the code (in the summary, no root needed), quieter log texts. Found and fixed: three readers of report.txt
  stopped at line 200 (the Checks done dialog never showed the summary).
- Correction to Phase 0: the stock APK carries `libserial_port.so` for arm64-v8a, armeabi-v7a, x86 and x86_64, not
  armeabi-v7a only.
- **Silent module (open):** in run 06:21 (phone time) the stock app's start-up got answers, but the probe a few seconds
  later got none; in the next run the stock app sent `AT+DMOCONNECT` 15 times, 1 s apart, unanswered. `gpio322` (power)
  stayed 1, nothing held `/dev/ttyS2`, the CPU was idle. A reboot brought it back (`+DMOCONNECT:0`). Cause unknown; the
  run before (06:09) did the same steps and the module answered throughout. Candidates: a vendor service powering the
  module down through another line when the stock app dies, or the probe's speed changes/binary at 230400 wedging it.
- **Kernel panic #2 (my fault): reading `flyscale_misc/dmr_pwd` panics the kernel too** (`dmr_pwd_show →
  gpiod_get_value_cansleep → validate_desc`, the same driver bug as `af_en`). I read it in a script that tested the
  silent-module question, because Phase 0 had read it safely on the first unit. It does not crash on every unit or
  boot, so **no attribute of the `misc_flyscale` driver may ever be read** (list names only). The report never reads
  them. The phone rebooted by itself and is fine; the module answered again.
- Next: hold. I asked the owner about decompiling the stock app (jadx was blocked by the permission check) before
  continuing with the silent-module question and the engine design.

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
