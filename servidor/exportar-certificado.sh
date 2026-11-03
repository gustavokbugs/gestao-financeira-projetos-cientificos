#!/usr/bin/env bash
# Modo TLS=internal: copia o certificado raiz do Caddy para o app Android e ativa a confiança nele.
# Rode no PC servidor, dentro da pasta servidor/, depois do primeiro "docker compose up -d".
# Em seguida: commit do .crt e dos .xml, e recompile o app.
set -euo pipefail
cd "$(dirname "$0")"
APP="../android/app/src"
DESTINO="$APP/main/res/raw/servidor_ca.crt"

mkdir -p "$(dirname "$DESTINO")"
docker compose exec -T caddy cat /data/caddy/pki/authorities/local/root.crt > "$DESTINO"
grep -q 'BEGIN CERTIFICATE' "$DESTINO" || { echo "Falha ao ler o certificado do Caddy"; rm -f "$DESTINO"; exit 1; }

for xml in "$APP/main/res/xml/network_security_config.xml" "$APP/debug/res/xml/network_security_config.xml"; do
  sed -i 's#<!-- SERVIDOR_CA \(.*\) SERVIDOR_CA -->#\1#' "$xml"
done

echo "Certificado salvo em android/app/src/main/res/raw/servidor_ca.crt e confiança ativada."
echo "O certificado raiz vale por 10 anos; o Caddy renova sozinho os certificados do site."
