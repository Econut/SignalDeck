# P8 main radio integration

Modified GPL SignalDeck development branch `uniwa-p8-support`. Application identity remains `com.macdmr.radio.p8lab` to update the existing development installation and preserve its settings. Launcher label is **P8 Radio (modified)**; this is not an official upstream release.

## Implemented

- Main Intercom page uses a dedicated P8 backend in RadioService. Generic root/power/firmware transport remains disabled for P8.
- Main Channels editor/database, frequency display, channel arrows, zones, scan/hold and priority channels. Unsupported channel configurations are rejected explicitly; displayed tuned channel changes after successful module ACK.
- Transactional, repeat-safe migration of saved workbench FM channels without deleting the original book. Migrated channels remain RX-only in the database; the explicitly armed first TX test is a temporary override on the approved test frequency only.
- Existing module level 1–9, media volume and software gain 1–4, saved notices and PCM meter. No further audio optimisation.
- Root-free Intercom handover: App info → Force stop, return, Connect. Checks stopped state and existing PTT/power/audio states. Hand back closes/restores UARTs before launching stock Intercom. Ordinary Android apps cannot silently force-stop another package. No uninstall, disable, UID-wide kill, boot takeover or GPIO power cycling.
- Foreground service owns the session across tab changes; partial wake lock is released after closure. Long-duration/background operation still needs device validation.
- Stock `dmr.knob.TIMER` signed `timer` channel changes and `dmr.play.SEND_START/END` PTT broadcasts. Physical button delivery still needs live validation. Two foreground programmable F/gamepad keys can be assigned next/previous channel, scan or monitor. Reserved power/home/back/volume keys cannot be learned. No transmit assignment in the learning screen; entering it disarms TX.
- Stock DMR 0x22 direct-mode, encryption-off receive packet (163 bytes, 32 groups). IDs/CC/slot bounds are checked in Java and native code. DMR audio/call reception is not yet hardware-tested; DMR TX, encryption, SMS and repeater modes are not enabled.

## First analogue TX test

The owner requested a first brief test on Channel 9, **446.10625 MHz**. Hardware/firmware remain unchanged; PMR446 equipment compliance and actual radiated power are not independently established. A suitable authorised setup is still the operator's responsibility.

The APK contains a deliberately bounded test implementation, not unrestricted or proven TX support:

1. Connect and confirm an ACKed analogue 446.10625 MHz channel, narrow FM, no tones. Receiver configuration keeps TX frequency equal to RX and uses the stock low-power field 0. This field does not establish a measured ERP.
2. Fit the correct antenna or a correctly rated 50-ohm dummy load. Set the separate receiver to Channel 9/no tones and keep audio feedback down.
3. Start host observation before arming. Read initial PTT/power/audio = 1/1/1, verify Intercom stopped and receiver healthy. Do not use synthetic PTT broadcasts for an unobserved test.
4. Tap **Arm one 2-second TX test**, then confirm. This does not key the radio. One PTT press may key, arm expires after 60 seconds and is never persisted. Start with about one second of speech and release PTT.
5. The controller prepares O_RDWR access only to the already identified PCM UART, starts 8 kHz mono 16-bit microphone capture, then uses stock `dmrptt=0` (active low). Output is raw 160-byte PCM blocks as in stock t2.b; no RX header is prepended.
6. Button-up, close and errors attempt `dmrptt=1`; an independent native watchdog also attempts release after two seconds. Check both TX status messages and PTT 0→1. A PTT write/GUI red indicator alone does not prove RF or modulation. Confirm received speech on the second radio.
7. Stop on failure; do not repeatedly arm/retry. Inspect logs and released state, then Hand back and verify Intercom. Do not modify calibration or firmware to resolve a failure.

The native watchdog lives in the app process: it cannot survive process death, OS/kernel failure or power loss. Do not kill/update the app during a keyed test. Maintain independent host observation and be ready to release the known PTT node or power the radio off if release fails. A native release/close error prevents automatic Intercom handback. Neither full FD exclusivity nor module-side TX timeout has been independently proven.

`p8TxTestReviewed` is a separate default-off Java/native build gate. The standalone receive-only workbench never enables transmit support, even in a TX-enabled main build. Native TX accepts only the approved analogue frequency, permits PCM writes only while locally keyed and rejects configuration changes during TX.

## Build and validation

Use the existing local JDK17, Android SDK and Gradle cache documented in the development handover.

