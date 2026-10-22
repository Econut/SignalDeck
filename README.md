# SignalDeck

## Mission Statement

> *This project is a showcase of what an Android ham radio can do. It is built for innovation, and built on the innovation, discovery and hard work of those who came before me. I hope it inspires others to create, and to look for the next step.*
>
> — IIMacGyverII

---

**SignalDeck** is an Android app for phones with a built-in walkie-talkie radio module (VHF/UHF, analog FM and DMR). It drives the module directly, without the phone's stock walkie-talkie app, and turns the phone into a full ham radio station.

> [!WARNING]
> **Public beta, for testing — bugs are expected.** SignalDeck needs a **rooted phone (Magisk)** and takes direct control of its radio module. It is provided as is, without warranty (GPL v3); the author is not responsible for any damage to your phone, its radio or your data. Transmitting legally (licence, frequencies, power) is your responsibility.

**Download:** the newest build is on the [Releases](https://github.com/IIMacGyverII/SignalDeck/releases) page (`SignalDeck-<version>.apk`). Once installed, **Device → Updates** finds later builds, with a Release path and a Beta path and the changes since your version.

## What it does

- **Talk and listen:** analog FM and DMR (group and private calls, RX group lists, text messages with quick replies), the side PTT key (or a key you teach it), channels with favourites, notes, nets and a full editor, zones, Nearby Repeaters and repeaters on the map, scanner, VFO, Soft SQ, REC, MON, live captions (on-device Vosk, or Google Cloud) with keyword alerts, voice announcements, Last heard with RadioID names, a real audio spectrum on the intercom.
- **Packet (PKT):** APRS (passive decode, monitoring with auto-ack, messages, beacons, digipeater, objects, telemetry, weather), APRS-IS iGate, KISS TNC for APRSdroid/YAAC, satellite passes with Doppler tracking (ISS APRS and SSTV), SSTV receive and send, NOAA SAME alerts, DTMF, RTTY/PSK31, packet terminal, parrot repeater, Morse, voice keyer, ID timer.
- **Pages and tools:** logbook (ADIF), nets near you and net control, fox hunt, exam practice, map and coverage maps, channel sharing (Android share sheet, CHIRP CSV), Android Auto, home widget, **SignalDeck Web** (the radio in a browser on your Wi-Fi), backups in the five-CSV layout of the DMRModHooks module and the OpenGD77 CPS fork, boot persistence, a Radio audio check and device reports for support, in-app updates and in-app help for every screen.

## Phones

| Phone | Status |
|---|---|
| **Ulefone Armor 26 Ultra** (walkie-talkie model) | Tested — the reference phone. |
| **Blackview Xplore 1** | **Untested profile** ([analysis](docs/multi-device-xplore1-findings.md)): same radio-module protocol, audio over a second serial port. Owners: please run **Device → Run all checks** and send the zip. |
| Other DMR phones (Oukitel WP61, Ulefone Armor 20WT, UNIWA P2 Plus…) | No profile yet — the app starts in discovery mode and offers **Run all checks**. See [docs/DEVICE_SUPPORT_REQUEST.md](docs/DEVICE_SUPPORT_REQUEST.md). |

**Root (Magisk) is required:** Android's SELinux policy only lets the stock app open the radio's serial port. SignalDeck applies the rules it needs with Magisk (Device → *Keep access after reboot* makes them permanent as a small Magisk module). The stock walkie-talkie app is turned off while SignalDeck owns the radio, and turned back on when you hand the radio back.

**Bugs and new phones:** open an [issue](https://github.com/IIMacGyverII/SignalDeck/issues) with your phone model, the SignalDeck version, what happened, and the zip from *Run all checks*.

## Building

```
./gradlew assembleRelease          # needs JDK 17 and the Android SDK
./gradlew testReleaseUnitTest      # host tests (DSP, decoders, parsers)
```

Release signing is read from `local.properties` (never committed) or the environment:
`signaldeck.storeFile`, `signaldeck.storePassword`, `signaldeck.keyAlias`, `signaldeck.keyPassword`
(or `SIGNALDECK_STORE_FILE` …). Without them the build is signed with the Android debug key, which installs fine but cannot update an official build.

The device profile list in [`profiles/`](profiles/README.md) is signed separately; the app only accepts a list signed with the project's key.

## History

SignalDeck began on 2026-10-17 inside [phonedmrapp](https://github.com/IIMacGyverII/phonedmrapp), the research project and **DMRModHooks** LSPosed module for the Ulefone Armor 26 Ultra, and moved here with its full history. Beta builds 1.0.0-beta.1 to beta.11 were published there.

## License

Copyright (C) 2026 IIMacGyverII. SignalDeck is free software under the **GNU General Public License v3.0 or later** ([LICENSE](LICENSE)), with **additional terms under section 7** set out in [COPYRIGHT](COPYRIGHT): keep the copyright notice and the author credit, mark modified versions as modified and don't present them as the original or as the author's work, and no rights are granted to the names "SignalDeck" / "IIMacGyverII" or the SignalDeck icon (a modified version needs its own name and icon).

[COPYRIGHT](COPYRIGHT) also lists the third-party components and data sources SignalDeck uses. Official builds are signed with the project's release key and published only on this repository's Releases page.
