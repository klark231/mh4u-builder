#!/data/data/com.termux/files/usr/bin/sh
# Pushes this folder to the private repo (replacing it), sets signing secrets, waits for CI and grabs the APK.
# Key lives in ~/.mh4u-builder, reused on every run so updates install over the old APK.
set -e
REPO_NAME=mh4u-builder
HERE=$(cd "$(dirname "$0")" && pwd)
KEYDIR="$HOME/.mh4u-builder"

command -v gh >/dev/null && command -v git >/dev/null && command -v keytool >/dev/null || \
    pkg install -y gh git openjdk-17
gh auth status >/dev/null 2>&1 || gh auth login -h github.com -p https -w -s repo,workflow
gh auth setup-git
OWNER=$(gh api user -q .login)
REPO="$OWNER/$REPO_NAME"

mkdir -p "$KEYDIR"
if [ ! -f "$KEYDIR/release.jks" ]; then
    head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' > "$KEYDIR/password"
    keytool -genkeypair -keystore "$KEYDIR/release.jks" -alias mh4u -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$(cat "$KEYDIR/password")" -keypass "$(cat "$KEYDIR/password")" -dname "CN=$OWNER" >/dev/null 2>&1
    echo "created $KEYDIR/release.jks - back it up"
fi
PW=$(cat "$KEYDIR/password")

gh repo view "$REPO" >/dev/null 2>&1 || gh repo create "$REPO" --private -d "MH4U lib builder"
base64 -w0 "$KEYDIR/release.jks" | gh secret set KEYSTORE_BASE64 -R "$REPO"
gh secret set KEYSTORE_PASSWORD -R "$REPO" -b "$PW"
gh secret set KEY_PASSWORD -R "$REPO" -b "$PW"
gh secret set KEY_ALIAS -R "$REPO" -b mh4u

cd "$HERE"
# one fresh commit each time, replaces whatever is on github
rm -rf .git
git init -q -b main
git add -A
git -c user.name="$OWNER" -c user.email="$OWNER@users.noreply.github.com" commit -qm "MH4U Builder"
git remote add origin "https://github.com/$REPO.git"
BEFORE=$(gh run list -R "$REPO" -L 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)
git push -qf -u origin main

echo "waiting for CI..."
RUN=
for i in $(seq 1 30); do
    RUN=$(gh run list -R "$REPO" -L 1 --json databaseId -q '.[0].databaseId' 2>/dev/null || true)
    [ -n "$RUN" ] && [ "$RUN" != "$BEFORE" ] && break
    RUN=; sleep 4
done
if [ -z "$RUN" ]; then
    gh workflow run build.yml -R "$REPO" --ref main; sleep 8
    RUN=$(gh run list -R "$REPO" -L 1 --json databaseId -q '.[0].databaseId')
fi
echo "https://github.com/$REPO/actions/runs/$RUN"
# gh run watch hangs sometimes on termux, just poll
while :; do
    ST=$(gh run view "$RUN" -R "$REPO" --json status,conclusion -q '.status + " " + .conclusion' 2>/dev/null || echo "?")
    case "$ST" in
        "completed success") echo "build ok"; break ;;
        completed*) echo "build failed ($ST)"; gh run view "$RUN" -R "$REPO" --log-failed | tail -40; exit 1 ;;
    esac
    printf '.'; sleep 15
done

OUT=/sdcard/Download/mh4u-builder
[ -d /sdcard/Download ] || { termux-setup-storage; sleep 4; }
rm -rf "$OUT" && mkdir -p "$OUT"
gh run download "$RUN" -R "$REPO" -n mh4u-builder-apk -D "$OUT"
APK=$(ls "$OUT"/*.apk | head -n 1)
echo
echo "APK: $APK"
command -v termux-open >/dev/null && termux-open "$APK" || true
