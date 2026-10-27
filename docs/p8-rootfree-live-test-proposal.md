# P8 root-free ownership and first open test — REVIEW REQUIRED

Superseding development status: owner has authorised routine root-free, non-transmitting tests. Open-only and stock version communication have now passed under ordinary app UID; see [measured implementation progress](p8-rootfree-progress.md). This document retains the initial narrow proposal and its historical restrictions. It is not the current live-test status.

Current status: the owner subsequently approved the conditional single open-only test; preflight stopped before handover/open because ownership remained unresolved. See [temporary Intercom release review](p8-intercom-force-stop-review.md) for the requested ADB force-stop lifecycle analysis and revised conditional procedure. No force-stop or UART open has been executed. Earlier approval-status wording below records the original proposal state, not a new approval request. Runtime safety gates remain unchanged.

No live open or handover has been authorised or performed. Default build is passive/offline. This proposal supersedes the earlier idea of starting with a version query: **first verify only open/fstat/close, with zero serial bytes and no line-setting ioctl**.

## Verified evidence

Owner offline.2 report: command/PCM UART and power/PTT/audio-control paths all report exists/read/write true; SELinux permissive. Supplied APK hash equals the preserved firmware extraction.

Read-only ADB observation on27October2026: Intercom PID4833, UID1000/shared android.uid.system, package stopped=false. `/dev/ttysWK0` is character device207:5, mode0666, SELinux device:s0; `/dev/ttyS0` is4:64,0666,ttyS_device:s0. Reading `/proc/4833/fd` is denied even to ADB shell. **No claim of exclusive/free UART ownership.** No serial node was opened by that audit.

Static APK inspection: foreground service is exported=false, stopWithTask=false; onTaskRemoved sends an exit broadcast, writes auctusctl4/power0 via t2.d.h(), then exits. Closing the visible activity is not a proven port release. Swiping its task away is not passive. No documented exported handover Binder service was found; do not call private/unexported components or spoof framework broadcasts.

## Root-free handover design

1. Keep the stock package enabled and installed. Do not use pm disable, root, uninstall, PID/UID kills, shared-UID process signals or APK modification.
2. A future reviewed session can use Android's ordinary app-details screen for **the owner to choose Force stop on Intercom only**, if available. This is an explicit takeover action and has NOT been done. Do not use the stock Exit/menu or swipe-away as a substitute: they may change GPIO state. A Force stop also may leave hardware powered; treat that as an observed state, not a new desired power setting.
3. Recheck package stopped flag and exact package process absence with ADB. A missing ActivityManager entry in an ordinary app means UNKNOWN. Do not infer absence from limited process visibility or pidof alone. UID1000 is shared: never kill all processes with that UID.
4. Resolve ownership visibility before opening. Current stock FD inspection is denied. Identify any other plausible radio holder and the limits of the audit. The implemented policy refuses an incomplete holder scan. **If ownership cannot be established, this plan stops here; manual app closure alone is not authority to override the gate.** Manufacturer cooperation may be needed for a documented release/claim interface. The native test is prepared, not automatically reachable merely because the paths are0666.
5. Read the existing PTT state only; require1 (stock release). If asserted/unknown, stop; no corrective GPIO write. Record power/audio-control state without changing it; unknown values remain unknown. Do not power on, unkey, select a channel or send a version query in this stage.
6. After an approved test, close the lab descriptor before any handback. A future owner action may reopen stock Intercom to resume its normal initialisation. That can reprogram its channel and GPIOs, so it is part of the separately reviewed handback, not a current passive action. If stop was required and the test cannot proceed, return control by reopening stock only when the owner chooses. Never leave its package disabled.

Android documentation: third-party apps on Android14+ cannot use killBackgroundProcesses to kill another app. App-details Settings intent can present the stock app's settings, but does not implement a safe automatic takeover. Sources: https://developer.android.com/reference/android/app/ActivityManager#killBackgroundProcesses(java.lang.String) and https://developer.android.com/reference/android/provider/Settings#ACTION_APPLICATION_DETAILS_SETTINGS

## Exact first probe, once reviewed and ownership is resolved

- App identity: ordinary `com.macdmr.radio.p8lab`, not ADB/su/system UID. Running an open from ADB shell would NOT prove app access.
- Node: `/dev/ttysWK0` only; reject anything except expected character device207:5. PCM UART is excluded.
- Native sequence: lstat; one `open(O_RDWR|O_NOCTTY|O_NONBLOCK|O_CLOEXEC|O_NOFOLLOW)`; fstat and compare node identity; immediate close; return result/errno. No retry, arbitrary path, descriptor returned to app code or intentionally held-open session.
- **Zero bytes read or written**; no baud/parity/raw-mode setting, tcflush, TIOCEXCL, TIOCM, ioctl, poll, select, module query, power/PTT/audio GPIO operation, channel change, RF, driver install or firmware change.
- Proposed baud: unchanged, not even set to57600 in this stage. The future actual transport needs a separately reviewed line-configuration test; stock native open changes line settings and is deliberately excluded from this first test.
- Report success only if open, fstat identity and close all pass; otherwise stop on exact errno/unexpected node/close error. A success proves only app-UID open/close, not communication, module power or safe takeover.
- Open can invoke driver startup and close can invoke shutdown; O_NONBLOCK does not guarantee every driver honours a wall-clock bound. Electrical/driver side effects and a hung open remain risks even with no explicit writes. Review driver assumptions and arrange an external observation/abort before execution; do not claim zero RF risk or a guaranteed timed return. Never reboot/unlock/flash as a response to an unexpected result.

## Implemented controls and tests

`P8Ownership` validates explicit review, fresh observations (<=5s), stopped stock state, confirmed process absence, complete holder audit, no holders and released PTT. Running/unknown/stale/partial visibility blocks before invoking a transport backend. Runtime evidence must be reacquired for a real test; host-test fixtures are not transferable approval or device evidence.

`P8OpenProbe` has no read/write/GPIO API. Java build guard `P8_OPEN_PROBE_REVIEWED` defaults false. Native guard independently defaults off; in that build the open branch is compiled out. No app menu invokes the live probe. A different, explicitly reviewed build and resolved ownership evidence are prerequisites; no settings switch or fetched profile can enable the shipped helper. Syntax-only objects for both ARM ABIs validate the gated-on C code on the host; they are not enabled in the APK or executed.

`P8OwnershipAudit` and the offline.3 workbench/report collect package stopped flag, positive visible process evidence and permission metadata. They always state that unobserved process absence/holders are UNKNOWN. They do not run a shell, start/stop Intercom, open a port or send a command. All existing offline/root/RF guards stay in place.

The proposed actual live open and stock-app handover remain pending review. No live test is armed by this document or by installing the passive offline.3 build.
