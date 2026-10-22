# SignalDeck changelog

Newest first. Each entry: what changed, the files, and how it was tested (host tests, device, bench). Entries before
the move to this repository (2026-10-17 to 2026-10-22, beta.1 to beta.11 and after) are in
[phonedmrapp/CHANGELOG_DRAFT.md](https://github.com/IIMacGyverII/phonedmrapp/blob/main/CHANGELOG_DRAFT.md).

## 2026-10-22 — Decode a recording (SSTV pictures, APRS packets) on the phone and in SignalDeck Web

| Backlog | Change | Files | Test |
|---|---|---|---|
| — | Owner's request: review and decode the pass recordings on the phone. **Decode a recording** (PKT → SSTV / APRS, the SSTV and APRS pages, SignalDeck Web's SSTV page): pass recordings, REC files or any 16-bit PCM WAV (any rate, mono/stereo; "Another WAV file…" copies it in through the document picker). `RecordingDecoder` (pure Java) runs its own continuous Robot 36 receiver (64 ms blocks like live) or the live AFSK decoder with the live windows (2 s + 1 s overlap, copies within 4 s merged), as fast as the CPU allows; nothing goes to the radio, the speaker or the live decoders, and packets are never digipeated, gated, acknowledged or given to the TNC. `RecordingDecodeJob`: one job at a time, progress, pictures saved to Download/DMR/SSTV as `SSTV_Robot36_<on-air time>_rec[_partial].png` (time from the file name), packets listed "mm:ss HH:MM:SS TNC2 — description" and written to `<recording>.aprs.txt`, a history line at the end. Web: `/api/decode/recordings`, `/api/decode` (POST start, GET job), `/api/decode/stop`. Help: PKT → Decode a recording; SignalDeck Web → Decode in the browser. | `RecordingDecoder.java` (new), `RecordingDecodeJob.java` (new), `ui/RecordingsDialog.java` (new), `ui/PktDialog.java`, `ui/SstvScreen.java`, `ui/AprsScreen.java`, `web/WebHost.java`, `assets/web/*`, `ui/HelpView.java`, `RecordingDecoderTest.java` (new, 5 tests) | Host tests: the bench Robot 36 recording → 1 complete 320×240 picture; direwolf's 100-packet benchmark → ≥ 24 (direwolf 24); WAV conversion, cancel. **Device** (owner's Armor, with ISS tracking armed): via the web API the SSTV recording (37 s) decoded in 0.98 s → 1 complete picture named for 09:00:37; APRS benchmark (77 s) in 5.4 s → 32 packets; phone sheet from the ISS SSTV page → list, progress, "1 picture (1 complete)"; zone/channel unchanged; test files deleted. |

## 2026-10-22 — ISS passes: fresh orbits before each pass, whole-pass recording; SSTV picture viewer in SignalDeck Web

| Backlog | Change | Files | Test |
|---|---|---|---|
| — | Prep for the SSTV event's high passes. **Orbits:** 15 min before a pass (or at once when closer), elements older than a day are downloaded again and the pass is recomputed from them (AOS/LOS/max elevation; logged with how far it moved); before, they were only refreshed from the Satellite passes sheet (the owner's were 2.5 days old). **Record each pass** (Satellite passes, default on): from tune-in to LOS + 30 s, everything the radio hears, below the squelch too, as `Download/DMR/Audio/<satellite>/yyyyMMdd_HHmmss_pass.wav` (8 kHz mono, ~1 MB/min), so a missed or torn picture/packet can be decoded again. **SignalDeck Web SSTV:** a picture opens in a card on the page instead of a new tab: ‹ › (also ← → and swipe, wrapping), × / Esc / click outside, Download, and **Share** through the device's share menu (Web Share API with the PNG; needs the https address, else it says to use Download). Help: ISS; SignalDeck Web → SSTV pictures. | `RadioService.java`, `ui/SatelliteDialog.java`, `ui/HelpView.java`, `assets/web/app.js`, `assets/web/index.html`, `assets/web/app.css` | Device (owner's Armor): fake pass (DIAG sattest iss-sstv) recorded 1.9 MB / 2 min; orbits refreshed (epoch 21 Oct 22:49 UTC), the 02:28 pass moved 1 s; real tracking resumed; test WAVs deleted. Browser: card opens, 1/2 ↔ 2/2 with wrap, Download .png, Esc closes; headless Chrome cannot share files (fallback message shown) — the share sheet itself needs a phone browser over https. |

## 2026-10-22 — Bench: the radio really tunes in 500 Hz steps (Doppler); DIAG testtone, vfo power

| Backlog | Change | Files | Test |
|---|---|---|---|
| — | Owner's question: does the radio actually follow the ISS Doppler retunes in sub-kHz steps? **Yes.** HackRF measurement of the carrier at low power on 467.625 MHz: requested +0.5 / +1 / +1.5 / +2.5 / +5 kHz landed within 3 Hz (+499.8 / +1000.3 / +1498.9 / +2499.5 / +4999.6 Hz), back to 0 within 1.5 Hz; during the 11:20 ISS pass all 9 frequency writes (437.5570 → 437.5445 MHz in 2 kHz steps, then back to the channel) were acknowledged. DIAG (adb only): new `testtone --ei ms N` (≤ 5 s, current channel or VFO); `vfo` now transmits at **low** power unless `--ei power 1` (it said low but used high). No user-facing change. | `DiagReceiver.java`, `RadioService.java` | Device + HackRF (owner's Armor), see the numbers; radio returned to the owner's channel. |

