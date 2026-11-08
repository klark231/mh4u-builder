package io.github.klark231.mh4ubuilder;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.OpenableColumns;
import android.system.Os;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int PICK_CIA = 1, PICK_GAME = 2, PICK_MODS = 3, PICK_OUT = 4;
    private static final String[] OPTS = {"O1", "O2", "O0"};
    private static final List<String> TITLE_DIRS = Arrays.asList("0004000000126300", "0004000e00126300");
    // same lookup as code_mods.find_mods
    private static final String[] MOD_FILES = {"exefs/code.bin", "code.bin", "exefs/code.ips", "exefs/code.bps",
            "code.ips", "code.bps"};

    private final String[] keys = {"", "cia", "game", "mods", "out"};
    private final TextView[] labels = new TextView[5];
    private EditText jobs;
    private Spinner opt;
    private Button build;
    private TextView log;
    private ScrollView logScroll;
    private SharedPreferences prefs;
    private volatile boolean busy;

    // build_game.py output ends up here
    public class LogSink {
        public void log(final String s) {
            runOnUiThread(() -> append(s));
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("builder", MODE_PRIVATE);
        if (!Python.isStarted()) Python.start(new AndroidPlatform(this));

        int pad = dp(14);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("MH4U Builder (unofficial)");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Builds libmh4u_code_native.so + .id from your decrypted dump. Rebuild after changing code mods.");
        root.addView(hint);

        addPicker(root, PICK_CIA, "Update .cia (v1.3.0)", v -> pickFile(PICK_CIA));
        addPicker(root, PICK_GAME, "Game .3ds/.cci (optional, faster lib)", v -> pickFile(PICK_GAME));
        addPicker(root, PICK_MODS, "Mods folder (optional)", v -> pickTree(PICK_MODS));
        addPicker(root, PICK_OUT, "Output folder (your MH4U folder)", v -> pickTree(PICK_OUT));

        LinearLayout row = new LinearLayout(this);
        TextView optLabel = new TextView(this);
        optLabel.setText("Opt ");
        opt = new Spinner(this);
        opt.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, OPTS));
        opt.setSelection(Math.max(0, Arrays.asList(OPTS).indexOf(prefs.getString("opt", "O1"))));
        TextView jobsLabel = new TextView(this);
        jobsLabel.setText("   Jobs ");
        jobs = new EditText(this);
        jobs.setInputType(InputType.TYPE_CLASS_NUMBER);
        jobs.setText(String.valueOf(prefs.getInt("jobs", defaultJobs())));
        row.addView(optLabel);
        row.addView(opt);
        row.addView(jobsLabel);
        row.addView(jobs);
        root.addView(row);

        LinearLayout row2 = new LinearLayout(this);
        build = new Button(this);
        build.setText("Build");
        build.setOnClickListener(v -> startBuild());
        Button clean = new Button(this);
        clean.setText("Clear cache");
        clean.setOnClickListener(v -> {
            if (busy) return;
            deleteTree(new File(getFilesDir(), "work"));
            append("build cache cleared\n");
        });
        row2.addView(build);
        row2.addView(clean);
        root.addView(row2);

        logScroll = new ScrollView(this);
        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(11);
        log.setTextIsSelectable(true);
        logScroll.addView(log);
        root.addView(logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView credits = new TextView(this);
        credits.setTextSize(10);
        credits.setText("Recompiler from 4u-native by khorne-hub - github.com/khorne-hub/4u-native (GPLv3). "
                + "Unofficial, not made or endorsed by khorne-hub. Uses Zig and Chaquopy.");
        root.addView(credits);
        setContentView(root);

        for (int i = 1; i <= 4; i++) showPick(i);
        askAllFiles();
        new Thread(() -> {
            try {
                prepare();
                String id = Python.getInstance().getModule("builder")
                        .callAttr("library_id", toolsRoot().getPath()).toString();
                runOnUiThread(() -> append("tools " + BuildConfig.TOOLS_VERSION + ", lib id " + id
                        + "\n"));
            } catch (Throwable t) {
                runOnUiThread(() -> append("setup failed: " + t + "\n"));
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (labels[1] != null) for (int i = 1; i <= 4; i++) showPick(i);
    }


    private boolean hasAllFiles() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED;
    }

    private void askAllFiles() {
        if (hasAllFiles()) return;
        append("Needs All files access.\n");
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        } else {
            requestPermissions(new String[]{"android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE"}, 9);
        }
    }

    // SAF uri -> real path, null if we can't map it
    private String realPath(Uri u, boolean tree) {
        if (u == null) return null;
        try {
            String doc = tree ? DocumentsContract.getTreeDocumentId(u) : DocumentsContract.getDocumentId(u);
            if (doc.startsWith("raw:")) return doc.substring(4);  // downloads provider
            if (!"com.android.externalstorage.documents".equals(u.getAuthority())) return null;
            int colon = doc.indexOf(':');
            if (colon < 0) return null;
            String volume = doc.substring(0, colon), rest = doc.substring(colon + 1);
            String base = "primary".equals(volume) ? Environment.getExternalStorageDirectory().getPath()
                    : "/storage/" + volume;
            File f = new File(rest.isEmpty() ? base : base + "/" + rest);
            return hasAllFiles() && f.exists() ? f.getPath() : null;
        } catch (Exception e) {
            return null;
        }
    }


    private void addPicker(LinearLayout root, int which, String text, View.OnClickListener pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(6), 0, 0);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView head = new TextView(this);
        head.setText(text);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        TextView value = new TextView(this);
        value.setTextSize(12);
        labels[which] = value;
        col.addView(head);
        col.addView(value);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button choose = new Button(this);
        choose.setText("Choose");
        choose.setOnClickListener(pick);
        row.addView(choose);
        if (which == PICK_GAME || which == PICK_MODS) {
            Button clear = new Button(this);
            clear.setText("X");
            clear.setOnClickListener(v -> {
                prefs.edit().remove(keys[which]).apply();
                showPick(which);
            });
            row.addView(clear);
        }
        root.addView(row);
    }

    private void pickFile(int which) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, which);
    }

    private void pickTree(int which) {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), which);
    }

    @Override
    protected void onActivityResult(int which, int result, Intent data) {
        super.onActivityResult(which, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null || which < 1 || which > 4) return;
        Uri uri = data.getData();
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | (which == PICK_OUT ? Intent.FLAG_GRANT_WRITE_URI_PERMISSION : 0);
        try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (SecurityException ignored) { }
        prefs.edit().putString(keys[which], uri.toString()).apply();
        showPick(which);
    }

    private Uri picked(int which) {
        String s = prefs.getString(keys[which], null);
        return s == null ? null : Uri.parse(s);
    }

    private void showPick(int which) {
        Uri u = picked(which);
        if (u == null) { labels[which].setText("(none)"); return; }
        String path = realPath(u, which == PICK_MODS || which == PICK_OUT);
        String name = path != null ? path : which == PICK_CIA || which == PICK_GAME ? displayName(u)
                : DocumentsContract.getTreeDocumentId(u);
        labels[which].setText(name == null ? u.toString() : name);
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { }
        return null;
    }


    private void startBuild() {
        if (busy) return;
        if (!hasAllFiles()) append("no All files access, falling back to SAF\n");
        if (picked(PICK_CIA) == null) { append("pick the update .cia\n"); return; }
        if (picked(PICK_OUT) == null) { append("pick an output folder\n"); return; }
        int j;
        try { j = Math.max(1, Integer.parseInt(jobs.getText().toString().trim())); } catch (NumberFormatException e) { j = 2; }
        final int nJobs = j;
        final String o = OPTS[opt.getSelectedItemPosition()];
        prefs.edit().putString("opt", o).putInt("jobs", nJobs).apply();

        busy = true;
        build.setEnabled(false);
        log.setText("");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        new Thread(() -> {
            boolean ok = false;
            List<ParcelFileDescriptor> open = new ArrayList<>();
            try {
                prepare();
                File work = new File(getFilesDir(), "work");
                File in = new File(work, "in");
                deleteTree(in);
                in.mkdirs();
                String cia = linkTo(picked(PICK_CIA), new File(in, "update.cia"), open);
                String game = "";
                if (picked(PICK_GAME) != null) {
                    String n = displayName(picked(PICK_GAME));
                    String ext = n != null && n.toLowerCase().endsWith(".cci") ? ".cci" : ".3ds";
                    game = linkTo(picked(PICK_GAME), new File(in, "game" + ext), open);
                }
                String modsDir = picked(PICK_MODS) == null ? "" : copyCodeMod(picked(PICK_MODS), new File(work, "android-mods"));

                String zig = new File(getApplicationInfo().nativeLibraryDir, "libzig.so").getPath();
                PyObject r = Python.getInstance().getModule("builder").callAttr("run", new LogSink(), cia, game,
                        modsDir, toolsRoot().getPath(), zig, new File(getFilesDir(), "zig/lib").getPath(),
                        work.getPath(), o, nJobs);
                String lib = r.toString();
                if (!lib.isEmpty()) {
                    File so = new File(lib);
                    File id = new File(lib.substring(0, lib.length() - 3) + ".id");
                    String outPath = realPath(picked(PICK_OUT), true);
                    if (outPath != null) {
                        for (File f : new File[]{so, id}) {
                            try (InputStream is = new FileInputStream(f);
                                 OutputStream os = new FileOutputStream(new File(outPath, f.getName()))) {
                                copy(is, os);
                            }
                        }
                    } else {
                        save(picked(PICK_OUT), so);
                        save(picked(PICK_OUT), id);
                    }
                    final String where = outPath != null ? outPath : DocumentsContract.getTreeDocumentId(picked(PICK_OUT));
                    runOnUiThread(() -> append("\nsaved " + so.getName() + " and " + id.getName() + " to "
                            + where + "\n"));
                    ok = true;
                }
            } catch (Throwable t) {
                final String msg = t.toString();
                runOnUiThread(() -> append("\nerror: " + msg + "\n"));
            } finally {
                for (ParcelFileDescriptor p : open) try { p.close(); } catch (IOException ignored) { }
            }
            final boolean done = ok;
            runOnUiThread(() -> {
                busy = false;
                build.setEnabled(true);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                append(done ? "\ndone\n" : "\nbuild failed\n");
            });
        }).start();
    }

    // symlink to /proc/self/fd/N so the tools see the right extension without copying the dump
    private String linkTo(Uri uri, File link, List<ParcelFileDescriptor> open) throws Exception {
        String real = realPath(uri, false);
        if (real != null) return real;
        ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
        if (pfd == null) throw new IOException("can't open " + uri);
        open.add(pfd);
        String target = "/proc/self/fd/" + pfd.getFd();
        try {
            Os.symlink(target, link.getPath());
            return link.getPath();
        } catch (Exception e) {
            return target;
        }
    }

    // copy code.bin/ips/bps into userDir/load/mods/0004000000126300 (where code_mods looks)
    private String copyCodeMod(Uri tree, File userDir) throws Exception {
        deleteTree(userDir);
        String top = DocumentsContract.getTreeDocumentId(tree);
        String title = findTitle(tree, top, 0);
        if (title == null) {
            runOnUiThread(() -> append("no 0004000000126300 in mods folder, skipping mods\n"));
            return "";
        }
        File dest = new File(userDir, "load/mods/0004000000126300");
        int copied = 0;
        for (String rel : MOD_FILES) {
            String doc = child(tree, title, rel);
            if (doc == null) continue;
            File f = new File(dest, rel);
            f.getParentFile().mkdirs();
            try (InputStream is = getContentResolver().openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, doc));
                 OutputStream os = new FileOutputStream(f)) {
                copy(is, os);
            }
            copied++;
        }
        final int n = copied;
        runOnUiThread(() -> append(n == 0 ? "no code mods found, skipping\n"
                : "code mod files: " + n + "\n"));
        return n == 0 ? "" : userDir.getPath();
    }

    private String findTitle(Uri tree, String doc, int depth) {
        if (child(tree, doc, "exefs") != null && depth == 0) return doc;
        for (String[] k : children(tree, doc)) {
            if (!Document.MIME_TYPE_DIR.equals(k[2])) continue;
            if (TITLE_DIRS.contains(k[1].toLowerCase())) return k[0];
        }
        if (depth >= 2) return null;
        for (String[] k : children(tree, doc)) {
            if (!Document.MIME_TYPE_DIR.equals(k[2])) continue;
            String t = findTitle(tree, k[0], depth + 1);
            if (t != null) return t;
        }
        return null;
    }

    private String child(Uri tree, String doc, String rel) {
        for (String part : rel.split("/")) {
            String next = null;
            for (String[] k : children(tree, doc)) if (k[1].equalsIgnoreCase(part)) { next = k[0]; break; }
            if (next == null) return null;
            doc = next;
        }
        return doc;
    }

    // {id, name, mime}
    private List<String[]> children(Uri tree, String doc) {
        List<String[]> out = new ArrayList<>();
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, doc);
        try (Cursor c = getContentResolver().query(kids, new String[]{Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (c != null && c.moveToNext()) out.add(new String[]{c.getString(0), c.getString(1), c.getString(2)});
        } catch (Exception ignored) { }
        return out;
    }

    // write into the tree, overwrite if it exists
    private void save(Uri tree, File f) throws Exception {
        ContentResolver cr = getContentResolver();
        String top = DocumentsContract.getTreeDocumentId(tree);
        String old = child(tree, top, f.getName());
        Uri target;
        if (old != null) {
            target = DocumentsContract.buildDocumentUriUsingTree(tree, old);
        } else {
            target = DocumentsContract.createDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, top),
                    "application/octet-stream", f.getName());
            if (target == null) throw new IOException("can't create " + f.getName() + " in the folder");
        }
        try (InputStream is = new FileInputStream(f); OutputStream os = cr.openOutputStream(target, "wt")) {
            if (os == null) throw new IOException("can't write " + f.getName());
            copy(is, os);
        }
    }


    private File toolsRoot() {
        return new File(getFilesDir(), "mh4n");
    }

    // unpack tools + zig lib once per install/update
    private synchronized void prepare() throws Exception {
        PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
        String stamp = String.valueOf(pi.lastUpdateTime);
        File mark = new File(getFilesDir(), "unpacked.stamp");
        if (mark.isFile() && stamp.equals(new String(java.nio.file.Files.readAllBytes(mark.toPath())))) return;
        runOnUiThread(() -> append("unpacking tools...\n"));
        deleteTree(toolsRoot());
        deleteTree(new File(getFilesDir(), "zig"));
        unzip("mh4n.zip", toolsRoot());
        unzip("ziglib.zip", new File(getFilesDir(), "zig/lib"));
        try (OutputStream os = new FileOutputStream(mark)) { os.write(stamp.getBytes()); }
        runOnUiThread(() -> append("ready\n"));
    }

    private void unzip(String asset, File dest) throws IOException {
        String canon = dest.getCanonicalPath() + File.separator;
        try (InputStream in = getAssets().open(asset); ZipInputStream z = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                File f = new File(dest, e.getName());
                if (!f.getCanonicalPath().startsWith(canon)) throw new IOException("bad entry " + e.getName());
                if (e.isDirectory()) { f.mkdirs(); continue; }
                f.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(f)) { copy(z, os); }
            }
        }
    }

    private static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }

    private void append(String s) {
        log.append(s);
        CharSequence t = log.getText();
        if (t.length() > 200_000) log.setText(t.subSequence(t.length() - 150_000, t.length()));
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ~1.5 GB per clang job at -O1 on the big files
    private int defaultJobs() {
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        int byRam = (int) (mi.totalMem / (1536L << 20));
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), byRam));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
