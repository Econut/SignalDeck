# W6 engine design: SignalDeck on an SA818-class analog module (2026-10-27)

**Status: approved by the owner 2026-10-27. Built: the `ModuleEngine` interface (SignalDeck #38) and `AtModuleEngine` with the W6 profile, receive only (#40). Transmit waits for the owner.** Sources: the stock app's start-up sniffed by the
device report (`sdtrace`, `reports/20261027_serial_sniff/`), the stock app `com.flyscale.analogpoc` disassembled with
the Android SDK's `dexdump` and `aapt2` (classes `SerialHandle`, `SerialManage`, `BoService`, `DmrReceiver`,
`CustomKeyEventImpl`, `ChannelValueUtil`; resource arrays `analog_*`), its channel table, and the probe's answers.
No transmission was made; nothing was written to the module except identity queries.

## 1. Verdict

The W6's radio is a UHF analog FM module (version `PB202EU05`) on `/dev/ttyS2` at 9600 8N1, driven with SA818-style
text commands, plus a set of GPIO lines that switch power, PTT, the microphone and the audio routes. **A second engine
is needed** (`AtModuleEngine`), not a profile: none of the `68 … 10` frames apply. Tuning, tones, squelch, power,
bandwidth, volume, mic gain and VOX map cleanly. **The open problem is audio**: on this phone the received audio goes
from the module to the speaker amplifier through hardware switches, and the microphone goes to the module the same
way. Whether SignalDeck can hear or feed audio at all decides which features beyond a plain walkie-talkie are possible
(section 6). That needs a receive test with a signal, then a transmit test in the cage.

## 2. The module and its line

| Fact | Value | Source |
|---|---|---|
| Port | `/dev/ttyS2`, label `console_device`, mode 0666 | `SerialManage.open`, report |
| Line | 9600 baud, 8N1, raw, no flow control | `open(path, 9600, 8, 1, 0)`, sniffed `TCSETS` |
| Version | `+DMOVERQ:PB202EU05` | sniff, probe |
| Frequency range | 400–470 MHz (listing); the module's own limits are not measured | listing |
| Command end | a bare `\r` (no `\n`); answers end `\r\n`, often with a leading `\r\n` | sniff |
| Pacing | one command per write, from a queue drained by one sender (~100 ms apart at start-up) | `SerialManage$1`, sniff |
| Answers | `+DMO<NAME>:0` = OK, `:1` = refused; unknown input is echoed back wrapped in `:1` | `BoService.readData`, probe |

## 3. Command set (all the stock app uses)

| Command | Parameters | Stock use | Answer |
|---|---|---|---|
| `AT+DMOCONNECT` | none | every 1 s from start until answered | `+DMOCONNECT:0` |
| `AT+DMOVERQ` | none | once, after the connect answer | `+DMOVERQ:<version>` |
| `AT+DMOSETGROUP=gbw,tfv,rfv,txcss,rxcss,sq,power` | gbw 0 = 12.5 kHz, 1 = 25 kHz; tfv/rfv `xxx.xxx` MHz; tone code (below); sq 0–8 (0 = monitor, always open); power 0 = low, 1 = high | after the version, and on every channel change | `+DMOSETGROUP:0` / `:1` (out of range) |
| `AT+DMOSETVOLUME=n` | 0–8 (receive audio level) | after the version; on volume key changes (via `android.flyscale.music.volume`) | `+DMOSETVOLUME:0/1` |
| `AT+DMOSETMIC=n` | 1–8 (mic sensitivity) | settings | `+DMOSETMIC:0/1` |
| `AT+DMOSETVOX=n` | 0 = off, 1–8 | settings | `+DMOSETVOX:0/1` |
| `AT+DMOAUTOPOWCONTR=n` | 0 = power saving off, 1–16 | settings ("power mode") | `+DMOAUTOPOWCONTR:0/1` |

