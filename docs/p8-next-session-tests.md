# P8 next-session tests

Baseline: root-free analogue receive and on-screen voice transmit confirmed on main.1f. This is not yet a fully validated radio release. Keep Intercom available. No unattended RF testing.

Work through these one at a time with logs running; stop on unexpected PTT or handover failure.

1. **Connection and fallback:** start with TX locked; release Intercom, Connect, confirm tune ACK. Hand back, check Intercom opens normally, then reconnect. No two applications should own the UART simultaneously.
2. **Saved channels:** add a receive-only channel, edit its name, select it, restart the application and confirm settings survive. Delete only the temporary channel. Verify frequency, narrow/wide setting, squelch and supported tone settings against the saved record; unsupported settings must be rejected clearly.
3. **Receive after transmit:** listen to the other radio, perform one manual voice test on the agreed setup, release, then have the other radio reply. Confirm speaker audio resumes and TX ends normally.
4. **Physical PTT:** check the hardware PTT preference, then press/release while TX is locked: no RF. Allow transmit on the selected channel and repeat with speech; confirm release stops immediately. Check the configured timeout separately. No unattended key presses.
5. **Rotary knob:** with two saved channels and TX locked, turn one step each direction. Confirm exactly one channel step, correct direction and acknowledged tuning. Do not assume the volume knob behaves as a volume control in the current adapter.
6. **Scanning:** use at least two receive channels. Start/stop from the main interface; signal should pause scanning, then resume according to settings. Confirm selecting a channel stops scanning and no TX is possible accidentally.
7. **Programmable buttons:** learn each supported Android function/gamepad key; assign next/previous, scan or monitor. Confirm a single action per press, no repeats while held, and saved assignments after restart. Some physical keys may not expose compatible Android events; record actual events rather than guessing mappings. Leave SOS unchanged.
8. **Audio persistence:** check module level, Android media volume and software gain survive restart; briefly check meter/clipping indication. No further audio optimisation during this pass.
9. **TX controls:** verify receive-only blocks PTT; allow transmit and check two separate hold/release cycles without arming. Confirm Stop transmitting releases the radio. Select a short configured timeout, hold until cutoff, and confirm it does not re-key until released and pressed again. Do not expand frequency or power scope.
10. **DMR:** verify profile and channel validation offline first. DMR live operation remains unverified; no DMR transmit test is included in this checklist. Do not present it as working until protocol/settings and an appropriate live setup are established.

Record for each item: APK version, pass/fail, visible behavior, relevant log timestamps and follow-up. The successful voice test does not validate every channel, physical key or digital mode.
