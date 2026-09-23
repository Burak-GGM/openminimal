# Open&minimal

Work in this directory. Product decisions are recorded in docs/PRODUCT.md.

- Native Android: Kotlin, Jetpack Compose; application ID org.openminimal.launcher.
- All user-visible strings must have English and Turkish resources. Avoid hardcoded UI copy.
- Local-first. Never add analytics, advertising, accounts, network SDKs, or INTERNET permission without an explicit product need.
- Preserve notes, tasks, usage history and credentials when switching presets. Presets must not grant permissions or reset daily usage.
- Built-in applets use common contracts and design components. Community code execution and a community catalog are deferred.
- Support both full-page applets and boards containing multiple applets and Android widgets.
- Build and test before reporting completion. After each successful installable debug build, if the authorized Nothing Phone (2) is connected via ADB, install and launch it. Never uninstall or clear app data as an automatic workaround.
- Use scripts/build-and-run.sh for normal debug builds. Prefer the authorized Nothing Phone (2); if USB is unavailable, use the existing Pixel_9a Android 16 emulator. Start with scripts/start-emulator.sh (add -no-window for headless tests). Do not wipe existing AVD data. Instrumentation tests must target the emulator with ANDROID_SERIAL, not the owner's phone. If ADB is unavailable, report it honestly. Never claim low-end performance based on the Nothing Phone (2) or this emulator.
- No autonomous subagents unless the user requests them.
- Configuration schema/migrations and preset boundaries are documented in docs/ARCHITECTURE.md. Use canonical typed Home/Drawer models; never restore a shared writable layout flag.
- Live previews must reuse Home/Drawer visual renderers. Register new settings in SettingsCatalog with English/Turkish search terms. Preserve snapshot exclusions for privacy, content and device consent.
- Track completed and deferred personalization work in docs/PERSONALIZATION.md and actual checks in docs/VALIDATION.md. The alternative B&W icon algorithm requires the owner's approval.
- Do not publish, push, or select a source license without discussion. The intent is open source; license selection is pending.
