# P8 channels, zones and scan lists — main.5

The main radio layout is unchanged. These features reuse ChannelStore, the channel editor, ZonesDialog and Scanner.

Main.5a adds **Scan lists** directly below the channel name in Add/Edit channel. Tick any saved lists (or leave None), confirm the selection, then press the channel's Save button. Existing memberships are preselected when editing. Cancelling either the picker or the editor does not save membership changes. New-channel membership is written after insertion supplies its stable ID; other list members and their order are preserved. Create named lists using SCAN → Manage scan lists first.

Main.5a validation: 88 focused regression tests and the isolated on-device database/PTT checks passed, including adding a channel to multiple lists, removing it from one list without removing other members, and preserving existing ordering. Actual Add-channel selection, cancel-without-saving and existing-channel preselection were checked against the user's PMR Channels list without changing the three saved channels. Version 136 APK SHA-256: `687f82c2b3ec117bbd93b96c513a94618e809133d8c8f06181dc28c12c87b33f`.

- **Channels tab:** add a channel with +; tap a saved channel for Use, Edit or Delete. Save retains frequency, FM/DMR settings, bandwidth, tones, squelch, power and receive-only status. New channels created while a named zone is selected are added to that zone. The channel detail card also offers **Zones for this channel** to assign it to existing zones.
- **ZONE: ALL:** select or manage named zones using the existing zone manager. Zones control the channel list and arrow navigation. A channel can belong to more than one zone.
- **SCAN:** choose Current zone or a saved scan list. **Manage scan lists → New scan list** names a list and lets you select channels from all zones. Each option includes its frequency and zone names. Existing lists can be renamed, refilled or deleted without deleting channels.
- Choosing a list remembers it and starts scanning if connected and eligible. Tap SCAN again to stop. Long-press retains the existing temporary-skip behavior. At least two supported channels are needed; unsupported P8 settings are filtered by the existing adapter. Priority scanning cannot add a channel outside the chosen list/zone.
- Channels, zones and lists are stored in the app database. Zone/list selections are stored in preferences. A restart retains data and selections but does not automatically resume scanning or take over Intercom.

Scan lists use stable channel row IDs, independent of zone membership and displayed channel numbers. Deleting/renumbering a channel prunes the deleted member and preserves the others. Channel/list membership edits stop an active scan before the next session. The scanlists table is added without invoking the legacy destructive database upgrade.

## Validation

- 88 focused host regression tests passed; native/release builds passed.
- On-device isolated scratch SQLite tests passed: all edited channel fields survive reopen, zones and custom lists survive reopen, a list spans two zones, duplicate/missing IDs are removed, deleting/renumbering channels preserves identity, deleting a zone keeps the independent list, case-insensitive duplicate list names are rejected, and deleting a list keeps channels.
- Existing isolated PTT touch/cancel/focus/detach/render tests also passed. They use a recording listener and never open the radio.
- Installed main.5 (version 135) preserving existing data. Actual UI creation/save/select/restart of a temporary scan list passed; its selected checkmark survived a full app restart. Deleted the temporary list afterwards. ZONE opens the existing manager; main-screen panel bounds remain unchanged. The test APK was removed.
- Intercom was restored cleanly before installation and remains available. No P8 RF transmission, firmware or calibration changes were performed. Live RF scan hold/resume across multiple populated channels remains the next functional check; the current user database has one channel.

APK SHA-256: `226e677a7b381f7df80cfd7e9556e9b85572a2f748b6a7740c870e3a9fcd3d87`.

## Main.6b: selectable P8 scan timing

SCAN → Scan speed offers Standard (1.5 s), Balanced (1.0 s) and Fast trial (0.8 s). Standard remains the default; invalid stored settings fall back to Standard. The setting persists across restart. DMR retains 1.5 s. Main-screen design is unchanged.

For P8, dwell starts only after the matching tune acknowledgement. The existing 700 ms settle, 2.5 s receive hang and activity hold remain intact. Relative timing uses the monotonic clock. Other hardware profiles retain their existing behaviour.

Validation: 99 focused host tests passed, release build passed, and isolated on-device tests covered acknowledgement waiting, wrong/late acknowledgements, settle time, activity hold, resume and stop. Balanced persisted through an app restart. Live receive-only testing across all 16 PMR channels measured complete quiet cycles of 19.500, 19.104 and 19.527 seconds (median hop 1.201 s including retuning). Channel 9 reception produced RX-start, valid RSSI and received PCM FFT; RX ended at 17:49:53.868 and scanning resumed at 17:49:56.475, preserving the hang. Audible quality awaits explicit user confirmation. Weak/short-signal performance and Fast trial remain unverified. The completed scan was stopped; no P8 RF transmission occurred.

