-- V26: Persistencia de seguridad para tolerancia a reinicios (brute force, 2FA pendiente, rate limit recuperación)

CREATE TABLE IF NOT EXISTS seguridad_login_intentos (
    clave VARCHAR(255) PRIMARY KEY,
    intentos INT NOT NULL,
    primer_intento BIGINT NOT NULL,
    expira_en TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_seguridad_login_intentos_expira
    ON seguridad_login_intentos (expira_en);

CREATE TABLE IF NOT EXISTS seguridad_login_2fa_pendientes (
    clave VARCHAR(255) PRIMARY KEY,
    intentos INT NOT NULL,
    invalidado BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en BIGINT NOT NULL,
    expira_en TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_seguridad_login_2fa_expira
    ON seguridad_login_2fa_pendientes (expira_en);

CREATE TABLE IF NOT EXISTS seguridad_recuperacion_solicitudes (
    id BIGSERIAL PRIMARY KEY,
    clave VARCHAR(255) NOT NULL,
    solicitado_en BIGINT NOT NULL,
    expira_en TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_seguridad_recuperacion_clave_tiempo
    ON seguridad_recuperacion_solicitudes (clave, solicitado_en);

CREATE INDEX IF NOT EXISTS idx_seguridad_recuperacion_expira
    ON seguridad_recuperacion_solicitudes (expira_en);
