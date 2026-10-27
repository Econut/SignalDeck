# P8 receive-volume controls — rx.6b

The owner's rx.5d test established intelligible, clean analogue reception on446.10625MHz, narrow FM, carrier/no tones, squelch1, monitor closed. Media15/15 and software gain4 produced usable but lower volume than other radios. The cumulative clip count stopped increasing during normal speech; its earlier growth may have included feedback, but that cause was not proven. Source review/development resumed under the owner's explicit approval; no more RF tests were needed.

## Stock Intercom comparison

Saved stock APK evidence, unchanged outside the repository:

- `y2.c` analogue body includes `key_receive_volume`, default1. Our rx.5d channel builder also fixed it at1.
- `s2.g.b`, key_receive_volume branch, sends `new e4("2E", t2.d.V(value,2))`; callback `r2.g` checks command success. This is the verified one-byte receive-level setter, distinct from MIC gain command2A, power or PTT. None of those other controls was added.
- The setter branch has fallback9 when reading that preference, while channel setup defaults1. The shipped settings_preferences.xml omits the receive-level control altogether. Generic SeekBarPreference defaults0..100 unless configured; these generic defaults do NOT establish the module's valid range. No manufacturer-certified normal range was found.
- The development control is conservatively bounded1..9, default1. Intermediate values are not individually certified; endpoints/2 were tested by acknowledgement. ACK means accepted command, not a calibrated gain or guaranteed louder PCM.

Golden requests: level1 `68 2E 01 01 95 BF 00 01 01 10`; level9 `68 2E 01 01 8D BF 00 01 09 10`. Native code independently builds/checks the exact shape/checksum and1..9 bound. Analogue tuning continues to carry the selected receive-level byte so scanning does not silently revert it to1. Module changes run on the owning UART worker, not the UI thread, and require a successful2E reply. No retries, arbitrary sends, GPIO/PTT or calibration access.

## Measured module test

One12s receive-only monitor-open session, per-track speaker muted to avoid loud noise, ordinary app UID10195. Levels1→2→9→1 all acknowledged.193,379 received bytes,1,157 PCM frames,0 clips and0 pending audio at close. Saved preferences were not changed by the test sequence. Original UART settings restored, descriptors closed, Intercom/service restored, PTT/power/auctusctl remained1/1/1.

Median raw PCM noise RMS (excluding the initial startup window):

| Module level | RMS dBFS |
| --- | --- |
| 1 | -17.82 |
| 2 | -17.83 |
| 9 | -17.61 |

No material noise-level increase was demonstrated. Possible different audio-path/voice-specific effects remain hypotheses; no speech-level A/B or hardware speaker-path measurement claimed. Default stays1; the UI explains the limitation rather than promising a volume boost at9. Receipt: private `p8-development/rx6_volume_7eaad05d2447453e98abf4ba35cf0747.json`.

## Separate user controls

- **Module receive level**: saved desired1..9; applies at connection or through verified2E on slider release. The label distinguishes saved/pending from acknowledged applied value. Default1.
- **Android media volume**: real STREAM_MUSIC value/current platform maximum; changes on release, persists the resulting actual value. Polls the platform while visible so physical knob changes and slider labels stay in sync. Saved target applies at explicit Connect, not by simply opening the screen. It affects Android media globally, not radio firmware.
- **Software gain**: separately saved1..4, changes local PCM only. UI/controller policy remains capped4. DSP primitive capacity16 is separate for a future policy change after testing; no user path enables >4 now.
- **Level meter**: pre-gain PCM peak/RMS dBFS, updated in short windows, and session-total/recent clipped-sample counts. Recent clipping turns the indication red; cumulative clips are not falsely presented as current distortion. Silence reads the meter floor-96dBFS. Gain uses saturation, never integer wrap.
- Settings and channel saves use confirmed SharedPreferences commit; persistent save notices have their own TextView and cannot be overwritten by live RX status. Save errors are labelled. Channel fields now have visible labels as well as hints. Channel save remains disconnected-only. Existing channel storage is preserved.

Foreground receive/TX restrictions and stock fallback remain as in rx.5d. No background/boot takeover or microphone input. Per-track test muting is not a persistent audio route/system setting.

## Verification and artifact

42 focused host tests pass,0 failures/errors. New coverage includes golden2E packets, module bounds, successful/mismatched/failed ACKs, calibrated meter scale/window reset, and separation of gain4 policy from wider DSP capability. ARM64/ARMv7 native build and APK signature verification pass. Initial host build caught a C hexadecimal-token spacing error; fixed before installation/device access. Final6b changes after the tested6 pilot are explanatory UI/save-result handling, with protocol/DSP unchanged.

Actual UI slider/persistence test, with Intercom running and no UART opens: set module3, media7/15, gain2; restart only lab app; verify stored/current values3/7/2. Restore module1/media15/gain1, reopen and verify. Save labels remained visible. Final existing-stock-task launch emitted Android's benign task-brought-forward warning, which the cautious host script recorded as STOP; subsequent read-only check confirmed stock PID20933/stopped=false, media15/15 and GPIO1/1/1. Raw warning retained, follow-up verification appended. Receipt `rx6-ui-persistence-results.json` outside repository.

Installed `P8-Radio-Lab-1.0.0-beta.20-p8-rx.6b.apk`,31,764,833 bytes.
SHA256 `0e2d6dfc3a9d92cda25343282342c637bd928254be5f662accb64d81cd0415f2`.
Same separate package/local development certificate. APK/`rx6b-verification.json` retained in development deliverables; older builds intact. No firmware, calibration, bootloader, original factory reference or backup modification; no RF transmission operation.

Current saved defaults after tests: module1, gain1, media15. Previously saved Channel9 is retained. Open the receive-only screen as before; the three sliders and meter are near the top. Adjust software gain up to4 if desired and watch recent clipping. No claim of intrinsically louder module PCM has been made.
