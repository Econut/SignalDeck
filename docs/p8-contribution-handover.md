# UNIWA P8 contribution handover

This is a development contribution for review, not an official SignalDeck release or a claim of complete P8 support.

## Branch and test baseline

- Branch: `uniwa-p8-support`.
- P8 development started from upstream `a22aaed` (1.0.0-beta.20).
- Latest device-tested edition: `1.0.0-beta.20-p8-main.8b`, version code 148.
- Tested device: UNIWA P8, MT6765, Android 14, stock build `P8_EU_U4_A14_NEU_EN_V1.0.1_20250814`, radio module `DMR003BN.UV4P.V026`.
- Root-free access relies on this device's UART/sysfs permissions and permissive SELinux. It is not guaranteed on other P8 variants or firmware.
- Upstream has advanced beyond this baseline. No automatic upstream merge or integration validation is claimed.

## Included work

Root-free control/PCM adapter; analogue channel tuning, receive audio and manually tested analogue PTT; physical/foreground programmable keys; main radio layout and connection status; module/media/software receive levels; measured RSSI and received-audio FFT (not an RF spectrum sweep); persistent zones and independent scan lists; standard versioned CSV import/export/replacement; independent channel-browser view; on-device recovery and bounded persistent logs.

The 0.6/0.4-second FM scan options are experimental. They passed isolated scanner timing/hold tests but still need field detection testing. DMR packet construction exists; P8 DMR reception/transmission is not validated end to end. Aviation AM is unsupported by the currently evidenced interfaces.

## Important remaining issues

1. Cold startup still uses stock Intercom initialization. The bundled opt-in Accessibility helper automates App info / Force stop, and separately opted-in bounded cold recovery passed one reboot test. See [setup evidence](p8-setup.md); field reliability is not yet established. Do not disable the stock app.
2. Post-reboot software PTT state and connection-fault recovery need further reliability work. Existing recovery only clears verified resolved faults and never flashes or resets calibration.
3. Phone-only module power management, independent boot initialization and comprehensive background/lock-screen reliability remain unfinished.
4. Existing generic root/boot access settings are not proof that those flows work on the P8 adapter.
5. User-reported crashes after battery exhaustion have not been conclusively diagnosed; persistent recovery logging was added afterwards.

## Building and testing

Use the repository's normal Android/JDK 17/SDK 34/NDK build environment. This modified edition uses a distinct app ID and label:

```text
./gradlew -Pp8Offline=true -Pp8OpenProbeReviewed=true -Pp8TxTestReviewed=true assembleRelease
```

Those explicit properties enable device-specific hardware boundaries. They are not proof that an arbitrary device is compatible and are not permission to transmit. Preserve the upstream licence/author notices and modified-app branding.

For focused host tests, leave the live gates at their default disabled values:

```text
./gradlew -Pp8Offline=true :app:testReleaseUnitTest --tests 'com.macdmr.radio.P8*' --tests 'com.macdmr.radio.ChannelListScopeTest'
```

The isolated `P8UiInstrumentation` tests use scratch databases, fake scanner hosts and detached PTT views. They do not establish live RF performance. Detailed milestone evidence and limitations are in the documents below; not every historical test was rerun for every subsequent commit.

## Review entry points

- `docs/uniwa-p8.md`: protocol and hardware evidence.
- `docs/p8-rootfree-progress.md`: root-free access milestones.
- `docs/p8-main-radio.md`: main radio integration and early TX evidence.
- `docs/p8-channels-and-scan-lists.md`: channel management, browser scope and scan timing.
- `docs/p8-csv-format-v1.md`: shared CSV contract and templates.
- `docs/p8-field-recovery.md`: field recovery, status UI and logging.
- `docs/p8-aviation-am-assessment.md`: bounded AM capability investigation.

## Sharing boundaries

This source branch contains no device firmware backups, calibration dumps, stock APKs, development signing credentials or personal channel exports. Device backups and installable development APKs remain outside this repository. Generic template/example CSV files are included. No pull request or maintainer contact is implied merely by publishing this fork branch.