Note the stock SETGROUP order is **tx tone before rx tone, then squelch, then power**: it differs from the public
SA818 manual's `GBW,TFV,RFV,Tx_CTCSS,SQ,Rx_CTCSS`. SignalDeck must use the order this module was sniffed with.

**Tone codes** (`analog_settings_ctcss_array_value`, `analog_settings_cdcss_array_value`, `ChannelValueUtil`):
`0` = no tone (carrier); `1–38` = the 38 standard CTCSS tones 67.0–250.3 Hz; `240–252` = 13 extra tones
(240 = 62.5, 241 = 69.3, 242 = 159.8, 243 = 165.5, 244 = 171.3, 245 = 177.3, 246 = 183.5, 247 = 189.9, 248 = 196.6,
249 = 199.5, 250 = 206.5, 251 = 229.1, 252 = 254.1 Hz); `39–121` = 83 DCS codes `023N … 754N` (normal polarity only in the list). **Every one of the 128 factory
channels uses code 240 (62.5 Hz) on transmit and receive**, so a W6 talks only to radios that send 62.5 Hz until the
owner changes it. SignalDeck's channel editor must show and write the tone explicitly.

Not used by the stock app and not tried: an RSSI query (`RSSI?` on some SA818 firmware), a scan command, a filter
command (`AT+SETFILTER`), a tail-tone command. Each would be one identity-style query to test; RSSI matters for the
S-meter, band scope and coverage maps (section 6).

## 4. Hardware lines

All are `/sys/class/gpio/gpioN/value` or `/sys/kernel/*` nodes, written by the stock app (uid 1000) as plain files. The
names are the stock app's own field names. **The `flyscale_misc` attributes of the same names are a different driver
and must never be read** (two kernel panics: `af_en`, `dmr_pwd`).

