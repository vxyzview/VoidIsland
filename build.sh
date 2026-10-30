#!/bin/bash
# VoidIsland build: no Gradle needed. javac -> dx -> aapt -> zipalign -> apksigner
# usage: ./build.sh [apk base name] [versionName] [versionCode]
#   defaults: VoidIsland 1.1 2
# SDK paths: uses $ANDROID_HOME when set (GitHub Actions / full SDK installs),
# otherwise falls back to Debian's packaged SDK. For android.jar, a copy at
# libs/android-34.jar takes precedence over $ANDROID_HOME/platforms/android-34.
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
echo "using build-tools: $BT"
echo "using android.jar: $AJ"

NAME=${1:-VoidIsland}
VER=${2:-1.1}
VCODE=${3:-2}

rm -rf out && mkdir -p out/classes
echo "[1/6] javac";   javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 -bootclasspath "$AJ" -d out/classes $(find src -name '*.java') 2>&1 | grep -v '^Note:' || true
echo "[2/6] dex";     "$BT/dx" --dex --min-sdk-version=26 --output=out/classes.dex out/classes
echo "[3/6] aapt";    "$BT/aapt" package -f -M AndroidManifest.xml -S res -I "$AJ" --min-sdk-version 26 --target-sdk-version 33 --version-code "$VCODE" --version-name "$VER" -F out/unsigned.apk
echo "[4/6] add dex"; (cd out && "$BT/aapt" add -f unsigned.apk classes.dex >/dev/null)
echo "[5/6] align";   "$BT/zipalign" -f -p 4 out/unsigned.apk out/aligned.apk
[ -f void.jks ] || keytool -genkeypair -keystore void.jks -alias void -keyalg RSA -keysize 2048 -validity 36500 \
    -storepass voidisland -keypass voidisland -dname "CN=vxyzview, OU=VoidIsland, O=vxyzview, C=ID" >/dev/null 2>&1
echo "[6/6] sign";    "$BT/apksigner" sign --ks void.jks --ks-pass pass:voidisland --key-pass pass:voidisland --out out/$NAME.apk out/aligned.apk
ls -la out/$NAME.apk
