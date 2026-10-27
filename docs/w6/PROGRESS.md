# W6 progress log

Newest first. Phone: ANYSECU W6+ replacement unit, adb `-s W6202601000904` (USB). Plan: `docs/W6_DEVICE_REPORT_PLAN.md`.

## 2026-10-27 16:15 CDT — Phase 1: rooted baseline B

- Worktree `SignalDeck-w6`, branch `w6/phase1-baseline` from `origin/main` `eb2797c0`. Copied the untracked
  `docs/w6/` notes from the main checkout (APKs, vendor partition heads and the third-party PoC app's dex strings left
  out; the dead unit's serial masked as `W6-UNIT1-SERIAL`).
- Built `1.0.0-beta.20` release (the APK carries `lib/armeabi-v7a` for all three native libs), `install -r` on the W6,
  root granted through Magisk's policy table (uid 10113).
- Ran **Run all checks** through screen taps over adb. Report B is in `reports/20261027_phase1/baselineB/`. A location
  prompt got a mis-tap ("while using"); the grant was revoked with `pm revoke` before the run.
- Result and gap list: `PHASE1_BASELINE_B.md`. Most important: the probe skipped `/dev/ttyS2` (label `console_device`)
  and wrote DMR queries to the Unisoc engineering port `/dev/ttySE0` instead; no AT probe; no serial bytes; the stock
  app was already running so its start-up was not seen.
- Next: the bench driver (`DiagReceiver checks` + `scripts/w6/run_checks.sh`), then the probe port choice and AT probe.
