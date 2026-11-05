-- =============================================================================
-- V16 · Pacientes v2 · Datos de Marketing, Captación y Ley 29733 (Datos Personales)
-- =============================================================================

ALTER TABLE pacientes
    ADD COLUMN IF NOT EXISTS genero text,
    ADD COLUMN IF NOT EXISTS referido_por_paciente_id uuid REFERENCES pacientes(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS referido_por_paciente_nombre text,
    ADD COLUMN IF NOT EXISTS doctor_referidor text,
    ADD COLUMN IF NOT EXISTS consentimiento_promociones boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS consentimiento_promociones_fecha timestamptz,
    ADD COLUMN IF NOT EXISTS numero_historia_clinica text,
    ADD COLUMN IF NOT EXISTS total_inasistencias integer NOT NULL DEFAULT 0;

-- Asegurar restricción de valores válidos para género (si no es nulo)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'pacientes_genero_check'
    ) THEN
        ALTER TABLE pacientes
            ADD CONSTRAINT pacientes_genero_check
            CHECK (genero IS NULL OR genero IN ('MASCULINO', 'FEMENINO', 'OTRO'));
    END IF;
END $$;

-- Índice para acelerar reportes de marketing por fuente de captación
CREATE INDEX IF NOT EXISTS pacientes_fuente_captacion_idx ON pacientes (fuente_captacion) WHERE NOT archivado;
CREATE INDEX IF NOT EXISTS pacientes_fecha_nacimiento_idx ON pacientes (fecha_nacimiento) WHERE NOT archivado;
