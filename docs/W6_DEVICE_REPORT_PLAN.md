# Anysecu W6 UHF: the bench phone for the device report (plan, 2026-10-24)

The W6 is bought as a **deliberately incompatible phone**. Its job is not to become a SignalDeck target on day one;
its job is to make **Run all checks** good enough that an owner of a phone we have never seen sends one zip and we can
draft a working profile from it without a second round trip. The Xplore 1 needed about eighteen betas of back and forth
with a remote tester; the W6 sits on the bench, reachable over adb, so every gap in the report can be found and closed
here instead of in a tester's inbox.

Transmit rule (see `CLAUDE.md`): anything that keys a transmitter is done by the owner, a licensed RF expert, inside the
Faraday cage. The agent prepares the scripts and the report steps; the owner presses the key.

## 1. What we know about the W6 before it arrives

| Fact | Source | What it changes |
|---|---|---|
| **W6 UHF = analog FM only** (400–470 MHz); DMR is the W6 Pro | eBay / Amazon listings: "W6-UHF (only with analogue UHF)" | The radio module is very likely **not** the `68 … 10` DMR MCU family SignalDeck drives. Expect a different module (an SA818/SA868-class AT-command module, or something proprietary). The report must therefore **discover the protocol**, not assume it. |
| Chipset probably **Unisoc SL8541E (SC9863A class)**, 4× A53, 2 GB / 16 GB | W6 Plus manual (sibling model) | First non-MediaTek phone. Different tty naming, different SELinux labels, likely a **32-bit userland** (`armeabi-v7a`), which the APK clue scanner does not read today. |
| Android 10 | all listings | Older storage and permission model; `getevent`, `cmd package` etc. behave slightly differently from Android 15. |
| Rooting: Unisoc bootloaders are hit or miss | XDA / Hovatek threads on SC9863A | May need the community `unisoc-unlock` tool or a signed boot image; unlocking wipes the phone. Plan for a day of root work and for the possibility that root fails. |
| Stock walkie-talkie app: unknown name | no listing names it | The app finder's evidence ranking gets its first real test on a package name that may match none of our regexes. |

None of these is confirmed. The first hour with the phone replaces this table with measured facts.

### Confirmed on arrival (2026-10-24, unrooted; full detail in `docs/w6/PHASE0_FINDINGS.md`)
The table above was written before the phone arrived. What the phone itself says:
- Model string `W6+`, Unisoc SL8541E, **Android 12** (not 10), 32-bit only, kernel 5.4, A/B slots.
- **Bootloader already unlocked** (verifiedbootstate orange) and **SELinux permissive**. No sepolicy work is needed
  on this phone; the root route is a Magisk-patched `boot_a`, but the boot block device is root-only and no vendor
  firmware package was found online, so the image must come from Anysecu or the seller.
- Stock UHF app `com.flyscale.analogpoc` (priv-app, uid 1000) drives `/dev/ttyS2` at 9600 8N1 with **SA818-class
  `AT+DMO…` commands**: the protocol guess in the table was right. Control nodes: `flyscale_misc` (`dmr_en`,
  `dmr_pwd`, `dmr_ptt`, …), ten `/sys/class/gpio/gpioNNN/value`, `/sys/kernel/dmr_state`. PTT input device `hook_dmr`.
- **Reading `flyscale_misc/af_en` panics the kernel.** The report must never loop-cat a vendor sysfs directory
  (Phase 2 item 4 is amended: names from the stock app and init.rc only, one read per step, marker before each).
- Baseline report A (beta.19) found the app, the port and the gpio paths unrooted but missed the baud, the protocol
  family, the misc/kernel nodes, the input devices, init.rc and the last panic: nine concrete gaps, listed in the
  findings file, all discoverable without root.

## 2. Success criteria

The W6 work is done when all of these hold:

1. **Run all checks** on the W6, unrooted, produces a report that already names the stock app, its APK, its serial
   library and JNI class, the candidate ports and the PTT key events.
