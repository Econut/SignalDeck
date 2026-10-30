# Independent P8 runtime initialization

Main.9c replaces the main.8b stock-launch initialization path. Intercom remains installed as fallback and is temporarily force-stopped when necessary for exclusive UART ownership. This does not disable its receiver, uninstall it, or change its settings.

## Verified source sequence

The owner's privately held stock APK supplies these source anchors (vendor APK/decompilation is not redistributed):

- MainActivity.onCreate opens the control/PCM threads and sets power/audio nodes to1. Its handler starts a one-second initialization timer.
- p2.f.run sends command0x27 with t2.d.V(1,2): one byte01. Successful response triggers channel selection via b0.m and r2.i.g. Stock retries up to11 times; this implementation deliberately sends once and stops on failure.
- w2.b.N sends version query0x34, body01. The test device reports DMR003BN.UV4P.V026.
- x2.c recognizes the fixed17-byte startup banner before framed control replies.

SignalDeck first establishes released PTT and stock ownership, completes the stock power1/audio1 enable sequence with UARTs closed, opens UARTs and allows four seconds for hardware startup, then uses its own UART transport for0x27, validates a complete checksummed success ACK, queries0x34, verifies the exact tested version, then tunes the selected saved channel and applies receive volume. Radio ready still requires tune ACK. Unknown framing/status, TX status, timeout, cancellation or version mismatch stop the attempt. No initialization retry or automatic stock launch is in P8Startup's cold path. Existing normal handback/failure fallback remains available.

The native boundary accepts only the fixed init/version request forms in addition to its existing reviewed commands. No arbitrary module command, reset, firmware update or calibration API is added. Power access is a separate fixed enable/disable operation; see [phone-only mode](p8-phone-only.md). Control is57600baud and PCM230400baud as before. A fresh runtime init ACK replaces reliance on a global dmr_init marker.

The previously approved optional release-only recovery is retained for cold PTT0 with UARTs closed and power/audio1/1. Factory kernel analysis proves the two power/audio show callbacks always print1; those reads are compatibility checks, not state telemetry. Other read values fail closed. See the phone-only notes for the distinct new power-management scope.

## Does stock Intercom start on boot?

Both the factory APK manifest and live package resolver declare enabled BootReceiver for BOOT_COMPLETED (priority-1000), with RECEIVE_BOOT_COMPLETED permission. BootReceiver reads key_auto_start (default true). When true and not initialized, it launches MainActivity; MainActivity starts ForegroundService. It does not directly start a standalone radio service from BootReceiver. When auto-start is false and initialization is absent, it calls t2.d.h, which writes audio4/power0, then exits. Therefore simply switching that setting off is not a neutral hardware change.

At inspection, Intercom was temporarily stopped=true, component setting enabled=0 (default, not disabled), and had no active service. These facts do not reveal its private auto-start preference or prove delivery of a boot broadcast. Previous cold observations found no stock process. The retained event buffer mainly contained foreground/manual startup, not conclusive boot delivery. No vendor background auto-start mechanism beyond the inspected APK has been proved or ruled out. No settings, package disable or synthetic boot broadcast used in this investigation.

## Validation

151 focused host tests passed, including fixed startup packet, ordering, ownership refusal, transport failure/no retry, bad checksum, truncated/empty/negative/duplicate/unexpected replies, non-empty init ACK, TX status rejection, known banner, receive-status forwarding and version mismatch. Native arm64/armv7 and release/lintVital build passed. Existing unrelated full-lint errors remain.

Historical warm test on main.9: connection and tune ACK43ms succeeded after the new mandatory initialization path. Main.9a cold test sent one init packet and timed out without ACK; it closed cleanly and the existing failure fallback launched stock afterward. No retry occurred. Stock logs showed enable writes followed by a boot banner about2.7seconds later, then init ACK. Main.9b added the missing enable sequence/settling and passed warm init/version/tune. Main.9b phone-only initially flagged read-back mismatch; factory kernel show callbacks were then confirmed constant1. Main.9c corrects that assumption. Its final cold/phone-only results are recorded below when tested.

Issue3 remains the tracker. No P8 transmission is part of initialization validation; historical operator TX events in retained logs are not new startup-test events.

## Final live result — main.9c/version152

After the previous off request, own startup at15:08 returned the known power-on banner plus init ACK, verified version and tune ACK42ms. Manual phone-only then closed cleanly (microphoneBytes0) and completed shutdown at15:08:43 without launching stock.

A normal reboot with the saved phone-only option enabled completed boot shutdown at15:10:17 while keyguard was still showing. No RadioService startup or Intercom process was observed before unlock. After normal credential unlock and launcher opening, own enable completed15:11:00; at15:11:04 the banner/init ACK, exact version and tune ACK42ms succeeded. Radio ready followed. Retained events showed no stock process start since this boot; final pid check was empty and PTT remained1. The normal-unlocked boot event caused no duplicate shutdown.35 channels, active17/PMR10, and module receive level9 persisted.

This is one verified locked-boot/off/open sequence on this firmware plus manual off/wake checks, not repeat/battery-exhaustion/current/RF qualification. No new receive-voice or transmit test was performed. App remains installed and ready; Intercom is installed and temporarily stopped, available through normal handback.

Local test APK SHA256:AD981ED62997E920252C63E0543AD368D29FC23C176614728B35F1F48B39FB43. APK and private evidence remain outside GitHub releases/repository.
