# P8 temporary Intercom release — lifecycle review

Latest: broader owner authorisation now covers routine root-free non-transmitting development; successful UART/version results are recorded in [implementation progress](p8-rootfree-progress.md). Restrictions below about obtaining another individual open approval record the earlier session, not the current authorisation. No firmware/calibration/security or RF-TX authorisation has been given.

Status: analysis and proposed procedure only. No force-stop, relaunch or UART test executed. The owner requested this review before execution. The prior single-open approval is conditional on resolved ownership and safe state; it is not permission to bypass those conditions.

Update: the owner subsequently approved **stop/observe/restore only**, which completed successfully as recorded below. The historical review text describes the pre-test assessment. UART access is explicitly excluded pending further approval.

## Approved stop/observe/restore result

- Exactly one `am force-stop --user 0 com.wtwd.ucee.dmr`; successful, no retry. Baseline PTT/power/auctusctl1/1/1; stock PID4833 with only Intercom in ActivityManager packageList.
- Three post-stop observations over1.954s (within the proposed5s limit): stock pidof empty, old /proc/4833 absent, no matching running AM process, package stopped=true and service `(nothing)`. No automatic restart observed during that window. PTT/power/auctusctl remained1/1/1 throughout sampled readings; no claim of continuous GPIO/RF monitoring.
- Factory-test PID5421 remained present throughout. No other package was stopped or disabled.
- Exactly one explicit stock MainActivity launch succeeded. Restored PID12738, foreground service and main window present, package stopped=false/enabled default, PTT/power/auctusctl1/1/1. Owner confirmed: "yes looks normal" (no TX indication/unusual behaviour in response to the visual-check question). Actual receive audio/RF operation was not tested.
- No UART open, serial read/write, line-setting operation, direct GPIO write, lab takeover, root or firmware modification. Stock startup may execute its own normal GPIO/channel initialisation as disclosed in the approved handback; this was not instrumented as a zero-traffic operation.

Private receipts outside repository: `p8-development/intercom-stop-observe-results.json` (pre-restore stage) and `intercom-restore-verification.json` (final stage). The former's restore_performed=false records its stage, not the final outcome. The single-use stop script refuses to run again if its receipt exists. Do not rerun it as a routine diagnostic.

### Factory-test interference analysis

Read-only copy of `/system_ext/app/HsFactoryTest/HsFactoryTest.apk`:1,950,498 bytes, SHA256 `2140c773af2b4c5679f73666c10e51dd0ffe7d99e93a09d75cabacbe323bd8dd`. Kept outside source repository with its decompilation. No factory-test activity was launched.

Readable `com.hebs.factorytest.fragments.DmrTestFragment.onViewCreated` only reads `Settings.System` key `dmr_init` and updates pass/fail UI/menu. KnobFragment receives `dmr.knob.TIMER`. Search of app sources found no command/PCM UART path, SerialPort, native declaration/loadLibrary or device-node open; APK contains no `.so` libraries. One JADX error is in support-library GridLayoutManager, not the inspected DMR fragment. These findings reduce suspicion of a direct radio opener; they do not establish a complete runtime FD audit or exclude framework/reflection/dynamic/native services. Factory-test FD listing remains denied. Mere continued factory-test presence is not evidence of UART interference.

Outcome: temporary stock-process release and normal stock handback demonstrated once without observed abnormality. Full UART exclusivity, app-UID open permissions and driver-open effects remain untested. No change to ownership gate or enabled build. Any UART open now requires further approval under the owner's latest restriction.

## Findings

`am force-stop --user 0 com.wtwd.ucee.dmr` is a root-free ADB-shell package stop, not a command an ordinary application can invoke with shell privileges. It does not uninstall, disable, clear app data or alter the APK. It does set the package's stopped state, which can remain across reboot until an explicit launch; therefore handback is required rather than assuming reboot restores it. Never substitute kill-by-UID: this package shares UID1000.

When all Intercom threads actually terminate, the kernel closes that process's descriptors. Reading its FD list beforehand is not necessary to establish that its own descriptors cannot survive its completed termination. This corrects an overly broad interpretation of the earlier FD-visibility blocker. Descriptors duplicated into another process, an independent opener or delayed kernel/driver release remain separate questions. A missing pidof result alone is insufficient.

Android14 AOSP ProcessList selects package-associated/dependent processes, not every UID1000 process simply because of the shared UID. Check the actual ActivityManager associations before execution; dependent/shared processes may be affected. OEM framework differences remain possible. Do not assume force-stop returning means process death completed.

### Evidence from the preserved stock APK

- `x2/c.java:42`: command SerialPort opened in-process at `/dev/ttysWK0`,57600. Its Java streams share the returned FileDescriptor (`com/hebs/dmrserial/jni/SerialPort.java`). No descriptor handoff was found in the inspected radio classes; this is not proof none exists in native/framework code.
- `ForegroundService.onTaskRemoved`: sends `android.hebs.action.DMR_EXIT`, calls `t2.d.h()` (auctusctl4 then dmr_pwr0), exits. Task swipe is an active vendor shutdown, not a substitute for force-stop.
- `ForegroundService.onUnbind`: stops service/foreground and exits; no explicit PTT release in this method. No custom onDestroy in this service. `MainActivity.onDestroy` unregisters its receiver; it is not UART cleanup.
- `WeApplication.onTerminate`: closes command UART/streams and calls power-down. Android explicitly documents that onTerminate is never called on production devices. This cleanup cannot be relied on during force-stop.
- `t2.d.M`: PTT0 asserted,1 released. No reviewed force-stop callback guarantees release. Never stop it while transmitting or while the PTT value is unknown/asserted.
- `MainActivity` startup writes dmr_pwr1 and auctusctl1 and initialises the radio. Relaunch is not passive and can send normal stock UART/configuration traffic. It must be covered explicitly by the reviewed handback; no claim that the entire session contains zero radio traffic.

