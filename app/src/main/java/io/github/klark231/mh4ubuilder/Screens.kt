@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.klark231.mh4ubuilder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.util.Date

// helpers

class Pickers(
    val update: () -> Unit, val game: () -> Unit, val mods: () -> Unit, val output: () -> Unit,
    val allFiles: () -> Unit, val notifications: () -> Unit,
)

@Composable
fun rememberPickers(vm: AppViewModel): Pickers {
    val ctx = LocalContext.current
    val update = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.setUpdate(u) }
    val game = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) vm.setGame(u) }
    val mods = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) vm.setMods(u) }
    val out = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) vm.setOutput(u) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    return remember {
        Pickers(
            { update.launch(arrayOf("*/*")) }, { game.launch(arrayOf("*/*")) },
            { mods.launch(null) }, { out.launch(null) },
            {
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}")),
                        )
                    } catch (e: Exception) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    legacy.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                }
            },
            { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}

@Composable
fun hasNotifications(): Boolean {
    val ctx = LocalContext.current
    return Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

fun fmtSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    else -> "%d KB".format(b / 1024)
}

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun stepText(st: BuildState): String {
    val step = if (st.step != 0) stringResource(st.step) else ""
    return if (st.total > 0) "$step ${st.done}/${st.total}" else step
}

@Composable
fun BuilderApp(vm: AppViewModel) {
    val pickers = rememberPickers(vm)
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }
    val st by Builder.state.collectAsStateWithLifecycle()
    LaunchedEffect(st.phase) { if (st.phase != Phase.RUNNING) vm.refresh() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (!vm.setupDone) {
            Wizard(vm, pickers, snack)
        } else when (vm.screen) {
            Screen.HOME -> HomeScreen(vm, pickers, snack, st)
            Screen.SETTINGS -> {
                BackHandler { vm.screen = Screen.HOME }
                SettingsScreen(vm, pickers, snack)
            }
        }
        if (vm.showBuild && st.phase != Phase.IDLE) BuildDialog(vm, st)
        vm.logText?.let { LogDialog(it) { vm.logText = null } }
    }
}

@Composable
fun PickerCard(title: String, hint: String, value: String?, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    value ?: hint, style = MaterialTheme.typography.bodyMedium,
                    color = if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = content)
    }
}

@Composable
fun LogBox(text: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(text.length) { scroll.scrollTo(scroll.maxValue) }
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .verticalScroll(scroll).padding(10.dp),
    ) {
        SelectionContainer {
            Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

// setup wizard

@Composable
fun StepPage(icon: ImageVector, title: String, body: String, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
fun Wizard(vm: AppViewModel, pickers: Pickers, snack: SnackbarHostState) {
    var step by remember { mutableIntStateOf(0) }
    val last = 4
    val canNext = when (step) { 2 -> vm.updateName != null; 4 -> vm.outputName != null; else -> true }
    val notifOk = hasNotifications()
    BackHandler(enabled = step > 0) { step-- }
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.back)) } else Spacer(Modifier.size(1.dp))
                Button(onClick = { if (step < last) step++ else vm.finishSetup() }, enabled = canNext) {
                    Text(stringResource(if (step == 0) R.string.get_started else if (step < last) R.string.next else R.string.finish))
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 24.dp).verticalScroll(rememberScrollState())) {
            LinearProgressIndicator(progress = { (step + 1) / (last + 1f) }, Modifier.fillMaxWidth().padding(top = 16.dp))
            AnimatedContent(step, label = "step") { s ->
                when (s) {
                    0 -> StepPage(
                        Icons.Default.SportsEsports, stringResource(R.string.welcome_title), stringResource(R.string.welcome_body),
                    )
                    1 -> StepPage(
                        Icons.Default.SdStorage, stringResource(R.string.perm_title), stringResource(R.string.perm_body),
                    ) {
                        PickerCard(
                            stringResource(R.string.all_files), stringResource(R.string.recommended), if (vm.allFiles) stringResource(R.string.allowed) else null,
                            stringResource(if (vm.allFiles) R.string.open else R.string.allow), pickers.allFiles,
                        )
                        if (Build.VERSION.SDK_INT >= 33) PickerCard(
                            stringResource(R.string.notifications), stringResource(R.string.optional), if (notifOk) stringResource(R.string.allowed) else null,
                            stringResource(if (notifOk) R.string.allowed else R.string.allow), pickers.notifications,
                        )
                    }
                    2 -> StepPage(
                        Icons.Default.Inventory2, stringResource(R.string.dump_title), stringResource(R.string.dump_body),
                    ) {
                        PickerCard(stringResource(R.string.update), stringResource(R.string.required), vm.updateName, stringResource(if (vm.updateName == null) R.string.select else R.string.change), pickers.update)
                        PickerCard(stringResource(R.string.game), stringResource(R.string.optional), vm.gameName, stringResource(if (vm.gameName == null) R.string.select else R.string.change), pickers.game)
                    }
                    3 -> StepPage(
                        Icons.Default.Extension, stringResource(R.string.mods_title), stringResource(R.string.mods_body),
                    ) {
                        PickerCard(
                            stringResource(R.string.file_mods), stringResource(R.string.optional), vm.modsName?.let { n -> vm.modsInfo?.let { "$n\n$it" } ?: n },
                            stringResource(if (vm.modsName == null) R.string.select else R.string.change), pickers.mods,
                        )
                    }
                    else -> StepPage(
                        Icons.Default.FolderOpen, stringResource(R.string.file_output), stringResource(R.string.output_body),
                    ) {
                        PickerCard(stringResource(R.string.file_output), stringResource(R.string.required), vm.outputName, stringResource(if (vm.outputName == null) R.string.select else R.string.change), pickers.output)
                    }
                }
            }
        }
    }
}

