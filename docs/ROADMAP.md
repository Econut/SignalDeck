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

- **Winlink gateway list, automatic**: ask Winlink for an API access key so SignalDeck can refresh the gateway list itself (today: the user downloads the CSV in the browser and imports it; winlink.org is behind a Cloudflare bot check, which SignalDeck never works around).

## Research, later

- **M17 and other digital voice**: after deeper work on the radio module's firmware and on other phones. Today the
  module's audio path is voice-filtered (no flat discriminator / raw modulator access).
- **POCSAG / DAPNET**: a deep dive. The 2026-10-20 measurement found 6–24 % bit errors from the audio, but better
  equalisation, soft decoding with BCH correction, or a firmware tap may change that.

## Not possible on this radio

FT8 and SSB modes (FM-only module), full-duplex satellites (one receiver), DCS code display from audio.
