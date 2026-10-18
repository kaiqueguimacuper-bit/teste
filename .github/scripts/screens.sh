#!/bin/bash
# Instala o APK no emulador, abre cada tela no modo demo e salva capturas em shots/.
PKG=com.kaique.casahub
mkdir -p shots
adb install -r saida/CasaHub.apk > shots/install.txt 2>&1
adb shell pm clear $PKG
adb logcat -c

# Toca no elemento pelo texto de acessibilidade (não depende de coordenadas fixas).
tap_desc() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
  python3 - "$1" <<'PY'
import re, sys, subprocess
xml = open('/tmp/ui.xml', encoding='utf-8').read()
m = re.search(r'(?:content-desc|text)="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % re.escape(sys.argv[1]), xml)
if not m:
    print("nao achei", sys.argv[1]); sys.exit(0)
x1, y1, x2, y2 = map(int, m.groups())
subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
PY
}

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
# Segurar VOL+ por 3 s e depois conferir que o controle continua respondendo (abre o teclado)
adb shell input swipe 540 1600 540 1000 300; sleep 1
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
B=$(python3 -c "import re;x=open('/tmp/ui.xml',encoding='utf-8').read();m=re.search(r'content-desc=\"VOL mais\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"',x);print('%d %d'%(((int(m[1])+int(m[3]))//2),((int(m[2])+int(m[4]))//2)) if m else '540 2100')")
adb shell input swipe $B $B 3000; sleep 3
tap_desc "Teclado numérico"; sleep 2
adb exec-out screencap -p > shots/06c-depois-de-segurar-volume.png
if [ -z "$(adb shell pidof $PKG)" ]; then echo "CRASH em 06c" >> shots/status.txt; else echo "ok 06c-segurar-volume" >> shots/status.txt; fi
shot 06-controle-tv --es screen device:d3
# Toques reais no controle: volume + duas vezes e abrir o teclado numérico
adb shell input swipe 540 1600 540 1000 300; sleep 1
tap_desc "VOL mais"; sleep 1; tap_desc "VOL mais"; sleep 2
tap_desc "Teclado numérico"; sleep 2
adb exec-out screencap -p > shots/06b-controle-tv-teclado.png
if [ -z "$(adb shell pidof $PKG)" ]; then echo "CRASH em 06b" >> shots/status.txt; else echo "ok 06b-toques" >> shots/status.txt; fi
shot 06-controle-tv --es screen device:d3
adb shell input swipe 540 1900 540 500 400; sleep 2
adb exec-out screencap -p > shots/07-controle-tv-baixo.png
shot 06d-tv-wifi --es screen device:d3
tap_desc "Conectar"
sleep 2
adb exec-out screencap -p > shots/06d-tv-wifi.png
shot 08-ar --es screen device:d4
shot 09-luz --es screen device:d1
shot 10-fechadura --es screen device:d7
shot 11-cortina --es screen device:d6
shot 12-sensor --es screen device:d14

adb logcat -d -b crash > shots/crash.txt 2>&1
adb logcat -d '*:E' | grep -i -A20 "casahub\|FATAL" | head -200 > shots/erros.txt 2>&1
exit 0