2. Rooted, the same run adds: the module **protocol family** (DMR MCU / AT-command / unknown, with the sniffed bytes
   that prove it), the **port and baud the stock app really uses** (from its termios, not from guesses), the **power and
   PTT lines** (which sysfs or GPIO values changed when the stock app started and when the owner keyed it in the cage),
   the **audio path** (mixer controls or a second UART), the **SELinux labels** and the rule lines they need.
3. The summary ends with a **draft profile**: every `DeviceProfile` field with a value or "unknown", and a confidence
   note per field. A maintainer can paste it into `DeviceProfile.java` or `profiles.json`.
4. The whole run is **driveable from adb** without touching the screen, so a bench phone can be exercised in a loop.
5. A report from any phone is **machine-readable** (`report.json` beside `report.txt`) and two reports can be diffed on
   the host.

## 3. Phases

### Phase 0: Day one, before root (adb only)

Goal: the unrooted baseline, the stock APK, the root route. Everything read-only.

- Unbox, enable Developer options and USB debugging, `adb devices`, `adb shell getprop > w6_getprop.txt`.
- Record the facts that decide the root route before anything is changed: `ro.bootloader`, `ro.boot.verifiedbootstate`,
  `ro.boot.flash.locked`, `ro.build.fingerprint`, partition list (`ls -l /dev/block/by-name`), OEM unlocking toggle
  present or not, `fastboot getvar all` from the bootloader.
- Install the current SignalDeck beta (`adb install -r`), open it, note what it says on an unknown phone, run
  **Generate device report** and **Capture the PTT key**, pull the folder (`adb pull /sdcard/Download/DMR/DeviceReport`).
  This is report baseline A: the unrooted view.
- Find the stock app by hand (`pm list packages -f`, the launcher) and compare with the report's ranked candidates.
  Pull its APK, run the clue scanner on the host (`apkClues` is pure Java: a small host harness or a unit test fixture),
  and `jadx` it. First question to answer: **which bytes does it write to the serial port?** `68 34 01 01 …` means
  the known MCU; `AT+DMOCONNECT` / `AT+DMOSETGROUP` means an SA818-class module; anything else is new.
- Use the stock app for a few minutes with `adb logcat` running; keep the log. Note how audio is routed (speaker,
  volume keys, a "radio" audio stream).
- Decide the root route from the recorded facts. Order of preference: official OEM unlock + Magisk-patched boot;
  `unisoc-unlock` (signed identifier token) if the toggle is missing; a vendor firmware package if one can be found.
  Back up what can be backed up first (the APK, `getprop`, the report). Unlocking wipes the phone: do it before any
  settings worth keeping exist.

Deliverable: `docs/w6/` with the getprop dump, baseline report A, the APK's clue output, the protocol verdict, and the
chosen root route. Update the table in section 1.

### Phase 1: Root and the rooted baseline

- Unlock, flash the Magisk-patched boot image, confirm `su` from adb (`adb shell su` with the commands on stdin; see
  `docs/DEVICE_SUPPORT_REQUEST.md` §4 for why `su -c "a; b"` does not work).
- Grant SignalDeck root once, by hand, in Magisk. Never `adb uninstall` afterwards.
- Run **Run all checks** (Checks only). Pull the folder: report baseline B, the rooted view. Diff A against B by eye.
- Write down every fact a profile needs that B does **not** contain, or contains wrongly. That list is the backlog for
  Phase 2. Expected entries, from what the code does today:
  - no serial bytes at all: the stock-app watch records which ports it opens and their termios, but not what it sends;
  - 32-bit stock app: no paths, baud or log texts from its native code (`apkClues` reads `lib/arm64` only);
  - sysfs search limited to names matching `dmr|intercom|walkie|ptt|…`: a Unisoc board may call the module something else;
  - audio path: `/proc/asound/cards` and a getprop grep say nothing about which mixer controls the stock app flips;
  - the probe sends only the DMR version query, so a non-DMR module always reads "no answer";
  - SELinux labels on Unisoc differ from MediaTek's `ttyS_device`; the derived rule lines need checking.

