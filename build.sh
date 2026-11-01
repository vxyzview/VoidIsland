#!/bin/bash
# VoidIsland build: no Gradle needed. javac -> dex (d8 or dx) -> aapt -> zipalign -> apksigner
# usage: ./build.sh [apk base name] [versionName] [versionCode]
#   defaults: VoidIsland 1.1 2
# Tools are resolved from $ANDROID_HOME/build-tools first (GitHub Actions / full SDK), then PATH.
# android.jar (API 34): libs/android-34.jar takes precedence over \$ANDROID_HOME/platforms/android-34.
set -e
cd "$(dirname "$0")"

BT=/usr/lib/android-sdk/build-tools/debian
AJ=libs/android-34.jar
if [ -n "$ANDROID_HOME" ]; then
    if [ -d "$ANDROID_HOME/build-tools/34.0.0" ]; then BT=$ANDROID_HOME/build-tools/34.0.0; fi
    if [ -f "$ANDROID_HOME/platforms/android-34/android.jar" ]; then AJ=$ANDROID_HOME/platforms/android-34/android.jar; fi
fi
if [ ! -f "$AJ" ]; then
    echo "error: no android.jar (API 34) found: put one at libs/android-34.jar or install the android-34 platform in \$ANDROID_HOME" >&2
    exit 1
fi

find_tool() { # prints the first candidate that exists as an executable or on PATH
    for c in "$@"; do
        if [ -x "$c" ]; then echo "$c"; return 0; fi
        if p="$(command -v "$c" 2>/dev/null)" && [ -n "$p" ]; then echo "$p"; return 0; fi
    done
    return 1
}
missing() { echo "error: $1 not found (looked in $BT and PATH)" >&2; exit 1; }

DEXER=""
if [ -x "$BT/dx" ]; then DEXER="$BT/dx"; fi
if [ -z "$DEXER" ] && [ -n "$ANDROID_HOME" ]; then
    for d in $(ls -1d "$ANDROID_HOME"/build-tools/*/ 2>/dev/null | sort -Vr); do
        if [ -x "${d}d8" ]; then DEXER="${d}d8"; break; fi
    done
fi
if [ -z "$DEXER" ]; then DEXER=$(find_tool "$BT/d8" d8 dx) || DEXER=""; fi
[ -n "$DEXER" ] || missing "d8/dx"
AAPT=$(find_tool "$BT/aapt" aapt)          || missing "aapt"
ZIP=$(find_tool "$BT/zipalign" zipalign)   || missing "zipalign"
SIGN=$(find_tool "$BT/apksigner" apksigner) || missing "apksigner"
echo "using build-tools: $BT"
echo "using android.jar: $AJ"
echo "dexer: $DEXER"

NAME=${1:-VoidIsland}
VER=${2:-1.1}
VCODE=${3:-2}

rm -rf out && mkdir -p out/classes
echo "[1/6] javac";   javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$AJ" -d out/classes $(find src -name '*.java') > out/javac.log 2>&1 || { cat out/javac.log >&2; echo "error: javac failed" >&2; exit 1; }
grep -v '^Note:' out/javac.log || true
if [ "${DEXER##*/}" = dx ]; then
    echo "[2/6] dex";
    "$DEXER" --dex --min-sdk-version=26 --output=out/classes.dex out/classes
else
    echo "[2/6] dex (d8)";
    "$DEXER" --release --min-api 26 --lib "$AJ" --output out $(find out/classes -name '*.class')
fi
echo "[3/6] aapt";    "$AAPT" package -f -M AndroidManifest.xml -S res -I "$AJ" --min-sdk-version 26 --target-sdk-version 33 --version-code "$VCODE" --version-name "$VER" -F out/unsigned.apk
echo "[4/6] add dex"; (cd out && "$AAPT" add -f unsigned.apk classes.dex >/dev/null)
echo "[5/6] align";   "$ZIP" -f -p 4 out/unsigned.apk out/aligned.apk
[ -f void.jks ] || keytool -genkeypair -keystore void.jks -alias void -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass voidisland -keypass voidisland -dname "CN=vxyzview, OU=VoidIsland, O=vxyzview, C=ID" >/dev/null 2>&1
echo "[6/6] sign";    "$SIGN" sign --ks void.jks --ks-pass pass:voidisland --key-pass pass:voidisland --out out/$NAME.apk out/aligned.apk
ls -la out/$NAME.apk
