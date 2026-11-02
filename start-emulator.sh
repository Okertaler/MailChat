#!/usr/bin/env bash
#
# Mailchat Emulator-Startscript
#
# Nutzung:
#   ./start-emulator.sh              # Emulator mit Fenster starten
#   ./start-emulator.sh --app        # Emulator starten + Mailchat installieren + starten
#   ./start-emulator.sh --headless   # Emulator ohne Fenster (nur adb)
#   ./start-emulator.sh --stop       # Emulator stoppen
#   ./start-emulator.sh --logs       # Emulator-Log folgen
#
set -euo pipefail

# --- Pfade & Umgebung -------------------------------------------------------
ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
JAVA_HOME="${JAVA_HOME:-$HOME/jdk/jdk-17.0.20.1+1}"
AVD_NAME="mailchat_avd"
APK="$HOME/mailchat/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.mailchat"
ACTIVITY="com.mailchat/.MainActivity"
PORT=5554
LOG="/tmp/mailchat_emulator.log"

ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"

export ANDROID_HOME
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

# --- Hilfsfunktionen --------------------------------------------------------
c_green() { printf '\033[32m%s\033[0m\n' "$*"; }
c_yellow() { printf '\033[33m%s\033[0m\n' "$*"; }
c_red() { printf '\033[31m%s\033[0m\n' "$*"; }

find_xauth() {
    # Xwayland-Auth-Datei der GNOME-Session (ändert sich bei Neustart)
    ls /run/user/$(id -u)/.mutter-Xwaylandauth.* 2>/dev/null | head -1 || true
}

wait_for_boot() {
    c_yellow "Warte auf Boot..."
    for i in $(seq 1 60); do
        local boot
        boot=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') || true
        if [ "$boot" = "1" ]; then
            c_green "Boot abgeschlossen."
            return 0
        fi
        sleep 3
    done
    c_red "Boot-Timeout."
    return 1
}

is_running() {
    "$ADB" devices 2>/dev/null | grep -q "emulator-${PORT}\s*device"
}

# --- Aktionen ---------------------------------------------------------------
do_stop() {
    c_yellow "Emulator wird gestoppt..."
    "$ADB" emu kill 2>/dev/null || true
    pkill -f "emulator -avd ${AVD_NAME}" 2>/dev/null || true
    sleep 2
    c_green "Gestoppt."
}

do_logs() {
    if [ -f "$LOG" ]; then
        tail -n 50 -f "$LOG"
    else
        c_red "Log-Datei nicht gefunden: $LOG"
    fi
}

do_start() {
    local headless="${1:-}"

    # adb-Server sicher starten (Emulator braucht Port 5037)
    "$ADB" start-server >/dev/null 2>&1 || true

    if is_running; then
        c_yellow "Emulator läuft bereits (emulator-${PORT})."
        return 0
    fi

    local display_args=()
    if [ "$headless" = "headless" ]; then
        display_args+=(-no-window)
        c_yellow "Starte Emulator (headless)..."
    else
        local xauth
        xauth=$(find_xauth)
        if [ -z "$xauth" ]; then
            c_red "Keine Xwayland-Auth-Datei gefunden. Grafische Session aktiv?"
            c_yellow "Falle auf headless zurück."
            display_args+=(-no-window)
        else
            export DISPLAY="${DISPLAY:-:0}"
            export XAUTHORITY="$xauth"
            c_yellow "Starte Emulator mit Fenster (DISPLAY=$DISPLAY)..."
        fi
    fi

    setsid env ${DISPLAY:+DISPLAY="$DISPLAY"} ${XAUTHORITY:+XAUTHORITY="$XAUTHORITY"} \
        "$EMULATOR" -avd "$AVD_NAME" \
        -no-audio -no-boot-anim \
        -gpu swiftshader_indirect \
        -memory 2048 -cores 4 \
        -port "$PORT" \
        "${display_args[@]}" \
        > "$LOG" 2>&1 < /dev/null &
    disown || true

    # Auf Gerätewait
    c_yellow "Warte auf Gerät..."
    for i in $(seq 1 30); do
        if is_running; then break; fi
        sleep 2
    done

    if ! is_running; then
        c_red "Emulator nicht erreichbar. Log: $LOG"
        tail -n 20 "$LOG" || true
        return 1
    fi
    c_green "Emulator verbunden: emulator-${PORT}"
    wait_for_boot
}

do_app() {
    if ! is_running; then
        c_red "Kein Emulator verbunden."
        return 1
    fi
    if [ ! -f "$APK" ]; then
        c_red "APK nicht gefunden: $APK"
        c_yellow "Erst bauen: cd ~/mailchat && ./gradlew assembleDebug"
        return 1
    fi
    c_yellow "Installiere Mailchat..."
    "$ADB" install -r "$APK" | tail -n 1
    c_yellow "Starte App..."
    "$ADB" shell am start -n "$ACTIVITY" | head -n 1
    c_green "Mailchat gestartet."
}

# --- Main -------------------------------------------------------------------
case "${1:-}" in
    --stop)
        do_stop
        ;;
    --logs)
        do_logs
        ;;
    --headless)
        do_start headless
        ;;
    --app)
        do_start
        do_app
        ;;
    --help|-h|"")
        grep '^#   ' "$0" | sed 's/^#   //'
        ;;
    *)
        c_red "Unbekannte Option: $1"
        grep '^#   ' "$0" | sed 's/^#   //'
        exit 1
        ;;
esac
