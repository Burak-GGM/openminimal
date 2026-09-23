# Public beta readiness

The source and CI are prepared for `0.3.0-beta.1`; this document separates a buildable candidate from an approved public release.

## Before a public release

1. **Done:** the owner selected GPL-3.0-only and the full LICENSE is in the repository.
2. **Key created:** the maintainer's long-lived 4096-bit RSA app-signing key is stored locally under ignored `.device/release/`; the keystore and `credentials.json` both require a separate offline/password-manager backup before Play enrollment. Its SHA-256 certificate fingerprint is `37:E8:A4:54:0F:C5:DD:2F:43:79:1A:81:78:BB:57:C8:2E:0D:6F:09:E0:FA:2A:02:55:4D:21:C7:CE:75:31:6D`. It expires in February 2054. To permit the same certificate on GitHub and Play, supply this app-signing key during Play App Signing setup instead of asking Google to generate a different app-signing key; a separate Play upload key can be registered afterward.
   Existing debug installs use a different certificate and cannot be upgraded in place to a newly signed release. Preserve any important notes/tasks before switching; there is no complete user-data export/migration flow yet. Never uninstall or clear the maintainer's phone automatically.
3. **Verified locally:** the four `release` environment secrets described in [DEVELOPMENT.md](DEVELOPMENT.md) are configured. The signed APK has the expected package, version and certificate. It installed, launched without an AndroidRuntime crash and reinstalled with `adb install -r` on a separate clean Android 16 AVD, without changing the maintainer's debug AVD or phone. The signed AAB passed `jarsigner -verify` and bundletool 1.18.3 `validate`. A data-preserving migration from a prior debug certificate remains impossible without an explicit export/import path.
4. Finish large-font, TalkBack, landscape, API 26 and low-memory acceptance checks. The existing Android 16 emulator and Nothing Phone (2) do not establish low-end performance or broad OEM compatibility.
5. Review the exact Play Data safety form, Accessibility API declaration, target API policy and store listing against the shipped artifact. Link to the public English/Turkish privacy policy. Prepare icon, screenshots and short/full descriptions in both languages. Keep the in-app consent and Play disclosure aligned.
6. For F-Droid, submit a reproducible source tag and build metadata. F-Droid builds and normally signs from source using its own key, so its installs may not update a GitHub/Play install in place; check the final F-Droid signing arrangement before claiming cross-store updates.

## Known beta limitations

Screen-time totals are estimates and Live Focus Guard depends on Android/OEM accessibility events. It does not force-stop other apps. The app has no Internet permission; onboarding's online selection is reserved for a future feature. Obsidian support opens a URI rather than syncing a vault. Third-party widgets retain their own behavior and privacy terms. API 26 hardware and low-end performance remain unverified.

## Release procedure

The `Android CI` workflow runs on each main-branch push and pull request and uploads a debug APK plus unsigned release artifacts for review. The unsigned release artifacts cannot be installed or submitted as the public beta. `Signed beta release` is manual; it requires a LICENSE file, a `v`-prefixed tag on the selected commit and the four signing secrets. The workflow builds and signs the release APK/AAB, verifies the APK, writes checksums and creates a GitHub prerelease. Do not publish a debug APK as the store build. Keep the release tag immutable once distributed.

The next phase is Play Console setup, review of store declarations and the appropriate tester track. F-Droid submission follows the license and source-tag decisions.
