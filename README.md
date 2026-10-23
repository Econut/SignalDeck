# SignalDeck

## Mission Statement

> *This project is a showcase of what an Android ham radio can do. It is built for innovation, and built on the innovation, discovery and hard work of those who came before me. I hope it inspires others to create, and to look for the next step.*
>
> — IIMacGyverII

---

**SignalDeck** is an Android app for phones with a built-in walkie-talkie radio module (VHF/UHF, analog FM and DMR). It drives the module directly, without the phone's stock walkie-talkie app, and turns the phone into a full ham radio station: voice, packet, digital modes, satellites, e-mail by radio, and the whole radio in a web browser.

> [!WARNING]
> **Public beta, for testing — bugs are expected.** SignalDeck needs a **rooted phone (Magisk)** and takes direct control of its radio module. It is provided as is, without warranty (GPL v3); the author is not responsible for any damage to your phone, its radio or your data. Transmitting legally (licence, frequencies, power) is your responsibility.

**Download:** the newest build is on the [Releases](https://github.com/IIMacGyverII/SignalDeck/releases) page (`SignalDeck-<version>.apk`). Once installed, **Device → Updates** finds later builds, with a Release path and a Beta path and the changes since your version.

**Help is built in:** every screen and feature is explained in the app (**Device → Help & how-to**, and the ⓘ buttons next to fields and settings).

## Getting started

1. Root the phone with Magisk and install the APK.
2. Open SignalDeck and grant root when Magisk asks. SignalDeck turns the stock walkie-talkie app off while it owns the radio, and imports the stock app's channels on the first start.
3. **Device → Keep access after reboot** installs a small Magisk module that makes the radio access permanent, so SignalDeck takes the radio back after every reboot.
4. More channels: open a CHIRP CSV with SignalDeck or use the share button on the Channels page, or restore a backup (the five-CSV layout of the DMRModHooks module and the OpenGD77 CPS fork, in `Download/DMR/DMR_Backups`).
5. To hand the radio back, use **Device → Exit SignalDeck**: it switches the module off, or gives the radio back to the stock app.

## What it does

### Talk and listen
- **Analog FM and DMR:** group and private calls, RX group lists, CTCSS and DCS, receive-only channels, high and low power, color codes and timeslots.
- **The intercom page:** channel and frequency display, live S-meter, a real audio spectrum, the history of receptions and captions, and the talk key with the SOFT SQ / TXT / VFO / REC / MON / PKT keys around it. Two looks (new and classic). The side PTT key works, or any key you teach it.
- **Channels:** full editor, favourites, notes, zones, a scanner with a scan list, VFO for any frequency, Nearby Repeaters and repeaters on the map.
- **Soft SQ** (a software squelch for weak signals), **REC** (recordings per channel), **MON** (squelch held open).
- **Live captions** of received speech (Vosk on the phone, or Google Cloud), with keyword alerts; **voice announcements** of channel and caller.
- **DMR extras:** text messages with quick replies and talkgroup threads, Last heard with RadioID names and callsigns, and **DMR roaming** (switches to the nearer repeater of your network as you travel).
- **Emergency** (sends your position) and an optional roger beep.

### Digital modes and packet (the PKT key)
- **APRS:** passive decode on every analog channel, monitoring with auto-ack, messages and message groups, beacons, objects and bulletins, telemetry and weather, a fill-in digipeater, text and e-mail gateways, and APRS stations on the map.
- **APRS-IS:** receive-only or two-way iGate, internet beacon and messages.
- **KISS TNC** over TCP for APRSdroid, YAAC and other packet apps.
- **Winlink e-mail** through a radio gateway or over the internet, with the gateway list from winlink.org (nearest first), scheduled mail checks and forms by Winlink.
- **Packet terminal** (connected mode) and the **Packet finder**, which listens across packet frequencies and lists the BBSs, nodes and Winlink gateways it hears.
- **FT8 over FM:** the WSJT-X mode on an FM channel, with a waterfall and automatic contacts logged in the Logbook. It talks to other FM FT8 stations; normal FT8 on SSB cannot be received with this radio.
- **M17 digital voice, receive only:** plays M17 stations and shows the caller and text (calibrated for the Ulefone Armor 26 Ultra so far).
- **SSTV:** receive (Robot, Martin, Scottie and PD, plus a continuous Robot 36 decoder that picks up pictures halfway) and send; a gallery with share, delete and **Combine** (several receptions of the same picture into a cleaner one).
- **Decode a recording:** finds SSTV pictures and APRS packets in any audio file.
- **RTTY and PSK31**, **DTMF** decode and send (with remote control by DTMF), **Morse** decode and send with a trainer, a **voice keyer**, a **simplex repeater (parrot)** and an **ID timer**.
- **Weather:** NOAA weather radio with SAME alert decoding.

### Satellites and propagation
- **Satellite passes** with Doppler tracking for the ISS (APRS and SSTV) and the FM satellites of the AMSAT list, with live AMSAT status. During a pass SignalDeck tunes and decodes by itself, pass after pass if you like.
- **VHF/UHF conditions:** tropo ducting forecast, aurora, meteor showers and the next passes on one page.
- **Band scope:** sweeps a band, or your own range, and shows which frequencies are busy.

### Operating
- **Logbook** with ADIF export, **POTA and SOTA** (spots, your activation, duplicate check), **nets near you** and **net control** (check-ins, traffic, a report).
- **Message forms:** ICS-213 and ARRL radiograms over APRS, DMR text or Winlink.
- **Scheduled operations:** at a set time, once or every week, listen to (and record) a channel, scan a zone, monitor APRS, receive SSTV, send a beacon, run the packet finder or check your Winlink mail.
- **Fox hunt**, **exam practice**, and the **map** with **coverage maps** (where your repeaters really work, measured as you move).

### Everywhere
- **SignalDeck Web:** the whole radio in a browser on your Wi-Fi, protected by a PIN (https on port 8443 for sharing and the microphone): intercom, channels, messages, APRS, SSTV, packet and Winlink, FT8, M17, satellites, band scope, map, logbook, recordings, and the phone's battery and signal. Transmitting from the web stays off until you allow it.
- **Android Auto** (the radio on the car's screen, including talk), a home-screen **widget**, and a notification with controls.
- **Backups**, channel sharing (Android share sheet, CHIRP CSV) and boot persistence.
- **Support tools:** Radio audio check, second radio test, device reports, and a watchdog that restarts a radio module that stops taking commands.

## What the hardware cannot do

These are limits of the radio module, measured on the reference phone. SignalDeck works within them:

- **Frequencies:** the module only tunes 136–174 MHz and 400–480 MHz (no 220 MHz, no out-of-band receive).
- **FM only on analog:** no SSB or CW receive, so no normal FT8 or weak-signal satellite work.
- **30-second transmissions:** the module's firmware ends every transmission after 30 s, so data modes are built to fit (SSTV sends Robot 24 Color, Robot 12 BW and Robot 8 BW, for example).
- **No M17 or POCSAG transmit** that other radios could decode: the transmit audio path filters and limits the signal. M17 receive works with an equalizer calibrated per phone.
- **DCS codes cannot be read from the audio** (the module handles DCS squelch itself); CTCSS tones can be identified.
- **DMR:** the module admits group calls only for the 32 talkgroups in a channel's RX list.

## Phones

| Phone | Status |
|---|---|
| **Ulefone Armor 26 Ultra** (walkie-talkie model) | Tested — the reference phone. |
| **Blackview Xplore 1** | **Profile in testing** ([analysis](docs/multi-device-xplore1-findings.md)): same radio-module protocol, audio over a second serial port. A tester has receive, channel import and CTCSS working; transmit stays blocked until the profile is verified. Owners: please run **Device → Run all checks** and send the zip. |
| Other DMR phones (Oukitel WP61, Ulefone Armor 20WT, UNIWA P2 Plus…) | No profile yet — the app starts in discovery mode and offers **Run all checks**. See [docs/DEVICE_SUPPORT_REQUEST.md](docs/DEVICE_SUPPORT_REQUEST.md). |

**Root (Magisk) is required:** Android's SELinux policy only lets the stock app open the radio's serial port. SignalDeck applies the rules it needs with Magisk (Device → *Keep access after reboot* makes them permanent as a small Magisk module). The stock walkie-talkie app is turned off while SignalDeck owns the radio, and turned back on when you hand the radio back.

**Bugs and new phones:** open an [issue](https://github.com/IIMacGyverII/SignalDeck/issues) with your phone model, the SignalDeck version, what happened, and the zip from *Run all checks* (or **Device → Device report**).

## Building

```
./gradlew assembleRelease          # needs JDK 17, the Android SDK and the NDK (Codec 2 is built from source)
./gradlew testReleaseUnitTest      # host tests (DSP, decoders, parsers)
```

Release signing is read from `local.properties` (never committed) or the environment:
`signaldeck.storeFile`, `signaldeck.storePassword`, `signaldeck.keyAlias`, `signaldeck.keyPassword`
(or `SIGNALDECK_STORE_FILE` …). Without them the build is signed with the Android debug key, which installs fine but cannot update an official build.

The device profile list in [`profiles/`](profiles/README.md) is signed separately; the app only accepts a list signed with the project's key.

## History

SignalDeck began on 2026-10-17 inside [phonedmrapp](https://github.com/IIMacGyverII/phonedmrapp), the research project and **DMRModHooks** LSPosed module for the Ulefone Armor 26 Ultra, and moved here with its full history. Beta builds 1.0.0-beta.1 to beta.11 were published there; the changes of every build since are in [CHANGELOG.md](CHANGELOG.md).

## License

Copyright (C) 2026 IIMacGyverII. SignalDeck is free software under the **GNU General Public License v3.0 or later** ([LICENSE](LICENSE)), with **additional terms under section 7** set out in [COPYRIGHT](COPYRIGHT): keep the copyright notice and the author credit, mark modified versions as modified and don't present them as the original or as the author's work, and no rights are granted to the names "SignalDeck" / "IIMacGyverII" or the SignalDeck icon (a modified version needs its own name and icon).

[COPYRIGHT](COPYRIGHT) also lists the third-party components and data sources SignalDeck uses. Official builds are signed with the project's release key and published only on this repository's Releases page.
