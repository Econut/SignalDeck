<!-- Generated from app/src/main/assets/radios/radios.json by RadioNotesExportTest. Do not edit by hand. -->

# ANYSECU W6+

*Analog UHF · Tested · profile anysecu-w6plus*

An analog-only UHF FM phone (400 to 470 MHz) with its own simpler radio module; tested for receive and transmit.

## The radio

| | |
|---|---|
| Module | SA818-class analog FM module (PB202EU05) |
| Modes | FM |
| Bands | UHF 400–470 MHz |
| Power | Not measured yet |
| Connection | A serial port inside the phone at 9600 baud with AT commands; the receive audio reaches the app through the phone's camcorder microphone input. |
| Stock firmware | Not readable |

The stock app carries no module firmware file and the module has no read command.

## What works

| Feature | State | Note |
|---|---|---|
| Signal meter | No | The module gives no signal readings; the meter shows RECEIVING or QUIET |
| FM receive | Works |  |
| FM transmit | Works | Confirmed on the air |
| DMR receive | No | No DMR in this module |
| DMR transmit | No | No DMR in this module |
| M17 receive | Works | With its own calibration |
| M17 transmit | No | Needs the DMR module's firmware engine |
| APRS | Partly | Receive works; sending not tested yet |
| SSTV | Partly | Receive works; sending not tested yet |
| FT8 over FM | Partly | Receive works; sending not tested yet |
| POCSAG pager | No |  |
| Band scope | No | Needs signal readings |
| Module firmware loader | No |  |
| Android Auto | Not tested |  |
| SignalDeck Web | Works |  |

## Known quirks

### FM only, 400 to 470 MHz

There is no DMR in this radio, so the DMR pages, contacts and messages are hidden. 2 m modes such as APRS monitoring on 144.390 and NOAA weather are out of band.

### No signal meter

The module answers no signal-strength query. The meter shows RECEIVING or QUIET from the radio's receive line, and tools that need readings (band scope, fox hunting, coverage maps) are limited or hidden.

### Low tones are cut hard

The receiver removes almost everything below about 300 Hz, more steeply than the Armor. Voice is fine; M17 receive uses its own calibration for it.

### SignalDeck plays the audio

The module's own speaker route stays off at a fixed volume and SignalDeck plays the radio audio itself, so the phone's volume keys work as usual. There is a slight delay.

### DCS codes

DCS tone squelch works with normal-polarity codes only.

### Small screen

The screen is 480 by 854. SignalDeck uses a compact intercom layout here.

## Measured on our test unit

Measured on one test unit; your radio may differ.

| What | Value | Note |
|---|---|---|
| Receive response at 250 Hz | \-48 dB | \-57 dB at 125 to 187 Hz (HackRF test transmissions). |
| M17 receive | 98 and 92 of 100 frames confirmed | Over the air, with this phone's calibration. |
| SSTV receive | 238 of 240 lines | Robot 36, clean colour bars. |

[All radios](README.md)
