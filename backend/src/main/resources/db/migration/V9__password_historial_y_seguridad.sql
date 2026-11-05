-- =============================================================================
-- V9: HISTORIAL DE CONTRASEÑAS, TOKENS DE RECUPERACIÓN Y SEGURIDAD 2FA (TOTP)
-- =============================================================================

CREATE TABLE IF NOT EXISTS password_historial (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      uuid NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    password_hash   text NOT NULL,
    creado_en       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_password_historial_usuario ON password_historial (usuario_id, creado_en DESC);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      uuid NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash      text NOT NULL UNIQUE,
    expira_en       timestamptz NOT NULL,
    usado           boolean NOT NULL DEFAULT false,
    creado_en       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_password_reset_token ON password_reset_tokens (token_hash);

-- Columnas para autenticación de dos factores (TOTP RFC 6238)
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS totp_secret text;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS totp_activado boolean NOT NULL DEFAULT false;
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS totp_backup_codes text;

-- Habilitar Row Level Security (RLS) y revocar accesos directos desde PostgREST
ALTER TABLE password_historial ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;

-- Actualizar plantillas de consentimiento para reflejar explícitamente el estado de borrador legal
UPDATE plantillas_consentimiento
   SET contenido = '[BORRADOR - Requiere validación por asesor legal y colegiatura profesional] ' || contenido
 WHERE contenido NOT LIKE '[BORRADOR%';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON password_historial, password_reset_tokens FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON password_historial, password_reset_tokens FROM authenticated';
    END IF;
END $$;