| Stock name | Node | Direction | Meaning |
|---|---|---|---|
| `dmr_pwd` | `gpio322` | out | module power: 1 on (`openModule`), 0 off (`exit`, only from the app's own exit) |
| `dmr_ptt` | `gpio188` | out | transmit: 1 keys, 0 unkeys |
| `dmr_mic_en` | `gpio222` | out | microphone to the module (1 while transmitting, 0 a little after) |
| `m6_mic_en` | `gpio179` | out | headset microphone instead of the built-in one |
| `dmr_spk_en` | `gpio189` | **in** | the module is receiving (squelch open); read by `isReceiving()` |
| `spk_dmr_en` | `gpio183` | out | module audio to the speaker path |
| `m6_dmr_en` | `gpio249` | out | module audio to the headset path |
| `poc_spk_en` | `gpio223` | out | PoC (cellular) audio to the speaker; not used by the radio path |
| `audio_pa_en` | `/sys/kernel/ext_pa_en/ext_pa_en` | out | speaker amplifier |
| `m6_pa_en` | `/sys/kernel/m6_pa_en/m6_pa_en` | out | headset amplifier |
| `dmr_state` | `/sys/kernel/dmr_state/dmr_state` | out | 1 while receiving or transmitting (the audio HAL likely reads it) |
| — | `gpio181`, `gpio212` | out | named by the app, idle 1, no use found in the radio path |

**Receive start/end are system broadcasts**, `android.flyscale.rece.dmr.start` / `android.flyscale.rece.dmr.end`,
sent by the vendor framework (a `FLYSCALE_SERVICE` is registered in the system), most likely on `gpio189` edges.

**Sequences** (from the code):
- Start: open the port; `gpio322 ← 1`; every 1 s `AT+DMOCONNECT\r` until `:0`; `AT+DMOVERQ\r`; then
  `AT+DMOSETGROUP=…\r`, `AT+DMOSETVOLUME=n\r`.
- Receive start (`receStart`): `dmr_state ← 1`; if the app's volume is not zero: speaker `ext_pa_en ← 0`,
  `gpio183 ← 1`, `ext_pa_en ← 1` (headset: `m6_pa_en`, `gpio249`).
- Receive end (`receEnd`): `dmr_state ← 0`, amplifier off, the route off after a short timer.
- Transmit start (`startSpeaker`): a key-press sound; `gpio222 ← 1`; `gpio179 ← headset ? 1 : 0`; `gpio188 ← 1`.
- Transmit end (`stopSpeaker`): `gpio188 ← 0`; a timer then `gpio222 ← 0` (and `gpio179 ← 0` with a headset).
- Stop (app exit only): `gpio322 ← 0`. A force-stop leaves the module powered.

**Keys:** the side PTT arrives as the broadcasts `fly.privkey.poc.down` / `fly.privkey.poc.up` / `fly.privkey.poc.long`
(input device `hook_dmr`, `KEY_F2`/`KEY_F3` underneath); the channel knob as `com.flyscale.analogpoc.CHANNELDOWN.down`
and `com.flyscale.analogpoc.CHANNELUP.up`.

## 5. The engine in SignalDeck

`RadioEngine` today is one concrete class for the `68 … 10` MCU; `RadioService` calls it in 81 places (most often
`ptt`, `close`, `setAnalog`, `isOpen`, `setMonitor`, `setSquelch`, `queryVersion`, `open`, `powerCycle`).

1. **Extract an interface** `ModuleEngine` with what an analog engine can honour: `open/close/isOpen`, power on/off/
   cycle and `waitBooted`, `queryVersion`, `setAnalog(rx, tx, band, power, sq, rxTone, txTone)`, `setVolume`,
   `setMic`, `setSquelch`, `setMonitor`, `ptt`, `queryRssi` (no-op where unsupported) and the `Events` callbacks.
   DMR-only calls (`setDigital`, `sendSms`, caller info, the `0x40` firmware/bench commands) stay on `RadioEngine` and
   are reached through a capability check, so the DMR phones' code paths do not change. First PR: the interface and
   `RadioEngine implements ModuleEngine`, no behaviour change, the Armor-family host tests as the guard.
2. **`AtModuleEngine`**: the port through the same `UartPort` (the W6 port opens as a plain file at 9600, the line set
   with `stty`/the JNI library as today); a single command queue with a 100 ms gap, `\r` endings, an answer matcher per
   command name (`+DMO<NAME>:`), a 1 s timeout and one retry; the start sequence above; `status()` events from the
   receive broadcasts (fallback: polling `gpio189` every 100 ms) mapped to the existing receive start/stop codes.
3. **Profile fields** (new, all optional so existing profiles are untouched): `protocol` (`mcu68` | `sa818`),
   `baud`, and a `lines` map (`power`, `ptt`, `micEn`, `micHeadset`, `rxDetect`, `spkRoute`, `hsRoute`, `spkPa`,
   `hsPa`, `state`), plus `pttActions` with the `fly.privkey.poc.*` pair. The W6 profile is `verified = false` until the
   owner confirms receive and transmit.
4. **Channel model:** the W6 is analog-only, 400–470 MHz, 12.5/25 kHz, tones as above. SignalDeck's analog channel
   already has rx/tx frequency, bandwidth, power, squelch and tone type/code; the engine translates its tone fields to
   this module's codes (including the 13 extra tones, which SignalDeck's tone list needs to gain for this profile).
   Squelch: SignalDeck's 0–9 maps to 0–8.
5. **Taking over:** as on the other phones, the stock app is disabled while SignalDeck owns the radio and re-enabled on
   hand-back. The stock app's `BootReceiver` and its system uid matter: disable it with `pm disable` (root), never
   uninstall. SignalDeck takes `/dev/ttyS2` only after the stock app is stopped.

## 6. Features: what maps, what is hidden, what depends on audio

