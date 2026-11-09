package io.github.klark231.mh4ubuilder

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Screen { HOME, SETTINGS }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Application get() = getApplication()
    val prefs = Prefs(app)
    val maxJobs = Runtime.getRuntime().availableProcessors()

    var setupDone by mutableStateOf(prefs.setupDone); private set
    var screen by mutableStateOf(Screen.HOME)

    var updateName by mutableStateOf<String?>(null); private set
    var gameName by mutableStateOf<String?>(null); private set
    var modsName by mutableStateOf<String?>(null); private set
    var modsInfo by mutableStateOf<String?>(null); private set
    var outputName by mutableStateOf<String?>(null); private set
    var allFiles by mutableStateOf(false); private set

    var ready by mutableStateOf(false); private set
    var libId by mutableStateOf<String?>(null); private set
    var cacheSize by mutableLongStateOf(0L); private set
    var lastAt by mutableLongStateOf(0L); private set
    var lastOk by mutableStateOf(false); private set
    var lastOpt by mutableStateOf(""); private set
    var lastMods by mutableStateOf(false); private set

    var opt by mutableStateOf(prefs.opt); private set
    var jobs by mutableIntStateOf(prefs.jobs.coerceIn(1, maxJobs)); private set
    var useMods by mutableStateOf(prefs.useMods); private set

    var message by mutableStateOf<String?>(null)
    var showBuild by mutableStateOf(false)
    var logText by mutableStateOf<String?>(null)

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Builder.prepare(ctx)
                libId = Builder.libraryId(ctx)
                ready = true
            } catch (e: Throwable) {
                message = ctx.getString(R.string.msg_unpack_failed, e.message ?: e.toString())
            }
        }
    }

    fun refresh() {
        allFiles = Files.hasAllFiles(ctx)
        updateName = prefs.update?.let { Files.label(ctx, Uri.parse(it), false) }
        gameName = prefs.game?.let { Files.label(ctx, Uri.parse(it), false) }
        modsName = prefs.mods?.let { Files.label(ctx, Uri.parse(it), true) }
        outputName = prefs.output?.let { Files.label(ctx, Uri.parse(it), true) }
        lastAt = prefs.lastBuildAt
        lastOk = prefs.lastBuildOk
        lastOpt = prefs.lastBuildOpt
        lastMods = prefs.lastBuildMods
        viewModelScope.launch(Dispatchers.IO) {
            modsInfo = prefs.mods?.let { Files.describeMods(ctx, Uri.parse(it)) }
            cacheSize = Files.size(Builder.workDir(ctx))
        }
    }

    private fun persist(uri: Uri, write: Boolean) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
        try { ctx.contentResolver.takePersistableUriPermission(uri, flags) } catch (e: Exception) { }
    }

    fun setUpdate(u: Uri) { persist(u, false); prefs.update = u.toString(); refresh() }
    fun setGame(u: Uri) { persist(u, false); prefs.game = u.toString(); refresh() }
    fun setMods(u: Uri) { persist(u, false); prefs.mods = u.toString(); refresh() }
    fun setOutput(u: Uri) { persist(u, true); prefs.output = u.toString(); refresh() }
    fun clearGame() { prefs.game = null; refresh() }
    fun clearMods() { prefs.mods = null; modsInfo = null; refresh() }

    fun changeOpt(v: String) { opt = v; prefs.opt = v }
    fun changeJobs(v: Int) { jobs = v; prefs.jobs = v }
    fun changeUseMods(v: Boolean) { useMods = v; prefs.useMods = v }

    fun finishSetup() { prefs.setupDone = true; setupDone = true }
    fun runSetupAgain() { prefs.setupDone = false; setupDone = false; screen = Screen.HOME }

    fun build() {
        if (Builder.state.value.phase == Phase.RUNNING) { showBuild = true; return }
        val update = prefs.update
        val output = prefs.output
        when {
            !ready -> message = ctx.getString(R.string.msg_unpacking)
            update == null -> message = ctx.getString(R.string.msg_pick_update)
            output == null -> message = ctx.getString(R.string.msg_pick_output)
            else -> {
                Builder.start(
                    ctx,
                    BuildRequest(
                        Uri.parse(update), prefs.game?.let(Uri::parse),
                        if (useMods) prefs.mods?.let(Uri::parse) else null,
                        Uri.parse(output), opt, jobs,
                    ),
                )
                showBuild = true
            }
        }
    }

    fun clearCache() {
        if (Builder.state.value.phase == Phase.RUNNING) { message = ctx.getString(R.string.msg_cant_clear); return }
        viewModelScope.launch(Dispatchers.IO) {
            Builder.workDir(ctx).deleteRecursively()
            cacheSize = 0
            message = ctx.getString(R.string.msg_cache_cleared)
        }
    }

    fun openLastLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val f = Builder.lastLog(ctx)
            logText = if (f.isFile) f.readText() else ctx.getString(R.string.no_log)
        }
    }
}
