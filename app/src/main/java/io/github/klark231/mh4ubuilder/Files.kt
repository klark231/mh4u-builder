package io.github.klark231.mh4ubuilder

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import android.system.Os
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

object Files {
    private val TITLE_DIRS = setOf("0004000000126300", "0004000e00126300")
    // same lookup as code_mods.find_mods
    private val MOD_FILES = listOf("exefs/code.bin", "code.bin", "exefs/code.ips", "exefs/code.bps", "code.ips", "code.bps")

    fun hasAllFiles(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    // SAF uri -> real path, null if we can't map it (or have no access to it)
    fun realPath(ctx: Context, u: Uri, tree: Boolean): String? = try {
        val doc = if (tree) DocumentsContract.getTreeDocumentId(u) else DocumentsContract.getDocumentId(u)
        val path = when {
            doc.startsWith("raw:") -> doc.removePrefix("raw:")
            u.authority != "com.android.externalstorage.documents" || ':' !in doc -> null
            else -> {
                val vol = doc.substringBefore(':')
                val rest = doc.substringAfter(':')
                val base = if (vol == "primary") Environment.getExternalStorageDirectory().path else "/storage/$vol"
                if (rest.isEmpty()) base else "$base/$rest"
            }
        }
        path?.takeIf { hasAllFiles(ctx) && File(it).exists() }
    } catch (e: Exception) {
        null
    }

    fun displayName(ctx: Context, u: Uri): String? = try {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun label(ctx: Context, u: Uri, tree: Boolean): String {
        realPath(ctx, u, tree)?.let { return it.removePrefix(Environment.getExternalStorageDirectory().path + "/") }
        if (!tree) displayName(ctx, u)?.let { return it }
        return try {
            val id = DocumentsContract.getTreeDocumentId(u)
            id.substringAfter(':').ifEmpty { id }
        } catch (e: Exception) {
            u.lastPathSegment ?: u.toString()
        }
    }

    // symlink to /proc/self/fd/N so the tools see the right extension without copying the dump
    fun linkTo(ctx: Context, u: Uri, link: File, open: MutableList<ParcelFileDescriptor>): String {
        realPath(ctx, u, false)?.let { return it }
        val pfd = ctx.contentResolver.openFileDescriptor(u, "r") ?: throw IOException("can't open $u")
        open += pfd
        val target = "/proc/self/fd/${pfd.fd}"
        return try {
            Os.symlink(target, link.path); link.path
        } catch (e: Exception) {
            target
        }
    }

    // {id, name, mime}
    private fun children(ctx: Context, tree: Uri, doc: String): List<Triple<String, String, String>> {
        val out = ArrayList<Triple<String, String, String>>()
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc)
        try {
            ctx.contentResolver.query(
                kids, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { c -> while (c.moveToNext()) out += Triple(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "") }
        } catch (e: Exception) { }
        return out
    }

    private fun child(ctx: Context, tree: Uri, doc: String, rel: String): String? {
        var cur = doc
        for (part in rel.split('/')) {
            cur = children(ctx, tree, cur).firstOrNull { it.second.equals(part, true) }?.first ?: return null
        }
        return cur
    }

    private fun findTitle(ctx: Context, tree: Uri, doc: String, depth: Int): String? {
        if (depth == 0 && child(ctx, tree, doc, "exefs") != null) return doc
        val dirs = children(ctx, tree, doc).filter { it.third == Document.MIME_TYPE_DIR }
        dirs.firstOrNull { it.second.lowercase() in TITLE_DIRS }?.let { return it.first }
        if (depth >= 2) return null
        for (d in dirs) findTitle(ctx, tree, d.first, depth + 1)?.let { return it }
        return null
    }

    private fun modFiles(ctx: Context, tree: Uri): Pair<String, List<Pair<String, String>>>? {
        val title = findTitle(ctx, tree, DocumentsContract.getTreeDocumentId(tree), 0) ?: return null
        return title to MOD_FILES.mapNotNull { rel -> child(ctx, tree, title, rel)?.let { rel to it } }
    }

    // short summary for the UI, null when there's nothing usable
    fun describeMods(ctx: Context, tree: Uri): String? {
        val (_, files) = modFiles(ctx, tree) ?: return null
        if (files.isEmpty()) return null
        return files.joinToString(", ") { it.first.substringAfterLast('/') }
    }

    // copy code.bin/ips/bps into userDir/load/mods/0004000000126300 (where code_mods looks)
    fun copyCodeMod(ctx: Context, tree: Uri, userDir: File, log: (String) -> Unit): String {
        userDir.deleteRecursively()
        val found = modFiles(ctx, tree)
        if (found == null || found.second.isEmpty()) {
            log("no code mods found, skipping")
            return ""
        }
        val dest = File(userDir, "load/mods/0004000000126300")
        for ((rel, doc) in found.second) {
            val f = File(dest, rel)
            f.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, doc))?.use { i ->
                FileOutputStream(f).use { i.copyTo(it) }
            } ?: throw IOException("can't read $rel")
        }
        log("code mod files: " + found.second.joinToString { it.first })
        return userDir.path
    }

    // returns where the files ended up, for the UI
    fun saveAll(ctx: Context, tree: Uri, files: List<File>): String {
        realPath(ctx, tree, true)?.let { dir ->
            for (f in files) f.copyTo(File(dir, f.name), overwrite = true)
            return dir
        }
        val cr = ctx.contentResolver
        val top = DocumentsContract.getTreeDocumentId(tree)
        for (f in files) {
            val old = child(ctx, tree, top, f.name)
            val target = if (old != null) DocumentsContract.buildDocumentUriUsingTree(tree, old)
            else DocumentsContract.createDocument(
                cr, DocumentsContract.buildDocumentUriUsingTree(tree, top), "application/octet-stream", f.name,
            ) ?: throw IOException("can't create ${f.name}")
            cr.openOutputStream(target, "wt")?.use { o -> f.inputStream().use { it.copyTo(o) } }
                ?: throw IOException("can't write ${f.name}")
        }
        return label(ctx, tree, true)
    }

    fun unzipAsset(ctx: Context, asset: String, dest: File) {
        val canon = dest.canonicalPath + File.separator
        ctx.assets.open(asset).use { input ->
            ZipInputStream(input).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val f = File(dest, e.name)
                    if (!f.canonicalPath.startsWith(canon)) throw IOException("bad entry ${e.name}")
                    if (e.isDirectory) { f.mkdirs(); continue }
                    f.parentFile?.mkdirs()
                    FileOutputStream(f).use { z.copyTo(it) }
                }
            }
        }
    }

    fun size(f: File): Long = f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
}
