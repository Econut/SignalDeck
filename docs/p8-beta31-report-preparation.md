# P8 device report preparation for upstream review

Prepared 3 November 2026. This is technical handover preparation for IIMacGyverII’s original SignalDeck project. The P8 is disconnected; beta.31 has not been installed or tested on it. Our stable modified main.9c / version 152 stays the working baseline. The operator reports recent stable everyday use; exact test coverage/duration is not established by that observation.

## Supplied APK checks

- User-supplied SignalDeck 1.0.0-beta.31, versionCode 130; package com.macdmr.radio, minSDK 29, targetSDK 34.
- SHA-256: 860B8A589328E40AE81A544843C475DD4797D7CCAF4382E9AE54F26EEE3A7B9B.
- APK signature verifies. Certificate SHA-256: 9a9a4eb2ec1e0c4a3faafe0039f8a7bae4d72f4039d49fa994bd51bd01a922b9, matching the previously held original beta.19 APK. This establishes signing-key continuity; no live release-page download was available to independently establish the newest published version.
- Our modified app is com.macdmr.radio.p8lab. Different package IDs allow separate app data and side-by-side installation. Actual installed packages/signatures still need checking before an update/install.
- The prepared APK copy, decompilation and evidence remain outside the repository. No original or modified APK is published here.

## Report flow reviewed in the supplied APK

The Device support/report menu has a separate Generate device report action. It calls DeviceReport.generate. It collects build/permissions/sysfs/package information, statically examines APK/native-library clues and copies candidate radio APKs into a local report. Root-only collections are gated on root availability. The reviewed generation path contains no UART command/PTT/power/firmware operation; separate probe, listen, transmit, firmware-loader and Run all checks actions have different scope.

Output directory is Download/DMR/DeviceReport; the generated ZIP is in the app’s cache/share directory. Report files include report.txt, report.json and 00_SUMMARY.txt, with copied candidate APKs/libraries where available. These can include stock Intercom and our modified app, package inventories and diagnostic/log information. Review the actual generated files before sending; do not assume automatic redaction catches everything. No report has yet been generated or shared.

The built-in detector has no P8-specific profile; P8 is expected to remain unsupported/discovery. RadioService.bringUp returns early for unsupported profiles. This is static analysis, not a live guarantee: confirm the detected profile and any saved/downloaded profile on the actual device before using the report flow. JADX’s default reconstruction failed for complex methods; a second simple-mode reconstruction was used to inspect generation. Decompiler output is evidence for review, not a rebuilt app.

## Device session when connected

1. Record the current device/app state and exit our app using Turn radio off and exit. Confirm released PTT and clean closure.
2. Check installed original-package state and signing compatibility; install the inspected APK alongside the P8 app without clearing either app’s data.
3. Launch initially with `adb shell am start -n com.macdmr.radio/com.macdmr.radio.MainActivity --ez noTakeOver true`. The inspected Activity honours this flag to suppress its automatic verified-profile takeover. Check discovery/profile state; dismiss any offered active checks and run only the reviewed Generate device report action. The P8 stays unrooted; no TX or firmware/calibration changes are part of this report session.
4. Pull and preserve a private original, then review and prepare a sanitized sharing copy. Stock/vendor APKs and private logs are not public-repository attachments by default.
5. Close the official app cleanly and restore the stable P8 app. Sending the report/contacting the maintainer is a separate user action.

## Requested upstream contribution structure

The maintainer asked for smaller contributions against current main: (1) module protocol within the shared radio engine plus P8 profile; (2) opt-in root-free access/handover; (3) compact layout as a profile option; (4) general features such as CSV/scan lists separately. Use translation strings for interface text. No upstream PR has been submitted.

The available cached/fork main snapshot remains a5536a6 from 30 October / version beta.20, so it is not evidence that beta.31’s full current source has been obtained. Obtain current source before implementing/reviewing integration against current main. Keep our stable branch available during porting. The fresh report is the immediate next dependency.

## Repository review scope

A local review fetched 132 public fork branch heads into audit refs without switching the working checkout or changing public branches. It examined 4,103 reachable blob versions; 4,017 text versions were checked for selected private-key/token patterns. 86 binary/large blobs were inventoried: images/fonts/audio/data test fixtures and the Gradle wrapper. No .apk, .img, .bin, .zip, signing-key or local-properties artifact candidates were found by the filename checks. The single token-pattern match was an alphabet-sequence dummy in BuildIdentityTest.remoteUrlsLoseCredentials; no confirmed live credential was found.

Original GPL/COPYRIGHT notices remain. Inherited histories include 36 diagnostic text paths and test audio; personal identifiers/consent and all third-party licensing have not been exhaustively classified. The scan is not a guarantee of no secrets, complete privacy/licensing compliance, or any GitHub moderation outcome. No inference is made about the upstream account’s restriction. No files, public branches or Git history were deleted/rewritten; no visibility or release changes were made. Private raw scan metadata stays outside Git.

Track progress in [Issue 20](https://github.com/Econut/SignalDeck/issues/20). Existing startup/power features remain Testing; stability observations do not replace broader acceptance tests.
