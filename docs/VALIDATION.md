# Validation — 0.3.0-beta.1 candidate — 2026-09-23

## Build and automated tests

Beta preparation: local debug unit tests and lint passed; release APK/AAB assembly with
resource shrinking and R8 passed without signing credentials. The emulator ran **41/41**
instrumentation tests successfully on 2026-09-23 after the first in-app privacy dialog
change. A final policy-link and dialog-scroll adjustment was subsequently compiled in
the normal debug build and installed/launched on the same emulator. Initial public
[GitHub CI](https://github.com/Burak-GGM/openminimal/actions/runs/35865504211) succeeded,
including JVM tests, lint, debug APK and release APK/AAB assembly. The unsigned release
APK is not installable.

For the signed release candidate, the 4096-bit RSA certificate SHA-256 is
`37:E8:A4:54:0F:C5:DD:2F:43:79:1A:81:78:BB:57:C8:2E:0D:6F:09:E0:FA:2A:02:55:4D:21:C7:CE:75:31:6D`.
`apksigner verify` passed; `jarsigner -verify` accepted the AAB (with self-signed
certificate warnings), and bundletool 1.18.3 validated its structure. The signed APK
installed, launched without AndroidRuntime errors and reinstalled on a separate clean
Android 16 AVD. No debug install or owner phone data was cleared.

- Debug assembly, JVM tests and lint succeeded through `bash scripts/build-and-run.sh`.
  Package `org.openminimal.launcher`, versionCode **12**, versionName **0.3.0-beta.1**.
- **38 JVM tests passed**, zero failures/errors. Existing personalization tests cover schema 8 migration into
  independent Home/Drawer settings, nested-field precedence and bounds, structural preset identity,
  private-state preservation and preset serialization, grouped row capacity, scaled text metrics,
  minimum grid columns and sparse overflow projection.
- Lint: **0 errors, 18 warnings**. Remaining warnings concern dependency versions, API-33 locale
  metadata, the API-31 accessibility-tool declaration, dynamic external icon resource lookup and
  optional KTX idioms. No new dependencies or INTERNET permission were added. English/Turkish each have **387** resource keys, matching
  without duplicates.
- Before the last lifecycle refinements, full instrumentation passed **40/40** (2 minutes 34 seconds).
  The final expanded run passed **40/41**, with one Compose-idle timeout in the existing icon-folder
  editor test. That exact test passed unchanged on a targeted rerun (24 seconds); the timeout was not
  reproducible. No tests were skipped. Keep this intermittent test-synchronization issue visible.
  The new real-service budget-expiry regression and phrase-completion regression both passed.
  Instrumentation targets **emulator-5554 / Pixel_9a / Android 16**, never the owner's phone.
- Tests restore configuration, favorites, permission modes and temporary default-home role. Custom
  preset tests delete only their own records. Wallpaper asset fixtures remove only their own files.
  No uninstall, app-data clear or AVD wipe is used.

## New regression coverage

- Focus Gate policy precedence, hide projection, generated arithmetic answers, memory-round counts,
  versioned rule round-trip, bounds and legacy direct-map migration are pure JVM regressions.
- A real app action saves a 15-minute policy and shows it in Screen time. A launcher-originated
  every-launch phrase gate remains disabled until the exact configured phrase is entered, then opens
  the target test application. The gate screen was captured and visually inspected.
- Gate content scrolls with keyboard/short viewports; its text input has a localized label, and
  arithmetic problems survive Activity recreation. Unconfigured packages skip usage queries;
  timer rechecks trigger only for a reached daily budget, avoiding premature every-launch gates.
- Independent Home grid / Drawer list through real Settings; the reverse combination and migration
  are checked in pure model tests. Activity recreation retains the current Settings section.
- Live clock preview grows while a slider is still held, while persisted configuration remains
  unchanged until release. The saved size and preview route survive Activity recreation.
- Settings search opens the matching section, including localized Turkish routes and existing
  screen-time access. The category hierarchy replaces old swipe-tab tests.
- Custom preset creation, Custom status after modification, apply, rename and confirmed delete.
  Saved names and settings are checked against DataStore; delete leaves the applied appearance.
- Folder-member ordering and Move out of folder preserve the favorite application.
- Wallpaper gradient/color selection, source persistence, returning to theme and retaining disabled
  system sync. Independent saved photo revisions are decoded and checked for different colors.
- Explicit application language originally broke document-picker initialization because its
  configuration context lost the Activity result registry. The registry is now preserved across
  localization; the wallpaper UI regression runs in explicitly selected English.

## Existing regressions retained

- Single non-scrolling Home, grouped row limits, fixed edit-cell count, full-state favorite actions,
  gaps and position persistence. A capacity fixture now fixes footer copy so setup does not change
  the available viewport while it fills favorites.
- Home long press opens only app actions; Edit layout controls both-axis movement, empty targets,
  edge swaps and center folder creation/addition. Done, cancellation and Activity recreation work.
  Icon folders remain centered dialogs; text folders remain bottom sheets.
- Drawer swipe open/close, optional animations, alphabet rail versus collapsed categories, manual
  category assignment, grid labels, hidden-navigation recovery and personalized Home text.
- Native shortcut fixture icons, expansion, launch, App info and cancelled system uninstall.
  Platform-shortcut loading allows 15 seconds. The App info assertion invokes its accessibility
  click action after confirming the target to avoid coordinate races during sheet animation.
- External icon-pack fixture/fallbacks; unchanged grayscale/B&W bitmap rules; widget picker grouping,
  labels/previews, binding, resize persistence, fixed-size providers and monochrome view filtering.
- Default-home role request, usage-access visibility, clock/usage actions and reversible daily limits.
  JVM tests retain midnight clipping, event transitions and current-day override accounting.

## Device and visual review

The 0.3.0-beta.1 candidate debug APK installed over existing data and launched on the reused Pixel_9a emulator.
The Nothing Phone (2) was not connected via ADB during the final build, so this version was not
deployed to the physical phone. APK: `app/build/outputs/apk/debug/app-debug.apk`.
No low-end performance conclusion follows from this emulator or the Nothing Phone (2).

Reviewed emulator captures of Home icons, centered folder, clock live preview and wallpaper picker.
Clock/list spacing and default-size labels fit the inspected layouts. Grid label space is now
reserved for two lines so short and long labels keep their icons aligned. Palette wallpaper
choices now use current light/dark tones and a muted gradient endpoint to improve text contrast.

The earlier 0.1.9 physical-device check recorded separate Home/lock wallpaper IDs. This iteration
retains FLAG_SYSTEM and tests bitmap generation/local assets; it does not repeat physical OEM
recents cache or lock-wallpaper pixel verification. Arbitrary photos can still need a stronger
readability overlay.

Live Focus Guard was also exercised outside the instrumentation lifecycle: the service was enabled
on the emulator, confirmed as bound in `dumpsys accessibility`, and a fixture was launched directly
with `am start`. Android focused `FocusGateActivity`; UIAutomator observed the configured phrase,
disabled Open app action and Home escape. The temporary rule and secure accessibility settings were
restored afterward. `FocusGuardIntegrationTest` now obtains UiAutomation with
`FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES`, so it can test the real service rather than having the
test framework suspend it. The fixture opens below its budget and the test observes the gate at the
next minute threshold while it remains open. This test and the phrase-completion UI test passed after
the final changes; they restore prior access settings and rules. Broader OEM behavior still needs checks.

## Remaining validation and product boundaries

The declared minimum is API 26, but current instrumentation is Android 16. API 26 hardware,
TalkBack, large system font scales, landscape/multi-window and release performance need dedicated
acceptance checks. Pure scaled-text tests do not substitute for these runtime checks.

Normal long press → immediate drag across the action menu is not implemented; explicit Edit layout
is retained to avoid the previous gesture conflict. Weather is a presentation contract only.
Smart Drawer ranking, portable preset assets, arbitrary coordinates/fonts and editable applet boards
remain on the roadmap. Proprietary icon engines, work profiles, OEM usage-event differences,
Obsidian vault synchronization and long-term usage history remain outside this iteration.

See [PERSONALIZATION.md](PERSONALIZATION.md) for the exact next-session starting point and
[ARCHITECTURE.md](ARCHITECTURE.md) for canonical configuration and snapshot boundaries.
