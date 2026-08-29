#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# RecordShield (v2.1.1) - Build, Deploy & Live Verification Script
# Target device: Vivo V40 (V2348) running Funtouch OS 16 / Android 16
# =============================================================================

set -e

APP_ID="com.vivorecordshield"
APP_ID_DEBUG="${APP_ID}.debug"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

# ---- Colors ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

get_device() {
    DEVICE=$(adb devices | grep -E "device$" | head -1 | awk '{print $1}')
    if [ -z "$DEVICE" ]; then
        log_error "No ADB device connected. Check adb connect."
        exit 1
    fi
    MODEL=$(adb -s "$DEVICE" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    log_success "Target ADB Device: $DEVICE ($MODEL)"
}

build() {
    log_info "Building RecordShield debug APK..."
    cd "$PROJECT_DIR"
    export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
    export ANDROID_HOME=/data/data/com.termux/files/home/android-sdk
    export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

    gradle assembleDebug --quiet || ./gradlew assembleDebug --quiet
    if [ -f "$APK_PATH" ]; then
        log_success "APK compiled successfully: $APK_PATH"
        ls -lh "$APK_PATH"
    else
        log_error "APK build failed."
        exit 1
    fi
}

install() {
    get_device
    log_info "Installing RecordShield to device..."
    adb -s "$DEVICE" install -r -t "$APK_PATH"
    log_success "APK installed"
}

grant_permissions() {
    get_device
    log_info "Granting overlay & battery permissions via ADB..."
    adb -s "$DEVICE" shell appops set "$APP_ID_DEBUG" SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
    adb -s "$DEVICE" shell cmd deviceidle whitelist "+$APP_ID_DEBUG" 2>/dev/null || true
    adb -s "$DEVICE" shell dumpsys deviceidle whitelist "+$APP_ID_DEBUG" 2>/dev/null || true
    adb -s "$DEVICE" shell pm grant "$APP_ID_DEBUG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
    log_success "Permissions granted"
}

start_app() {
    get_device
    log_info "Starting RecordShield Dashboard..."
    adb -s "$DEVICE" shell am start -n "$APP_ID_DEBUG/com.vivorecordshield.ui.MainActivity"
    log_success "App started"
}

start_service() {
    get_device
    log_info "Starting foreground service via Intent..."
    adb -s "$DEVICE" shell am start-foreground-service \
        -n "$APP_ID_DEBUG/com.vivorecordshield.service.ShieldForegroundService" \
        -a "com.vivorecordshield.START_FG" 2>/dev/null || true
    log_success "Service start requested"
}

simulate_kill() {
    get_device
    log_warn "Simulating process kill (am force-stop)..."
    adb -s "$DEVICE" shell am force-stop "$APP_ID_DEBUG"
    log_info "Process terminated. Watchdogs will auto-recover..."
}

simulate_5_kills() {
    get_device
    log_warn "Simulating 5 consecutive process kills to test FULL RECOVERY MODE..."
    for i in {1..5}; do
        echo -e "  ${YELLOW}Kill #$i / 5...${NC}"
        adb -s "$DEVICE" shell am force-stop "$APP_ID_DEBUG"
        sleep 2
    done
    log_success "5 consecutive kills simulated. Full Recovery Mode triggered."
}

logs() {
    get_device
    log_info "Streaming logcat for RecordShield (Ctrl+C to stop)..."
    adb -s "$DEVICE" logcat -s "RS/CALL_UI:*" "RS/RECORD_NODE:*" "RS/OVERLAY:*" "RS/CALL:*" "RS/SHIELD:*" "RS/BOOT:*" "RS/CONFIG:*"
}

dump_ui() {
    get_device
    log_info "Dumping in-call UI hierarchy (ensure active call)..."
    adb -s "$DEVICE" shell uiautomator dump /sdcard/window_dump.xml
    adb -s "$DEVICE" pull /sdcard/window_dump.xml ./window_dump.xml
    log_success "UI hierarchy saved to ./window_dump.xml"
    if command -v python3 &>/dev/null; then
        python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('window_dump.xml')
root = tree.getroot()
print('=== RECORD-RELATED NODES ===')
for elem in root.iter():
    text = elem.get('text','')
    desc = elem.get('content-desc','')
    rid = elem.get('resource-id','')
    if any(k in (text+desc+rid).lower() for k in ['record','recording']):
        print(f'text={text!r} desc={desc!r} id={rid!r} bounds={elem.get(\"bounds\")} clickable={elem.get(\"clickable\")}')
" 2>/dev/null || true
    fi
}

verify_recording() {
    get_device
    log_info "Checking call recording files..."
    adb -s "$DEVICE" shell "ls -lt /sdcard/Recordings/Record/Call/ | head -10" 2>/dev/null || \
    adb -s "$DEVICE" shell "find /sdcard/Recordings -name '*.m4a' 2>/dev/null | head -10"
}

full_setup() {
    build
    install
    grant_permissions
    start_app
    echo ""
    log_success "=== SETUP COMPLETE ==="
}

case "${1:-}" in
    build)             build ;;
    install)           install ;;
    grant)             grant_permissions ;;
    start)             start_app ;;
    service)           start_service ;;
    simulate-kill)     simulate_kill ;;
    simulate-5-kills)   simulate_5_kills ;;
    logs)              logs ;;
    dump-ui)           dump_ui ;;
    verify)            verify_recording ;;
    setup|"")          full_setup ;;
    *)
        echo "Usage: $0 {build|install|grant|start|service|simulate-kill|simulate-5-kills|logs|dump-ui|verify|setup}"
        exit 1
        ;;
esac
