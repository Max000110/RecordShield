#!/usr/bin/env bash
# =============================================================================
# RecordShield - Termux Build Script
# Sets up environment variables and calls gradlew with correct SDK paths
# =============================================================================

set -e

export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=/data/data/com.termux/files/home/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "[*] Java: $(java -version 2>&1 | head -1)"
echo "[*] ANDROID_HOME: $ANDROID_HOME"
echo "[*] Project: $PROJECT_DIR"

cd "$PROJECT_DIR"

# Check if platform is installed
if [ ! -d "$ANDROID_HOME/platforms/android-36" ]; then
    echo "[!] Android platform 36 not installed. Installing..."
    SDKMGR="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
    yes | ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" sh "$SDKMGR" --licenses 2>/dev/null || true
    ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" sh "$SDKMGR" "platforms;android-36" "build-tools;35.0.1"
fi

echo "[*] Building debug APK..."
ANDROID_HOME="$ANDROID_HOME" JAVA_HOME="$JAVA_HOME" gradle assembleDebug \
    -p "$PROJECT_DIR" \
    --info 2>&1 | grep -E "BUILD|error:|warning:|Compiling|Processing|Transforming|^>" | head -50

APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    echo "[✓] APK built successfully: $APK"
    ls -lh "$APK"
else
    echo "[✗] Build failed. Run with full output:"
    echo "    ANDROID_HOME=$ANDROID_HOME JAVA_HOME=$JAVA_HOME gradle assembleDebug -p $PROJECT_DIR"
fi
