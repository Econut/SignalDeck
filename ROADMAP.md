# UNIWA P8 Development Roadmap

[Open the GitHub Project board](https://github.com/users/Econut/projects/1) · [All feature issues](https://github.com/Econut/SignalDeck/issues?q=is%3Aissue)

Experimental P8 compatibility work for technical handover to **[IIMacGyverII, the original SignalDeck developer](https://github.com/IIMacGyverII/SignalDeck)**. This is not an independent product release or a promise of upstream acceptance.

The **Project Status is the main tracker**. This page is its readable summary, last reconciled 30 October 2026. Each linked issue records progress, source/evidence, uncertainties and acceptance criteria. Historical milestone documents are evidence, not separate current work lists.

## Current priorities

1. Reliable cold startup and predictable Intercom handover (#3).
2. Diagnose low-battery/restart incidents and qualify background operation (#11).
3. Finish analogue/scanning/button field checks (#1, #6, #7).
4. Develop the agreed Windows CPS workflow (#13), using the existing channel schema.
5. Stage DMR interoperability tests with the owner's AnyTone D878UV (#9).

## Status summary

Completed means the issue's bounded scope was implemented and verified on the test setup; it does not imply full device/RF qualification. Testing means code exists but the stated validation remains outstanding. No feature is marked In Progress merely because it was discussed.

## Ideas / Future Features

- [#8 — Short-press and long-press programmable button actions](https://github.com/Econut/SignalDeck/issues/8)
- [#14 — Internal FM broadcast radio application feasibility](https://github.com/Econut/SignalDeck/issues/14)

## Planned

- [#9 — DMR003BN digital interoperability with AnyTone D878UV](https://github.com/Econut/SignalDeck/issues/9)
- [#11 — Low-battery crash, background and lock-screen reliability](https://github.com/Econut/SignalDeck/issues/11)
- [#12 — Phone-only mode: safely power down the radio module](https://github.com/Econut/SignalDeck/issues/12)
- [#13 — Windows CPS: Read radio, edit channel book, Write radio](https://github.com/Econut/SignalDeck/issues/13)
- [#19 — Small-screen secondary-menu layout polish](https://github.com/Econut/SignalDeck/issues/19)
- [#20 — Maintainer handover: reproducible build and sanitized evidence](https://github.com/Econut/SignalDeck/issues/20)

## In Progress

- [#3 — Independent cold startup and reliable Intercom handover](https://github.com/Econut/SignalDeck/issues/3): main.8 optional Settings-only Accessibility handover and five-second ACK-ready notice implemented; two warm handovers verified (50/42 ms tune ACK), ready notice shown/dismissed. Normal reboot retained helper but booted with PTT output0/stock absent, correctly blocking connection. Cold recovery/initialization remains unresolved.

## Testing

- [#1 — Analogue FM radio adapter: qualify RX/TX reliability](https://github.com/Econut/SignalDeck/issues/1)
- [#6 — FM scanning: field validation of faster dwell settings](https://github.com/Econut/SignalDeck/issues/6)
- [#7 — Programmable physical buttons: validate both P8 keys](https://github.com/Econut/SignalDeck/issues/7)

## Completed

- [#2 — Receive volume controls and clipping indication](https://github.com/Econut/SignalDeck/issues/2)
- [#4 — Channels, named zones and cross-zone scan-list management](https://github.com/Econut/SignalDeck/issues/4)
- [#5 — CSV import/export and atomic channel-book replacement](https://github.com/Econut/SignalDeck/issues/5)
- [#10 — Field connection recovery and persistent diagnostic logs](https://github.com/Econut/SignalDeck/issues/10)
- [#17 — RSSI gap smoothing and received-audio spectrum](https://github.com/Econut/SignalDeck/issues/17)
- [#18 — Independent channel-browser view selector](https://github.com/Econut/SignalDeck/issues/18)

## Blocked

- [#15 — Aviation AM reception: unsupported evidenced interface](https://github.com/Econut/SignalDeck/issues/15)
- [#16 — Bootloader confirmation without physical Volume Up](https://github.com/Econut/SignalDeck/issues/16)

## Evidence and review

- [Technical contribution handover](docs/p8-contribution-handover.md): baseline, build commands and limitations.
- [Hardware/protocol findings](docs/uniwa-p8.md), [takeover/lifecycle review](docs/p8-intercom-force-stop-review.md), [recovery tests](docs/p8-field-recovery.md).
- [Channel/scan test milestones](docs/p8-channels-and-scan-lists.md), [CSV contract and fixtures](docs/p8-csv-format-v1.md).
- Focused host suite re-run 30 October: **102 tests, zero failures/errors/skips** against main.7c source, using the existing dependency cache. Not a clean-machine or new live RF test.
- Early [main-radio notes](docs/p8-main-radio.md) describe initial bounded TX failures; later peer-received voice was confirmed by the operator. Private host logs/receipts are not public fixtures. Distinguish operator observations, isolated tests and independently reproducible evidence.
- DMR voice, fastest-scan weak/short-signal capture, calibrated RF measurements and comprehensive background reliability remain unverified.

## Keeping one tracker

When work starts, move its existing issue to In Progress. When implementation is ready, attach commit links and the actual test result and move it to Testing. Move to Completed/close only after the stated acceptance checks pass. A blocker needs its dependency and next action recorded. Reopen when a regression invalidates completion.

Update the issue and Project first, then reconcile this summary in the same development task. Notion may retain a handover/journal and link here, but must not become a competing status list. This is a maintenance convention, not a scheduled automation or an automatic claim that future work has run.

Do not upload proprietary firmware/APKs, private backups, identifiers, keys or unreviewed logs as evidence. No release, upstream pull request or maintainer contact is part of this tracker setup.
