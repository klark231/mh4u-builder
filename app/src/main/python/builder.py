"""On-device wrapper around mh4n/tools/build_game.py (same as build-android-library.sh, with zig cc)."""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import traceback

CC_NAME = "zigcc"  # needs "zig" in the name, see recomp.build.is_zig


_bridge = None  # Builder.Bridge: log(str), isCancelled()


def _cancelled():
    return _bridge is not None and _bridge.isCancelled()


class _Sink:
    def __init__(self, bridge):
        self.bridge = bridge

    def write(self, s):
        if s:
            self.bridge.log(s)
            if self.bridge.isCancelled():
                raise SystemExit("cancelled")
        return len(s)

    def flush(self):
        pass


def library_id(tools_root):
    tools = os.path.join(tools_root, "tools")
    if tools not in sys.path:
        sys.path.insert(0, tools)
    import build_game
    return build_game.code_library_id(True, False, tools_root)


def run(bridge, cia, game, mods_user_dir, tools_root, zig, zig_lib, work_dir, opt, jobs):
    """Returns the .so path, or "" on failure."""
    global _bridge
    _bridge = bridge
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout = sys.stderr = _Sink(bridge)
    try:
        return _run(cia, game, mods_user_dir, tools_root, zig, zig_lib, work_dir, opt, int(jobs))
    except SystemExit as e:
        print(f"\nstopped: {e.code}")
        return ""
    except BaseException:
        traceback.print_exc()
        return ""
    finally:
        sys.stdout, sys.stderr = old_out, old_err
        _bridge = None


def _run(cia, game, mods_user_dir, tools_root, zig, zig_lib, work_dir, opt, jobs):
    tmp = os.path.join(work_dir, "tmp")
    cache = os.path.join(work_dir, "zig-cache")
    os.makedirs(tmp, exist_ok=True)
    os.makedirs(cache, exist_ok=True)
    os.environ.update({"ZIG_LIB_DIR": zig_lib, "ZIG_GLOBAL_CACHE_DIR": cache, "ZIG_LOCAL_CACHE_DIR": cache,
                       "TMPDIR": tmp, "HOME": work_dir})

    # zigcc -> libzig.so cc, same extra flags as build_game's zigcc script
    real_run = getattr(subprocess, "_mh4u_real_run", subprocess.run)
    subprocess._mh4u_real_run = real_run

    def run_cc(cmd, *a, **k):
        if isinstance(cmd, (list, tuple)) and cmd and cmd[0] == CC_NAME:
            cmd = [zig, "cc", "-fno-sanitize=undefined", "-g0", *cmd[1:]]
        return real_run(cmd, *a, **k)
    subprocess.run = run_cc

    tools = os.path.join(tools_root, "tools")
    if tools not in sys.path:
        sys.path.insert(0, tools)
    import build_game

    _install_caches(build_game, tools_root, work_dir)

    out = os.path.join(work_dir, "android-game")
    argv = ["--dump", cia, "--native", "--android", "--cc", CC_NAME, "--out", out, "--opt", opt, "-j", str(jobs)]
    if game:
        print("using game romfs for shaders")
        argv += ["--romfs", game]
    argv += ["--mods", mods_user_dir] if mods_user_dir else ["--no-mods"]

    print("building...\n")
    rc = build_game.main(argv)
    if rc:
        raise SystemExit(f"build_game.py ended with {rc}")
    lib = os.path.join(out, "recomp", "android", "libmh4u_code_native.so")
    if not os.path.isfile(lib) or not os.path.isfile(build_game.id_path(lib)):
        raise SystemExit(f"can't find {lib} and its .id")
    _prune_objects(os.path.join(work_dir, "objcache"))
    return lib


# Caches, patched in at runtime since editing the tools would change the lib id.
# - skip recompile.recompile when code + tables match the last build
# - keep objects by source hash, so the same C never compiles twice (shader C gets rewritten every run,
#   and toggling a mod would otherwise rebuild everything it touches)

