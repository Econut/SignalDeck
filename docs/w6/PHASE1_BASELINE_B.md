# W6 Phase 1: rooted baseline report B (2026-10-27)

Bench phone: the **replacement** ANYSECU W6+ (adb serial `W6202601000904`, USB), Magisk 31.1, SELinux permissive.
SignalDeck `1.0.0-beta.20` release build from `origin/main` `eb2797c0`, root granted through the Magisk policy table
(uid 10113, policy 2). **Run all checks** was driven over adb (`input tap` + `uiautomator dump`), with `com.flyscale.analogpoc`
chosen for the 15 s watch. No PTT was pressed. Run time ~100 s; the zip is 5.7 MB (11 MB of it is the stock APK).

Raw copy: `reports/20261027_phase1/baselineB/` (summary + report.txt; the APK is not committed). Report A (beta.19,
unrooted, the first unit): `reports/20261024_phase0/baselineA/`.

The phone's clock says 2026-05-25 (the build date), so the folder is `UNISOC_W6__20260525_184548`.

## What B adds over A

Root sections are new: serial labels and live termios (`/dev/ttyS2: speed 9600`), who holds the ports
(`com.flyscale.analogpoc` holds `/dev/ttyS2`, `engpc` holds `ttyGS0/1`), the stock app's `dumpsys package`, its database
structure (`CHANNEL_INFO`: `CHANNEL_ID GBW TFV RFV RXCXCSS TXCXCSS SQ POWER`, 128 rows), SELinux denials, vendor services,
local sockets, Magisk modules, the sound card's streams, input device names from `getevent`, the 15 s watch of the stock
app and the radio probe.

## The nine Phase 0 gaps, rechecked in B

| # | Phase 0 gap | In B? |
|---|---|---|
| 1 | baud | **partly**: the root termios lines say 9600 for `ttyS2`, but the clue scan still says `serial speeds: -` and the summary says nothing |
| 2 | protocol family | **no**: the dex has `AT+DMOCONNECT` etc.; nothing in B names SA818-class |
| 3 | `flyscale_misc` sysfs nodes, `/sys/kernel/dmr_state` | **no**: only the `/dev/flyscale_misc` char device and the exported gpio names |
| 4 | input devices | **partly**: names listed (`hook_dmr` is `event12`), but not their key bits (`KEY_F2`, `KEY_F3`) |
| 5 | init.rc / ueventd lines | **no** |
| 6 | last panic / boot reason | **no** |
| 7 | phone clock wrong | **no**: folder and header carry the phone's wrong date |
| 8 | `lib/armeabi-v7a` in the clue scan | **no**: `DeviceReport.java:233` reads `lib/arm64` only |
| 9 | blind attribute reads | **still there**: the platform-device section reads every attribute of matched dirs (`gpio-keys/hook_status`, …). Harmless on these dirs, the same pattern that panicked the kernel on `af_en` |

## New gaps found in B

| # | Gap | Evidence in B | Why it matters |
|---|---|---|---|
| B1 | **The probe skipped the module's port and probed the wrong one** | `skipped /dev/ttyS2: its label console_device belongs to another subsystem` (`RadioProbe.java:57`); then `/dev/ttySE0` was opened at 57600/115200/9600/230400 with the DMR `0x34` query, and answered `FAIL: Invalid command` | `ttySE0` is the Unisoc `sprd-serial-ex` engineering port, not a radio. The probe wrote to a vendor port and never reached the module. On this phone the stock app's own port carries the `console_device` label |
| B2 | **No AT probe** | only the DMR version query is sent | an SA818-class module never answers it, so B says "answered the version query: no", which reads like a dead module |
| B3 | **No serial bytes from the stock app** | the watch lists open fds and termios only | protocol, power-up order and timing need the bytes (plan item 1, strace) |
| B4 | **The watch never saw a start-up** | `Warning: Activity not started, its current task has been brought to the front` | the stock app was already running and holding `ttyS2`; the watch should stop it first (root `am force-stop`, never clear) so the start is recorded |
| B5 | **Wrong sepolicy lines** | `allow untrusted_app device chr_file …`: derived from `ttySE0` | the module port is `console_device` |
| B6 | **PTT capture drowned in sensor events** | ~1000 lines of `event9` (accelerometer) `EV_ABS` | a real key press would be lost; keep `EV_KEY` lines and skip sensor devices |
| B7 | **No draft profile, no verdict in the summary** | the summary is the generic 10-line header | plan item 8 |
| B8 | **The Device page shows another phone's defaults on an unknown phone** | `/dev/ttyS0 · 57600 · vendor audio` | misleading: the W6 is `ttyS2` @ 9600 and no SignalDeck audio route exists for it |
| B9 | **The "looks like a stock app" list is empty** while the ranked list scores the app at 18 | `==== Installed apps that look like the stock walkie-talkie app ====` followed by nothing | redundant section, its name regex misses `analogpoc` |
| B10 | **Audio path unknown** | sound card streams are listed, no mixer controls, no before/after diff | plan item 5 |
| B11 | **GPIO values not read** | names only | plan item 4 (values before/after the stock app starts) |
| B12 | **Log texts are noise** | `FPTt\@n`, `in onInterceptTouchEvent` | the dex string filter matches "PTT" inside random bytes |
| B13 | **No `report.json`, no adb driver** | the run needed ~15 screen taps, two of them on a dialog that waits forever for a choice | plan items 9 and 10 |

## Backlog order (this branch onwards)

1. **Bench driver** (plan item 10): `DiagReceiver` `checks` with the stock app given by package, host script
   `scripts/w6/run_checks.sh`, host-time folder stamp (gap 7). Every later item is verified through it.
2. **Probe port choice and the AT probe** (B1, B2, B5; plan item 6): probe the port the stock app holds whatever its label,
   never a port no app names (`ttySE0`), add `AT+DMOCONNECT` at the stock app's baud.
3. **Serial sniff + start-up watch** (B3, B4; plan item 1).
4. **Protocol classifier** from dex strings and sniffed bytes (gap 2; plan item 2).
5. **32-bit native code** (gap 8; plan item 3).
6. **Safe node discovery** from app and init.rc names, one read each with a marker, `af_en` denied (gaps 3, 5, 9, B11;
   plan item 4), plus last panic and boot reason (gap 6).
7. **PTT capture** key bits and sensor filter (gap 4, B6; plan item 7).
8. **Audio path by behaviour** (B10; plan item 5).
9. **`report.json`** + `scripts/report_diff.py` (plan item 9), then the **draft profile** (B7; plan item 8).
10. Smaller: B8, B9, B12, plan item 11.
