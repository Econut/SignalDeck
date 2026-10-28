# ANYSECU W6+: what SignalDeck can and cannot do on it

The W6+ has an analog UHF module (SA818 class, PB202EU05) instead of the HR-C7000 DMR module of the other phones:
**400–470 MHz FM only, no DMR, no signal-strength reading, no firmware path.** SignalDeck hides or refuses what this
radio cannot do (profile `anysecu-w6plus`: `protocol sa818`, `bandsHz 400–470 MHz`, `rssi false`, `muteLines`; in-app
help: Device → Radios that do less). Status as of 2026-10-27, from the bench (HackRF in a shielded enclosure) and a
walk through the app and SignalDeck Web on the phone.

Legend: **Works** = measured on the W6. **Should work** = built for every analog radio, not yet exercised on the W6.
**Needs transmit** (before 2026-10-28) = worked only once transmit was verified (until then the app refused
push-to-talk). **No** = not available on this radio, with the reason.

## Radio and receive

| Feature | W6 | Notes |
|---|---|---|
| Take over / hand back the radio, stock channels imported (128) | Works | `AT+DMOCONNECT` handshake, the stock app paused and restored |
| Channels, zones, favourites, multi-select (delete, add to a zone, star) | Works | Phone and web |
| Channel editor, VFO | Works | Refuses frequencies outside 400–470 MHz and says so; VFO starts on your channel, FM only |
| CTCSS / DCS tone squelch | Works (CTCSS) | Every stock channel uses 62.5 Hz; DCS codes are normal polarity only |
| Receive audio to the speaker | Works | The module drives the speaker itself; loudness = Android media volume (stock mapping), volume 0 switches the speaker route off |
| Receive audio into the app (camcorder input) | Works | Fixed gain 12; recording and every decoder use it, also at volume 0 |
| Receive response | Measured | Cut below ~300 Hz (−48 dB at 250 Hz, −57 dB at 125–187 Hz), steeper than the Armor; the module's filter command is refused |
| Signal meter (S-units, dBm) | No | The module answers no RSSI query; the meter says RECEIVING / QUIET from the receive-detect line |
| Band scope | No | Needs signal readings (hidden) |
| Scan | Should work | Steps channels on the squelch; untested on the W6 |
| Soft SQ, MON, REC | Should work | REC/MON use the same audio as the decoders (measured working) |
| Live captions (TXT, Vosk) | Should work | Same audio input; untested |

## Data modes

| Feature | W6 | Notes |
|---|---|---|
| APRS receive (passive decode on your channel) | Works | 6/6 demo packets on 435.025 MHz |
| APRS monitoring on 144.390 | No | 2 m, outside 400–470 MHz (hidden; the service refuses it) |
| APRS send (message, beacon, object, telemetry), auto-ack, digipeater, KISS TNC transmit | Should work (transmit verified, mode untested) | On a UHF channel; automatic beacons go out on 144.390 only, so they do not run here |
| KISS TNC receive, APRS-IS (internet) | Should work | |
| ISS APRS 437.825 | Should work | In band, both ways |
| SSTV receive | Works | Robot 36: 238/240 lines, clean colour bars ("Receive here"; 144.500 is hidden) |
| ISS SSTV 437.550 | Should work | In band |
| SSTV send | Should work (transmit verified, mode untested) | |
| FT8 over FM receive | Works | Both messages of a two-signal slot, in the app and offline |
| FT8 send | Should work (transmit verified, mode untested) | |
| M17 receive | Works | Own calibration (`eq_anysecu-w6plus`): 98 and 92 of 100 frames confirmed over the air; self-calibration refines it from received calls (it cannot create one on this phone) |
| M17 send | No | Needs the HR-C7000 bench firmware's symbol engine |
| POCSAG pages | No | Same reason (hidden) |
| RTTY / PSK31, DTMF decode, Morse decode, SAME header decode | Should work | Same audio path as the modes that work; untested |
| NOAA weather alerts | No | 162 MHz is outside the bands (hidden) |
| Packet terminal, Winlink over radio | Should work (transmit verified, mode untested) | Only with a UHF packet station or gateway |
| Packet finder | Limited | Its 2 m default list is out of band: add UHF frequencies in its Settings |
| Satellite passes | Limited | Only satellites with a 400–470 MHz downlink are listed; cross-band ones (ISS repeater, SO-50: 145.9 MHz up) are tracked listen-only |

## Voice tools and the rest

| Feature | W6 | Notes |
|---|---|---|
| Talk (push-to-talk, side key, web, Android Auto) | Works | Transmit confirmed on the air by the owner (2026-10-28, profile revision 3); PTT line `gpio188` and mic line `gpio222` mapped from the stock app |
| Parrot, voice keyer, Morse ID, DTMF send, emergency (APRS) | Should work (transmit verified, mode untested) | |
| Fox hunt | Limited | Be the fox needs transmit; hunting needs signal readings (not available) |
| Coverage logging | Limited | Key-ups and positions only: no signal readings to map |
| DMR: digital channels, talkgroups, contacts, texts, RadioID names, DMR roaming, DMR ID | No | No DMR in the module (hidden) |
| Module firmware: loader, catalogue, variant submission | No | No firmware file in the stock app, no read command in the module (hidden) |
| Last heard, logbook, POTA/SOTA, nets, message forms (APRS / Winlink), exam practice, schedules | Works / should work | Not radio-specific |
| SignalDeck Web | Works | Same rules: DMR parts, 144.390 monitoring, band scope, weather and the dBm bar hidden |
| Small screen (480 × 854, 320 dp wide) | Works | Compact intercom, crowded headers on two rows, mode-page status card and splash version sized for it |

## Never do on this phone

Never read any attribute of `/sys/devices/virtual/misc/flyscale_misc/` (two of them panicked the kernel), and never
flash or enter the bootloader (no recovery path is known). See `docs/w6/PROGRESS.md`.
