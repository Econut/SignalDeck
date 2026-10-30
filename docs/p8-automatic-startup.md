# P8 automatic startup

**Current: main.8b setup and opted-in cold recovery are described in [installation/setup](p8-setup.md).** The main.8 notes below are historical, including the cold-boot blocker that prompted the later release-only path. Stock initialization remains required but can now be orchestrated automatically after opt-in; broader field validation is still pending.


## Scope and setup

User-approved optional root-free workaround for Intercom ownership. The user enables **P8 Intercom handover** once in Android Accessibility settings. P8 MENU → Automatic startup provides disclosure, the settings link and a persistent automatic-startup on/off option. Default is on in the separately branded P8 edition; the helper service is disabled in non-P8 builds. No root, Shizuku, ADB companion or bootloader change is required for this approach.

Accessibility is a powerful Android permission. The restriction described here is implemented by this helper's code/event configuration; it is not an assertion that Android grants a narrowly scoped Force stop permission. Event package filtering is com.android.settings only; while idle no node inspection or click occurs. No screenshots/UI contents are saved or transmitted.

## Flow

On activity/service attachment or a new launcher MAIN intent: retain an existing connection, otherwise connect directly if Intercom is already force-stopped. If Intercom is running and the helper is enabled, open only its fixed App info URI; require visible Intercom label and App info heading before selecting the exact Force stop control. Require the known confirmation message and OK before confirming. No generic package, command or click input is exported.

The state machine allows one Force stop click and one confirmation, with a 12-second deadline. Released PTT and expected power/audio states are rechecked before each action; active native radio sessions block handover. Missing/changed UI, permission loss, unexpected radio state or timeout fails without retry. A successful package stopped flag is followed by the existing receiver ownership/GPIO guards, not an unguarded UART open.

Return to P8 Radio, perform the usual channel tune and load saved receive settings. Only service ready (acknowledged tune) displays Radio ready, automatically dismissed after five seconds. Failure shows a recovery message, never a false Ready. No automatic PTT assertion or experimental module initialization commands are added. Manual Connect also uses the helper when enabled; normal Intercom handback remains available.

This version supports the observed English P8 Settings controls. It intentionally stops rather than guesses on another locale/UI. Settings briefly appears during takeover; this is not an invisible privileged force-stop call.

## Limits and validation

- Main.8/version146 installed as a data-preserving update after clean UART handback. Existing channel and audio preference stores are reused, not rewritten.
- 108 focused host tests passed, including six pure policy tests covering ordered single-use actions, unrelated pages, PTT/state refusal, timeout/no retry, premature confirmation and already-stopped completion. Focused suite passed; device Settings App info structure was inspected read-only.
- Release APK assembled and signature verified. Full lintRelease reports 20 errors in existing source areas (audio/location/media/API/custom views and notices), none in the new helper classes; do not report full lint clean.
- User enabled the helper. Two live automatic handovers succeeded: initial tune ACK in 50 ms and repeat in 42 ms; stock process absent and PTT released afterward. Ready popup captured during the second connection and absent afterward. Clean handback logs reported zero microphone bytes in both new sessions. No new live voice/RF sensitivity test performed. Normal reboot validation is recorded below when complete; do not infer unattended post-reboot behavior solely from warm handovers.
- Cold module initialization remains a separate problem: an uninitialised module may still need stock Intercom initialization. No guessed startup command or power cycle is added. Battery-exhaustion/native-crash/background qualification remains outstanding.

Relevant source: [handover policy](../app/src/main/java/com/macdmr/radio/P8TakeoverPolicy.java), [Accessibility helper](../app/src/main/java/com/macdmr/radio/P8TakeoverAccessibility.java), [foreground coordinator](../app/src/main/java/com/macdmr/radio/P8Startup.java), [policy tests](../app/src/test/java/com/macdmr/radio/P8TakeoverPolicyTest.java). Tracker: [Issue 3](https://github.com/Econut/SignalDeck/issues/3).

## Normal reboot check

Normal reboot performed only after clean handback (PTT1 and zero microphone bytes in the closed receive session). Accessibility permission persisted. After boot, Intercom had no observed PID, power/audio read1/1, and dmrptt read0. This is the previously observed cold-boot output condition, not proof of RF transmission or a physically held key. Automatic takeover correctly remains blocked by its release guard. Do not mark Issue3 complete: warm takeover works, but unattended cold recovery/initialization is not yet implemented. No automatic GPIO correction was made during this check. Recovery/stock initialization remains the next controlled step.

After the owner unlocked the P8 and confirmed released buttons, host recovery rechecked stock process absence and GPIO0/1/1, wrote only the known PTT release value1, verified1, and launched stock normally. Stock init0x27 ACK observed. Reopening P8 Radio then completed automatic handover with tune ACK42 ms and module volume9 ACK; PTT remained1 and stock process exited. All35 channels retained. This was controlled host-assisted cold recovery, not evidence of app-only automatic cold recovery. Device left ready in P8 Radio. No assistant-triggered TX; two clean receive closures logged microphoneBytes0. Screenshots/log receipts remain local to development evidence.
