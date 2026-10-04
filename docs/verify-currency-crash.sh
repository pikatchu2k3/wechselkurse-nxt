#!/bin/bash
# Reproduziert/prüft den Absturz im "Betrag ändern"-Screen (Krypto/Metall, z. B. BTC/XAU):
#   java.util.IllegalFormatPrecisionException: 110  in MainViewModel$ratesInformationFooter$1.update
#
# Aufruf:  ./verify-currency-crash.sh <adb-serial> <apk> [CURRENCY]
# Beispiel: ./verify-currency-crash.sh emulator-5554 app/build/outputs/apk/play/debug/de.salomax.currencies-v12300-play-debug.apk BTC
#
# Ablauf: App zurücksetzen -> MainActivity direkt mit getippter Fremdwährung öffnen -> warten, bis der
# zweite Snapshot (Krypto/Metalle) eintrifft -> Crash-Puffer + Footer-Text auslesen.
# Erwartung VOR dem Fix: FATAL EXCEPTION (IllegalFormatPrecisionException). NACH dem Fix: kein Crash,
# Footer zeigt "1 <ASSET> ≈ <Zahl> EUR".
set -u
SER="${1:?adb-serial fehlt}"
APK="${2:?APK-Pfad fehlt}"
CUR="${3:-BTC}"
A=(adb -s "$SER")
PKG=de.salomax.currencies.debug
ACT="$PKG/de.salomax.currencies.view.main.MainActivity"

"${A[@]}" root >/dev/null 2>&1
"${A[@]}" wait-for-device
echo "### Gerät: $("${A[@]}" shell getprop ro.product.model | tr -d '\r')   APK: $APK (md5 $(md5sum < "$APK" | cut -c1-12))"
"${A[@]}" shell am force-stop $PKG
"${A[@]}" install -r -d "$APK" | tail -1
"${A[@]}" shell pm clear $PKG | tail -1
"${A[@]}" logcat -c; "${A[@]}" logcat -b crash -c

echo "### Start: $ACT --es ARG_TAPPED_CURRENCY $CUR"
"${A[@]}" shell am start -n "$ACT" --es ARG_TAPPED_CURRENCY "$CUR" 2>&1 | tail -2
sleep "${4:-45}"

echo "### topResumedActivity"
"${A[@]}" shell 'dumpsys activity activities | grep -m1 topResumedActivity' | sed 's/^ *//'
echo "### Crash-Puffer (FATAL +5 Zeilen)"
"${A[@]}" logcat -d -b crash | grep -A5 -m1 'FATAL' | head -20
echo "### Prozess läuft?"
echo "pidof: '$("${A[@]}" shell pidof $PKG | tr -d '\r')'"
echo "### Footer-Text aus dem UI-Dump"
"${A[@]}" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
"${A[@]}" exec-out cat /sdcard/ui.xml | grep -o 'text="[^"]*"' | grep -E '≈|EUR|Betrag' | head -10
