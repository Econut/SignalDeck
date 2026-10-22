# SignalDeck — agent notes

SignalDeck (package `com.macdmr.radio`) drives the radio module of DMR phones directly (root/Magisk). This repository
holds only the app. The hardware facts, measured limits and dead ends (MCU protocol, tuning range, 30 s TX cap,
squelch, band=1 on VHF, audio format, channel-write rules, Xplore/other phones) are in the research repository's
`CLAUDE.md`: https://github.com/IIMacGyverII/phonedmrapp (locally `D:\Documents\code\personal\phonedmrapp`). Read it
before touching radio behaviour; it wins over guesses.

## Every change
- Build + host tests: `JAVA_HOME=<JDK 17> ./gradlew assembleRelease testReleaseUnitTest`.
- Install the **release** build on the owner's phone: `adb install -r app/build/outputs/apk/release/app-release.apk`.
  Never `adb uninstall` (loses the Magisk grant and the app data).
- Update the in-app help in the same change (`ui/HelpView.java` TOPICS, `ChannelEditorView` `H_*` texts).
- Log it in `CHANGELOG.md` (newest first, dated section, `| Backlog | Change | Files | Test |` row, say whether it was
  device-tested).
- Never commit signing keys or their passwords (`local.properties` holds them), the profile-signing key
  (`~/.signaldeck/`), callsigns, APRS-IS passcodes, the web PIN or the owner's location.

## Releases (only when the owner asks)
- Asset name `SignalDeck-<version>.apk` (the updater reads the version from it). Betas `1.0.0-beta.N`: `gh release create
  <tag> <apk> --prerelease`; releases without `-` in the version. Notes start with `## What's new …` and list the APK's
  SHA-256. Builds up to beta.11 updated from phonedmrapp's releases, so publish the first build from this repository
  there as well, until those users have moved.
- Device profiles: edit `profiles/profiles.json`, bump `serial`, sign with `scripts/sign_profiles.py`; builds up to
  beta.11 read phonedmrapp's copy, so update both.
