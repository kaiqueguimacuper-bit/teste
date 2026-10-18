#!/bin/bash
# Instala o APK no emulador, abre cada tela no modo demo e salva capturas em shots/.
PKG=com.kaique.casahub
mkdir -p shots
adb install -r saida/CasaHub.apk > shots/install.txt 2>&1
adb shell pm clear $PKG
adb logcat -c

shot() {
  local name=$1; shift
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity "$@" > /dev/null
  sleep 7
  adb exec-out screencap -p > "shots/$name.png"
  if [ -z "$(adb shell pidof $PKG)" ]; then echo "CRASH em $name" >> shots/status.txt; else echo "ok $name" >> shots/status.txt; fi
}

shot 01-login
shot 02-inicio --ez demo true --es screen home
shot 03-aparelhos --es screen devices
shot 04-cenas --es screen scenes
shot 05-ajustes --es screen settings
shot 06-controle-tv --es screen device:d3
adb shell input swipe 540 1900 540 500 400; sleep 2
adb exec-out screencap -p > shots/07-controle-tv-baixo.png
shot 08-ar --es screen device:d4
shot 09-luz --es screen device:d1
shot 10-fechadura --es screen device:d7
shot 11-cortina --es screen device:d6
shot 12-sensor --es screen device:d14

adb logcat -d -b crash > shots/crash.txt 2>&1
adb logcat -d '*:E' | grep -i -A20 "casahub\|FATAL" | head -200 > shots/erros.txt 2>&1
exit 0
