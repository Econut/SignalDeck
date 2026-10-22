# SignalDeck changelog

Newest first. Each entry: what changed, the files, and how it was tested (host tests, device, bench). Entries before
the move to this repository (2026-10-17 to 2026-10-22, beta.1 to beta.11 and after) are in
[phonedmrapp/CHANGELOG_DRAFT.md](https://github.com/IIMacGyverII/phonedmrapp/blob/main/CHANGELOG_DRAFT.md).

## 2026-10-22 — SignalDeck moves to its own repository

| Backlog | Change | Files | Test |
|---|---|---|---|
| — | SignalDeck now lives in `IIMacGyverII/SignalDeck`, with its full history (188 commits from phonedmrapp's `DMRStandalone/`, via `git subtree split`) and only its own work: no decompiled stock app, no firmware. The updater reads this repository's releases, the device-profile list is fetched from here, device reports and issues point here, and About shows this source link. Release signing moved out of the build file into `local.properties`/environment (debug key fallback). COPYRIGHT adapted (interoperability note: the app ships none of the phone maker's software), new README, `profiles/`, `scripts/sign_profiles.py`, the SignalDeck docs, and the synthetic test vectors (direwolf benchmark, SSTV colour-bar reference) are committed so every host test runs from a clean clone. | `README.md`, `COPYRIGHT`, `LICENSE`, `CHANGELOG.md`, `CLAUDE.md`, `.gitignore`, `app/build.gradle`, `settings.gradle`, `Updater.java`, `ProfileRegistry.java`, `repeaters/HttpClient.java`, `DeviceReport.java`, `About.java`, `profiles/`, `scripts/`, `docs/`, `app/src/test/resources/` | Clean clone builds; 169 host tests pass; the APK is signed with the same certificate as the installed builds. |
