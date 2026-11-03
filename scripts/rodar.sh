#!/usr/bin/env bash
# Sobe o banco + API (docker compose), inicia o emulador, compila e instala o app.
# Uso: ./scripts/rodar.sh [nome-do-avd]   (padrão: Pixel_API_36)
set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
AVD="${1:-Pixel_API_36}"
export JAVA_HOME="${JAVA_HOME:-$HOME/android-studio/jbr}"

echo "> Subindo PostgreSQL + API (docker compose)"
(cd "$RAIZ" && docker compose up -d --build)
until curl -sf http://localhost:3000/api/v1/status >/dev/null; do sleep 2; done

if ! "$ADB" devices | grep -q 'emulator-'; then
  echo "> Iniciando emulador $AVD"
  QT_QPA_PLATFORM=xcb nohup "$SDK/emulator/emulator" -avd "$AVD" -no-snapshot-save > /dev/null 2>&1 &
fi

echo "> Aguardando o Android iniciar"
"$ADB" wait-for-device
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done

echo "> Compilando e instalando"
(cd "$RAIZ/android" && ./gradlew installDebug -q)
"$ADB" shell am start -n br.gestaofinanceira.app/.ui.login.LoginActivity > /dev/null
echo "> Pronto. Login: coordenador@exemplo.com / gestor@exemplo.com / consulta@exemplo.com (senha: senha123)"
