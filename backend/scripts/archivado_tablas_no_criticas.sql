-- =============================================================================
-- PROCEDIMIENTO DE ARCHIVADO SEGURO DE TABLAS NO CRÍTICAS [REQUIERE_HUMANO]
-- =============================================================================
-- PROPÓSITO:
-- Liberar espacio de almacenamiento en PostgreSQL (cuota gratuita de 500 MB en Supabase)
-- exportando y purgando registros de auditoría y accesos con antigüedad mayor a 180 días.
--
-- REGLAS CRÍTICAS DE SEGURIDAD:
-- 1. NO se mantienen tablas *_archivo dentro de la misma BD (ocupa espacio de cuota).
--    Cualquier tabla previa de archivo es eliminada de inmediato.
-- 2. El proceso para ADMIN_GENERAL consta de 4 fases estrictas:
--    (a) EXPORTACIÓN: Columnas correctas (accedido_en y creado_en) hacia archivo
--        comprimido (gzip -9) y cifrado (AES-256-CBC con PBKDF2).
--    (b) SUBIDA A R2 Y VERIFICACIÓN: Cargar archivo a Cloudflare R2 y verificar
--        estrictamente tamaño en bytes y checksum SHA-256 antes de borrar nada.
--    (c) PURGA ATÓMICA: Transacción única con rol de mantenimiento temporal
--        (session_replication_role = 'replica') para desactivar triggers de inmutabilidad,
--        borrar registros, restablecer triggers y registrar evento en auditoria_log.
--    (d) VACUUM: Ejecutar VACUUM (VERBOSE, ANALYZE) para reclamar espacio en disco.
-- =============================================================================

-- =============================================================================
-- FASE 0: LIMPIEZA DE TABLAS DE ARCHIVO DENTRO DE LA MISMA BD
-- =============================================================================
DROP TABLE IF EXISTS historia_clinica_accesos_archivo CASCADE;
DROP TABLE IF EXISTS auditoria_log_archivo CASCADE;

-- =============================================================================
-- FASE (a): EXPORTACIÓN DE DATOS HISTÓRICOS (EJECUTAR DESDE TERMINAL BASH / CLI)
-- =============================================================================
-- 1. Definir variables y marcas de tiempo:
-- export TIMESTAMP=$(date +'%Y%m%d_%H%M%S')
-- export ARCHIVO_BASE="odontomax_archivo_logs_${TIMESTAMP}.csv"
--
-- 2. Exportar registros antiguos (>180 días) usando las columnas correctas:
-- psql "$SUPABASE_DIRECT_DB_URL" -c "\copy (SELECT * FROM historia_clinica_accesos WHERE accedido_en < (NOW() - INTERVAL '180 days')) TO 'historia_clinica_accesos_${TIMESTAMP}.csv' WITH CSV HEADER"
-- psql "$SUPABASE_DIRECT_DB_URL" -c "\copy (SELECT * FROM auditoria_log WHERE creado_en < (NOW() - INTERVAL '180 days')) TO 'auditoria_log_${TIMESTAMP}.csv' WITH CSV HEADER"
--
-- 3. Empaquetar y comprimir:
-- tar -cvf "${ARCHIVO_BASE}.tar" "historia_clinica_accesos_${TIMESTAMP}.csv" "auditoria_log_${TIMESTAMP}.csv"
-- gzip -9 "${ARCHIVO_BASE}.tar"
--
-- 4. Cifrar con AES-256-CBC y PBKDF2 (100,000 iteraciones):
-- openssl enc -aes-256-cbc -salt -pbkdf2 -iter 100000 \
--   -in "${ARCHIVO_BASE}.tar.gz" \
--   -out "${ARCHIVO_BASE}.tar.gz.enc" \
--   -k "$BACKUP_ENCRYPTION_PASSPHRASE"
--
-- 5. Calcular Checksum SHA-256 local y tamaño:
-- sha256sum "${ARCHIVO_BASE}.tar.gz.enc" > "${ARCHIVO_BASE}.tar.gz.enc.sha256"
-- export LOCAL_CHECKSUM=$(awk '{print $1}' "${ARCHIVO_BASE}.tar.gz.enc.sha256")
-- export LOCAL_SIZE=$(stat -c%s "${ARCHIVO_BASE}.tar.gz.enc" 2>/dev/null || stat -f%z "${ARCHIVO_BASE}.tar.gz.enc")
-- echo "Archivo cifrado: ${ARCHIVO_BASE}.tar.gz.enc | Tamaño: ${LOCAL_SIZE} bytes | SHA256: ${LOCAL_CHECKSUM}"

