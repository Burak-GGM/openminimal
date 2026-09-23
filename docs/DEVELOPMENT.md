# Open&minimal

A local-first Android launcher for more intentional phone use.

**Status: 0.3.0-beta.1 public GitHub beta. Play Store and F-Droid distribution are pending.**

Start with [Architecture](ARCHITECTURE.md), [Personalization and remaining work](PERSONALIZATION.md)
and [Validation](VALIDATION.md) when continuing development. Paths and shell commands below
are relative to the repository root.

Open this directory as the Android Studio project. The launcher package is
`org.openminimal.launcher`; the display name is **Open&minimal**.

## Implemented in this prototype

- Native Kotlin / Jetpack Compose launcher activity, with a system default-home action.
- Simple/advanced onboarding, five built-in presets, light/dark/system theme and English/Turkish UI.
  Save, apply, rename and delete up to 12 local presets. Manual visual changes show Custom through
  typed snapshot comparison; presets preserve notes, tasks, usage, folders and permission preferences.
- Independent Home and Drawer list/grid settings with live previews using the actual app and clock
  renderers. List text weight/size, icon size/gap and row spacing are adjustable; Home also has list
  width, alignment, top/center/bottom placement and a maximum count. Grid has columns, icon size and labels.
  Clock visibility, size, weight, alignment, typeface, 12/24/system format, date format, weekday and
  spacing are independent; oversized clocks fit narrow displays.
- Installed-app list, search, long-press favorites, home-screen app launching and optional
  swipe-up app drawer (enabled by default); swipe down at the top of the list or on its
  header to close. Hold a favorite and choose **Edit layout** to rearrange it; **Done** returns
  to normal use. Editing supports horizontal/vertical grid movement, accessible
  move actions and order persistence. Ungrouped icon layouts preserve empty cells; grid drops
  swap only the source and destination on release. A subtle optional wiggle marks editing. Home long-press shortcuts apply only to empty space.
- Home's **All apps** button can be hidden in Home Screen settings. A long press on empty Home space
  always offers All apps and Settings, even with navigation controls and swipe-up disabled.
- Heading and footer text can be independently hidden or personalized under Home Screen → Home text.
  Blank custom fields restore the localized default; hiding text keeps its saved value.
- Home folders support icon previews and named text rows. Open a folder to see its apps; long-press
  to rename/change members, edit its home position or remove it while returning its apps to Home.
  In icon layout editing, drop an app onto another icon/folder to create/join a folder immediately;
  dropping near a cell edge swaps cells. No creation form is shown. The app's **Add to folder** menu
  can also create a one-app folder immediately; its existing editor handles later naming/membership.
- Folder members can be reordered in the folder editor by drag or accessible buttons; an app's
  context menu can move it out of the folder. Icon folders open in an animated centered dialog;
  text folders keep the bottom sheet. Folders stay on Home; the drawer continues to list individual apps. Membership
  is exclusive, nested folders are excluded, daily limits apply inside folders, and presets keep them.
- Text-only, text + icon and icon-only appearance for both home and the drawer. Icon-only mode
  shows names below drawer icons and uses a grid with 3–6 requested columns (4 by default), reduced on narrow screens to preserve
  48 dp touch targets. Favorites can be grouped by the same local app categories; editing shows
  a flat order and restores category sections on exit.
- Home favorites occupy one fixed, non-scrolling page. Capacity follows the usable screen height,
  visible Home controls and category headings; list layouts intentionally hold fewer entries than
  icon grids, and a folder uses one cell. The app menu explains when Home is full. Existing overflow
  stays saved and available in the drawer if a layout change temporarily reduces capacity.
- Compact app-action sheets include favorites, group assignment, daily limits and home layout
  editing. App info and uninstall use icon buttons and open Android-owned screens; uninstall
  always requires system confirmation and is offered for non-system apps. Static, dynamic and
  pinned app shortcuts come from LauncherApps when this is the default launcher. Missing shortcut
  access and apps without shortcuts have explicit states. Native shortcut icons appear in two-column
  cards, showing four initially with an explicit expansion control.
