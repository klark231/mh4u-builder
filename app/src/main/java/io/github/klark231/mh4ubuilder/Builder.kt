package io.github.klark231.mh4ubuilder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import kotlin.concurrent.thread

enum class Phase { IDLE, RUNNING, DONE, FAILED, CANCELLED }

data class BuildState(
    val phase: Phase = Phase.IDLE,
    @StringRes val step: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val log: List<String> = emptyList(),
    val message: String? = null,
    val startedAt: Long = 0,
    val endedAt: Long = 0,
)

data class BuildRequest(
    val update: Uri, val game: Uri?, val mods: Uri?, val output: Uri, val opt: String, val jobs: Int,
)

object Builder {
    private val _state = MutableStateFlow(BuildState())
    val state: StateFlow<BuildState> = _state

    @Volatile private var cancelled = false
    private val lines = ArrayDeque<String>()
    private val partial = StringBuilder()
    private const val MAX_LINES = 2000
    private val COUNT = Regex("""(\d+)/(\d+)""")

    fun toolsRoot(ctx: Context) = File(ctx.filesDir, "mh4n")
    private fun zigLib(ctx: Context) = File(ctx.filesDir, "zig/lib")
    fun workDir(ctx: Context) = File(ctx.filesDir, "work")
    fun lastLog(ctx: Context) = File(ctx.filesDir, "last-build.log")

    // called from python
    class Bridge {
        fun log(s: String) = append(s)
        fun isCancelled(): Boolean = cancelled
    }

    // unpack tools + zig lib once per install/update
    @Synchronized
    fun prepare(ctx: Context) {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val stamp = pi.lastUpdateTime.toString()
        val mark = File(ctx.filesDir, "unpacked.stamp")
        if (mark.isFile && mark.readText() == stamp) return
        toolsRoot(ctx).deleteRecursively()
        File(ctx.filesDir, "zig").deleteRecursively()
        Files.unzipAsset(ctx, "mh4n.zip", toolsRoot(ctx))
        Files.unzipAsset(ctx, "ziglib.zip", zigLib(ctx))
        mark.writeText(stamp)
    }

    fun libraryId(ctx: Context): String =
        Python.getInstance().getModule("builder").callAttr("library_id", toolsRoot(ctx).path).toString()

    fun start(ctx: Context, req: BuildRequest) {
        if (_state.value.phase == Phase.RUNNING) return
        val app = ctx.applicationContext
        cancelled = false
        synchronized(lines) { lines.clear(); partial.setLength(0) }
        _state.value = BuildState(Phase.RUNNING, R.string.step_starting, startedAt = System.currentTimeMillis())
        ContextCompat.startForegroundService(app, Intent(app, BuildService::class.java))
        thread(name = "build") { run(app, req) }
    }

    fun cancel() {
        if (_state.value.phase != Phase.RUNNING) return
        cancelled = true
        _state.update { it.copy(step = R.string.step_cancelling) }
    }

    fun dismiss() {
        if (_state.value.phase != Phase.RUNNING) _state.value = BuildState()
    }

    private fun append(s: String) {
        synchronized(lines) {
            for (ch in s) {
                when (ch) {
                    '\n' -> { addLine(partial.toString()); partial.setLength(0) }
                    '\r' -> partial.setLength(0)
                    else -> partial.append(ch)
                }
            }
            _state.update { it.copy(log = lines.toList()) }
        }
    }

    private fun addLine(line: String) {
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        val t = line.trim()
        val n = COUNT.find(t)
        when {
            t.startsWith("extracting ") -> step(R.string.step_reading)
            t.startsWith("code mods:") || t.startsWith("code mod files:") -> step(R.string.step_mods)
            t.startsWith("recompiling") -> step(R.string.step_analysing)
            t.startsWith("analysed ") && n != null -> progress(R.string.step_analysing, n)
            t.startsWith("code unchanged") -> step(R.string.step_reusing)
            t.startsWith("compiling with") -> step(R.string.step_compiling)
            t.startsWith("compiled ") && n != null -> progress(R.string.step_compiling, n)
            t.startsWith("linked ") -> step(R.string.step_checking)
        }
    }

    private fun step(@StringRes s: Int) = _state.update { it.copy(step = s, done = 0, total = 0) }

    private fun progress(@StringRes s: Int, m: MatchResult) = _state.update {
        it.copy(step = s, done = m.groupValues[1].toInt(), total = m.groupValues[2].toInt())
    }

    private class Cancelled : Exception("cancelled")

    private fun run(ctx: Context, req: BuildRequest) {
        val wake = ctx.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mh4ubuilder:build")
        wake.acquire(3 * 60 * 60 * 1000L)
        val open = ArrayList<ParcelFileDescriptor>()
        var phase = Phase.FAILED
        var msg: String? = null
        try {
            step(R.string.step_unpacking)
            prepare(ctx)
            val work = workDir(ctx).apply { mkdirs() }
            val input = File(work, "in").apply { deleteRecursively(); mkdirs() }
            val cia = Files.linkTo(ctx, req.update, File(input, "update.cia"), open)
            val game = req.game?.let {
                val ext = if (Files.displayName(ctx, it)?.lowercase()?.endsWith(".cci") == true) "cci" else "3ds"
                Files.linkTo(ctx, it, File(input, "game.$ext"), open)
            } ?: ""
            val mods = req.mods?.let { Files.copyCodeMod(ctx, it, File(work, "android-mods")) { s -> append(s + "\n") } } ?: ""
            if (cancelled) throw Cancelled()

            val zig = File(ctx.applicationInfo.nativeLibraryDir, "libzig.so").path
            val lib = Python.getInstance().getModule("builder").callAttr(
                "run", Bridge(), cia, game, mods, toolsRoot(ctx).path, zig, zigLib(ctx).path, work.path, req.opt, req.jobs,
            ).toString()
            if (cancelled) throw Cancelled()
            if (lib.isEmpty()) throw IllegalStateException("build failed, check the log")

            step(R.string.step_saving)
            val so = File(lib)
            val id = File(lib.removeSuffix(".so") + ".id")
            msg = Files.saveAll(ctx, req.output, listOf(so, id))
            append("\nsaved ${so.name} and ${id.name} to $msg\n")
            phase = Phase.DONE
        } catch (e: Throwable) {
            if (cancelled || e is Cancelled) {
                phase = Phase.CANCELLED
                msg = null
            } else {
                msg = e.message ?: e.toString()
                append("\nerror: $msg\n")
            }
        } finally {
            open.forEach { runCatching { it.close() } }
            if (wake.isHeld) wake.release()
        }
        synchronized(lines) {
            if (partial.isNotEmpty()) { lines.addLast(partial.toString()); partial.setLength(0) }
            runCatching { lastLog(ctx).writeText(lines.joinToString("\n")) }
        }
        val end = System.currentTimeMillis()
        Prefs(ctx).recordBuild(phase == Phase.DONE, end, req.opt, req.mods != null)
        _state.update { it.copy(phase = phase, message = msg, endedAt = end, log = synchronized(lines) { lines.toList() }) }
    }
}
