// SPDX-License-Identifier: GPL-3.0-only
package com.oldcucumber.oppocastunlock

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model: AppSelectionViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            CastTheme {
                val state by model.state.collectAsStateWithLifecycle()
                SelectionScreen(state, model::setVisible, model::setVisible, model::reset, model::clearError)
            }
        }
    }
    override fun onResume() { super.onResume(); model.load() }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable fun CastTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) darkColorScheme() else expressiveLightColorScheme()
    MaterialExpressiveTheme(colorScheme = colors, content = content)
}

enum class AppFilter(val title: String) { ALL("全部"), SHOWN("已显示"), HIDDEN("已隐藏") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable fun SelectionScreen(
    state: SelectionUiState,
    onToggle: (String, Boolean) -> Unit,
    onBatch: (List<AppEntry>, Boolean) -> Unit,
    onReset: () -> Unit,
    onErrorShown: () -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var systemApps by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var menu by remember { mutableStateOf(false) }
    val visible: (AppEntry) -> Boolean = { state.policy.isVisible(it.packageName, it.userApp, state.baseline) }
    val matches = remember(state.apps, state.policy, state.baseline, query, systemApps, filter) {
        state.apps.filter { app ->
            app.userApp != systemApps &&
                (query.isBlank() || app.label.contains(query.trim(), true) || app.packageName.contains(query.trim(), true) || app.initial.equals(query.trim(), true)) &&
                when (filter) { AppFilter.ALL -> true; AppFilter.SHOWN -> visible(app); AppFilter.HIDDEN -> !visible(app) }
        }
    }
    val groups = matches.groupBy { it.initial }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val headerIndexes = remember(groups) {
        var index = 0
        groups.mapValues { (_, apps) -> val header = index; index += apps.size + 1; header }
    }
    LaunchedEffect(query, filter, systemApps) { listState.scrollToItem(0) }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it); onErrorShown() } }
    val count = state.apps.count { visible(it) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("投屏应用", fontWeight = FontWeight.Bold) },
                subtitle = { Text("选择在电脑上显示的应用") },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("全选") }, onClick = { menu = false; onBatch(matches, true) })
                            DropdownMenuItem(text = { Text("全不选") }, onClick = { menu = false; onBatch(matches, false) })
                            DropdownMenuItem(text = { Text("恢复默认") }, onClick = { menu = false; onReset() })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("$count", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("个应用已显示", style = MaterialTheme.typography.titleMedium)
                    Text("更改自动保存", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .72f))
                }
            }
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp).testTag("search"),
                placeholder = { Text("搜索应用") }, shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "清除搜索") } },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter.entries.forEach { value ->
                    FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value.title) })
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { systemApps = !systemApps }) { Text(if (systemApps) "系统应用" else "用户应用") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    matches.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (query.isBlank()) "暂无应用" else "没有找到应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 44.dp).testTag("app-list"),
                        contentPadding = PaddingValues(bottom = 28.dp)) {
                        groups.forEach { (letter, apps) ->
                            item(key = "header-$letter", contentType = "header") {
                                Text(letter, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            items(apps, key = { it.packageName }, contentType = { "app" }) { app ->
                                AppRow(app, visible(app), { onToggle(app.packageName, it) })
                                Spacer(Modifier.height(3.dp))
                            }
                        }
                    }
                }
                if (matches.isNotEmpty()) AlphabetRail(groups.keys, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) { letter ->
                    val target = headerIndexes[letter] ?: headerIndexes.entries.firstOrNull { it.key >= letter }?.value
                    target?.let { scope.launch { listState.scrollToItem(it) } }
                }
            }
        }
    }
}

@Composable private fun AppRow(app: AppEntry, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
        .background(if (checked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f) else MaterialTheme.colorScheme.surfaceContainerLow)
        .toggleable(checked, role = Role.Switch, onValueChange = onToggle).testTag("app:${app.packageName}")
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (app.icon != null) Image(app.icon, null, Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)))
        else Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center) { Text(app.initial, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onCheckedChange = null,
            thumbContent = if (checked) { { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) } } else null)
    }
}

@Composable private fun AlphabetRail(active: Set<String>, modifier: Modifier, onLetter: (String) -> Unit) {
    val letters = remember { ('A'..'Z').map { it.toString() } + "#" }
    var height by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val currentCallback by rememberUpdatedState(onLetter)
    fun select(y: Float) {
        val letter = letters[(y / height * letters.size).toInt().coerceIn(letters.indices)]
        if (dragging != letter) {
            dragging = letter; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); currentCallback(letter)
        }
    }
    Box(modifier) {
        Column(Modifier.width(32.dp).fillMaxHeight(.95f).heightIn(max = 460.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).onSizeChanged { height = it.height.coerceAtLeast(1) }
            .testTag("alphabet-rail").pointerInput(Unit) {
                detectDragGestures(onDragStart = { select(it.y) }, onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null }, onDrag = { change, _ -> change.consume(); select(change.position.y) })
            }, horizontalAlignment = Alignment.CenterHorizontally) {
            letters.forEach { letter ->
                Box(Modifier.weight(1f).fillMaxWidth().testTag("letter-$letter").clickable { currentCallback(letter) }, contentAlignment = Alignment.Center) {
                    Text(letter, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = if (letter in active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .25f))
                }
            }
        }
        AnimatedVisibility(dragging != null, Modifier.align(Alignment.CenterEnd).offset(x = (-44).dp)) {
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) { Text(dragging ?: "", style = MaterialTheme.typography.headlineLarge) }
        }
    }
}
