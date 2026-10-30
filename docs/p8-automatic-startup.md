# P8 automatic startup — main.8 candidate

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
- Six pure policy tests cover ordered single-use actions, unrelated pages, PTT/state refusal, timeout/no retry, premature confirmation and already-stopped completion. Focused suite passed; device Settings App info structure was inspected read-only.
- Release APK assembled and signature verified. Full lintRelease reports 20 errors in existing source areas (audio/location/media/API/custom views and notices), none in the new helper classes; do not report full lint clean.
- User permission activation and live stop/connect/notice validation are pending at this entry. Do not claim unattended post-reboot readiness yet.
- Cold module initialization remains a separate problem: an uninitialised module may still need stock Intercom initialization. No guessed startup command or power cycle is added. Battery-exhaustion/native-crash/background qualification remains outstanding.

Relevant source: [handover policy](../app/src/main/java/com/macdmr/radio/P8TakeoverPolicy.java), [Accessibility helper](../app/src/main/java/com/macdmr/radio/P8TakeoverAccessibility.java), [foreground coordinator](../app/src/main/java/com/macdmr/radio/P8Startup.java), [policy tests](../app/src/test/java/com/macdmr/radio/P8TakeoverPolicyTest.java). Tracker: [Issue 3](https://github.com/Econut/SignalDeck/issues/3).
