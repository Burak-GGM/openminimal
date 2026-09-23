# Public beta readiness

The source and CI are prepared for `0.3.0-beta.1`; this document separates a buildable candidate from an approved public release.

## Before a public release

1. The owner selects and adds an open-source license. F-Droid requires freely licensed source; a GitHub repository without a LICENSE file is not enough.
2. The owner chooses a long-lived signing key. Keep the keystore and passwords outside Git and back them up offline. Decide whether Play App Signing and direct-download/F-Droid APKs should share an app-signing certificate before enrolling in Play.
   Existing debug installs use a different certificate and cannot be upgraded in place to a newly signed release. Preserve any important notes/tasks before switching; there is no complete user-data export/migration flow yet. Never uninstall or clear the maintainer's phone automatically.
3. Set the GitHub `release` environment secrets described in README. Test the signed APK on a clean emulator without clearing the owner's phone data; verify its package/version/certificate and that it updates the intended prior install.
4. Finish large-font, TalkBack, landscape, API 26 and low-memory acceptance checks. The existing Android 16 emulator and Nothing Phone (2) do not establish low-end performance or broad OEM compatibility.
5. Review the exact Play Data safety form, Accessibility API declaration, target API policy and store listing against the shipped artifact. Link to the public English/Turkish privacy policy. Prepare icon, screenshots and short/full descriptions in both languages. Keep the in-app consent and Play disclosure aligned.
6. For F-Droid, submit a reproducible source tag and build metadata after the license is selected. F-Droid builds from source; the GitHub APK is a separate distribution channel.

## Known beta limitations

Screen-time totals are estimates and Live Focus Guard depends on Android/OEM accessibility events. It does not force-stop other apps. The app has no Internet permission; onboarding's online selection is reserved for a future feature. Obsidian support opens a URI rather than syncing a vault. Third-party widgets retain their own behavior and privacy terms. API 26 hardware and low-end performance remain unverified.

## Release procedure

The `Android CI` workflow runs on each main-branch push and pull request and uploads a debug APK plus unsigned release artifacts for review. The unsigned release artifacts cannot be installed or submitted as the public beta. `Signed beta release` is manual; it requires a LICENSE file, a `v`-prefixed tag on the selected commit and the four signing secrets. The workflow builds and signs the release APK/AAB, verifies the APK, writes checksums and creates a GitHub prerelease. Do not publish a debug APK as the store build. Keep the release tag immutable once distributed.

The next phase is Play Console setup, review of store declarations and the appropriate tester track. F-Droid submission follows the license and source-tag decisions.
