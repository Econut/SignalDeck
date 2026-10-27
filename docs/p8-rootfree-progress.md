# Root-free P8 implementation — live communication milestone

Latest milestone: [testable receive-only app rx.5d](p8-receive-only.md) adds analogue tuning, audible PCM playback, local channel management and live-tested two-channel scanning. The earlier diagnostic-only limitations below are retained as historical progress.

Owner authorisation now covers routine stock stop/restart, UART open/close and verified non-transmitting communication tests. No bootloader, firmware, calibration or RF-transmit operation is permitted. Earlier per-operation approval statements are historical. No root is needed for the results below.

## Measured results

Ordinary lab application UID10195, same factory Android and drivers:

1. Open/fstat/close of `/dev/ttysWK0` chr207:5 succeeded in69ms. Zero bytes, termios calls or GPIO writes. Stock stopped and handback verified.
2. Version query at57600 succeeded. Exactly `68 34 01 01 95 B9 00 01 01 10` sent once; bounded1500ms receive; saved termios restored; descriptor closed. Reply `68340000318B0013444D52303033424E2E555634502E563032360010` passes existing checksum/framing/status validation and reports **DMR003BN.UV4P.V026**. Intercom restored; PTT remained released.
3. Rootfree.4c receive-only PCM sample opened chr4:64 at230400, restored settings and closed successfully in1505ms;0 bytes/frames arrived during the1500ms window. No commands sent; no claim of working or failed receive audio without a known incoming signal. Stock handback verified. Receipt `p8_36841b7aeed7402da1931e9e70395540.json`.
4. Rootfree.4c regression version test passed its new on-device checksum/status/text parser, stored the version and historical result for the workbench;1566ms, same exact reply/version. Stock handback verified. Receipt `p8_6a247fd6b33e458d880e81bbaaa29d2a.json`.

Private host receipts: `p8_f4aa2a92850e4da99251b1abc3a4a305.json` and `p8_70a7e01e2ce14eb0bd75a813111cedbe.json`, under p8-development. These are measured tests, not an assertion of complete radio compatibility or exclusive ownership.

An intervening launch hit Android's existing-activity intent delivery. No onNewIntent diagnostic handler exists, no test receipt was produced and no UART test ran. Stopped on warning, checked unchanged state and restored stock. Fixed by completing/removing the diagnostic task and explicitly launching a fresh task. Failed host receipt `p8_fa8601d47d0948978911c030389d2ef2.json` preserved. No repeated radio command was hidden as a retry.

## Implemented adapter

- `P8RootFreeSession`: scoped root-free development gate, distinct from the unchanged strict complete-holder gate. Requires enabled lab build, stock stopped, fresh host-confirmed process absence, PTT1 and an allowlisted operation. Other-holder exclusivity is explicitly unproven, not represented as a successful FD audit.
- `P8DiagnosticActivity`: DUMP-permission-restricted host entry point, runs under lab app UID; no arbitrary port/path/bytes. Per-session no-replay file, no activity-recreation replay. Checks stock package/PTT, writes receipt and finishes. Does not stop or launch stock itself, does not enable radio engine or persistent takeover.
- JNI: default compiled off. Enabled development edition supports open-only; exact hard-coded version query; bounded read-only PCM capture. Verifies node type/device/inode, uses nonblocking/no-follow/no-ctty opens, restores termios and closes before result. No GPIO, PTT, tune, volume, MCU-update or arbitrary command API. Version is57600; PCM is read-only ttyS0 chr4:64 at230400. C open/close can still have driver effects; nonblocking is not a guaranteed kernel wall-clock bound.
- Strict bounded version-capture parser rejects corrupt, incomplete, failed, empty or conflicting responses and unexpected transmit status. Valid unsolicited frames are not acknowledged.
- Workbench shows the last diagnostic as historical evidence, never current ownership. General offline radio engine, PTT, native vendor wrapper, root and GPIO gates remain.
- Host coordinator checks package/process/service release and PTT/power/audio state, passes a fresh uptime attestation, invokes one diagnostic, observes its receipt, and restores stock after known successful close/safe state. It stops on warning/error or unexpected state; no automatic command retries. A timeout leaves stock stopped rather than relaunching into an unknown open descriptor. Scripts and receipts stay outside the source repository.

## Validation and limitations

29 focused Java tests pass, including the actual measured reply, checksum corruption/truncation/status rejection, ownership policy and unchanged TX/hardware guards. Default-disabled and enabled builds are separate. Native libraries compile for ARM64/ARMv7. Local development certificate, separate com.macdmr.radio.p8lab package; proprietary APK/decompilation and original backups stay outside repository.

The app is not yet a standalone receive radio: live diagnostic handover currently uses authorised ADB shell. Android cannot grant an ordinary app the shell's force-stop privilege. Normal FM channel application, scanning, PCM playback/gain and programmable buttons need integration and validation. Do not mark the P8 profile generally supported merely because the version query works. DMR MCU calibration preservation remains separate from Android backups.

Next engineering work is receive-only PCM/routing observation, then verified analogue tuning with a receive-only service state machine. Any unknown command, calibration access, firmware/security change or RF transmission is outside current scope.

Final installed package: `P8-Radio-Lab-1.0.0-beta.20-p8-rootfree.4d.apk`,31,711,397 bytes, SHA256 `9701cb895da3c12733c773c856f8cdd94dc3c6335ea08be0756885d09add558f`. Stored with `rootfree4d-verification.json` in the separate development deliverables folder. Same local development certificate; no official release key used. Final .4d changes after live-tested .4c are version metadata and accurate passive-audit status text; no further radio test was needed for those changes. Installation preserves lab data/history and leaves stock Intercom installed/enabled/running. Device → P8 offline workbench displays the stored root-free result without another radio operation.