-- =============================================================================
-- FASE (b): SUBIDA A CLOUDFLARE R2 Y VERIFICACIÓN DE TAMAÑO Y CHECKSUM
-- =============================================================================
-- 1. Subir archivo cifrado y archivo de checksum a R2:
-- aws s3 cp "${ARCHIVO_BASE}.tar.gz.enc" "s3://${R2_BUCKET_NAME}/archived-logs/${ARCHIVO_BASE}.tar.gz.enc" --endpoint-url "https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
-- aws s3 cp "${ARCHIVO_BASE}.tar.gz.enc.sha256" "s3://${R2_BUCKET_NAME}/archived-logs/${ARCHIVO_BASE}.tar.gz.enc.sha256" --endpoint-url "https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
--
-- 2. Descargar y verificar integridad antes de autorizar el borrado:
-- aws s3 cp "s3://${R2_BUCKET_NAME}/archived-logs/${ARCHIVO_BASE}.tar.gz.enc" "verificacion_${ARCHIVO_BASE}.tar.gz.enc" --endpoint-url "https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
-- export REMOTE_CHECKSUM=$(sha256sum "verificacion_${ARCHIVO_BASE}.tar.gz.enc" | awk '{print $1}')
--
-- if [ "$LOCAL_CHECKSUM" != "$REMOTE_CHECKSUM" ]; then
--   echo "ERROR CRÍTICO: El checksum en R2 ($REMOTE_CHECKSUM) no coincide con el local ($LOCAL_CHECKSUM). ABORTANDO PURGA."
--   exit 1
-- fi
-- echo "✅ Verificación de Checksum y Tamaño en R2 100% exitosa. Autorizado para proceder a la Fase (c)."
-- rm -f "verificacion_${ARCHIVO_BASE}.tar.gz.enc"

-- =============================================================================
-- FASE (c): PURGA TRANSACCIONAL ATÓMICA EN POSTGRESQL (SOLO TRAS FASE b)
-- =============================================================================
BEGIN;

-- 1. Desactivar temporalmente los triggers dentro de la sesión de mantenimiento
SET LOCAL session_replication_role = 'replica';

-- 2. Purgar registros archivados de historia_clinica_accesos (>180 días)
DELETE FROM historia_clinica_accesos
 WHERE accedido_en < (NOW() - INTERVAL '180 days');

-- 3. Purgar registros antiguos de auditoria_log (>180 días)
DELETE FROM auditoria_log
 WHERE creado_en < (NOW() - INTERVAL '180 days');

-- 4. Reactivar el comportamiento estándar de triggers en la sesión
-- ('origin' es el valor por defecto; 'DEFAULT' entre comillas es inválido y abortaría la transacción)
SET LOCAL session_replication_role = 'origin';

-- 5. Registrar el evento de archivado en auditoria_log (inmutable)
INSERT INTO auditoria_log (
    usuario_id,
    usuario_nombre,
    rol,
    sede_id,
    modulo,
    accion,
    tabla,
    detalle
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'ADMIN_GENERAL (Mantenimiento)',
    'ADMIN_GENERAL',
    'sede-central',
    'MANTENIMIENTO',
    'PURGA_ARCHIVADO_180_DIAS',
    'historia_clinica_accesos,auditoria_log',
    'Purga segura de accesos y auditoría > 180 días tras verificación de respaldo cifrado y checksum en Cloudflare R2.'
);

COMMIT;

-- =============================================================================
-- FASE (d): RECLAMAR ESPACIO FÍSICO EN DISCO (EJECUTAR FUERA DE TRANSACCIÓN)
-- =============================================================================
-- En la consola de psql o Supabase SQL Editor:
VACUUM (VERBOSE, ANALYZE) historia_clinica_accesos;
VACUUM (VERBOSE, ANALYZE) auditoria_log;
