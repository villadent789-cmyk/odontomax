#!/usr/bin/env bash
# =============================================================================
# SCRIPT DE RESTAURACIÓN MANUAL DE BASE DE DATOS ODONTOMAX [REQUIERE_HUMANO]
# =============================================================================
# ADVERTENCIA CRÍTICA:
# Este script restaura un volcado PostgreSQL cifrado (.sql.gz.enc) sobre la base
# de datos indicada. Esta acción es DESTRUCTIVA e irreversible sobre los datos
# existentes en el esquema public. Exige intervención y validación humana.
#
# CONEXIÓN RECOMENDADA (SUPABASE POOLER):
# - Host: aws-0-[region].pooler.supabase.com
# - Puerto: 5432 (Session Mode) - NO usar el puerto 6543 (Transaction Mode).
# - Usuario: postgres.<project_ref> (ej: postgres.abcdefghijklmnopqrst)
# - Base de datos: postgres
# =============================================================================

set -euo pipefail

echo "================================================================="
echo "    ODONTOMAX - RESTAURACIÓN DE RESPALDO CIFRADO POSTGRESQL     "
echo "================================================================="

if [ "$#" -lt 1 ]; then
    echo "Uso: $0 <archivo_respaldo.sql.gz.enc> [host] [puerto] [nombre_bd] [usuario]"
    echo ""
    echo "Ejemplos:"
    echo "  1. Directo local:"
    echo "     $0 odontomax_backup_20261001_020000.sql.gz.enc localhost 5432 postgres postgres"
    echo "  2. Supabase Session Pooler:"
    echo "     $0 odontomax_backup_20261001_020000.sql.gz.enc aws-0-us-west-1.pooler.supabase.com 5432 postgres postgres.mi-proyecto-ref"
    exit 1
fi

ENC_FILE="$1"
DB_HOST="${2:-${DB_HOST:-localhost}}"
DB_PORT="${3:-${DB_PORT:-5432}}"
DB_NAME="${4:-${DB_NAME:-postgres}}"
DB_USER="${5:-${DB_USER:-postgres}}"

if [ ! -f "$ENC_FILE" ]; then
    echo "ERROR: El archivo de respaldo cifrado '$ENC_FILE' no existe."
    exit 1
fi

# Validar dependencias necesarias
command -v openssl >/dev/null 2>&1 || { echo "ERROR: openssl no está instalado en el sistema."; exit 1; }
command -v gzip >/dev/null 2>&1 || { echo "ERROR: gzip no está instalado en el sistema."; exit 1; }
command -v psql >/dev/null 2>&1 || { echo "ERROR: psql (cliente PostgreSQL) no está instalado."; exit 1; }

# Verificar checksum sha256 si existe
if [ -f "${ENC_FILE}.sha256" ]; then
    echo "Validando checksum SHA-256 de ${ENC_FILE}..."
    sha256sum -c "${ENC_FILE}.sha256"
    echo "Integridad verificada correctamente."
fi

echo ""
echo "Archivo a restaurar: $ENC_FILE"
echo "Destino: postgresql://$DB_USER@$DB_HOST:$DB_PORT/$DB_NAME"
echo ""
echo "⚠️  ADVERTENCIA DE SEGURIDAD [REQUIERE_HUMANO]:"
echo "La restauración reemplazará tablas, secuencias y vistas del esquema 'public'."
read -r -p "Escriba 'RESTAURAR' para confirmar la operación: " CONFIRMACION

if [ "$CONFIRMACION" != "RESTAURAR" ]; then
    echo "Operación cancelada por el usuario. No se modificó la base de datos."
    exit 0
fi

TEMP_DIR=$(mktemp -d /tmp/odontomax_restore_XXXXXX)
chmod 700 "$TEMP_DIR"
DECRYPTED_GZ="$TEMP_DIR/dump.sql.gz"
DECRYPTED_SQL="$TEMP_DIR/dump.sql"

cleanup() {
    echo "Limpiando archivos temporales descifrados de forma segura..."
    if command -v shred >/dev/null 2>&1; then
        shred -u "$DECRYPTED_GZ" "$DECRYPTED_SQL" 2>/dev/null || true
    fi
    rm -rf "$TEMP_DIR"
    echo "Limpieza completada."
}
trap cleanup EXIT

if [ -z "${BACKUP_ENCRYPTION_PASSPHRASE:-}" ]; then
    echo ""
    echo "Ingrese la frase secreta de descifrado (BACKUP_ENCRYPTION_PASSPHRASE):"
    read -r -s PASSPHRASE
else
    PASSPHRASE="$BACKUP_ENCRYPTION_PASSPHRASE"
fi

echo "Descifrando respaldo con OpenSSL AES-256-CBC..."
openssl enc -d -aes-256-cbc -pbkdf2 -iter 100000 \
    -in "$ENC_FILE" \
    -out "$DECRYPTED_GZ" \
    -k "$PASSPHRASE"

echo "Descomprimiendo archivo de volcado..."
gzip -d -c "$DECRYPTED_GZ" > "$DECRYPTED_SQL"

echo ""
echo "Preparando extensiones requeridas en la base de datos destino..."
echo "Habilitando extensiones obligatorias: btree_gist, pgcrypto..."
PGPASSWORD="${DB_PASSWORD:-}" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "CREATE EXTENSION IF NOT EXISTS btree_gist; CREATE EXTENSION IF NOT EXISTS pgcrypto;"

echo "Iniciando aplicación del script SQL en la base de datos destino..."
PGPASSWORD="${DB_PASSWORD:-}" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -f "$DECRYPTED_SQL"

echo ""
echo "================================================================="
echo "✅  RESTAURACIÓN COMPLETADA SATISFACTORIAMENTE"
echo "================================================================="
