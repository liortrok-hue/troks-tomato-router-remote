package com.troks.tomatorouters.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.troks.tomatorouters.data.LogLevel
import com.troks.tomatorouters.data.LogLine
import com.troks.tomatorouters.data.RouterApi
import com.troks.tomatorouters.data.syslog

/** Full-screen viewer for the router's syslog (Status > Logs), newest first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsDialog(api: RouterApi, onClose: () -> Unit) {
    val s = LocalStrings.current
    val m = s.mon
    val state = rememberLoadState { api.syslog(1000) ?: LOGS_OFF }
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableIntStateOf(-1) } // -1 all, else LogLevel.ordinal (that level only)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(m.logs) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, s.cancel) } },
                    actions = { IconButton(onClick = state::reload) { Icon(Icons.Outlined.Refresh, null) } },
                )
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(m.search) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, null) }
                        },
                        singleLine = true,
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // the router's own levels (Status > Logs); each shows how many lines it has
                    val counts = remember(state.data) {
                        state.data.orEmpty().takeIf { it !== LOGS_OFF }.orEmpty().groupingBy { it.level }.eachCount()
                    }
                    val total = state.data.takeIf { it !== LOGS_OFF }?.size
                    DropdownField(
                        label = m.levelLabel,
                        options = listOf("-1" to m.levelAll.withCount(total)) +
                            LogLevel.entries.map { lv ->
                                lv.ordinal.toString() to m.levelNames[lv.ordinal].withCount(counts[lv] ?: 0)
                            },
                        selected = level.toString(),
                        onSelect = { level = it.toInt() },
                    )
                }

                val all = state.data
                val q = query.trim()
                val listState = rememberLazyListState()
                // a new filter or search starts from the newest line
                LaunchedEffect(q, level) { listState.scrollToItem(0) }
                val lines = remember(all, q, level) {
                    all.orEmpty().asReversed().filter { l ->
                        (level < 0 || l.level.ordinal == level) &&
                            (q.isEmpty() || l.message.contains(q, true) || l.process.contains(q, true))
                    }
                }

                PullToRefreshBox(
                    isRefreshing = state.loading && state.data != null,
                    onRefresh = state::reload,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        state.error != null -> Box(Modifier.padding(16.dp)) { ErrorBox(state.error!!, state::reload) }
                        all == null -> LoadingBox()
                        all === LOGS_OFF -> Text(
                            m.logsOff,
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> Box(Modifier.fillMaxSize()) {
                          LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                            item {
                                Text(
                                    if (lines.isEmpty()) m.noMatches else m.logCount(lines.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            itemsIndexed(lines) { i, l ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                LogRow(l)
                            }
                          }
                          ListScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

private fun String.withCount(n: Int?) = if (n == null) this else "$this ($n)"

@Composable
private fun LogRow(l: LogLine) {
    val cs = MaterialTheme.colorScheme
    val color = levelColor(l.level)
    // log text is English from the router: always left to right
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = color.copy(alpha = 0.15f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(color, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(l.level.name.lowercase().let { if (it == "warning") "warn" else it }, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    l.process.ifBlank { l.facility },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(l.date, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            SelectionContainer {
                Text(l.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun levelColor(l: LogLevel): Color {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    return when {
        l <= LogLevel.ERR -> cs.error
        l == LogLevel.WARNING -> if (dark) Color(0xFFFFCA28) else Color(0xFFB26A00)
        l == LogLevel.NOTICE -> cs.primary
        l == LogLevel.DEBUG -> cs.outline
        else -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    }
}

/** Marker for "logging is turned off on the router". */
private val LOGS_OFF = ArrayList<LogLine>(0)