// home

@Composable
private fun SectionLabel(t: String) {
    Text(
        t, Modifier.padding(start = 4.dp, top = 12.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun HomeScreen(vm: AppViewModel, pickers: Pickers, snack: SnackbarHostState, st: BuildState) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val running = st.phase == Phase.RUNNING
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scroll,
                actions = { IconButton(onClick = { vm.screen = Screen.SETTINGS }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.build() }, icon = { Icon(Icons.Default.Build, null) },
                text = { Text(stringResource(if (running) R.string.building else R.string.build)) },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatusCard(vm, st) }
            item { SectionLabel(stringResource(R.string.section_files)) }
            item { FilesCard(vm, pickers) }
            item { SectionLabel(stringResource(R.string.section_build)) }
            item { OptionsCard(vm) }
        }
    }
}

@Composable
fun StatusCard(vm: AppViewModel, st: BuildState) {
    val cs = MaterialTheme.colorScheme
    val running = st.phase == Phase.RUNNING
    val container = when {
        running -> cs.primaryContainer
        vm.lastAt > 0 && !vm.lastOk -> cs.errorContainer
        else -> cs.surfaceContainerLow
    }
    val onContainer = when {
        running -> cs.onPrimaryContainer
        vm.lastAt > 0 && !vm.lastOk -> cs.onErrorContainer
        else -> cs.onSurface
    }
    Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    when {
                        running -> Icons.Default.Memory
                        vm.lastAt == 0L -> Icons.Default.Build
                        vm.lastOk -> Icons.Default.CheckCircle
                        else -> Icons.Default.Error
                    },
                    null, Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            running -> stringResource(R.string.building)
                            vm.lastAt == 0L -> stringResource(R.string.status_not_built)
                            vm.lastOk -> stringResource(R.string.status_ready)
                            else -> stringResource(R.string.status_failed)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            running -> stepText(st)
                            vm.lastAt == 0L -> stringResource(R.string.status_hint)
                            else -> DateFormat.getMediumDateFormat(LocalContext.current).format(Date(vm.lastAt)) + " " +
                                DateFormat.getTimeFormat(LocalContext.current).format(Date(vm.lastAt)) +
                                (if (vm.lastOpt.isNotEmpty()) " · ${vm.lastOpt}" else "") +
                                (if (vm.lastMods) " · " + stringResource(R.string.with_mods) else "")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (running) {
                if (st.total > 0) LinearProgressIndicator(progress = { st.done / st.total.toFloat() }, Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("tools ${BuildConfig.TOOLS_VERSION}", cs.secondaryContainer, cs.onSecondaryContainer)
                Pill(vm.libId?.let { "id $it" } ?: stringResource(R.string.unpacking), cs.tertiaryContainer, cs.onTertiaryContainer)
                Spacer(Modifier.weight(1f))
                if (running) TextButton(onClick = { vm.showBuild = true }) { Text(stringResource(R.string.show)) }
                else if (vm.lastAt > 0) TextButton(onClick = { vm.openLastLog() }) { Text(stringResource(R.string.view_log)) }
            }
        }
    }
}

