# UNIWA P8 receive-only build rx.5d

Testable modified SignalDeck edition: `com.macdmr.radio.p8lab`, ordinary UID10195 on this P8, no root. Original Android, drivers, stock Intercom, protected factory firmware and backups are retained. This is a foreground-only experimental analogue receiver, not general P8/DMR or transmit support.

## Using the installed build

1. Open P8 Radio Lab (modified) → Device → P8 offline workbench → **P8 receive-only radio**.
2. Use **Intercom App info**, choose Android **Force stop**, and return. Do not swipe Intercom away or use its Exit action, which can power the module down. Start Intercom normally first if the radio has not been initialised. Keep physical PTT/SOS/function buttons untouched.
3. Enter the RX frequency in MHz, narrow/wide bandwidth, squelch0–9 and monitor mode. **Connect RX** opens both UARTs and applies the receive configuration. Monitor open is useful for checking receiver hiss. Software gain cycles1/2/4; clipping/backlog is reported. It changes speaker PCM only.
4. Save/update/add/delete local RX channels while disconnected. Import supported plain FM channels from the lab's existing SignalDeck channel database if desired. No stock database/private channel data is copied automatically. Tones/DMR/M17 are omitted. Saved RX list is capped at64; the scan list is a snapshot taken at connection.
5. Connect, then use **Tune selected saved channel** or **Start/stop scan**. Scan dwell is1.2s, with a3s hold extended by receive status or significant PCM amplitude. Use monitor closed for scanning; monitor-open noise holds the scanner by design.
6. **Stop RX and restore Intercom** closes ports/restores UART settings before launching stock. Leaving/locking the screen also closes the foreground RX session. There is no boot/background takeover. The receive window keeps the display awake while visible; manual locking still ends RX. Background listening is a later feature.

Android does not allow an ordinary app to force-stop another app programmatically. The manual App-info step is the root-free standalone handover. Host tests use the already-authorised ADB package stop. The app verifies stock stopped plus readable PTT/power/audio state1; these do not constitute a complete FD-holder audit. Other-holder exclusivity remains unproven. Unexpected state, response/checksum/TX status, UART or playback error closes the session without retries. Failure to restore UART settings prevents automatic handback and is reported.

## Implementation and safety

- `P8RxProtocol` builds the verified stock19-byte command23 body. RX/TX frequency fields are equal; power field0, carrier/no tones, power-save2, module volume1, monitor repeated in its final byte. No PTT command exists in this transport.
- `p8_receive.c` independently validates packet markers, checksum, command, field ranges and constrained136–174/400–480MHz frequency windows. These software windows are not a certification of all frequencies/hardware capabilities; live tests covered two UHF frequencies only. Arbitrary serial commands, GPIO writes, calibration, MCU update and transmit are excluded.
- Command UART is expected chr207:5 at57600; PCM is expected chr4:64 at230400, opened read-only. Both descriptors stay on one worker, saved termios restored on close. No serial flush or automatic unsolicited-frame ACK. A complete tune acknowledgement returns promptly, then Java verifies checksum/status. No automatic retry.
- `P8Receiver` owns control stream parsing, tune/scan,167-byte/160-payload PCM framing and8kHz mono PCM16 AudioTrack. AudioTrack is playback-only: no microphone. `P8AudioQueue` retains partial/nonblocking writes, caps backlog at two seconds and stops instead of silently discarding overflow. Local gain saturates rather than wraps.
- No root, bootloader change, firmware flash, radio calibration change or RF transmission operation was performed. No claim of independent RF monitoring. Existing main radio-engine/TX/GPIO guards remain; general P8 profile supported/verified flags are not promoted.

## Validation and measured results

38 focused Java tests pass,0 failures/errors: packet layout/bounds, fragmented ACK/status across successive reads, bad checksum/status and TX-status rejection, scan dwell/hold/wrap, gain clipping, partial/blocked playback writes and backlog bounds, plus existing profile/report/ownership/hardware gates. Release/native ARM64+ARMv7 compilation and development-signature verification pass. No full-suite rerun claimed for this milestone.

Live ordinary-UID tests, with Intercom restored after each completed radio test:

| Test | Result |
| --- | --- |
| Six-second pilot,446.00625MHz narrow, monitor open | Tune ACK;88,850 UART bytes /532 PCM frames; owner confirmed audible receiver noise. Detected4,320 discarded playback bytes in initial pilot; corrected with retained-write queue. |
| Corrected monitor-open regression | Tune ACK;91,854 UART bytes /549 frames;0 clipping,0 pending audio bytes at close; safely restored both UARTs/Intercom. |
| Two-channel monitor-closed scan | Five ACKed tunes alternating446.00625 and446.01875MHz;0 PCM frames in the quiet window; safe close/handback. |

Pilot receipt `rx5_1ba2dcb45f8e4f7abfbe56c0be0e3757.json`; corrected audio `rx5_6ffc5367de7c49cf8a9df362bc3c6aca.json`; scan `rx5_babe681d258d49dc9a995baf8061281c.json`. Private local p8-development receipts, not repository data.

Two intermediate automated launches were blocked by foreground/secure-keyguard state, before any RX worker/UART access. Preserved receipts `rx5_04b466085d2b4bc59321528db12feda9.json` and `rx5_6a94eec44e004bb18e9d1c39a3b309a3.json`. Cancelled pending lab launch and restored stock; no blind retry of a radio transfer. Corrected launcher to wait for actual window focus; owner unlocked the secure screen, then both regression tests passed. No keyguard-security bypass.

Final sampled states remained PTT1/power1/auctusctl1. Factory-test stayedPID5421. Latest scan handback restored stockPID17171/service/stopped=false. No physical RF signal/voice test or scan hold-on-real-voice validation performed. Unit-tested scan hold is not equivalent to that live evidence. Local save/import UI is implemented but not independently persistence-tested on the phone in this session.

## Artifact

Installed `P8-Radio-Lab-1.0.0-beta.20-p8-rx.5d.apk`,31,756,637 bytes.
SHA256 `3ac6b3086f75989b36482312d18ee88ca551f44a692b7d772b8ca835b33c309d`.
Same local Android development certificate as previous lab builds, not the official SignalDeck release key. APK/verification receipt are in the separate p8-development/deliverables directory. Prior builds and logs preserved. Source stays on uniwa-p8-support, no publication/push.

Live audio/scan measurements above used .5/.5c. Final .5d retains the same native implementation and adds unit-tested fragmented status preservation across tune replies, atomic pending tune requests, screen keep-awake and10s cancellation of an unstarted host test awaiting foreground. The latter refinements were built/tested but no additional live session was needed to repeat the unchanged analogue/PCM native tests. The previously verified .5c APK (31,752,545 bytes, SHA25613f37e5870f191e07db7422e2f030207597454380afca3bcb85a0484c4d2a164) is retained alongside the final build.

Next: known incoming voice/receive quality and scan hold validation without transmitting from P8, channel persistence/import UX verification, then foreground/background lifecycle/audio-focus improvements and programmable-button integration. Firmware/root work is unnecessary for the proven command and PCM paths.
