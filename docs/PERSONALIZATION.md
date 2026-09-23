# Personalization implementation — updated 2026-09-23

## Repository audit and plan

The starting point is 0.1.9-dev / schema 8. Compose route state lives in Launcher with
rememberSaveable; Home and applet boards use a pager. LauncherViewModel exposes DataStore
state and platform app/usage flows. ConfigCodec owns JSON compatibility. Notes, tasks,
favorites, widget bindings and limits have separate DataStore keys. Home and drawer currently
share layout booleans; Settings is a seven-tab pager. Presets only patch selected fields and
their label never becomes Custom. Existing keyed drag/drop and folder operations should stay.

The three owner-supplied reference images were inspected. Useful principles: a shared clock/list
axis, generous but regular rhythm, clear app names and localized category headings. We will keep
OpenMinimal's muted palette, rounded controls and quiet Home. No copied curved alphabet rail,
oversized category tiles, bundled reference imagery or network weather provider.

Implementation order (each slice must build/test before the next):

1. Typed Home/Drawer, reusable list/grid and clock models; schema migration; structural preset
   snapshots and local custom-preset storage. Preserve content and platform grants.
2. Category-based Settings with a localized searchable section catalog. Home/Drawer layout
   controls independently update their model. Stable section IDs replace numeric settings pages.
3. Shared app-entry and clock renderers for Home/Drawer and live preview. List dimensions,
   alignment/placement, font/icon size/weight/spacing, fitted count and independent clock/date.
4. Preset management (up to 12 local presets, rename/apply/delete), dependable Custom status;
   wallpaper selection polish with explicit Home-only scope and background extension points.
5. Preserve and refine existing layout edit/drop/folder flows; regression and accessibility
   checks, emulator visual review, documentation and authorized phone installation.

## Boundaries

This session targets finished vertical slices, not the entire future product. Weather has a
presentation contract but no data provider or permission. Smart Drawer already has local Android
category hints and user overrides; recent-app ranking, richer automatic classification, community
catalogs, arbitrary layouts, network code and preset file import/export remain separate work.
The proposed alternative B&W icon algorithm still needs owner approval and is not part of this work.

## Completion ledger

### Completed in 0.2.0-dev

- Schema 9 typed Home/Drawer, list/grid, clock/date and wallpaper configurations with schema 8
  migration. Existing private data is retained. Home and Drawer layouts are independently editable.
- Category → section Settings navigation and localized keyword search, with saved destinations.
- Shared app-entry/clock live previews. Sliders update the preview during dragging and persist on
  release. List sizing, weight, icon gap/size, row spacing, width, alignment, vertical placement
  and maximum count; grid columns, icon size and labels; independent clock/date controls.
- Five built-in starting configurations; structural Custom status; 12 local named presets with
  save/apply/rename/delete. Versioned records exclude private data and permission preferences.
- Wallpaper preview, theme/photo/solid/gradient sources, color choices, readability overlay and
  Home-only system sync. Immutable photo revisions allow different photos in saved local presets.
- Folder-member reordering by drag or accessible buttons, and Move out of folder app action.
  Existing Home edit mode, empty slots, edge swaps and center folder drops remain in use.
- Measured single-page Home capacity incorporates new dimensions and category rows. Sparse saved
  positions outside a smaller viewport project into visible vacancies without deleting saved data.
- List position moves the complete list block; row contents remain start-to-end with stable icon
  and label columns and a uniform configured gap.
- Explicit language selection preserves the Activity result registry for the photo picker.

### Completed in 0.3.0-dev

- Per-app Focus Gate rules stay outside visual preset snapshots and preserve existing daily budgets.
  Rules support daily hide/block or challenge behavior plus independent every-launch friction.
- Editable phrase, generated arithmetic, screen-reader-announced memory sequence and 10/30/60-second
  wait challenges share one typed model and one decision function across launcher surfaces.
- Home/Drawer hide projection, app/native-shortcut launches and the Screen time rule editor use the
  same policy. Switching a reached app from Hide to Focus Gate immediately restores it to Drawer.
- Optional Live Focus Guard observes foreground package changes through a content-blind accessibility
  service and schedules a usage recheck when a budget should expire inside an already-open app.
  Disclosure and Android consent remain separate from Usage Access.
- `focus_rules_v1` uses a versioned envelope and reads the earlier direct-map development format.
  Presets cannot enable the service, grant permissions, reset usage or change focus rules.

