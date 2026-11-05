-- =============================================================================
-- V54 · Doctor derivador obligatorio en Villamax y comisión automática al cobrar
-- =============================================================================

ALTER TABLE citas
    ADD COLUMN IF NOT EXISTS doctor_derivador_id UUID REFERENCES doctores_derivadores(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS paciente_directo BOOLEAN DEFAULT FALSE;

ALTER TABLE comisiones_radiologicas
    ADD COLUMN IF NOT EXISTS cita_id UUID REFERENCES citas(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS tratamiento_id UUID REFERENCES tratamientos(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_citas_doctor_derivador ON citas(doctor_derivador_id);
CREATE INDEX IF NOT EXISTS idx_comisiones_cita ON comisiones_radiologicas(cita_id);
CREATE INDEX IF NOT EXISTS idx_comisiones_tratamiento ON comisiones_radiologicas(tratamiento_id);