| Group | On the W6 |
|---|---|
| Channels, VFO, zones, scan (by retuning), favourites, backup/restore of analog channels | **maps** (analog only) |
| CTCSS/DCS per channel, squelch, monitor, power, bandwidth, volume, mic gain, VOX | **maps** |
| Side PTT key, channel knob | **maps** (broadcasts above) |
| Receive indicator, last heard time stamps | **maps** (receive broadcasts / `gpio189`) |
| S-meter, band scope, coverage maps, Soft SQ by level | **needs an RSSI query** (untested; hidden until one answers) |
| DMR (talkgroups, contacts, texts, caller IDs, colour codes), M17, firmware loader, `0x40` engines | **hidden** (no DMR, different module) |
| Recording, live captions, VU bars, all receive decoders (APRS, SSTV, FT8, DTMF, SAME, POCSAG, M17, Morse, Winlink) | **maps: test 1 passed (2026-10-27)**: the receive audio reaches `AudioRecord` on the `CAMCORDER` source (`RX_AUDIO_TEST.md`); the engine's audio backend records it at 8 kHz mono |
| Everything that sends generated audio (APRS/packet, SSTV, FT8, DTMF, Morse, voice announcements on air, the parrot) | **depends on transmit audio from Android reaching the module** (test 3) |
| Android Auto / car Talk, the web remote | maps for control; audio parts as above |

The engine design does not depend on the audio answer; the feature set does. If neither direction exists, the W6 gets
a clean analog walkie-talkie with SignalDeck's channel management, scanning, keys and help, and the decoders say
"not available on this phone".

## 7. Tests needed (in order)

| # | Test | Who | Why |
|---|---|---|---|
| 1 | **Done 2026-10-27, passed (`RX_AUDIO_TEST.md`): `CAMCORDER` carries the module's audio.** Receive audio path: a carrier with a 1 kHz tone on a W6 channel (HackRF in the cage, or a station); while the receive broadcast is on, record every ALSA capture device and Android's `MIC`, `VOICE_RECOGNITION`, `UNPROCESSED` sources, and dump the mixer before/after. Receive only. | owner provides the signal; I run the capture | decides recording, captions and every decoder |
| 2 | **Done 2026-10-27: no answer** (`RSSI?` six times, each with a retry, with and without a signal), so no signal readings on the W6. **RSSI query:** `RSSI?\r` (and `AT+DMORSSI?\r`) once each, with and without a signal. Identity-style reads. | me (needs the owner's OK: it is a command the stock app never sends) | decides S-meter, scope, coverage |
| 3 | **Transmit path in the cage:** the stock app keyed twice with the device report's sniffer and a GPIO/mixer snapshot running, a receiver confirming RF; then, with SignalDeck playing a tone to each Android playback route while the owner keys, whether the tone is on air. | owner keys | confirms the PTT sequence; decides every generated-audio feature |
| 4 | **The silent module:** once (2026-10-27) the module stopped answering until a reboot, powered and with the port free. Watch for it with the bench driver in a loop; when it happens, record `gpio322` and try `gpio322 ← 0`, 1 s, `← 1`, then `AT+DMOCONNECT`. | me, after the owner OKs writing the power line | the engine needs a recovery step; the report must never leave a radio dead |
| 5 | **Power saving:** the module's `AUTOPOWCONTR` value is stored in the module and not readable. Ask the owner what the stock settings show before anything is changed; power saving may explain test 4. | owner reads the stock settings page | |
| 6 | **Range and time-out:** `SETGROUP` with 399.995 / 470.005 MHz must be refused (`:1`) and the module must stay on its frequency, as the DMR module does; the stock app has no transmit timer, so SignalDeck's applies. | me (receive-only frequencies), owner for the time-out | band limits |

## 8. Order of work after approval

1. PR: `ModuleEngine` interface, `RadioEngine` implements it, no behaviour change.
2. PR: `AtModuleEngine` + `DeviceProfile` protocol/lines + the W6 profile (`verified = false`), receive and control only
   (PTT refused on an unverified profile, as on the other untested phones), host tests with the sniffed exchanges as
   fixtures, help topic for analog-only phones, Restricted mode unchanged (no new mode; transmit still goes through
   `txInhibited`).
3. Tests 1 and 2, then the audio backend that test 1 allows, or the "not available" wording if none.
4. Test 3 in the cage with the owner; transmit enabled only after the owner confirms on air.
