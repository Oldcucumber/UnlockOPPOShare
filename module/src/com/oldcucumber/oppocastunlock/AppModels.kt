// SPDX-License-Identifier: GPL-3.0-only
package com.oldcucumber.oppocastunlock

import android.app.Application
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.icu.text.AlphabeticIndex
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

data class AppEntry(val packageName: String, val label: String, val userApp: Boolean,
                    val initial: String, val icon: ImageBitmap? = null)
data class SelectionUiState(val apps: List<AppEntry> = emptyList(), val policy: SelectionPolicy = SelectionPolicy.defaults(),
                            val baseline: Set<String> = emptySet(), val loading: Boolean = true, val error: String? = null)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SelectionStore(application)
    private val mutable = MutableStateFlow(SelectionUiState(policy = store.read(), baseline = store.baseline()))
    val state = mutable.asStateFlow()
    private val writes = Mutex()
    private var loading = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { mutable.update { it.copy(baseline = store.baseline()) } }
    }
    init { application.contentResolver.registerContentObserver(SelectionStore.URI, false, observer); load() }

    fun load() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val apps = withContext(Dispatchers.IO) {
                    val pm = getApplication<Application>().packageManager
                    val index = AlphabeticIndex<String>(Locale.SIMPLIFIED_CHINESE).addLabels(Locale.ENGLISH).buildImmutableIndex()
                    val collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE)
                    pm.getInstalledApplications(0).map { info ->
                        val label = pm.getApplicationLabel(info).toString()
                        val bucket = index.getBucket(index.getBucketIndex(label)).label.uppercase(Locale.ROOT)
                        val initial = bucket.takeIf { it.length == 1 && it[0] in 'A'..'Z' } ?: "#"
                        val icon = runCatching {
                            val drawable = pm.getApplicationIcon(info)
                            val bitmap = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
                            drawable.setBounds(0, 0, 72, 72); drawable.draw(Canvas(bitmap)); bitmap.asImageBitmap()
                        }.getOrNull()
                        AppEntry(info.packageName, label, (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0, initial, icon)
                    }.sortedWith { a, b ->
                        val ai = if (a.initial == "#") "[" else a.initial
                        val bi = if (b.initial == "#") "[" else b.initial
                        ai.compareTo(bi).takeIf { it != 0 } ?: collator.compare(a.label, b.label)
                    }
                }
                mutable.update { it.copy(apps = apps, loading = false, baseline = store.baseline()) }
            } catch (_: Exception) { mutable.update { it.copy(loading = false, error = "应用加载失败") } }
            finally { loading = false }
        }
    }
    fun setVisible(pkg: String, visible: Boolean) { save(mutable.value.policy.withVisible(pkg, visible)) }
    fun setVisible(apps: List<AppEntry>, visible: Boolean) {
        var next = mutable.value.policy
        apps.forEach { next = next.withVisible(it.packageName, visible) }
        save(next)
    }
    fun reset() { save(SelectionPolicy.defaults()) }
    fun clearError() { mutable.update { it.copy(error = null) } }
    private fun save(next: SelectionPolicy) {
        mutable.update { it.copy(policy = next) }
        viewModelScope.launch {
            try { writes.withLock { withContext(Dispatchers.IO) { store.write(next) } } }
            catch (_: Exception) { mutable.update { it.copy(error = "保存失败，请重试") } }
        }
    }
    override fun onCleared() { getApplication<Application>().contentResolver.unregisterContentObserver(observer) }
}