### Read-only live observations during this review

Intercom PID4833, service in that process, ActivityManager packageList contains only com.wtwd.ucee.dmr. PTT1, dmr_pwr1, auctusctl1. These are observations, not future test evidence. Foreground service is present; no stop/relaunch performed.

Additional process `com.hebs.factorytest` PID5421,UID1000 is running. Its FD list is also denied. Its name is a reason for investigation, NOT proof it owns or accesses the radio. `com.szhebs.update` is also present. A narrow init-file text search produced no radio references; this does not prove no service can open the UART. Do not force-stop additional packages under Intercom approval.

## Safety assessment

Force-stop is a credible temporary release of **Intercom's own** UART references. It bypasses graceful Java cleanup; leaves GPIO/module state potentially latched and can invoke serial driver shutdown on final close. Pending output/driver work may complete after kill. PTT1 and no physical button operation are necessary, but do not establish zero RF risk or complete module safety. Independent RF observation is preferable; a quiet UI is not an RF measurement. No driver-specific guarantee of harmless final-close was established.

The stock descriptor-release question is substantially answered by process-exit semantics. Full ownership of the node is still unresolved because other potential holders cannot be inspected. The existing P8Ownership complete-holder gate remains unchanged. Do not label a stop-only observation as a complete holder scan or weaken it silently. The complete five-step test is conditional, not ready to execute automatically.

## Controlled procedure for a reviewed future session

1. Prepare the separately reviewed app-UID open-only build and external observation first; no normal radio engine or automatic takeover. Save process/package/service associations and radio-state baseline. Keep physical PTT/SOS/programmable buttons untouched; owner confirms no TX indication. Read PTT repeatedly and require1 immediately before stopping. Power/audio-control must be readable and match the reviewed baseline; otherwise stop without writing them.
2. Execute exactly once through authorised ADB shell: `am force-stop --user 0 com.wtwd.ucee.dmr`. No root, PID/UID kills, disable, uninstall, clear-data, Exit or task swipe. Stop on errors; no retry or escalation.
3. Bounded observation (up to5s, no repeat force-stop): require package stopped=true, no package-associated running process in ActivityManager, no corresponding ps/pidof process, old PID gone (account for PID reuse), foreground service absent and no restart. Re-read PTT/power/audio state. Unexpected changes, asserted/unknown PTT, incomplete visibility, unrelated process loss or delayed/stuck exit -> no UART open. Do not "fix" GPIOs. Retain evidence.
4. Only if the separate other-holder and driver-safety questions are resolved, reacquire fresh evidence within the policy's5s window and invoke the previously specified **one** app-UID lstat/open/fstat/close on chr207:5. No read/write, baud/termios/ioctl/poll/flush, PCM, GPIO or retries. Do not use shell open as a substitute. Record errno, node identity, close outcome and duration. Any unexpected result -> stop.
5. After confirmed descriptor closure and safe state, reviewed handback is one explicit stock launch: `am start --user 0 -n com.wtwd.ucee.dmr/.common.MainActivity`. No monkey input. This performs the stock app's normal initialisation; it is outside the zero-byte probe itself. Verify launch, package stopped=false, stock process/service and released PTT; owner checks normal receive UI without TX. If probe hangs or radio state is unexpected, do not relaunch into uncertain concurrent ownership: stop/report first. If handback fails, report it rather than retrying, rebooting or touching GPIOs automatically.

No persistent takeover setting, boot receiver or package modification is part of this procedure. Force-stop's temporary stopped-state side effect must be reported if handback cannot complete. Receive/audio settings may be reinitialised by stock startup; this does not promise an identical transient pre-stop hardware state.

## Recommended next action

Review a **stop/observe/stock-handback-only** stage first to validate this OEM's lifecycle effects, with no UART open if other-holder evidence remains unknown. Before the combined test, identify whether factory-test/update/vendor services can hold ttysWK0 (saved APK/init/native inspection or manufacturer confirmation). This is an explicit unresolved condition, not a requirement to root or inspect a dead process's FDs. No additional package stop is proposed.

## Primary references

- Android14 AOSP ProcessList, killPackageProcessesLSP: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/services/core/java/com/android/server/am/ProcessList.java
- Android Application.onTerminate: https://developer.android.com/reference/android/app/Application#onTerminate()
- Android process lifecycle: https://developer.android.com/guide/components/activities/process-lifecycle
- Linux process exit/descriptor close: https://man7.org/linux/man-pages/man2/_exit.2.html and https://man7.org/linux/man-pages/man2/close.2.html

These describe framework/kernel semantics; they do not certify the OEM UART driver's electrical effects. Decompiled APK findings refer to inspected methods; known JADX errors elsewhere and native code remain limitations.
