-- =============================================================================
-- PROCEDIMIENTO DE RECUPERACIÓN DE CONTINGENCIA: ADMINISTRADOR GENERAL 2FA
-- =============================================================================
-- ETIQUETA: [REQUIERE_HUMANO]
-- UBICACIÓN: /backend/scripts/recuperacion_2fa_admin.sql
-- MOTIVO: Pérdida o daño irreparable del dispositivo autenticador (TOTP) o
-- agotamiento de códigos de respaldo del Administrador General de OdontoMax.
--
-- POLÍTICA DE SEGURIDAD OBLIGATORIA:
-- 1. Este script SOLO debe ser ejecutado por el Oficial de Seguridad de la Información,
--    DBA o Director Médico tras validar presencialmente o mediante videoconferencia
--    grabada la identidad del usuario solicitante (DNI físico vigente).
-- 2. Registrar el número de ticket de soporte o acta de contingencia firmada.
-- 3. Una vez ejecutado este script, el Administrador General DEBE:
--    a) Iniciar sesión de inmediato con su correo y contraseña institucional.
--    b) Cambiar su contraseña inmediatamente.
--    c) Enrolarse obligatoriamente en un nuevo autenticador TOTP (escaneando el nuevo QR)
--       y respaldar los 5 nuevos códigos de recuperación de un solo uso en bóveda segura.
-- =============================================================================

BEGIN;

-- 1. Declarar correo objetivo y verificar existencia y rol
DO $$
DECLARE
    target_email CONSTANT text := 'admin@odontomax.pe'; -- [REQUIERE_HUMANO]: Ajustar si aplica a otro correo admin
    admin_id uuid;
    admin_nombre text;
    admin_rol text;
    is_2fa_active boolean;
BEGIN
    SELECT id, nombre, rol, totp_activado
    INTO admin_id, admin_nombre, admin_rol, is_2fa_active
    FROM usuarios
    WHERE lower(email) = lower(target_email) AND activo = true;

    IF admin_id IS NULL THEN
        RAISE EXCEPTION 'ERROR DE SEGURIDAD: Usuario no encontrado o inactivo con email: %', target_email;
    END IF;

    IF admin_rol != 'ADMIN_GENERAL' THEN
        RAISE EXCEPTION 'ERROR DE SEGURIDAD: Este procedimiento es exclusivo para ADMIN_GENERAL. El rol actual es: %', admin_rol;
    END IF;

    RAISE NOTICE 'Iniciando recuperación de 2FA para usuario: % (ID: %, Rol: %, 2FA Previo: %)',
        target_email, admin_id, admin_rol, is_2fa_active;

    -- 2. Restablecer parámetros de 2FA (TOTP)
    -- Los secretos y códigos de respaldo cifrados son eliminados
    UPDATE usuarios
    SET totp_activado = false,
        totp_secret = NULL,
        totp_backup_codes = NULL,
        actualizado_en = NOW()
    WHERE id = admin_id;

    -- 3. Registrar auditoría inmutable de la intervención manual
    INSERT INTO auditoria_sistema (
        id,
        usuario_id,
        usuario_nombre,
        rol,
        sede_id,
        modulo,
        accion,
        tabla,
        detalle,
        ip,
        creado_en
    ) VALUES (
        gen_random_uuid(),
        admin_id,
        admin_nombre,
        admin_rol,
        'CENTRAL',
        'SEGURIDAD_CONTINGENCIA',
        'RECUPERACION_2FA_MANUAL',
        'usuarios',
        '[REQUIERE_HUMANO] Desactivación manual de 2FA por contingencia de administrador ejecutada vía script SQL. Requiere re-enrolamiento inmediato.',
        '127.0.0.1 (DBA Console)',
        NOW()
    );

    RAISE NOTICE 'Recuperación completada exitosamente para %. 2FA ha sido desactivado temporalmente.', target_email;
END $$;

COMMIT;

-- 4. Verificación final de estado del usuario
SELECT id, nombre, email, rol, totp_activado, totp_secret IS NOT NULL AS tiene_secreto, actualizado_en
FROM usuarios
WHERE lower(email) = 'admin@odontomax.pe';
