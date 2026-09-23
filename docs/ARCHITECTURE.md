# OpenMinimal architecture

## State and persistence

`LauncherViewModel` exposes lifecycle-observed DataStore state and independent platform app/usage
flows. Mutations are atomic repository transactions. Compose routes and editor destinations use
`rememberSaveable`; HOME explicitly resets the launcher route, whereas Activity recreation restores
it. A preferences update never restarts an Activity or clears private data.

`LauncherConfig` is the root configuration. Schema 9 adds:

- `HomeScreenConfig`: application layout, clock/date, weather presentation and Home categorization.
- `AppDrawerConfig`: its own `AppLayoutConfig`. Changing Home never changes Drawer implicitly.
- `AppLayoutConfig`: display enum plus reusable `ListLayoutConfig` and `GridLayoutConfig`.
- `WallpaperConfig`: theme/photo/solid/gradient source, colors, overlay and system-sync preference.

Existing color/icon/gesture/applet fields remain in the root model for incremental compatibility.
Do not introduce a second owner for them. New grouped preferences belong in a typed submodel.
Read-only Home adapters (`icons`, `clockSize`, etc.) bridge older folder/rendering code; they are
not writable global layout state. Drawer code must explicitly use `config.drawer.apps`.

`ConfigCodec` reads both schema 8 flat fields and schema 9 nested objects. Missing nested fields
inherit the migrated legacy defaults; valid nested values take precedence. Unknown enum values
fall back safely and dimensions are bounded. New writes contain canonical nested objects. Custom
presets use the same codec, so migrations apply equally to live and saved configurations.

Notes, tasks, favorites, widget host IDs, daily limits, daily overrides and Focus Gate rules remain
separate DataStore keys. Custom presets use `custom_presets_v1`; no config migration touches private
content or focus rules. `focus_rules_v1` uses a version-1 envelope containing a package-keyed rule
map. Its reader also accepts the pre-release direct-map format, bounds all text/numeric values and
falls back safely for future enum values. Daily limits keep their existing key so upgrades preserve
them. Favorite IDs stay real application IDs; Home folders and sparse slots project them into Home entries.

## Rendering and navigation

`OpenMinimalApp.kt` owns theme, onboarding and launcher routes. `HomeScreen.kt` owns the Home
projection and existing drag/drop integration. `AppDrawer.kt` owns search, categories and alphabet
navigation. `AppEntry.kt` supplies interactions; `AppEntryContent` in `PersonalizationPreview.kt`
is its visual renderer. `HomeClock` and `WallpaperSurface` are shared visual components too.

Preview uses those same components at a smaller density, with real installed applications and
current time. It supplies no launch/drag callbacks and executes no system-wallpaper effect.
List/clock editor state updates locally on every slider event; only release persists the relevant
submodel. The renderer therefore updates while the finger is still down without writing DataStore
on every frame. Toggle/chip changes commit immediately. Settings persist the section destination.

`ListLayoutConfig.alignment` positions the complete list block within Home. It never changes label
alignment inside a row: list rows always flow start-to-end, with the configured fixed icon/text gap,
so every icon and label begins on a consistent column even when the block itself is centered or
placed at the end.

Settings navigation is root categories → sections → controls. `SettingsCatalog.kt` is the single
localized section/search index, with stable enum identifiers and localized keywords. No growing
horizontal tab strip. Add a catalog section and its detail handler for a new settings area.

Home measures its viewport, clock/header and footer. `HomeLayout.kt` accounts for complete rows,
category headings and scaled text; grid columns also respect minimum target width. List mode can
align/start/center/end, use a narrower width and position its list at top/center/bottom. Home stays
one non-scrolling page. Runtime measured capacity is shared through the ViewModel with drawer
favorite actions; it is not stored in a preset. Layout changes never delete overflow favorites.

## Presets

