# SignalDeck roadmap

Owner-approved work (2026-10-22), in the order it is being built. Each item lands with in-app help, a CHANGELOG.md
entry and a device test.

| # | Feature | What it is | Status |
|---|---|---|---|
| 1 | More FM satellites + live status | The active FM satellites besides ISS / SO-50 / PO-101, AMSAT's live "heard / not heard" reports, same Doppler tracking | **done** 2026-10-22 |
| 2 | Scheduled operations | Start a scan, beacon, monitoring or recording at set times (once, daily, weekly) | **done** 2026-10-22 |
| 3 | VHF/UHF conditions | Tropospheric ducting forecast, aurora (Kp) alerts, meteor shower calendar, upcoming satellite passes | **done** 2026-10-22 |
| 4 | Band scope | Receive-only sweep of a band (signal strength per step), live graph + waterfall over time (about 0.7 s per step: the radio's signal reading needs that long after a retune) | **done** 2026-10-22 |
| 5 | POTA / SOTA | Activator spots (FM, 2 m / 70 cm) with tune-to, activation mode with park/summit reference, dupe check, ADIF | planned |
| 6 | DMR roaming | Switch to the best repeater carrying your talkgroup as you move (GPS + signal) | planned |
| 7 | ICS-213 / radiogram forms | Standard message forms over APRS messages, packet or DMR text, with a message log | planned |
| 8 | Winlink over packet | E-mail through a Winlink packet gateway (AX.25 connected mode, B2F); bench-tested against a HackRF test gateway | planned |

## Research, later

- **M17 and other digital voice**: after deeper work on the radio module's firmware and on other phones. Today the
  module's audio path is voice-filtered (no flat discriminator / raw modulator access).
- **POCSAG / DAPNET**: a deep dive. The 2026-10-20 measurement found 6–24 % bit errors from the audio, but better
  equalisation, soft decoding with BCH correction, or a firmware tap may change that.

## Not possible on this radio

FT8 and SSB modes (FM-only module), full-duplex satellites (one receiver), DCS code display from audio.
