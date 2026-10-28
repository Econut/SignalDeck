# SignalDeck P8 channel CSV v1

This is the shared Android import/export contract for a future Windows CPS. Implementation: `ChannelCsv` (codec/validation) and `ChannelCsvStore` (existing ChannelStore database). Version 1 is independent of CHIRP/OpenGD77 CSV; use **Channels → Share/import icon → Import CSV / Export CSV**.

Files are UTF-8. Export includes a UTF-8 BOM for Excel, comma delimiters, CRLF record endings, and quoted values with doubled internal quotes. Import accepts BOM/no BOM, CRLF/LF and quoted embedded newlines. Do not add Excel's `sep=,` line. In Excel choose **CSV UTF-8 (Comma delimited)** when saving. Frequencies use a decimal point, MHz, and six decimal places (exact integer Hz); Excel scientific notation is accepted when it resolves to an exact Hz. No rounding of imported frequencies occurs.

All 25 column names are required, in any order; exported order is fixed. Unknown, duplicate or missing columns and unknown format versions are rejected. The 2 MB / 5000-row limits apply in both directions. Download a header-only template or the separate receive-only example sheet via Export CSV. Example rows are real rows, not comments: remove them when entering your own channels.

| Column | Value |
| --- | --- |
| format_version | `1` |
| name | Unique channel name within the file, 1–32 characters |
| rx_mhz, tx_mhz | 136–174 or 400–480 MHz; e.g. `446.106250` |
| mode | `FM` or `DMR` |
| bandwidth_khz | `12.5` or `25` |
| rx_tone, tx_tone | `None`, `CTCSS 88.5`, `D023N` or `D023I`; uses existing OEM tone tables |
| squelch | 0–9 (blank defaults to 3) |
| power | `Low` or `High`, the existing module level rather than an assumed wattage |
| receive_only | `true` or `false` (also accepts 1/0) |
| zones, scan_lists | Names separated by `|`; empty means none. Escape a literal pipe as `\|` and backslash as `\\`. Names are 1–32 characters. |
| colour_code | 0–15; blank defaults to 1 |
| rx_slot, tx_slot | Human-facing 1 or 2; blank defaults to 1 (converted to model's 0/1) |
| talkgroup | DMR target ID 1–16777215; FM may use 0 |
| contact_type | `Private`, `Group`, `All` |
| rx_groups | Up to 32 numeric IDs (1–16777215), separated by `|` |
| dmr_mode | `Direct` or `Double_slot` |
| relay | `Disconnect` or `Normal` |
| interrupt | `true` or `false` (existing DMR interrupt setting) |
| soft_squelch | `true` or `false` |
| soft_squelch_level | 1–10; blank defaults to 5 |
| notes | Optional free text, including quoted newlines |

FM rows still carry the DMR columns so changing a mode does not require another format. Copy the example defaults for unused columns. CSV v1 does not support M17 or encrypted channel/key export; it reports these rather than silently dropping those settings. Importing DMR settings does not establish live DMR compatibility on the P8.

## Import semantics

The preview shows new rows and duplicate saved names. Matching uses the trimmed channel name, ignoring case—not frequency, since different tones or uses can share a frequency. Renaming a row makes it a new channel. Multiple existing channels with the same matching name are ambiguous and produce an error; rename them first.

**Update matches** preserves the existing row ID, channel number, active flag and unrepresented location metadata; replaces the CSV fields and that channel's zone/list membership. Blank membership removes that channel from those groups. Other group members stay unchanged. **Skip matches** leaves matched channels and memberships entirely unchanged, and creates no groups mentioned only by skipped rows. New rows append to the channel book. Missing referenced zones/lists are created; name matching ignores case. Group membership uses existing zone numbers and stable scan-list IDs, not a second channel system.

The entire file is validated first, with physical line numbers in row errors (up to 50 displayed). No invalid rows are silently imported. Saving is one SQLite transaction; a later failure rolls everything back. Import pauses scanning, blocks PTT for the save, and refreshes the active channel afterwards. It never transmits. It does not resume scanning automatically.

Export All, a selected zone, or a saved scan list. Each exported channel includes all its zone/list memberships, including groups outside the selected export scope. Empty groups have no channel row and are not represented by channel CSV. Contacts, messages, history, device identity and calibration are outside this format; it is not a full application/firmware backup.

## Main.6 verification

- 96 focused host tests passed, including exact Hz/scientific notation, quoted commas/quotes/Unicode/newlines, escaped group names, FM/DMR tone and slot round trips, malformed quoting, invalid fields/header/version, repeated names, templates and input limits. Native/release build passed.
- On-device scratch-database tests passed: update/skip, stable IDs/active flag/location metadata, automatic zones/lists, cross-zone export, persistence after reopening, preserving unrelated members, skipped rows creating no groups, and rollback after a later ambiguity. Existing channel management and isolated PTT UI tests also passed; the test APK was removed.
- Real Android storage test: exported all three existing PMR channels to Downloads/P8-channels.csv. Readback verified UTF-8 BOM, 25 columns, exact MHz values 446.106250 / 446.118750 / 446.131250, and PMR Channels list membership. Reopened through Import CSV: preview correctly found 3 matches; Skip matches reported 0 added / 0 updated / 3 skipped.
- A temporary malformed CSV showed a useful physical Line 2 frequency error and saved nothing. Removed only that temporary Android file. Existing channels retained after app restart. No RF transmission, firmware or calibration change performed.
- Installed APK: main.6, version 137. SHA-256: `9adc926703f77c750ac0cbfaa51388171b2aaa3492859dea780f4571ae3331ae`.
- Excel-ready UTF-8 BOM/CRLF files: `docs/templates/P8-channel-template.csv` and `docs/templates/P8-channel-examples.csv`. Verified using an independent CSV reader; no interactive Excel session was used.
