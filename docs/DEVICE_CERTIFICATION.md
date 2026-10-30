# Getting your phone certified for SignalDeck

**Version 1, 2026-10-29. This is a living document:** it changes as we learn from new phones. The history is at the
bottom, and every change is in this file's git history. Questions and suggestions are welcome in GitHub Discussions
or an issue.

SignalDeck talks to the radio module inside a phone directly, through a small **device profile** for each phone model.
This page explains how a phone goes from "unknown" to "supported": the levels, the tests at each level, what to send
us, and who decides. It complements [Request support for your DMR phone](DEVICE_SUPPORT_REQUEST.md), which covers the
first step (the device report).

## 1. Safety and the law first

- **Receiving is always fine. Transmitting is your responsibility.** Only transmit where your licence allows it: a
  valid amateur licence for amateur frequencies, or a licence-free service the radio is approved for. Use your own
  callsign, at the lowest power that works, and identify as your rules require.
- **Transmit tests go into a dummy load, or at low power on a quiet simplex frequency you are allowed to use.**
  Keep every test key short (10 s or less unless a step says otherwise) and never leave a keyed radio unattended.
- **Watch the heat.** SignalDeck stops long transmissions when the phone gets hot. If the phone feels hot, stop.
- **Don't experiment with system files.** Only follow the steps here. Some phones crash or reboot if certain hidden
  system files are read; the device report knows which ones to avoid. Never load radio-module firmware unless a step
  tells you to, and only with the image SignalDeck offers for your exact module.
- **Privacy:** reports never need your callsign, DMR ID, phone number or location. Remove them if they appear in a
  screenshot or log before you share it. Use a test callsign such as N0CALL in anything you post.

## 2. The levels

Each phone model has one of these levels. It's shown in the app (Device → About this radio) and on the website.

| Level | Meaning | Transmit in the app |
|---|---|---|
| **Unknown** | No profile yet. SignalDeck won't take the radio; it offers the device report. | Off |
| **Experimental** | A draft profile from a device report and the stock app. Nothing confirmed on the air yet. | Off |
| **In progress** | Receive works on at least one tester's phone. Transmit not yet confirmed. | Off |
| **Beta** | Receive and transmit confirmed on at least one phone, measured by a trusted tester. Some features untested. | On |
| **Supported** | All core tests passed on at least two phones (or one phone with the maintainer's bench measurements), the radio notes are complete, and the help describes every known limit. | On |
| **Discontinued** | No longer supported (hardware unavailable, a blocking problem). | As last known |

Moving up a level always needs the evidence in section 3 and the maintainer's review (section 5). A level can also go
down when a new problem is found.

## 3. The tests

Run them in order. Each one says what to record. **(TX)** marks a step that transmits: do it only within section 1.

### 3.1 Identify the phone (for Experimental)
1. Install SignalDeck, allow root, and run **Device → Device report → Run all checks** (receive only). Send the report.
2. Note: phone model and Android version, the radio module's version (the report reads it), the frequency range the
   stock app allows, and whether the phone has DMR, FM only, or both.

### 3.2 Receive (for In progress)
With a second radio (any handheld) or an SDR as the signal source:
1. **FM receive:** a voice transmission on a simplex channel comes through clearly. Check with tone squelch (CTCSS)
   off first; then with a CTCSS tone that matches, and one that doesn't (the second must stay silent).
2. **Signal reading:** the S-meter moves with the signal (if the phone has one; some don't, and that's a known limit).
3. **Squelch:** with no signal the speaker is quiet; Soft SQ opens on voice and stays closed on noise.
4. **Channel changes and scanning:** change channels and zones; scan stops on an active channel.
5. **DMR receive** (DMR phones): a group call on your talkgroup and time slot shows the caller ID and plays the voice.
6. **Band limits:** try a frequency just inside and just outside the bands the stock app allows. SignalDeck must refuse
   the outside one. Note the result.
7. **Leave it running** for 30 minutes and receive a few times: no drop-outs, no app restarts. Note any.

Record: a short screen recording or screenshots (with private data removed), the app's log (Device → Diagnostics →
Share), and a one-line result per step.

### 3.3 Transmit (for Beta) (TX)
This step needs a way to check your own signal: a second radio, plus ideally an SDR (an RTL-SDR is enough for most
checks) or a service monitor. Transmit is off in the app below Beta; the maintainer sends a test build for this step.
1. **FM voice:** key for 5 s on a simplex channel; the second radio hears you clearly and at a normal level.
2. **Release:** the transmission ends when you release the key, every time. The app's 28 s limit ends a held key.
3. **Frequency:** with an SDR, note how far your carrier is from the channel frequency (a few hundred Hz is normal;
   several kHz is worth reporting).
4. **DMR** (DMR phones): a group call on your talkgroup is received by another DMR radio, with your caller ID.
5. **Heat:** note the battery temperature before and after five short keys (Device report shows it).

### 3.4 Data and extras (for Supported)
Each that the phone can do; mark the others "not supported" with the reason.
1. **APRS:** receive a packet; send a position beacon (TX) that another station or an SDR decodes.
2. **SSTV, FT8, RTTY:** receive; send (TX) one each if the phone can send app audio (some phones only transmit from the
   microphone, which is a known limit, not a failure).
3. **Messages** (DMR): send and receive a text.
4. **SignalDeck Web, Android Auto, the widget:** each opens and works.
5. **Restart and reboot:** SignalDeck takes the radio again after the app restarts and after a phone reboot (with "Own
   the radio at boot" on); handing back to the stock app works.

## 4. What to send

- The **device report** (3.1) and, for each level, the results above: a table with each step, pass/fail, and a note.
- Logs from Device → Diagnostics → Share for anything that failed.
- Measurements with the tool you used (for example "RTL-SDR, carrier +0.6 kHz").
- Where: a GitHub issue using the device template, or attached to your Discussions thread. One thread per phone model,
  so other owners of the same phone can add their results.

## 5. Who decides, and what happens then

- The **maintainer** reviews the evidence, may ask for more, and sets the level in the phone's profile and its radio
  notes ("About this radio" in the app, and the device page on the website). Measurements from your phone are listed as
  "measured on a test unit", without your name unless you ask to be credited.
- **Transmit is enabled in the app only from Beta**, after the maintainer is satisfied the radio keys, releases and
  identifies correctly. If a problem appears later (for example a transmission that doesn't end), the level drops and
  transmit is turned off again for everyone until it's fixed.
- New levels reach users with the next SignalDeck release (or, for transmit and safety changes, sooner through the
  signed profile list).

## 6. Make your own phone compatible

If you write code: **fork** SignalDeck on GitHub, add a profile for your phone in your fork (see the existing profiles in
`DeviceProfile.java` and the device report's findings), and then open a pull request **from your fork** with your test
results from section 3. You don't need to be a collaborator for this: a pull request from a fork is GitHub's normal way
for anyone to offer changes, and nothing is merged until the maintainer has reviewed it. You can also just share a link
to your fork in your Discussions thread. Keep transmit off
(`verified = false`) in the PR; the maintainer turns it on after the Beta checks. Never add code that loads module
firmware for a phone it wasn't made for.

## History

- **v1 (2026-10-29):** first version: levels, tests per level, evidence, review.
