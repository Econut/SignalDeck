# P8 installation and first-run setup

**Current main.9c:** module initialization is now performed by SignalDeck, with [fixed power/phone-only controls](p8-phone-only.md) and [initialization evidence](p8-independent-initialization.md). The main.8 validation below remains historical; its stock-assisted cold path has been replaced. Power/audio node reads are constant1 in the factory kernel, not state telemetry.

Setup now also offers Phone-only after reboot. Enable it once for the requested default-off behavior. It acts before unlock where supported, but skips if Intercom ownership is uncertain. P8 MENU → Radio off / Phone only closes the radio without opening stock; Connect powers/enables and initializes it again. See the linked scope before assuming battery-life or other-ROM qualification.

The separate P8 development APK includes its radio adapter, native libraries, setup wizard and Intercom Accessibility helper. No separate helper APK, Magisk/root or PC-side service is required for this path. It retains the existing Android firmware and stock Intercom fallback.

## One-time user steps

1. Open the modified P8 app. Setup checks stock Intercom, microphone permission, notifications and whether the Accessibility helper is actually connected.
2. Use Next step to grant microphone access through Android. RadioService is not started before that grant. Notifications are recommended and can be skipped explicitly; location/storage permissions are not bundled into the initial P8 microphone request.
3. Enable P8 Intercom handover in Android Accessibility settings. Android requires user consent; installing an APK cannot silently grant this permission. On some Android distributions sideloaded apps may have additional restricted-settings confirmation; that path was not encountered/validated here.
4. Choose automatic connection and, optionally, bounded cold-start recovery. The recovery choice defaults OFF on a new installation, is explained in setup, and persists after opting in. The owner explicitly approved it for this test unit.
5. Continue to connection. Only an acknowledged radio tune produces Radio ready; its notice dismisses after five seconds. An old setup-complete flag cannot override missing current permissions or a disconnected helper.

Reopen setup at P8 MENU → Automatic startup. Existing channels and receive audio preferences are retained. The main radio design is unchanged. Permissions normally persist across app updates; do not assume another P8/ROM grants hardware node access simply because this unit does.

## Opted-in cold recovery

With the app's UARTs closed, no local PTT/transmit and no unsafe-close/recovery state, require power/audio1/1 and PTT0. Write the fixed release value1 once through the already reviewed native release routine; verify the three states. An already released PTT causes no recovery write. Unknown/unexpected states or failed verification stop without retry. It never asserts PTT, changes power/calibration or sends a guessed module command.

Launch original Intercom for normal initialization, observe a fresh Settings.Global dmr_init0 then1 (15-second limit), recheck safe GPIO state, then use the fixed-package Settings helper for handover. A stale stored1 alone is insufficient. The stock app remains a dependency; this is automated stock-assisted initialization, not an independent implementation of its complete startup protocol.

## Validation and limits

Main.8a/version147: one normal reboot after clean receive handback. Permission and opted-in preference survived. Opening the app automatically released PTT0→1 at12:08:52, observed fresh stock init0→1 at12:08:57, and completed takeover/tune ACK41 ms at12:09:02. No host GPIO recovery command used in that test; PTT released and stock process absent afterward. Normal unlocking remains a user action; no unattended lockscreen/boot takeover is claimed.

Main.8b/version148: same recovery implementation, setup layout correction. With microphone temporarily revoked after clean handback, setup displayed missing permission and dumpsys showed no RadioService; Intercom remained available and PTT1. Next step opened Android's real permission prompt. Restoring While using the app granted access, then service startup, helper handover and tune ACK41 ms succeeded.35 channels persisted, module setting9 ACK and existing audio preference stores unchanged. This tests the missing-microphone path on the existing device, not a fully fresh second-phone installation.

126 focused host tests passed, including setup order/current-state completion, optional notification skip, recovery state/opt-in/write-once/error refusal and fresh-marker initialization tests. Both native ABIs/release assembly/lintVital passed. Full lint was previously not clean (20 errors elsewhere in existing source); no new full-lint-clean claim is made. No P8 TX or firmware/calibration modification during these checks.

Field repetition, battery exhaustion, permission/helper removal, alternate-language Settings and other P8 firmware variants need further testing. English Settings selector checks fail closed. Keep [Issue3](https://github.com/Econut/SignalDeck/issues/3) in Testing. Private logs/screenshots remain outside the repository.