### Phase 2: Improve the report, one gap at a time, verified on the W6

Each item is a backlog row in `CHANGELOG.md` with "Device: W6" in its test column. Suggested order, highest value first.

1. **Serial sniff of the stock app (root).** Bundle a static `strace` for arm64 and armeabi-v7a in the APK assets (or
   fetch it once with root), attach to the stock app's pid during the 15 s watch
   (`strace -f -p <pid> -e trace=read,write,ioctl,openat -xx -s 256 -tt`), keep only the calls on tty fds, and write a
   timestamped byte log to the report (`stock_serial.txt`) plus a short decoded view in `report.txt`. This single step
   gives protocol, baud (from the `TCSETS` ioctl), the power-up sequence and the command timing. It is the most
   important improvement the W6 can drive.
2. **Protocol classifier.** From the sniffed bytes: `68 … 10` framing with the known command set, SA818-class
   `AT+DMO…` text, or unknown. The summary says which, and SignalDeck's "unsupported phone" message says *why*:
   "the radio module speaks a protocol SignalDeck does not drive yet (SA818-class AT commands)" is a different
   outcome from "no profile yet", and the maintainer knows at once whether a profile or a new engine is needed.
3. **32-bit native code.** `apkClues` scans `lib/arm64` only; add `lib/armeabi-v7a` (and `lib/armeabi`). `jniSymbols`
   and `registeredNatives` already read any ELF. Verify on the W6's stock library.
4. **Node discovery by behaviour, not by name.** Snapshot candidate sysfs and GPIO state before the stock app starts
   and after it is up (and, in the cage step, during PTT): `/sys/class/gpio/*/value`, `/sys/kernel/debug/gpio` (root),
   every `/sys` path the APK clues named, `/sys/devices/platform/*/` files that are writable. Report what changed.
   This finds the power and PTT lines on a board whose node names match none of our regexes.
5. **Audio path by behaviour.** `tinymix` dump (or `cat /dev/snd/controlC0` via a small helper) before and after the
   stock app starts; grep the audio HAL's `mixer_paths*.xml` and `audio_policy_configuration.xml` for
   intercom/walkie/radio/fm routes; note whether a second tty streams bytes while the stock app receives. On the W6 the
   module may feed the phone's codec through an analog line, which none of our two backends covers; the report must say
   so plainly.
6. **Probe for more than one protocol.** After the DMR version query gets nothing, send `AT+DMOCONNECT\r\n` at 9600
   and look for `+DMOCONNECT:0`. Both are harmless reads of module identity. Record the answer, never anything that
   tunes or keys.
7. **PTT key on a new framework.** Verify the capture on the W6: the side key may be a plain keycode, a vendor
   broadcast, or a GPIO input the framework never sees (then only `getevent` shows it). Add the input device names
   (`getevent -pl`) to the capture so a key that arrives on a device named "ptt" or "gpio-keys" is recognisable.
8. **Draft profile in the summary.** Collect what sections 1–7 found into one block in `00_SUMMARY.txt`:

   ```
   draft profile (confidence):
     controlPort  /dev/ttyS1 @ 9600      (high: stock app TCSETS)
     protocol     SA818-class AT         (high: sniffed AT+DMOCONNECT)
     audio        unknown                (mixer path "radio_in" changed; no second UART stream)
     powerNode    /sys/class/gpio/gpio123/value  (medium: 0→1 at app start)
     pttNode      unknown                (cage step not run)
     pttActions   com.anysecu.ptt.down/up (high: broadcast seen in capture)
     stockApp     com.anysecu.intercom   (high: ranked 12, holds ttyS1)
     sepolicy     allow untrusted_app serial_device chr_file {…}   (from ls -Z)
     serialLib    /data/app/…/lib/arm/libserial.so → com.anysecu.serial.SerialPort (JNI symbols)
   ```