```text
gradlew.bat --no-daemon -Pp8Offline=true :app:testReleaseUnitTest --tests com.macdmr.radio.P8* --tests com.macdmr.radio.DeviceProfileTest --tests com.macdmr.radio.DeviceReportTest
gradlew.bat --no-daemon -Pp8Offline=true -Pp8OpenProbeReviewed=true -Pp8TxTestReviewed=true assembleRelease
```

56 focused host tests passed (default-off hardware build), including FM wire mapping, TX-frequency isolation, VHF narrow preservation, DMR layout/bounds, one-use arm/expiry/cancel/DMR rejection and reserved key exclusions. Native ARM64/ARMv7 compile checks are separate from live TX validation. Keep previous APKs and private receipts. Do not describe this candidate as fully validated general P8/DMR support until hardware tests pass.

### Installed candidate

`P8-Radio-1.0.0-beta.20-p8-main.1b.apk`, versionCode121, 31,809,881 bytes.

SHA-256: `70db1edb0051f92fa16451fc9e033cccef74cb465e33e2955681a1aef81ace59`.

Installed with the existing development certificate using an update, without deleting app data. MainActivity cold launch succeeded; RadioService logged `P8 main store ready: 1 channels; active 1`. Stock Intercom PID20933 remained running and PTT/power/audio stayed1/1/1. The main screen was behind the secure keyguard, so visible layout, main-interface UART receive/scanning and physical buttons have not yet been checked. No UART connection or transmission was requested by the install/startup check. The earlier workbench's successful FM reception is not being represented as a new-main-interface hardware test.

Private development evidence: `deliverables/main1b-verification.json`; independently observing host helper `watch-first-p8-tx.py` (not yet run). The helper never keys/arms the radio; it can attempt one emergency release if an observed low PTT exceeds2.5 seconds. GPIO observations do not prove RF/modulation. Next physical step: unlock the phone, inspect the normal interface, establish the main receive session and observe one bounded first TX test with the separate receiver.

## First bounded attempt and status correction

Unlocked UI inspection showed the initial control strip crowded PTT off-screen. Installed main.1c compacted it into Connect / Hand back / P8 controls; menu contains receive volume, test arm and button assignments. On-screen PTT then became visible. Main-interface tune ACK confirmed446.10625MHz and module level1, with SQ1/no tones and GPIO1/1/1. Both sessions used temporary Intercom force-stop with confirmed absence/stopped flag.

Owner performed one on-screen test after confirming antenna/peer receiver/no tones. Observer receipt `tx1_d23b136c9032443bad7a1fd85be6b16f.json` recorded1→0→1 and a2.094s first-low to following-high sample interval. This is not an RF-duration measurement (the old receipt field name incorrectly says upper bound). No host emergency release was needed. Owner reported **no voice and no signal indication** on the other radio; RF remains unproven.

App stopped on Unexpected TX status about2.2s after key assertion. UART closure completed without a reported close/release error. Manual stock restoration followed verified GPIO1/1/1 and completed controller closure; Intercom PID25538. No live retry. Host receipt was saved before a Windows console Unicode printing failure; helper output was corrected separately.

Stock x2.c.c acknowledges all status pushes using e4("36",01). Our omission and duplicate-END rejection are software defects; they are plausible explanations for the stop, not established explanations for absent RF. First attempt did not log raw status frames or microphone counts, so those cannot be reconstructed retrospectively.

main.1d correction adds a fixed native ACK (`68 36 01 01 95 B7 00 01 01 10`), validates stock push direction rw2/sr0, logs status frames, permits repeated END only within1s following our own release and continues stopping on unsolicited/late TX start/end. Adds microphone byte/peak counters without recording audio.61 offline tests passed, including golden ACK and bounded END acceptance/rejection. Maximum key time/frequency/one-use arm remain unchanged. Build/install does not authorise an automatic repeat after the stopped test.

main.1d APK installed as a data-preserving update, versionCode123,31,809,881 bytes, SHA256 `9c040ea52191b9d4d0b5b71023cf91ad4563dad9eb7c1bb996529739f66481c9`. Both ARM builds and signature verification passed. Intercom left active PID25538, GPIO1/1/1; no new UART session, arm or RF test after the failure. Receipt `deliverables/main1d-verification.json`. Missing RF remains unresolved until the corrected instrumentation is used in a separately observed test.