OBJ_CACHE_LIMIT = 1536 << 20  # LRU by mtime


def _hash_files(paths):
    h = hashlib.sha256()
    for p in paths:
        h.update(p.encode() + b"\0")
        if os.path.isfile(p):
            with open(p, "rb") as f:
                h.update(f.read())
    return h.hexdigest()


def _install_caches(build_game, tools_root, work_dir):
    import recompile
    from recomp import build as rbuild

    tools_id = build_game.code_library_id(True, False, tools_root)
    runtime = rbuild.RUNTIME
    headers = _hash_files([os.path.join(runtime, n) for n in ("recomp.h", "pica_native.h")] +
                          [os.path.join(rbuild.ANDROID_INCLUDE, n) for n in sorted(os.listdir(rbuild.ANDROID_INCLUDE))])

    real_recompile = getattr(recompile, "_mh4u_real", recompile.recompile)
    recompile._mh4u_real = real_recompile

    def cached_recompile(img, entries, out, log=print, data_segments=None, title_id=0, hooks=None, tunables=None):
        h = hashlib.sha256()
        h.update(tools_id.encode())
        h.update(repr((img.base, img.text_size, img.extra_code, data_segments, title_id, sorted((entries or {}).items()),
                       sorted((hooks or {}).items()), sorted((tunables or {}).items()))).encode())
        h.update(img.code)
        key = h.hexdigest()
        saved = os.path.join(out, "recompile-cache.json")
        src = os.path.join(out, "src")
        try:
            with open(saved) as f:
                last = json.load(f)
            if last["key"] == key and all(os.path.isfile(p) for p in last["files"]) \
                    and os.path.isfile(os.path.join(src, "table.c")):
                log("  code unchanged, reusing recompiled C")
                return {"report": last["report"], "funcs": {}, "files": last["files"], "problems": last["problems"]}
        except (OSError, ValueError, KeyError):
            pass
        t = time.time()
        res = real_recompile(img, entries, out, log=log, data_segments=data_segments, title_id=title_id,
                             hooks=hooks, tunables=tunables)
        log(f"  recompile took {time.time() - t:.0f}s")
        with open(saved + ".tmp", "w") as f:
            json.dump({"key": key, "report": res["report"], "files": res["files"], "problems": res["problems"]}, f)
        os.replace(saved + ".tmp", saved)
        return res
    recompile.recompile = cached_recompile

    cache = os.path.join(work_dir, "objcache")
    os.makedirs(cache, exist_ok=True)
    real_compile = getattr(rbuild, "_mh4u_real_compile", rbuild.compile_one)
    rbuild._mh4u_real_compile = real_compile

    def cached_compile(cc, src, obj, flags):
        if _cancelled():
            return src, 0.0, "cancelled"
        with open(src, "rb") as f:
            text = f.read()
        h = hashlib.sha256(text)
        h.update("\0".join([os.path.basename(cc), headers, *flags]).encode())
        kept = os.path.join(cache, h.hexdigest() + ".o")
        if os.path.isfile(kept):
            shutil.copyfile(kept, obj)
            os.utime(kept)
            return src, 0.0, ""
        result = real_compile(cc, src, obj, flags)
        if not result[2] and os.path.isfile(obj):
            shutil.copyfile(obj, kept + ".tmp")
            os.replace(kept + ".tmp", kept)
        return result
    rbuild.compile_one = cached_compile


def _prune_objects(cache):
    try:
        objs = [(e.stat().st_mtime, e.stat().st_size, e.path) for e in os.scandir(cache) if e.name.endswith(".o")]
    except OSError:
        return
    total = sum(s for _, s, _ in objs)
    for _, size, path in sorted(objs):
        if total <= OBJ_CACHE_LIMIT:
            break
        try:
            os.remove(path)
            total -= size
        except OSError:
            pass