Built-ins are starting configurations, not alternate application modes. `presetStatus` compares
typed snapshots using Kotlin structural equality, never localized names, JSON strings or hashes.
The selected built-in ID/custom preset ID is metadata; mismatched settings yield `Custom`, and
returning to the snapshot yields the saved identity again. A deleted selected preset yields Custom
without changing the current layout.

`presetSnapshot` defines the exclusion boundary. It strips onboarding, advanced-setup and network
preference, locale, folder/slot IDs, manual app groups and transient collapsed categories. System
wallpaper consent is excluded. `applySnapshot` restores these fields from the live configuration.
Notes/tasks/usage/widget bindings are outside configuration entirely. Appearance, Home/Drawer,
clock, wallpaper source, applet visibility and gestures are included.

Up to 12 local `CustomPreset` records have stable UUIDs, user names, record version 1 and a schema-9
configuration object. Unknown future record versions are not executed/applied. Rename affects only
the name; delete affects only the record. Saving, applying and deleting are repository operations.
Built-in application preserves existing personalized heading/footer text; a built-in plus such
personal copy can correctly show Custom because it differs from the pristine built-in snapshot.

## Wallpaper assets and platform boundary

New photos are bounded during decode and stored atomically as `home-wallpaper-<revision>.jpg`.
Local presets retain that immutable revision. Legacy photos still resolve through `home-wallpaper.jpg`.
After successful selection, pruning keeps the active revision and every saved preset revision.
File export/import and portable asset manifests are deferred; local photo IDs are not shareable URIs.

`HomeWallpapers.apply` writes only `WallpaperManager.FLAG_SYSTEM`. Source parameters and the current
system wallpaper ID are checked before skipping redundant work. Theme/solid/gradient sources use
display-shaped bitmaps. Picker changes apply before dismissal and surface failures. The overlay is
launcher-only readability treatment; it is not painted onto the user's system photo.

## Interaction and extension boundaries

Existing explicit Edit layout keeps Home/app-action long presses from competing. Its keyed drag
state supports gaps, edge swaps and center folder drops. Folder contents can be reordered in their
editor with drag or accessible move buttons; app actions can move an app out of its folder. An
ordinary grid long-press-to-drag session spanning menu and Home is separate remaining work.

Weather presentation has a typed disabled-by-default contract. No provider, fabricated values,
location permission or networking was added. Local Android category hints/manual overrides remain
the optional smart-organization foundation. Community execution, ranked recents, arbitrary widget
boards and exported presets require separate bounded designs, not additional launcher booleans.

## Focus Gate and live enforcement

`AppFocusRule` is per-package private state outside visual presets. It contains the action after a
daily budget is reached and an optional every-launch challenge. `FocusChallenge` is a typed text,
math, memory or wait challenge with bounded difficulty/duration fields. `focusGateDecision` is the
single pure policy function used by Home, Drawer, direct launches and the live service. A reached,
non-overridden budget takes priority over every-launch friction. A hide rule projects the app out of
Home and Drawer; a gate rule keeps it visible and requires its configured challenge.

Launcher-originated app and native-shortcut launches pass through `FocusGateContract` and
`FocusGateActivity`. A completed challenge grants a short, process-local, one-shot bypass so the
optional service does not immediately challenge the same successful launch again. This is not a
persistent permission or a daily override.

`FocusGuardService` is an opt-in `AccessibilityService` limited to window-state changes with
`canRetrieveWindowContent=false`. It observes only the foreground package, evaluates external
launches and schedules a usage recheck for the remaining daily budget while an app stays open.
Enabling it requires a prominent in-app disclosure followed by Android's own accessibility settings.
The service never reads view trees, screen text, taps or passwords. Daily accounting still requires
separate Usage Access; every-launch gates do not.

Android/OEM scheduling and usage-event delivery are not hard real-time. The service can return a
guarded app to Home or cover it with the gate, but it cannot force-stop another package, prevent the
user disabling the service, or provide tamper-proof parental control. Screen-off/keyguard states do
not open UI. Keep these boundaries explicit in UI and documentation.