9. **`report.json`.** The same facts as structured data, written beside `report.txt` and included in the zip, plus a
   host script `scripts/report_diff.py` that compares two reports field by field. The W6 produces the first pair
   (A, B) to test it on.
10. **Bench driver.** `DiagReceiver` commands for each step (`report`, `capture`, `watch`, `probe`, `checks`, `zip`) so
    the whole flow runs from adb; a host script `scripts/w6/run_checks.sh` that triggers it, waits, pulls the folder.
    This is how the agent iterates on the W6 without the screen.
11. **The two gaps found in the 2026-10-24 review.** The radio audio check loses its result when no report folder
    exists (`DeviceView.runDmrAudioCheck`); the PTT capture, probe and audio check append to whatever folder is newest,
    however old. Both should use `currentOrNew`.
12. **Phone-side hygiene found on the W6.** Whatever the Unisoc build breaks: path quoting, `toybox` differences on
    Android 10 (`timeout`, `stat -c`, `cmd package resolve-activity`), the 200-line `cat` limit, generation time on a
    slow quad A53.

### Phase 3: Receive side on the W6

- With the protocol known, decide what "compatible" means for the W6:
  - same MCU family (unlikely): write the profile from the draft, run the probe and the trial take-over, then the
    listen check and the second radio test as on the Xplore 1;
  - a different module: the report is the product. SignalDeck states the protocol it found and that no engine exists
    for it; a second engine (`AtModuleEngine` or similar) is a separate project and **not** part of this plan.
- Either way, run the full **Help support this phone** flow as a stranger would, from a fresh install, and fix every
  wording or ordering problem the W6 exposes. Time the run; it should stay near two minutes.

### Phase 4: Transmit side (Faraday cage, licensed owner)

Today the stock-app watch says "do not press PTT" and nothing in the report describes transmission. The W6 lets us
design that step safely.

- New report step **"Transmit watch"**, reached only from the Device report menu behind a confirmation that says it is
  for a shielded enclosure operated by a licensed operator. It never keys anything itself. While the owner presses the
  stock app's PTT for about two seconds, twice, it records: the serial bytes (strace), every sysfs/GPIO change (the
  Phase 2 snapshot diff at 100 ms), audio mixer changes, `getevent`, kernel messages, and the key-down to key-up timing.
  Output: a "PTT sequence" block in the report (what was written where, in order, with offsets in ms) and the draft
  profile's `pttNode` with high confidence.
- The owner runs it in the cage with a receiver (HackRF or the second radio) confirming the W6 actually transmitted,
  so the recorded sequence is known to be the real one, not a half-press.
- From the sequence, the maintainer knows whether PTT is a GPIO line, a serial command, or both, and the lead time
  between the line change and RF. That is the transmit half of a profile.
- Measure, in the cage, what the stock app's limits are (time-out timer, re-key delay) so a future engine for this
  module family starts with facts.

### Phase 5: Close the loop on the other phones

- Re-run the improved Run all checks on the owner's Armor 26 Ultra (verified profile) to make sure every new section
  behaves on a known-good phone and the draft profile matches the real one field for field. That comparison is the
  acceptance test for the draft-profile generator.
- Ship a beta; ask the Xplore 1 tester and the Armor 20WT owner for one more run. Their reports with the new sections
  are the first real-world proof.
- Update `docs/DEVICE_SUPPORT_REQUEST.md` (its intro still talks about DMRModHooks) and the in-app help.

## 4. Working method with the agent on the bench

- Scripts for root live in `scripts/w6/` and run as `adb shell su < script.sh`; outputs are pulled into `docs/w6/` or
  the scratchpad, never pasted into chat by hand.
- Every report produced on the W6 is kept (`docs/w6/reports/<date>_<step>/`) so regressions in the report are visible.
- Each improvement is built, host-tested, installed on the W6 with `adb install -r`, verified by pulling the report,
  then logged in `CHANGELOG.md` with "Device: W6 (Unisoc, analog UHF)" in the test column, and the help updated.