Installed APK: version 139, 1.0.0-beta.20-p8-main.6b. SHA-256: 7909ef99a2cf310f614dc6f2d2089971200893e90928dd7fe14c9c6305a7d10d.

## Main.6c: bottom-left programmable key

Passive getevent capture identified the physical bottom-left programmable key as KEY_CAMERA from /dev/input/event2, with normal separate down/up events. The mapper previously accepted only F/gamepad codes and misleadingly called Camera reserved. Added KEYCODE_CAMERA to the existing foreground assignment allowlist and clarified the learning prompt. No keylayout, firmware or GPIO changes. Existing assignments and learning TX inhibition retained. Three focused button-policy tests and release build passed; version 140 installed with data-preserving update after clean Intercom handback. Physical relearning remains for the user to verify. APK SHA-256: 20970f187bb05d9ea19afdc3e5cdcd33b65baa6f7a22932b484e8576a49d0098.

## Main.6d: repeated startup failure diagnostics

Two main.6c connection attempts stopped before initial tune ACK. Stock app had been automatically restored after the safe failure; PTT/power/audio remained 1/1/1. Added a bounded 2.5-second first-command response window (running-session window remains 700 ms), reset on UART open, with no retransmission or new command. Added bounded response-byte and elapsed-time logs to the main service. Release/native/lint build passed and APK version 141 installed preserving data.

Two independent clean stock handover/connect sessions succeeded with tune ACK, module volume ACK, PCM FFT and RSSI; first session closed cleanly with zero microphone bytes. Final build initial reply took only 40 ms and returned 6823000087dc000010. Therefore increased timeout is a mitigation, NOT proof of root cause; repeated startup failure remains under observation. Final app is connected on PMR12 with analogue PTT eligibility, no assistant-triggered transmission. Existing scan timing, channel flags and calibration unchanged. Logs: main6c-startup-failures.log and main6d-startup-verification.log in development directory.

## Main.7b — independent Channels view

Added **View** directly below the Channels toolbar: Follow radio zone (existing default), All channels, or a specific named zone. The selection is a separate persistent preference and only filters the existing channel database for browsing/editing. It does not change RadioService's zone, scan list or tuned channel. Search and All/Favourites/Analog/DMR chips continue to filter within that view. The separate toolbar zone icon is labelled Manage zones. A deleted selected zone falls back to All channels; invalid negative scope values fall back to Follow. Explicit channel Use retains its existing tuning behaviour.

Four focused tests passed: all-channel order/copy, named-zone filtering with duplicates/missing members, empty zone and deleted-scope fallback. Release/native/lint build passed. Installed version 144 preserving data. Live checks showed Follow PMR446 = 16 channels, All = 35 and Marine VHF = 10. Marine view survived force-stop/relaunch; main radio zone remained PMR446 with its PMR09 selection. No channel Use, edits or transmissions were triggered by this validation. Left channel browser preference on All channels. Main radio design unchanged. APK SHA-256: 9d85c48f9577b95ecb9a82a78c9846ff9f7dcf1461c2ab4502e9c8c4d594761f.

## Main.7c — additional experimental FM scan speeds

Added 0.6 s Very fast trial and 0.4 s Extreme trial to SCAN → Scan speed. Existing Standard 1.5/Balanced 1.0/Fast trial 0.8 remain unchanged. Existing saved selection on this device was 0.8 s and was preserved, not changed to either new option. DMR remains 1.5 s.

New 0.6/0.4 settings use 300/200 ms settle guards respectively; all existing settings keep the 700 ms guard. This ensures the new dwell values can actually detect/hold activity before advancing. Tune ACK gating, the 150 ms scheduler tick and 2.5 s signal hang remain unchanged. Stated values are dwell after ACK, not guaranteed measured channel-to-channel rates; UART retune/scheduler overhead adds time. Trial toast warns that weak/short calls may be missed. Other device profiles retain 700 ms settle and their original dwell.

Five focused timing tests passed, including validity/fallback, FM/digital separation, labels and settle guards. Isolated on-device regression suite passed, including a new fake-host 400 ms scan checking ACK wait, receive hold, hang/resume and quiet-channel advancement, with no UART/GPIO access. Release/native/lint build passed; version 145 installed preserving data. All five speed choices verified on device; test APK removed. No live RF capture/detection sensitivity validation of 0.6/0.4 yet; user field testing remains necessary. No assistant-triggered transmission or firmware/calibration changes.

APK SHA-256: 5421dfb7fbedb943af7de93d156e37c3f3b2dcd21192d766003b5c84d67c64fd.
