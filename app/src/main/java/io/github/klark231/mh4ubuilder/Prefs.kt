package io.github.klark231.mh4ubuilder

import android.app.ActivityManager
import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("builder", Context.MODE_PRIVATE)
    private val defaultJobs: Int

    init {
        // ~1.5 GB per clang job at -O1 on the big files
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        defaultJobs = (mi.totalMem / (1536L shl 20)).toInt().coerceIn(1, Runtime.getRuntime().availableProcessors())
    }

    var setupDone: Boolean
        get() = sp.getBoolean("setup", false)
        set(v) = sp.edit().putBoolean("setup", v).apply()

    var update: String?
        get() = sp.getString("cia", null)
        set(v) = sp.edit().putString("cia", v).apply()

    var game: String?
        get() = sp.getString("game", null)
        set(v) = sp.edit().putString("game", v).apply()

    var mods: String?
        get() = sp.getString("mods", null)
        set(v) = sp.edit().putString("mods", v).apply()

    var output: String?
        get() = sp.getString("out", null)
        set(v) = sp.edit().putString("out", v).apply()

    var opt: String
        get() = sp.getString("opt", "O1") ?: "O1"
        set(v) = sp.edit().putString("opt", v).apply()

    var jobs: Int
        get() = sp.getInt("jobs", defaultJobs)
        set(v) = sp.edit().putInt("jobs", v).apply()

    var useMods: Boolean
        get() = sp.getBoolean("use_mods", true)
        set(v) = sp.edit().putBoolean("use_mods", v).apply()

    val lastBuildAt: Long get() = sp.getLong("last_at", 0)
    val lastBuildOk: Boolean get() = sp.getBoolean("last_ok", false)
    val lastBuildOpt: String get() = sp.getString("last_opt", "") ?: ""
    val lastBuildMods: Boolean get() = sp.getBoolean("last_mods", false)

    fun recordBuild(ok: Boolean, at: Long, opt: String, mods: Boolean) = sp.edit()
        .putBoolean("last_ok", ok).putLong("last_at", at).putString("last_opt", opt).putBoolean("last_mods", mods).apply()
}
