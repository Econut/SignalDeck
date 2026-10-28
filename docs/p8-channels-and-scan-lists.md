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
