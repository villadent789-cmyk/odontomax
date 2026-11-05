#!/usr/bin/env bash
# Prueba que una copia de seguridad SE PUEDE RESTAURAR: hace el mismo volcado que .github/workflows/backup-db-r2.yml
# (pg_dump formato custom -> gzip -> AES-256 con openssl), lo descifra, lo restaura en una base nueva y compara las filas de
# todas las tablas con las de la base original. Solo usa Docker: no toca nada fuera de una base temporal que se borra al final.
#
# Uso:  scripts/probar-restauracion.sh [contenedor-origen]     (por defecto: odontomax-demo-db)
# Variables opcionales: PGUSER (odontomax), PGDATABASE (odontomax)
set -euo pipefail

ORIGEN="${1:-odontomax-demo-db}"
PGUSER="${PGUSER:-odontomax}"
PGDATABASE="${PGDATABASE:-odontomax}"
DESTINO="odontomax-restauracion-prueba"
CLAVE="clave-solo-para-esta-prueba"
# Carpeta temporal relativa (el openssl de Windows no entiende /tmp de Git Bash); se borra al terminar.
TMP="$(mktemp -d ./restauracion.XXXXXX)"

limpiar() {
  docker rm -f "$DESTINO" >/dev/null 2>&1 || true
  rm -rf "$TMP"
}
trap limpiar EXIT

echo "==> 1/5 Volcando $ORIGEN (pg_dump custom + gzip + AES-256)"
docker exec "$ORIGEN" pg_dump -U "$PGUSER" -d "$PGDATABASE" --format=custom | gzip > "$TMP/dump.gz"
openssl enc -aes-256-cbc -pbkdf2 -iter 100000 -in "$TMP/dump.gz" -out "$TMP/dump.enc" -pass "pass:$CLAVE"
echo "    copia cifrada: $(wc -c < "$TMP/dump.enc") bytes"

echo "==> 2/5 Descifrando"
openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 -in "$TMP/dump.enc" -out "$TMP/restaurar.gz" -pass "pass:$CLAVE"
gunzip -c "$TMP/restaurar.gz" > "$TMP/restaurar.dump"

echo "==> 3/5 Levantando una base vacía y temporal"
docker rm -f "$DESTINO" >/dev/null 2>&1 || true
docker run -d --name "$DESTINO" -e POSTGRES_USER="$PGUSER" -e POSTGRES_PASSWORD=prueba -e POSTGRES_DB="$PGDATABASE" postgres:15-alpine >/dev/null
for _ in $(seq 1 30); do
  docker exec "$DESTINO" pg_isready -U "$PGUSER" -d "$PGDATABASE" >/dev/null 2>&1 && break
  sleep 2
done
sleep 3

echo "==> 4/5 Restaurando"
docker cp "$TMP/restaurar.dump" "$DESTINO:/tmp/restaurar.dump"
docker exec "$DESTINO" pg_restore -U "$PGUSER" -d "$PGDATABASE" --no-owner --exit-on-error /tmp/restaurar.dump

echo "==> 5/5 Comparando filas de cada tabla"
CONSULTA="select format('select %L, count(*) from %I.%I', table_name, table_schema, table_name) from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by table_name"
contar() {
  local contenedor="$1"
  docker exec "$contenedor" psql -U "$PGUSER" -d "$PGDATABASE" -tAc "$CONSULTA" \
    | while read -r sql; do docker exec "$contenedor" psql -U "$PGUSER" -d "$PGDATABASE" -tAF'|' -c "$sql"; done
}
contar "$ORIGEN" > "$TMP/origen.txt"
contar "$DESTINO" > "$TMP/destino.txt"

TABLAS="$(wc -l < "$TMP/origen.txt" | tr -d ' ')"
if diff "$TMP/origen.txt" "$TMP/destino.txt" >/dev/null; then
  echo "OK: la copia se restauró y las $TABLAS tablas tienen exactamente las mismas filas."
else
  echo "ERROR: la restauración no coincide con el original:"
  diff "$TMP/origen.txt" "$TMP/destino.txt" || true
  exit 1
fi
