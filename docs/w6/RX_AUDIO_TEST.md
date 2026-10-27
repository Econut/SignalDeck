# W6 receive-audio test (ENGINE_DESIGN test 1), 2026-10-27

**Result: the W6's receive audio reaches Android's recording API on the `CAMCORDER` source.** A 1 kHz tone sent to
the radio arrives there almost pure, with the speaker amplifier on or off, so it is an electrical path, not the
microphone hearing the speaker. SignalDeck can record, caption and decode on this phone with a plain `AudioRecord`.

## Setup

- Inside the Faraday cage, the W6 next to the HackRF One (owner's permission to send and receive, 2026-10-27).
- Signal: `scripts/w6/fm_tone_iq.py`, FM at 435.025 MHz (the W6's channel 61), 1 kHz tone at ±2.5 kHz plus the
  channel's 62.5 Hz CTCSS at ±0.5 kHz, HackRF tuned 200 kHz below. `hackrf_transfer -x 20 -a 0` (amp off), a 1 s
  dummy burst first. `-x 0` was too weak; `-x 20`, `35` and `47` all opened the squelch.
- The stock app (`com.flyscale.analogpoc`) ran the module: `SETGROUP=1,435.025,435.025,240,240,3,1`, receive volume
  1 of 8. Its squelch opened on the 62.5 Hz tone, which confirms the tone table (code 240 = 62.5 Hz).
- Receive detected on `gpio189` = 1, `gpio183` = 1, `ext_pa_en` = 1, `dmr_state` = 1 (the stock app's `receStart`).
- Measurement: SignalDeck's new bench command `audioprobe` (`AudioSourceProbe`): 2–3 s from each source an app may
  open, 8 kHz mono, the overall level and the 1 kHz level (Goertzel).

## Numbers (dBFS; the first 200 ms of each recording skipped)

| Source | quiet | receiving, amp on | receiving, amp off | second run, receiving |
|---|---|---|---|---|
| DEFAULT | silent (zeros) | silent | silent | silent |
| MIC | silent | silent | silent | silent |
| **CAMCORDER** | silent | **−64.7, tone −66.2** | **−64.6, tone −66.2** | **−64.2, tone −65.2** |
| VOICE_RECOGNITION | −57.2, no tone | −62.7, no tone | −48.1, no tone | −61.0, no tone |
| VOICE_COMMUNICATION | silent | −70.0, no tone | −69.6, no tone | −27.6, no tone |
| UNPROCESSED | −65.1, no tone | −65.7, no tone | −64.6, no tone | −64.2, no tone |
| VOICE_PERFORMANCE | silent | silent | silent | silent |

On `CAMCORDER` the tone is 1.0–1.6 dB under the whole recording: nearly all of the energy is the tone. Audioflinger
reports the input as `AUDIO_DEVICE_IN_BUILTIN_MIC`; the only mixer change while recording is `AD IISMUX` `DIGFM` →
`AUDIIS0` (set by the audio HAL when a capture starts). The module's audio presumably enters the codec on the input the
HAL's camcorder route selects while `dmr_state` is 1.

The level is low because the stock app sets the module's receive volume to 1 of 8. SignalDeck's engine sets the volume
itself (`AT+DMOSETVOLUME`), so the level for decoders is in its hands; the right setting is to be measured when the
engine exists (a write to the module, done with the engine, not in this test).

## What did not work

- Raw ALSA capture (our `sdalsa` helper) on every capture device: `hw_params … Invalid argument` or `read: Invalid
  argument` at every rate, channel count and period tried. On this Unisoc board a capture device delivers data only
  after the audio HAL has set up the VBC route; going around the HAL gets nothing. Recording goes through Android.

## Still open

- Whether the camcorder route carries the module's audio without the stock app's `receStart` (that is, when
  SignalDeck sets `dmr_state`, `gpio183` and the amplifier itself). The engine's receive path repeats `receStart`, so
  this is checked first when the engine runs.
- Transmit audio from Android into the module (test 3, in the cage, the owner keys).
