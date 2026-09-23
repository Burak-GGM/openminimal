package org.openminimal.launcher

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.openminimal.launcher.data.LauncherRepository
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.*
import java.util.UUID

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LauncherRepository(app)
    private val device = DeviceServices(app)
    private val _error = MutableStateFlow(false)
    val error = _error.asStateFlow()
    val state = repository.state.retryWhen { cause, _ ->
        if (cause is CancellationException) throw cause
        _error.value = true
        delay(2000)
        true
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()
    private val _iconPacks = MutableStateFlow<List<IconPack>>(emptyList())
    val iconPacks = _iconPacks.asStateFlow()
    private val _homeCapacity = MutableStateFlow<Int?>(null)
    val homeCapacity = _homeCapacity.asStateFlow()
    fun homeMeasured(capacity: Int) { _homeCapacity.value = capacity }
    init {
        viewModelScope.launch {
            state.filterNotNull().map { it.config.iconPack to it.config.themedIcons }.distinctUntilChanged().collectLatest { pack ->
                try { _apps.value = device.apps(pack.first, pack.second) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { _error.value = true }
            }
        }
    }
    private val _usage = MutableStateFlow(UsageSnapshot())
    val usage = _usage.asStateFlow()
    private var refreshJob: Job? = null
    private var usageJob: Job? = null

    fun refresh() {
        refreshUsage()
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            runCatching {
                _iconPacks.value = device.iconPacks()
                val pack = state.value?.config.let { it?.iconPack.orEmpty() to (it?.themedIcons ?: false) }
                val refreshed = device.apps(pack.first, pack.second)
                if (pack == state.value?.config.let { it?.iconPack.orEmpty() to (it?.themedIcons ?: false) }) _apps.value = refreshed
            }
                .onFailure { _error.value = true }
        }
    }
    fun refreshUsage() {
        usageJob?.cancel()
        val allowed = device.hasUsageAccess()
        _usage.value = if (allowed) _usage.value.copy(granted = true, checked = true) else UsageSnapshot(checked = true)
        usageJob = viewModelScope.launch { _usage.value = device.usage() }
    }
    fun configure(change: (LauncherConfig) -> LauncherConfig) = write { repository.configure(change) }
    suspend fun commitConfiguration(change: (LauncherConfig) -> LauncherConfig): Boolean = try {
        repository.configure(change); true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { _error.value = true; false }
    fun savePreset(name: String) = write { repository.savePreset(name) }
    fun renamePreset(id: String, name: String) = write { repository.renamePreset(id, name) }
    fun deletePreset(id: String) = write { repository.deletePreset(id) }
    fun applyCustomPreset(id: String) = write { repository.applyCustomPreset(id) }
    fun note(text: String) = write { repository.note(text) }
    fun reorderFavorites(ids: List<String>) = write { repository.reorderFavorites(ids) }
    fun saveFavoriteLayout(keys: List<String>) = write {
        val slots = keys.map { if (isEmptySlot(it)) "" else it }
        repository.reorderFavorites(slots.filter { it.isNotEmpty() }, slots)
    }
    fun mergeIntoFolder(source: String, target: String, name: String, suggestions: List<String>) = write { repository.mergeIntoFolder(source, target, name, suggestions) }
    fun saveFolder(folder: HomeFolder) = write { repository.saveFolder(folder) }
    fun removeFolder(id: String) = write { repository.removeFolder(id) }
    fun removeFromFolder(folderId: String, appId: String) = write { repository.removeFromFolder(folderId, appId) }
    fun favorite(id: String, capacity: Int = Int.MAX_VALUE) = write { repository.toggleFavorite(id, capacity) }
    fun addTask(text: String) {
        if (text.isBlank()) return
        write { repository.tasks { it + TaskItem(UUID.randomUUID().toString(), text.trim()) } }
    }
    fun toggleTask(id: String) = write { repository.tasks { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } } }
    fun removeTask(id: String) = write { repository.tasks { list -> list.filterNot { it.id == id } } }
    fun addWidget(id: Int, height: Int) = write { repository.widgets { it + WidgetSlot(id, height.coerceIn(100, 500)) } }
    fun removeWidget(id: Int) = write { repository.widgets { list -> list.filterNot { it.widgetId == id } } }
    fun resizeWidget(id: Int, height: Int) = write { repository.widgets { list -> list.map { if (it.widgetId == id) it.copy(heightDp = height.coerceIn(100, 500)) else it } } }
    fun toggleWidget(id: Int, enabled: Boolean) = write { repository.widgets { list -> list.map { if (it.widgetId == id) it.copy(enabled = enabled) else it } } }
    fun setDailyLimit(packageName: String, minutes: Int?) = write { repository.setDailyLimit(packageName, minutes) }
    fun overrideDailyLimit(packageName: String, epochDay: Long) = write { repository.overrideDailyLimit(packageName, epochDay) }
    fun setFocusPolicy(packageName: String, minutes: Int?, rule: AppFocusRule?) = write { repository.setFocusPolicy(packageName, minutes, rule) }
    fun dismissError() { _error.value = false }
    private fun write(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (error: Exception) {
            Log.e("LauncherViewModel", "Could not persist launcher state", error)
            _error.value = true
        }
    }
}
