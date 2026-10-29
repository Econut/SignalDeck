# SignalDeck roadmap

Owner-approved work (2026-10-22), in the order it is being built. Each item lands with in-app help, a CHANGELOG.md
entry and a device test.

| # | Feature | What it is | Status |
|---|---|---|---|
| 1 | More FM satellites + live status | The active FM satellites besides ISS / SO-50 / PO-101, AMSAT's live "heard / not heard" reports, same Doppler tracking | **done** 2026-10-22 |
| 2 | Scheduled operations | Start a scan, beacon, monitoring or recording at set times (once, daily, weekly) | **done** 2026-10-22 |
| 3 | VHF/UHF conditions | Tropospheric ducting forecast, aurora (Kp) alerts, meteor shower calendar, upcoming satellite passes | **done** 2026-10-22 |
| 4 | Band scope | Receive-only sweep of a band (signal strength per step), live graph + waterfall over time (about 0.7 s per step: the radio's signal reading needs that long after a retune) | **done** 2026-10-22 |
| 5 | POTA / SOTA | Activator spots (FM, 2 m / 70 cm) with tune-to, activation mode with park/summit reference, dupe check, ADIF | **done** 2026-10-22 |
| 6 | DMR roaming | Switch to the nearest repeater carrying your talkgroup as you move (GPS distance with hysteresis; signal strength is not used because idle DMR repeaters are silent and every reading costs a 0.7 s retune) | **done** 2026-10-22 (not yet tested on a drive) |
| 7 | ICS-213 / radiogram forms | Standard message forms over APRS messages or DMR text (or read on air), with a message log | **done** 2026-10-22 (over-the-air send not yet bench-tested) |
| 8 | Winlink over packet | E-mail through a Winlink packet gateway (AX.25 connected mode, B2F), or the CMS over the internet; bench-tested against a fake CMS (internet) and a HackRF test gateway (radio, full session); not yet tested with the real Winlink system (users asked to report) | done |

## Later (owner notes)

- **Range test with two SignalDeck radios** (owner, 2026-10-27): a base that stays put and a rover that beacons by distance; packets both ways carry the level heard and the base's noise floor; uplink, downlink and misses go into the coverage store with a fitted range band per sector on the map. Plan: phonedmrapp `docs/plan-range-test.md`. After the new phones are compatible and the hotspot session.
- **Restricted mode** (owner, 2026-10-27): a voluntary PIN lock that hides and refuses chosen zones, transmit, modes and admin items, enforced in the engine so every transmit path meets the same rule; presets are starting points, no regulation text; no back door (clearing app data removes it). Plan: phonedmrapp `docs/plan-restricted-mode.md`. After the POCSAG/web work and the range test.
- **ATAK (CivTAK) integration** (owner, 2026-10-27): tier 1 a local Cursor-on-Target bridge (positions, stations heard, alerts and texts into ATAK; ATAK positions, markers, chat and 911 back; KMZ exports), tier 2 the radio as a TAK transport between SignalDeck phones (compact CoT over AFSK, DMR texts or M17 packets), tier 3 an optional ATAK plugin. Plan: phonedmrapp `docs/plan-atak-integration.md`. After the current work, Restricted mode, the range test and the new phones.

- **Winlink gateway list, automatic**: ask Winlink for an API access key so SignalDeck can refresh the gateway list itself (today: the user downloads the CSV in the browser and imports it; winlink.org is behind a Cloudflare bot check, which SignalDeck never works around).

- **Translations** (owner, 2026-10-28; a tester asked for German first): move every user-facing string (screens, dialogs, the in-app guide `HelpView.TOPICS`, the web pages) into string resources with an English base, then German; follow the phone's language with an override in Device. The guide is large, so plan the export so translators work from one file per language.
- **DMR position data shown as text** (tester report 2026-10-28): a DMR radio's position report (its "APRS over DMR" / GPS data, from ID 22 on TG 1, 7 times) arrived as a `0x2D` text and was shown as CJK garbage (binary bytes read as UTF-16LE). Wanted: recognise data that is not text (do not file it as a message; show "data from …" or decode it), then decode the common DMR position formats (ETSI LIP, Motorola LRRP, Hytera, the NMEA sentences some radios send) onto the map and Last heard, and offer forwarding to APRS-IS. Needs the raw bytes: a debug dump of non-text `0x2D` bodies first.
- **Tester requests, channels and zones** (2026-10-28):
  - Nearby repeaters: when a DMR repeater has no known talkgroup list, let the user enter one or more talkgroups in the details sheet (today it only says to add them later in the channel editor).
  - Clone a channel: duplicate it into the editor, to change one or two fields.
  - The channel pop-up (tap a channel): an "Add to zone" button with a zone picker.
  - The channel editor: a zone picker for the zones the channel belongs to.

## Research, later

- **M17 and other digital voice**: after deeper work on the radio module's firmware and on other phones. Today the
  module's audio path is voice-filtered (no flat discriminator / raw modulator access).
- **POCSAG / DAPNET**: a deep dive. The 2026-10-20 measurement found 6–24 % bit errors from the audio, but better
  equalisation, soft decoding with BCH correction, or a firmware tap may change that.

## Not possible on this radio

FT8 and SSB modes (FM-only module), full-duplex satellites (one receiver), DCS code display from audio.