- Optional app categories with collapsible groups. The alphabet rail is available only when
  grouping is off; its stored preference returns when grouping is disabled. The compact rail
  shows nearby letters on a slim track; dragging advances one letter per 36 dp without flinging. Android category
  hints are used; choose a group from an app’s long-press menu, including Health/Finance.
  Unknown apps appear under Other; categorization needs no network access.
- Separate grayscale and B&W icon styles; B&W quantizes icon colors to pure black/white while
  retaining transparency. Enabling either style disables the other.
- Installed ADW/Nova-compatible icon packs (`appfilter.xml`, resources or assets), plus
  Android 13 themed-icon support when an app supplies a monochrome adaptive icon.
  Original icons remain for missing mappings/uninstalled packs. Icon masks, dynamic clock/
  calendar mappings and proprietary OEM-only theme engines are not implemented. Nothing's
  first-party icon pack does not expose mappings to third-party launchers and is labeled accordingly.
- Searchable settings categories and sections replace the horizontal tab strip. Search matches
  localized section names and keywords; Back and Activity recreation preserve navigation.
  Drawer, alphabet and press animations and haptic feedback are optional.
- Horizontal home / applet board / full-page notes navigation. Each built-in applet and
  Android widget can be hidden without deleting content or widget bindings. Empty applet
  pages disappear. Absolute Focus supplies defaults which can be overridden.
- Independent Sage/Monochrome/Ocean/Sand/Plum palettes and dark-background selection.
  Absolute Focus defaults to true black in dark mode; colors do not change page layout.
- Optional bottom navigation and settings icon. Long-press an empty area of the home
  screen to reveal a Settings shortcut, even when both are hidden.
- Theme-color, solid, gradient or user-selected-photo home wallpaper with a visual picker and
  adjustable readability overlay. Saved local presets retain their own photo revisions. Optional system-home sync
  that uses Android's system-only wallpaper flag and leaves the lock screen unchanged. Returning
  from a photo to a theme color immediately writes a display-sized solid wallpaper so recents does
  not retain the prior photo.
- Default-launcher requests use Android's activity-result flow, with a system-settings
  fallback. Usage-access buttons disappear when access is granted and update on resume.
- Device-local notes and tasks persisted with DataStore. Preset configuration and
  private content use separate storage keys.
- Optional usage-access integration with an estimated foreground-time summary.
  Event accounting clips sessions at midnight and avoids double-counting app transitions.
- Per-app **Focus Gate** rules combine optional daily budgets and independent every-launch friction.
  Reached apps can be hidden from Home/search or stay visible behind an editable phrase, arithmetic,
  memory sequence or 10/30/60-second wait. Math and memory difficulty are configurable. Manage rules
  from app actions or Screen time. Presets preserve these rules and usage totals.
- Optional **Live Focus Guard** uses Android accessibility window events to identify the foreground
  app and show a gate on external launches or when a budget expires during use. It requires explicit
  Android consent and never retrieves screen content. Daily accounting separately requires Usage
  Access. Timing depends on Android/OEM events; the service can be disabled and does not force-stop apps.
- Android widget picker, binding/configuration, persistence, removal and height controls
  on the board. The searchable picker groups widgets by application display name and shows
  provider previews when available, with a labeled fallback otherwise. Edit a widget to
  reveal size/removal controls; the live widget updates as its height changes. Fixed-height
  providers do not expose resize controls. Picker grouping can be disabled, and previews/live
  content can be shown in monochrome. Third-party widget layout remains owned by its provider.
- Explicit Obsidian URI action to create a note; Obsidian must be installed. This is
  not background vault synchronization.
- Versioned applet descriptors and page models for future extensibility.

## Privacy

No `INTERNET` permission, analytics, advertising, accounts or remote fonts. App cloud
backup is disabled. The onboarding network preference is stored for future connections;
both choices currently run entirely locally. Other apps and their widgets manage their
own networking. Usage and accessibility access are optional and are not granted automatically.
Read the full [English](PRIVACY.md) or [Turkish](PRIVACY.tr.md) privacy policy.