@Composable
private fun FileRow(
    icon: ImageVector, title: String, value: String?, hint: String, onClick: () -> Unit, onClear: (() -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                value ?: hint, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (value != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            if (value != null && onClear != null) IconButton(onClick = onClear) { Icon(Icons.Default.Close, stringResource(R.string.remove)) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun FilesCard(vm: AppViewModel, pickers: Pickers) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            FileRow(Icons.Default.Inventory2, stringResource(R.string.file_update), vm.updateName, stringResource(R.string.hint_update), pickers.update)
            FileRow(Icons.Default.SportsEsports, stringResource(R.string.file_game), vm.gameName, stringResource(R.string.hint_game), pickers.game, vm::clearGame)
            FileRow(
                Icons.Default.Extension, stringResource(R.string.file_mods), vm.modsName?.let { n -> vm.modsInfo?.let { "$n · $it" } ?: "$n · " + stringResource(R.string.no_code_mods) },
                stringResource(R.string.optional), pickers.mods, vm::clearMods,
            )
            FileRow(Icons.Default.FolderOpen, stringResource(R.string.file_output), vm.outputName, stringResource(R.string.hint_output), pickers.output)
        }
    }
}

@Composable
fun OptionsCard(vm: AppViewModel) {
    val opts = listOf("O0", "O1", "O2")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.opt_title), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                opts.forEachIndexed { i, o ->
                    SegmentedButton(
                        selected = vm.opt == o, onClick = { vm.changeOpt(o) },
                        shape = SegmentedButtonDefaults.itemShape(i, opts.size),
                    ) { Text(o) }
                }
            }
            Text(
                stringResource(
                    when (vm.opt) {
                        "O0" -> R.string.opt_o0
                        "O2" -> R.string.opt_o2
                        else -> R.string.recommended
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.use_mods), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (vm.modsName == null) stringResource(R.string.no_mods_folder) else vm.modsInfo ?: stringResource(R.string.no_code_mods_in_folder),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(vm.useMods && vm.modsName != null, { vm.changeUseMods(it) }, enabled = vm.modsName != null)
            }
            HorizontalDivider()
            Text(stringResource(R.string.jobs, vm.jobs), style = MaterialTheme.typography.titleMedium)
            if (vm.maxJobs > 1) Slider(
                value = vm.jobs.toFloat(), onValueChange = { vm.changeJobs(it.toInt().coerceIn(1, vm.maxJobs)) },
                valueRange = 1f..vm.maxJobs.toFloat(), steps = (vm.maxJobs - 2).coerceAtLeast(0),
            )
            Text(
                stringResource(R.string.jobs_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// build dialog

@Composable
fun BuildDialog(vm: AppViewModel, st: BuildState) {
    val running = st.phase == Phase.RUNNING
    var showLog by remember { mutableStateOf(st.phase == Phase.FAILED) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(st.phase) { if (st.phase == Phase.FAILED) showLog = true }
    val clip = LocalClipboardManager.current
    val elapsed = (if (running) now else st.endedAt) - st.startedAt
    val close = { Builder.dismiss(); vm.showBuild = false }

    AlertDialog(
        onDismissRequest = { },
        icon = {
            Icon(
                when (st.phase) {
                    Phase.DONE -> Icons.Default.CheckCircle
                    Phase.RUNNING -> Icons.Default.Memory
                    else -> Icons.Default.Error
                },
                null,
            )
        },
        title = {
            Text(
                stringResource(
                    when (st.phase) {
                        Phase.RUNNING -> R.string.building
                        Phase.DONE -> R.string.dlg_done
                        Phase.CANCELLED -> R.string.dlg_cancelled
                        else -> R.string.dlg_failed
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (st.phase) {
                    Phase.RUNNING -> {
                        Text(if (st.step != 0) stringResource(st.step) else "", fontWeight = FontWeight.SemiBold)
                        if (st.total > 0) LinearProgressIndicator(progress = { st.done / st.total.toFloat() }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            (if (st.total > 0) "${st.done} / ${st.total}  ·  " else "") + fmtDuration(elapsed),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Phase.DONE -> Text(stringResource(R.string.dlg_saved, st.message ?: "", fmtDuration(elapsed)))
                    else -> Text(
                        st.message ?: "", color = if (st.phase == Phase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showLog = !showLog }) {
                        Icon(Icons.Default.Terminal, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(if (showLog) R.string.hide_log else R.string.show_log))
                    }
                    Spacer(Modifier.weight(1f))
                    if (showLog) TextButton(onClick = { clip.setText(AnnotatedString(st.log.joinToString("\n"))) }) { Text(stringResource(R.string.copy)) }
                }
                if (showLog) LogBox(st.log.takeLast(400).joinToString("\n"), Modifier.heightIn(max = 260.dp))
            }
        },
        confirmButton = {
            if (running) TextButton(onClick = { vm.showBuild = false }) { Text(stringResource(R.string.hide)) }
            else TextButton(onClick = close) { Text(stringResource(R.string.close)) }
        },
        dismissButton = {
            if (running) TextButton(onClick = { Builder.cancel() }) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun LogDialog(text: String, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.last_log)) },
        text = { LogBox(text, Modifier.heightIn(max = 420.dp)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = { clip.setText(AnnotatedString(text)) }) { Text(stringResource(R.string.copy)) } },
    )
}

// settings

@Composable
fun SettingsScreen(vm: AppViewModel, pickers: Pickers, snack: SnackbarHostState) {
    val notifOk = hasNotifications()
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { vm.screen = Screen.HOME }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(stringResource(R.string.section_files))
            PickerCard(stringResource(R.string.file_update), stringResource(R.string.not_set), vm.updateName, stringResource(R.string.change), pickers.update)
            PickerCard(stringResource(R.string.file_game), stringResource(R.string.optional_not_set), vm.gameName, stringResource(R.string.change), pickers.game)
            PickerCard(stringResource(R.string.file_mods), stringResource(R.string.optional_not_set), vm.modsName, stringResource(R.string.change), pickers.mods)
            PickerCard(stringResource(R.string.file_output), stringResource(R.string.not_set), vm.outputName, stringResource(R.string.change), pickers.output)

            SectionLabel(stringResource(R.string.perm_title))
            PickerCard(stringResource(R.string.all_files), stringResource(R.string.all_files_off), if (vm.allFiles) stringResource(R.string.allowed) else null, stringResource(R.string.open), pickers.allFiles)
            if (Build.VERSION.SDK_INT >= 33) PickerCard(
                stringResource(R.string.notifications), stringResource(R.string.not_allowed), if (notifOk) stringResource(R.string.allowed) else null, stringResource(if (notifOk) R.string.allowed else R.string.allow), pickers.notifications,
            )

            SectionLabel(stringResource(R.string.section_storage))
            ListItem(
                headlineContent = { Text(stringResource(R.string.build_cache)) },
                supportingContent = { Text(stringResource(R.string.cache_summary, fmtSize(vm.cacheSize))) },
                trailingContent = { TextButton(onClick = { vm.clearCache() }) { Text(stringResource(R.string.clear)) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.last_log)) },
                trailingContent = { TextButton(onClick = { vm.openLastLog() }) { Text(stringResource(R.string.view)) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            OutlinedButton(onClick = { vm.runSetupAgain() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.run_setup)) }

            SectionLabel(stringResource(R.string.section_about))
            Text(
                stringResource(R.string.app_name) + " ${BuildConfig.VERSION_NAME} · tools ${BuildConfig.TOOLS_VERSION}" +
                    (vm.libId?.let { " · lib id $it" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.about_credits),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