## 2026-10-22 — SignalDeck Web: Phone panel (battery, cell, Wi-Fi)

| Backlog | Change | Files | Test |
|---|---|---|---|
| — | Owner's request: the phone's own stats in the browser. A full-width **PHONE** panel on the web Intercom page: **battery** (level bar, charging/on battery and plug type, current in mA signed by the charging state with µA/mA detected, %/h by least squares over the last 30 min of one-minute samples taken while the web server runs, time to full / empty, temperature, voltage, charge counter only when plausible, health when not good), **cell** (dBm, bars, carrier, 2G–5G, LTE RSRQ/SNR, no SIM / airplane mode), **Wi-Fi** (dBm, bars, network name, band, link speed). SSE `phone` every 4 s, `/api/phone`; NOT UPDATING after 15 s without data. New normal permission ACCESS_WIFI_STATE (no prompt). Help: SignalDeck Web → Phone panel. | `web/PhoneStats.java` (new), `web/WebHost.java`, `AndroidManifest.xml`, `assets/web/index.html`, `assets/web/pages.js`, `assets/web/app.js`, `assets/web/app.css`, `ui/HelpView.java`, `PhoneStatsTest.java` (new, 3 tests) | Host tests pass. **Device + browser** (owner's Armor): 93 % charging +1586 mA, 31.1 °C, 4.36 V; no SIM shown; Wi-Fi −80 dBm 5 GHz with 1 bar; the phone's charge counter reads 6 (wrong unit), so it is hidden; LIVE updates. |

## 2026-10-22 — SignalDeck 1.0.0-beta.12 (pre-release)

| Backlog | Change | Files | Test |
|---|---|---|---|
| N8 | versionCode 111 / versionName 1.0.0-beta.12. First build from this repository; published as a pre-release here (tag `v1.0.0-beta.12`) and on phonedmrapp (tag `signaldeck-v1.0.0-beta.12`, `--latest=false`) so beta.1–11 updaters find it. Notes: new home, Radio audio check verdict on the Xplore, Release/Beta update paths, real spectrum, ISS tracking after restarts, Mission Statement + About; APK SHA-256 in the notes. | `app/build.gradle` | Release build installed on the owner's Armor (versionName 1.0.0-beta.12, tracking resumed, no crash). |

## 2026-10-22 — Radio audio check: right verdict on serial-port audio phones (Xplore 1)

| Backlog | Change | Files | Test |
|---|---|---|---|
| N8 | Xplore 1 tester's report (Faraday cage, beta.11): audio **works** over the UART PCM path (MON: 168 kB in 10 s at −21 dBFS, receptions at −50 dBm carried audio, squelch now closes after MON), but every run's verdict said "this ROM does not have PrizeTinyService … can't get any receive audio", because the missing-class check ran on every phone. The verdict now depends on the profile's audio backend: a `UartPcmAudio` phone skips the PrizeTinyService verdict (the system section notes the class is not used there), and its "no audio" sentences name the radio's audio serial port instead of the tinyservice daemon. Help: Device → Radio audio check. | `DmrAudioCheck.java`, `ui/HelpView.java`, `DmrAudioCheckTest.java` (+1 test with the report's numbers) | Host tests pass; installed on the owner's Armor (TinyService path unchanged). Not yet re-run on an Xplore. |

## 2026-10-22 — SignalDeck moves to its own repository

| Backlog | Change | Files | Test |
|---|---|---|---|
| — | SignalDeck now lives in `IIMacGyverII/SignalDeck`, with its full history (188 commits from phonedmrapp's `DMRStandalone/`, via `git subtree split`) and only its own work: no decompiled stock app, no firmware. The updater reads this repository's releases, the device-profile list is fetched from here, device reports and issues point here, and About shows this source link. Release signing moved out of the build file into `local.properties`/environment (debug key fallback). COPYRIGHT adapted (interoperability note: the app ships none of the phone maker's software), new README, `profiles/`, `scripts/sign_profiles.py`, the SignalDeck docs, and the synthetic test vectors (direwolf benchmark, SSTV colour-bar reference) are committed so every host test runs from a clean clone. | `README.md`, `COPYRIGHT`, `LICENSE`, `CHANGELOG.md`, `CLAUDE.md`, `.gitignore`, `app/build.gradle`, `settings.gradle`, `Updater.java`, `ProfileRegistry.java`, `repeaters/HttpClient.java`, `DeviceReport.java`, `About.java`, `profiles/`, `scripts/`, `docs/`, `app/src/test/resources/` | Clean clone builds; 169 host tests pass; the APK is signed with the same certificate as the installed builds. |