- Nothing in this plan keys the W6's transmitter from software. Any step that needs RF is handed to the owner with a
  written procedure, run in the cage, and its results are read back from the report.

## 4a. Starting over: the "forget this phone" reset

As the report improves, the W6 must be put back to the state a stranger's phone is in, so the next Run all checks has
to rediscover everything and we can see whether the new report does it alone. A profile is only one of the places
SignalDeck remembers a phone; a reset that misses one lets the rerun cheat. Three tiers, each a script in
`scripts/w6/` run over adb, plus one small app change.

| Tier | What it clears | How | When |
|---|---|---|---|
| **1 Forget** (seconds, no reboot) | the detected profile; support-flow progress (`supportChecks`, `supportListen`, `supportSecond`); the cached signed profile list; the stock serial library copied into the app's files dir; report folders (moved to `docs/w6/reports/<date>/`, never deleted) | a `DiagReceiver` command (adb only, DUMP permission): `profile --es id unknown` sets a `profileOverride` pref that `ProfileRegistry.active()` honours before built-in detection; `forget` clears the progress prefs and caches; the script archives the report folders | after every report improvement: the normal loop |
| **2 Fresh install** (a minute, one reboot) | everything in tier 1 plus all app data (`pm clear com.macdmr.radio`: channels, settings, first-run flags, stock-firmware record); the `dmr-standalone` Magisk module (Keep access after reboot) and the live sepolicy rules; the stock app re-enabled and started | script: `pm clear`, `rm -rf /data/adb/modules/dmr-standalone`, `pm enable <stock>`, reboot. The Magisk grant survives (same UID), so **never `adb uninstall`** | before timing the stranger's end-to-end run, and whenever an in-app state might be masking a report gap |
| **3 Factory** (an hour, re-root) | the phone itself: factory reset, re-flash the Magisk boot image if needed, re-grant root | by hand, following the route recorded in `docs/w6/` during Phase 1 | only when the ROM or module state is itself in doubt (after a flashing experiment, or a sysfs write that stuck) |

Rules:
- The override pref applies to the W6 only through adb; a release build never exposes it in the UI, so no user can
  switch their phone to "unknown" by accident. Tier 2 and 3 are never run on the owner's Armor.
- A reset is verified before the rerun: the app's Device page must say "not supported yet" and offer Run all checks at
  launch, `pm list packages -e` must show the stock app enabled, and `ls /data/adb/modules` must not list the module.
- Every rerun's report is kept beside the one before it, so the question "does the new report find X on its own?"
  is answered by `scripts/report_diff.py` between the two, not by memory.
- If a W6 profile is ever added to `DeviceProfile.java`, it stays `verified = false` until Phase 3 says otherwise, and
  the tier-1 override is how the discovery path is tested with the profile present in the build.

## 5. Risks and fallbacks

| Risk | Fallback |
|---|---|
| Bootloader cannot be unlocked | Phases 0 and parts of 2 (app finder, APK clues, PTT key, 32-bit libs, `report.json`, bench driver) still proceed unrooted; root-only items wait for another bench phone. |
| Unisoc needs a signed boot image | The bootloader on the bench unit was already unlocked (`orange`). Stock `boot_a` is dumped. No public PAC matches `sl8541e_3h10`. The replacement follows `docs/w6/REPLACEMENT.md`. Do not `adb reboot autodloader` (it erased `splloader`). |
| Analog line-level audio into the phone's codec | Out of scope for SignalDeck's two backends; the report must detect and name it (mixer path diff), which is itself the deliverable. |
| `strace` not available or blocked by SELinux | Ship a static binary in assets and run it from `/data/local/tmp` as root; if ptrace is denied, fall back to `/proc/<pid>/fdinfo` polling plus tty `stty -g` sampling, and say so in the report. |
| The W6 is too different to generalise from | That is the point: a report that survives Unisoc + analog + 32-bit + unknown app covers the MediaTek DMR phones as a subset. Phase 5 checks that. |
