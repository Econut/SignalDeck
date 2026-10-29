# P8 on-device connection recovery — main.7

Installed version 142, 29 October 2026. APK SHA-256: 40f87bcbf2a377f3e9cfd0f72cd843f477cf149a3df50bbf24bcfb55a5dde2ce.

## Out in the field

1. Choose **P8 MENU → Recover radio connection**. Scanning and PTT requests stop, the current UART/audio owner closes, and recovery waits up to eight seconds for completion.
2. If Intercom is running, use the offered **Intercom App info** button, Force stop it, return and choose Recover again. Android prevents this unprivileged app from force-stopping another package itself.
3. Once recovery reports success, choose **Open Intercom** and wait for its normal channel screen. This lets the stock app perform its module startup handshake.
4. Return to SignalDeck and choose **Connect**. If asked, use Intercom App info → Force stop, return and Connect. This is the existing root-free ownership handover.

Recovery releases only the fixed software PTT output and verifies PTT/power/audio states 1/1/1. It clears the in-memory connection fault only after verification, with no process restart needed. It does not send radio commands, power-cycle the module, change calibration, flash firmware or erase channels/settings. A failed UART restoration, unclosed session, failed release/readback or unexpected power/audio state remains blocked and is reported; this option does not blindly dismiss every hardware fault. It is a connection recovery, not a factory reset or Android reboot. The cold-module startup handshake still relies on the stock app.

## Saved evidence

**P8 MENU → Recovery log** displays recent events and offers Share. Service/radio events, tune acknowledgements, STOP errors and recovery outcomes append to UTF-8 logs in app external files under `Android/data/com.macdmr.radio.p8lab/files/p8-recovery/` (internal-files fallback). Files `radio.log` and `radio-previous.log` rotate at about 512 KiB each. The Java uncaught-exception handler records a bounded stack trace before delegating to Android's original handler. No audio is recorded. Hard power loss, native crash and events before service startup may not leave a complete crash trace. Logs survive ordinary restart/reboot, but app-data clearing/uninstall or rotation can remove old evidence. Note approximate incident time for later inspection.

## Validation

- Focused P8 regressions passed; eight recovery/journal tests cover exactly one release, active-session refusal, restoration-failure refusal, stock ownership, unexpected power/audio, release failure without retry, failed release readback, persistence and rotation. Recovery tests rerun after crash-log integration passed. Native/release/lint build passed.
- On-device running-stock refusal displayed the App info action; no recovery write performed in that blocked path.
- With Intercom stopped, new recovery menu completed and PTT read 1; journal file created. Open Intercom action restored the original app.
- Recovery records persisted through forced SignalDeck process restart. Receive connection after stock initialization acknowledged tune in 41 ms and volume 5.
- Recovery invoked on an active receive session closed cleanly (zero microphone bytes) before reporting success and released PTT. Subsequent receive reconnect succeeded. All 35 saved channels still present.
- Did not inject a GPIO-low fault, force a native crash, or repeat a battery-exhaustion reboot for testing. Fault paths tested with a fake backend. No assistant-triggered transmission.