Architecture and rationale are in [ARCHITECTURE.md](ARCHITECTURE.md). Current verification and
device limitations are in [VALIDATION.md](VALIDATION.md).

### Partial / deliberately bounded

- Clock supports start/center, two typefaces, weight, 12/24/system format, date styles and weekday;
  arbitrary x/y placement, custom fonts and independent day-number visibility are not implemented.
- List placement uses start/center/end and top/center/bottom, not arbitrary coordinates. Home stays
  fixed and non-scrolling to honor the earlier product decision. Overflow favorites remain stored.
- Native shortcuts and existing explicit Edit layout are supported. A single normal long-press
  gesture flowing from the action menu into drag is still pending; avoid reintroducing overlapping
  Home/app menus when implementing it.
- Weather has only a disabled presentation contract; no provider or weather UI is shipped.
- Local Android categories/manual overrides remain available. Ranking by recents, favorites filters
  and more sophisticated automatic classification are not implemented.
- Wallpaper colors are palette choices. Arbitrary color entry, generated palettes and wallpaper
  color extraction are deferred. Saved photos are local assets, not portable preset attachments.
- Root appearance/gesture/applet fields remain canonical where a refactor has no immediate value.
  Move them only when a concrete slice needs it; do not duplicate state in wrapper models.

### Next session entry point

1. Read AGENTS.md, ARCHITECTURE.md, BETA_RELEASE.md and VALIDATION.md, inspect current files and rerun the normal
   build script. Preserve local state and do not assume untracked files are disposable.
2. Validate Live Focus Guard and budget-expiry timing on additional OEM/API versions, including
   screen-off/unlock, recents, notifications and multi-window. Extend `FocusGuardIntegrationTest`,
   which keeps accessibility services active during instrumentation and verifies live budget expiry.
3. Stress Home, Focus Gate and preview at large system font scales, landscape and multi-window.
   Exercise TalkBack, measure touch targets and inspect clipping before extending gesture complexity.
4. Design the menu-to-drag transition using existing FavoriteDragState; add cancellation, lifecycle,
   folder-entry/exit and gesture-conflict regressions before enabling it outside Edit layout.
5. Follow with editable applet/widget board layout, then versioned preset export/import including
   an explicit asset manifest. Community catalogs/execution remain a separate security design.

Do not change the B&W icon algorithm without the owner's approval. No low-end performance claim
is supported by the current Nothing Phone (2) / Pixel_9a emulator coverage.

## Main file inventory for this update

Paths below are relative to `app/src/main/java/org/openminimal/launcher/` unless specified.

| Area | Files |
| --- | --- |
| Typed state and layout metrics | `model/Personalization.kt`, `model/Models.kt`, `model/HomeLayout.kt`, `model/FavoriteSlots.kt` |
| Migration and persistence | `data/ConfigCodec.kt`, `data/PersonalizationCodec.kt`, `data/LauncherRepository.kt`, `LauncherViewModel.kt` |
| Settings navigation and editors | `ui/SettingsScreen.kt`, `ui/SettingsCatalog.kt`, `ui/SettingsDetail.kt`, `ui/PersonalizationControls.kt`, `ui/PresetControls.kt`, `ui/SettingsControls.kt` |
| Shared rendering and interactions | `ui/PersonalizationPreview.kt`, `ui/AppEntry.kt`, `ui/HomeScreen.kt`, `ui/AppDrawer.kt`, `ui/HomeFolders.kt`, `ui/OpenMinimalApp.kt`, `ui/Theme.kt` |
| Wallpaper | `ui/Wallpapers.kt`, `platform/HomeWallpapers.kt` |
| Focus Gate | `model/FocusGate.kt`, `data/FocusRuleCodec.kt`, `ui/DailyLimits.kt`, `FocusGateActivity.kt`, `FocusGuardService.kt`, `platform/FocusGateContract.kt` |
| Localization | `app/src/main/res/values{,-tr}/personalization.xml` and `strings.xml` |
| Tests | `PersonalizationTest`, `HomeLayoutTest`, adapted config/slots tests; `LauncherInteractionTest`, `PlatformIntegrationTest` |
| Version/docs | `app/build.gradle.kts`, `README.md`, `AGENTS.md`, `docs/{PRODUCT,ARCHITECTURE,PERSONALIZATION,VALIDATION}.md` |

This is a logical change inventory, not a Git diff.