## Build and run

Requires JDK 17+ (tested locally with JDK 21), Android SDK platform 36 / build tools 36,
and the included Gradle wrapper. Set `sdk.dir` in your untracked `local.properties`,
or configure `ANDROID_HOME` for your machine.

```sh
bash scripts/build-and-run.sh
```

This runs debug unit tests, Android lint and APK assembly, then installs and launches
the APK if an authorized Nothing Phone (2) is connected, or uses a single running
Android emulator when the phone is unavailable. It does not uninstall the
app, clear its data, or change the default launcher. Other physical devices are not selected
automatically. Use Android Settings to select Open&minimal as your default home app.

Individual commands:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
bash scripts/install-and-run.sh
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

The SDK baseline is provisionally Android 8.0/API 26. Nothing Phone (2) testing is
not evidence of performance on low-end devices. Release performance and API 26
compatibility need dedicated measurements.

## Emulator fallback

The existing **Pixel_9a** AVD (Android 16/API 36) is available on this machine. Its
missing emulator and system image were restored without wiping the existing AVD.
KVM acceleration is available. A numeric skin avoids its missing Studio skin files.

```sh
bash scripts/start-emulator.sh Pixel_9a
# For headless debugging:
bash scripts/start-emulator.sh Pixel_9a -no-window -port 5554
```

Do not start a second copy while the same AVD is already running. UI regression tests
are intended for the emulator and skip physical hardware. Explicitly select the emulator:

```sh
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

Test bodies restore configuration and usage-access state after each test and do not
edit notes/tasks. Instrumentation keeps APKs installed and disables incompatible-APK uninstallation. Use the emulator
for these development tests, never the owner’s physical phone. Tests cover gestures, hidden-navigation recovery,
applet visibility, palette independence, usage permission, clock customization and system home-role consent.
Test-only icon-pack/widget fixtures exercise real component mappings, preview images,
widget binding and hidden resize controls; they are not included in production APKs.

## Next milestones

- Large-font, TalkBack, landscape/multi-window and release performance acceptance checks.
- Normal long-press menu-to-drag transition using the existing editor drag state.
- Editable board grid, multiple board pages, applet sizing/order.
- Preset file import/export, portable photo assets and safe missing-dependency handling.
- Weather provider design and further independent layout controls; see the detailed personalization roadmap.
- Persistent usage history and broader OEM/multi-window accounting validation.
- Obsidian document-tree integration, opt-in connection brokers and credential storage.
- Applet runtime isolation, user scripting and a block builder.
- Community presets/applets, accessibility hardening and measured performance budgets.

Product decisions: [PRODUCT.md](PRODUCT.md).

## Source license

Open&minimal is licensed under [GNU GPL v3.0 only](../LICENSE) (`GPL-3.0-only`).
Third-party dependencies retain their respective licenses.

## GitHub builds and beta releases

Every push and pull request runs unit tests, Android lint, and debug/release APK and AAB
builds. CI uploads a debug APK and unsigned release build artifacts for review. These
temporary artifacts are not signed public releases.
The release build remains unsigned unless all four `OPENMINIMAL_RELEASE_*` signing
environment variables are supplied. A stable private signing key is required before
publishing an installable release. Never commit a keystore or its passwords.

Configure the `release`
GitHub environment with `OPENMINIMAL_RELEASE_KEYSTORE_BASE64`,
`OPENMINIMAL_RELEASE_STORE_PASSWORD`, `OPENMINIMAL_RELEASE_KEY_ALIAS` and
`OPENMINIMAL_RELEASE_KEY_PASSWORD` secrets. Create a `v`-prefixed tag at the desired
commit and run the **Signed beta release** workflow from that commit with the tag as
input. It checks the tag, tests and lints, verifies APK signing, and publishes a
prerelease APK, AAB and SHA-256 checksums. Retain the signing key securely; an APK
signed with a different key cannot update an existing direct-install copy.
