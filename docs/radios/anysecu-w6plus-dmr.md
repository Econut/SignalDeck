<!-- Generated from app/src/main/assets/radios/radios.json by RadioNotesExportTest. Do not edit by hand. -->

# ANYSECU W6Plus DMR

*DMR (V69) · Being brought up · profile anysecu-w6plus-dmr*

The W6+ body with a UHF DMR module; receive works, transmit is held back until the profile is confirmed.

## The radio

| | |
|---|---|
| Module | DMR006.U4A.V140 (the Atom XL's module family, on a plain serial port) |
| Modes | FM, DMR Tier II |
| Bands | UHF 400–480 MHz |
| Power | Not measured yet |
| Connection | A serial port inside the phone at 57600 baud with the Armor's command set; the receive audio reaches the app through the phone's camcorder microphone input. |
| Stock firmware | DMR006.U4A.V140 |

SignalDeck does not load module firmware on this phone.

## What works

| Feature | State | Note |
|---|---|---|
| Signal meter | Works |  |
| FM receive | Works |  |
| FM transmit | Not tested | Blocked in the app until the profile is confirmed; one bench test passed |
| DMR receive | Partly | Calls and caller ID detected; voice audio level is very low |
| DMR transmit | Not tested | Blocked in the app until the profile is confirmed; one bench test decoded |
| M17 receive | Not tested |  |
| M17 transmit | No | No module firmware loading on this phone |
| APRS | Not tested | Sending is not possible: the microphone is the only transmit audio |
| SSTV | Not tested | Sending is not possible: the microphone is the only transmit audio |
| FT8 over FM | Not tested | Sending is not possible: the microphone is the only transmit audio |
| POCSAG pager | No |  |
| Module firmware loader | No | The vendor's module image stays |
| Android Auto | Not tested |  |
| SignalDeck Web | Not tested |  |

## Known quirks

### UHF only

Despite "dual band" in some listings, the radio answers only 400 to 480 MHz; VHF frequencies are never acknowledged.

### Transmit is held back

The profile is not confirmed yet, so SignalDeck does not take over the radio by itself and does not transmit on it. One owner-authorized bench test keyed FM and DMR correctly.

### The microphone is the only transmit audio

The module takes its transmit audio straight from the phone's microphone. The app cannot send tones, Morse, APRS packets, SSTV pictures or FT8 through it.

### Transmit frequency runs high

Our test unit transmitted about 1.6 kHz above the set frequency on FM and DMR alike, measured against a test receiver whose own error is part of that figure. It is inside what most DMR radios accept, but a narrow or strict receiver, or a DMR hotspot, may need its receive offset adjusted or may show a high bit error rate for this radio.

### DMR voice audio is quiet

DMR calls are detected with the caller's ID, but the decoded voice reaches the app far below FM audio. This is under investigation.

### Its transmitter desensitises nearby radios

While it keys, a radio close by on another UHF frequency hears less (its signal reading drops), though it does not open.

## Measured on our test unit

Measured on one test unit; your radio may differ.

| What | Value | Note |
|---|---|---|
| Carrier offset | about +1.6 kHz | \+1598, +1575 and +1604 Hz on three keys (2026-10-29). Includes the test receiver's own error: the same receiver reads an Armor 26 Ultra +0.8 to 1.0 kHz high, so this radio alone is probably 0.6 to 0.8 kHz (about 1.5 ppm) high. Measured on one test unit; your radio may differ. |
| DMR outer deviation | 1815 Hz | Nominal is 1944 Hz (2026-10-29, one test unit). |
| DMR voice audio | about 50 dB below FM | At the camcorder input (2026-10-29, one test unit). |
| FM receive audio | \-32 dBFS | A 1 kHz tone at 2.5 kHz deviation, 12 dB louder than the W6+ (2026-10-29). |
| Heat during the bench test | battery 42.1 to 42.8 °C, processor 51 to 53 °C | Including a 28 s key (2026-10-29). |

[All radios](README.md)
