package org.openminimal.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.*
import org.openminimal.launcher.ui.OpenMinimalApp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val model: LauncherViewModel by viewModels()
    val widgetHost by lazy { AppWidgetHost(this, 1001) }
    private var pendingWidgetId = -1
    val homeRequest = mutableIntStateOf(0)
    val isDefaultHome = mutableStateOf(false)
    val focusGuardEnabled = mutableStateOf(false)
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Cancellation is a user choice; do not reopen another prompt automatically.
        updateDefaultHome()
    }
    private val binding = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) configureWidget() else discardPendingWidget()
    }
    private val widgetConfiguration = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) finishWidget() else discardPendingWidget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingWidgetId = savedInstanceState?.getInt("pendingWidget", -1) ?: -1
        homeRequest.intValue = savedInstanceState?.getInt("homeRequest", 0) ?: 0
        enableEdgeToEdge()
        setContent { OpenMinimalApp(model, this) }
    }
    override fun onStart() { super.onStart(); runCatching { widgetHost.startListening() } }
    override fun onStop() { widgetHost.stopListening(); super.onStop() }
    override fun onResume() { super.onResume(); model.refresh(); updateDefaultHome(); updateFocusGuard() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequest.intValue++
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("pendingWidget", pendingWidgetId)
        outState.putInt("homeRequest", homeRequest.intValue)
        super.onSaveInstanceState(outState)
    }
    fun openApp(id: String) = safeStart(Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = ComponentName.unflattenFromString(id)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    })
    fun openAppGuarded(id: String, appPackage: String, label: String, state: StoredState, usage: UsageSnapshot) {
        val decision = focusGateDecision(appPackage, state.focusRules, state.dailyLimits, state.limitOverrides,
            usage.durations, LocalDate.now().toEpochDay())
        if (decision is FocusGateDecision.Allow) openApp(id)
        else safeStart(FocusGateContract.intent(this, appPackage, id, label, decision))
    }
    fun openAppInfo(packageName: String) = safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    @Suppress("DEPRECATION")
    fun requestUninstall(packageName: String) = safeStart(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", packageName, null)))
    fun openShortcut(shortcut: android.content.pm.ShortcutInfo) {
        runCatching { getSystemService(android.content.pm.LauncherApps::class.java).startShortcut(shortcut, null, null) }
            .onFailure { showError() }
    }
    fun openShortcutGuarded(shortcut: android.content.pm.ShortcutInfo, appLabel: String, state: StoredState, usage: UsageSnapshot) {
        val decision = focusGateDecision(shortcut.`package`, state.focusRules, state.dailyLimits,
            state.limitOverrides, usage.durations, LocalDate.now().toEpochDay())
        if (decision is FocusGateDecision.Allow) openShortcut(shortcut)
        else safeStart(FocusGateContract.intent(this, shortcut.`package`, shortcut.activity?.flattenToString(), appLabel, decision, shortcut))
    }
    fun requestHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                // RequestRoleActivity uses the calling package attached to a for-result launch.
                runCatching { homeRoleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                    .onFailure { openHomeSettings() }
                return
            }
        }
        openHomeSettings()
    }
    fun openHomeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
            .onFailure { showError() }
    }
    private fun updateDefaultHome() {
        isDefaultHome.value = if (Build.VERSION.SDK_INT >= 29) {
            getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName == packageName
        }
    }
    private fun updateFocusGuard() {
        val expected = ComponentName(this, FocusGuardService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        focusGuardEnabled.value = enabled.split(':').mapNotNull(ComponentName::unflattenFromString).any { it == expected }
    }
    suspend fun applyHomeWallpaper(config: org.openminimal.launcher.model.LauncherConfig, color: Int) {
        try { org.openminimal.launcher.platform.HomeWallpapers(this).apply(config, color) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { Toast.makeText(this, R.string.wallpaper_error, Toast.LENGTH_LONG).show() }
    }
    fun openClock() {
        runCatching { startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
            .recoverCatching { startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS)) }
            .onFailure { showError() }
    }
    fun openSystemScreenTime() {
        runCatching { startActivity(Intent("android.settings.WELLBEING_SETTINGS")) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .onFailure { showError() }
    }
    fun requestUsage() = safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).setData(Uri.parse("package:$packageName")))
    fun openAccessibilitySettings() = safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    fun openPrivacyPolicy(language: String) {
        val document = if (language == "tr") "PRIVACY.tr.md" else "PRIVACY.md"
        safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Burak-GGM/openminimal/blob/main/docs/$document")))
    }
    fun openSourceLicense() = safeStart(Intent(Intent.ACTION_VIEW,
        Uri.parse("https://github.com/Burak-GGM/openminimal/blob/main/LICENSE")))
    fun openObsidian(note: String) {
        val uri = if (note.isBlank()) Uri.parse("obsidian://open") else Uri.Builder()
            .scheme("obsidian").authority("new").appendQueryParameter("content", note).build()
        safeStart(Intent(Intent.ACTION_VIEW, uri))
    }
    fun addWidget(info: AppWidgetProviderInfo) {
        discardPendingWidget()
        pendingWidgetId = widgetHost.allocateAppWidgetId()
        val manager = AppWidgetManager.getInstance(this)
        if (manager.bindAppWidgetIdIfAllowed(pendingWidgetId, info.provider)) configureWidget()
        else runCatching {
            binding.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider))
        }.onFailure { discardPendingWidget(); showError() }
    }
    private fun configureWidget() {
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(pendingWidgetId)
        if (info == null) { discardPendingWidget(); return }
        if (info.configure == null) { finishWidget(); return }
        runCatching {
            widgetConfiguration.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(info.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId),
            )
        }.onFailure { discardPendingWidget(); showError() }
    }
    private fun finishWidget() {
        if (pendingWidgetId < 0) return
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(pendingWidgetId)
        if (info == null) { discardPendingWidget(); return }
        val id = pendingWidgetId
        val height = (info.minHeight / resources.displayMetrics.density).toInt().coerceAtLeast(180)
        lifecycleScope.launch { model.addWidget(id, height).join() }
        pendingWidgetId = -1
    }
    private fun discardPendingWidget() {
        if (pendingWidgetId >= 0) widgetHost.deleteAppWidgetId(pendingWidgetId)
        pendingWidgetId = -1
    }
    fun removeWidget(id: Int) {
        widgetHost.deleteAppWidgetId(id)
        model.removeWidget(id)
    }
    private fun safeStart(intent: Intent) { runCatching { startActivity(intent) }.onFailure { showError() } }
    private fun showError() { Toast.makeText(this, R.string.action_unavailable, Toast.LENGTH_LONG).show() }
}
