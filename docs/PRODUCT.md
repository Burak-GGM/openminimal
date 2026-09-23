# Open&minimal — agreed product direction

Updated 2026-09-23. Display name: Open&minimal. Project: openminimal.

## Personalization update — 0.2.0-dev

This section supersedes earlier shared-layout and tabbed-settings decisions below.
Home and Drawer have independent typed layout configurations. Minimalism results from combining
individual controls, never a global mode switch. Five built-in presets are starting configurations;
manual differences produce Custom by structural equality. Up to 12 local named presets support
save/apply/rename/delete, preserving private content, Home organization and permission preferences.
Settings uses searchable categories and sections. Live previews reuse actual clock/app renderers.
Home remains one fitted, non-scrolling page. Wallpaper supports theme/photo/solid/gradient, always
scoped to Home; presets keep immutable local photo revisions. EN/TR and offline operation remain
requirements. Weather/provider UI, portable presets and community execution are deferred.
See [PERSONALIZATION.md](PERSONALIZATION.md) for exact completion boundaries and next work,
and [ARCHITECTURE.md](ARCHITECTURE.md) for models, migrations and rendering contracts.

## Purpose
Reduce unintentional phone use and help people complete useful tasks efficiently.
Softened, quiet visual design; capable defaults and progressively disclosed customization.

## Presets
Everyday, Monochrome, Absolute Focus. Presets encompass appearance, home layout,
page order, applet composition, gestures and focus preferences. Appearance-only
application and community-distributed presets are planned. Light/dark is independent.
Keep private content and connection credentials separate from shareable configuration.
Changing a preset must never reset usage or erase notes/tasks. Imported preferences
must not silently enable network access or grant capabilities.

## Pages and applets
Home, full-page applet, and vertically scrolling board with multiple applets and
third-party Android widgets. Applet sizes must be explicitly supported. Widget host
must honor provider size/configuration requirements. Do not promise to restyle widget
internals or control the provider app's networking. Shared presets describe widget
slots; device-bound widget IDs/configuration require rebinding on another device.

Built-ins: quick notes, tasks/today's priority, usage time. Obsidian integration starts
with explicit URI actions; direct Markdown vault access is a separate prototype.
User-authored applets are not required in v0.1, but capability, lifecycle, version and
data boundaries are required now. Scripting sandbox, block editor and community
catalog are later milestones. Avoid inventing a language in the first release.

## Privacy and focus
Ask offline vs online-assisted preference during setup; changeable later. Network
connections require a concrete implementation and explicit capability grants. Initial
prototype has no INTERNET permission. External apps/widgets are outside that boundary.
No telemetry or account requirement. Store data locally; cloud backup disabled.
Usage access is optional. Without Live Focus Guard, limits affect launches and visibility inside
this launcher only. Always offer recovery through Open&minimal settings. Usage totals must be
described as estimates and never fabricated if permission is absent.

## Platform and testing
Kotlin + Compose, EN/TR from day one. Initial engineering baseline Android 8/API 26;
this is provisional pending compatibility tests. Compile/target API 36 for prototype.
Nothing Phone (2) is the physical test device. After successful APK builds install and
open on that authorized device if connected. Low-end hardware still needs validation.
Open-source intent; license to be selected by owner before public distribution.

## Interaction refinements agreed on 2026-09-09

- Swipe up on the home screen opens the app drawer by default; a setting disables it.
- Bottom navigation and its Settings icon have independent visibility switches. A long
  press on empty home space always reveals a Settings shortcut, including when both
  controls are hidden. The app drawer button remains a fallback when swiping is disabled.
- Colors are independent settings. Palette changes do not change pages or applets.
  Absolute Focus defaults to true black in dark mode, with a soft-dark override available.
- Every built-in applet can be enabled/disabled from Settings; third-party widgets have
  group and individual visibility controls. Hidden content and widget bindings are kept.
- Usage permission prompts are shown only when access is actually missing; refresh the
  grant state on resume so granting/revoking permission is reflected without restarting.
- Default-home requests must use an activity-result launch so Android receives the
  calling package. Show the current default state and an explicit system-settings link.
- Existing Pixel_9a Android 16 AVD is the debug fallback when the phone is unavailable.
  Restore missing SDK components without wiping AVD data; do not use inaccessible VMs.

## Milestones
1. Compiling, device-installable foundation: setup, presets, home/app drawer, persisted
   notes/tasks, full-page and board navigation, real usage-access integration, widget host.
2. Focus Gate/OEM hardening, layout editing, preset import/export, deeper customization,
   Obsidian file access, accessibility and performance hardening with measured baselines.
3. Third-party applet API and sandbox; block builder; community presets/applets.

Record implemented behavior and remaining work in README; this document is intent,
not a claim that all features already work.

## Drawer and customization refinements — 2026-09-09

- Swipe-down dismissal at the top of the drawer, tied to the optional swipe-up setting.
  The header always supports dismissal when gestures are enabled; list scrolling and
  the alphabet rail do not conflict with the dismissal gesture.
- Installed ADW/Nova icon packs with original-icon fallback, selected under Appearance.
- Local categories: Games, Health, Finance, Productivity, Social, Media, Maps/travel,
  News and Other. Android supplies coarse hints; long-press app options set overrides.
  Grouping defaults off; the alphabet rail defaults on but is available only when grouping is off.
- Six settings tabs instead of one growing page.
- Widget picker groups by app display name, searches both names, and uses provider
  preview images where supplied. Edit toggles reveal resize/removal controls.
- Favorite reordering uses the explicit Edit layout mode described below; long-press itself
  only opens application actions.

Platform references used for the implementation:
[Android widget metadata](https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo),
[Android application categories](https://developer.android.com/reference/android/content/pm/ApplicationInfo),
[an open appfilter implementation](https://github.com/LawnchairLauncher/lawnicons/blob/develop/app/assets/appfilter.xml).

## Focus, appearance and lifecycle refinements — 2026-09-10

- Daily limits are launcher filters backed by today's usage events. The newer Focus Gate decision
  below supersedes the original single hide/reveal behavior. If access is missing or usage retrieval
  fails, do not hide apps or display invented totals.
- Home wallpaper can follow the active theme color or a user-selected local image. System sync
  changes only `FLAG_SYSTEM`; never write the lock-screen target. Image decoding is bounded and
  the internally stored copy is replaced atomically.
- The first-party Nothing icon package currently exposes neither common launcher theme actions
  nor appfilter mappings. Show this installed-but-unsupported state honestly. Android adaptive
  monochrome icons are a separate supported choice on Android 13 and newer.
- Settings use seven horizontally swipeable categories. Long-press actions use a bottom sheet;
  press motion and haptic feedback are separate preferences. Drawer transitions, the compact
  alphabet animation, group collapsing and widget grouping/monochrome rendering are configurable.
- Clock size, system/monospace typeface, start/center alignment and date visibility belong to
  the preset schema and remain individually adjustable. Large clocks must fit the available width.
- Activity recreation preserves the current launcher route and page. A new HOME intent explicitly
  returns to the first home page; merely restoring an Activity must not do so.


## Explicit layout editing and app actions — 2026-09-10

- Long-pressing a favorite opens only its app-action sheet. Edit layout enters a dedicated
  mode where dragging reorders favorites. Done or Back exits. HOME exits editing; Activity
  recreation preserves it. Disable pager/drawer gestures while editing and provide accessible
  move actions. Persist reorder operations without losing hidden/limited favorites.
- Text, text + icon and icon-only appearances apply to both home and drawer. Icon-only mode
  uses a configurable column grid with 48 dp minimum touch targets. Home categories are optional;
  the drawer retains collapsible categories and search; the alphabet rail requires grouping off.
- Use compact, scrollable action sheets with app info and uninstall icon buttons. System apps
  retain app info access. REQUEST_DELETE_PACKAGES is solely for user-initiated Android uninstall
  confirmation; never silently delete an app.
- Query static/dynamic/pinned shortcuts through LauncherApps, gated by shortcut-host permission.
  Show a default-launcher action when access is missing, and a clear empty state when an app has
  no shortcuts. External shortcut labels belong to their provider. Do not execute arbitrary
  intent text or imitate proprietary launcher actions unsupported by the platform API.

Platform references: [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps),
[uninstall intent](https://developer.android.com/reference/android/content/Intent#ACTION_UNINSTALL_PACKAGE).

## Visual refinement and sparse layouts — 2026-09-10

- Icon-only drawer cells include two-line names; home icons retain the minimal label-free appearance.
- Ungrouped icon home layouts store explicit empty slots. Dropping into a vacancy leaves a hole at
  the source; dropping onto another app swaps those two cells only, without moving intervening apps.
  Hidden/limited favorites reserve their slots. Grouped home is a dense category projection and keeps
  the saved free layout for when grouping is disabled. Text mode retains ordered-list reordering.
- Layout editing uses a compact rounded header and a clear Done action. Bare icons use a very subtle
  wiggle instead of gray tile backgrounds; disabling press animation removes the wiggle. Small dots
  identify empty edit targets and disappear outside editing.
- Native shortcut cards load provider icons with an application-icon fallback. Show two columns and
  four shortcuts initially; expand/collapse explicitly. Cap labels at two lines and keep the sheet scrollable.
- B&W is separate from grayscale monochrome: RGB is binary black/white with original alpha. Both
  apply to application and shortcut icons. Keep the bitmap transformation bounded and cached.
- Alphabet navigation uses a slim centered track, active-letter chip and a few nearby letters.
  Relative dragging advances one letter per 36 dp; no fling or absolute jump on touch-down. Accessible
  previous/next/progress actions remain. Grouping hides the rail and disables its settings with a reason;
  the user's stored rail preference is retained. The letter chosen by the rail must not be overwritten
  by the list clamping its final viewport to the end of the app list.

Design reference: Samsung's distinction between alphabetical scrolling and custom organization
([Apps screen guide](https://www.samsung.com/uk/support/mobile-devices/guide-to-apps-screen-changes-on-the-samsung-galaxy-devices/)).
The compact relative-drag rail is an Open&minimal design choice, not a copied Samsung component.

## Personal home text and folders — 2026-09-10

- The All apps button has its own Navigation switch. Empty-space Home actions always include
  All apps and Settings, so disabling the button and swipe gesture cannot strand the user.
- Appearance includes independent heading/footer visibility and a text editor. Empty custom text
  means the localized default. Keep hidden custom values and retain them when switching presets.
  Functional layout-edit instructions remain visible during editing.
- Folders are a home-only organization layer, separate from automatic categories. Real app IDs
  remain in favorites; stable `folder:` IDs occupy home layout cells. Folder metadata uses schema 8.
  Moving membership between folders, renaming, removing folders and reordering are local atomic writes.
  Removing a folder or deselecting its members returns the apps to Home without uninstalling anything.
- Icon folders show up to four mini icons and a name, opening a labeled icon grid. Text mode shows a
  name, available-app count and disclosure indicator, opening a quiet list; text + icon mode includes
  the mini-icon preview. Folder sheets keep app long-press actions and respect availability/usage limits.
- Updated 2026-09-11: In icon layout-edit mode, dropping onto the center of another app/folder
  immediately creates or joins a folder. No dwell timer or creation form. Drop near a cell edge
  to swap positions; empty-cell movement remains. Folder tiles move but do not nest.
- The app menu can create a one-app folder immediately, also supporting text layouts. The separate
  editor is only for existing folder names/membership; empty-space Home no longer offers a creation form.
- Icon-only folders open in a centered rounded dialog with scale/fade entry and exit; the drawer-motion
  setting disables these animations too. Text/text+icon folders retain their existing bottom sheets.
- B&W and grayscale icon rendering are unchanged. A themed-mask/dark-grayscale alternative has been
  proposed to the owner and requires explicit approval before implementation.
- User folders display in their own section if automatic home categories are enabled. Presets keep
  membership and saved sparse positions; the app drawer remains a complete individual-app list.

Reference: [Niagara's pop-up folders](https://help.niagaralauncher.app/article/115-pop-ups) demonstrate
named collections opening on demand. Open&minimal uses its own text row and shared sheet design;
widget-containing folders and nested navigation are outside this change.

## Single-page Home capacity and wallpaper reset — 2026-09-11

- Home favorites never scroll. Calculate a fixed capacity from usable screen height and the Home
  elements currently visible. Text/text-with-icon lists use a smaller maximum than icon grids;
  requested grid columns determine the grid capacity, and every folder occupies one cell.
- Category headings consume Home space when favorite grouping is enabled. Layout editing exposes
  exactly the available cells and no synthetic row below the fitted grid.
- Disable adding another favorite or creating another Home entry when the current layout is full,
  with localized guidance to remove an item, use a folder or hide a Home element. Removing an
  existing favorite and adding an app to an existing folder remain available.
- Never delete saved favorites when switching to a layout with a lower capacity. Hide the overflow
  from Home while retaining it in local state and the complete app drawer, so a later larger layout
  restores access without data loss.
- Changing from a custom photo to theme color applies the system Home wallpaper before closing the
  picker and surfaces a platform failure in place. Generate a display-shaped solid bitmap to force
  OEM recents pipelines to replace cached photo imagery. Continue targeting `FLAG_SYSTEM` only so
  lock-screen wallpaper remains untouched.

## Focus Gate and Live Focus Guard — 2026-09-23

- **Focus Gate / Odak Kapısı** is the product name for intentional launch friction. Each application
  can have an optional daily budget, an optional every-launch gate, or both.
- When a budget is used, the user chooses between hiding/blocking the app until its rule is removed
  in Screen time, or leaving it visible behind a one-launch Focus Gate. A temporary “show today”
  override remains available only for the gate behavior; hide/block deliberately requires removing
  the rule so recovery is possible but not effortless.
- Challenge types are an editable exact phrase, arithmetic, a 3–5 round memory sequence and a
  10/30/60-second intentional wait. Math and memory have easy/medium/hard levels. All generation and
  evaluation is local.
- Launcher icon and native-shortcut launches are gated without extra platform access. **Live Focus
  Guard / Canlı Odak Koruması** is separately optional. With explicit disclosure and Android
  accessibility consent, it observes foreground package changes so launches through recents,
  notifications or another launcher can be gated, and it rechecks when a daily budget expires while
  the selected app remains open.
- Live guard requests window-change events only and does not retrieve screen content. Daily budgets
  still need Usage Access. This is intentional friction, not device administration: timing depends on
  Android/OEM event delivery, the user can disable the service, and Open&minimal does not force-stop
  another app. Never describe it as tamper-proof parental control.
