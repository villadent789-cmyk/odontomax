-- =============================================================================
-- V8: Horarios de Especialistas, Reprogramación de Citas y Control de Turnos
-- =============================================================================

-- 1. Tabla de Horarios de Especialistas por Sede y Día de Semana
CREATE TABLE IF NOT EXISTS especialista_horarios (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    especialista_id uuid NOT NULL REFERENCES especialistas(id) ON DELETE CASCADE,
    sede_id         text NOT NULL REFERENCES sedes(id) ON DELETE CASCADE,
    dia_semana      smallint NOT NULL CHECK (dia_semana BETWEEN 1 AND 7), -- 1=Lunes, 7=Domingo
    hora_inicio     time NOT NULL,
    hora_fin        time NOT NULL,
    activo          boolean NOT NULL DEFAULT true,
    creado_en       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_esp_horario_valido CHECK (hora_fin > hora_inicio)
);

CREATE INDEX IF NOT EXISTS idx_esp_horarios_lookup 
ON especialista_horarios(especialista_id, dia_semana, activo);

-- 2. Columna para motivo de reprogramación en citas (si no existe)
ALTER TABLE citas ADD COLUMN IF NOT EXISTS motivo_reprogramacion text;

-- 3. Habilitar RLS en especialista_horarios
ALTER TABLE IF EXISTS especialista_horarios ENABLE ROW LEVEL SECURITY;

-- 4. Semilla de horarios predeterminados para especialistas activos
-- Dr. Alex Jhoel Ríos Romero (Ortodoncia): Lunes a Viernes 09:00 - 18:00
INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo)
SELECT '10000000-0000-0000-0000-000000000001', 'sede-chilca-1', d, TIME '09:00', TIME '18:00', true
FROM generate_series(1, 5) AS d
ON CONFLICT DO NOTHING;

-- Dra. Thalía Vilcapoma Palma (Odontopediatría): Lunes, Miércoles, Viernes 09:00 - 17:00
INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo)
SELECT '10000000-0000-0000-0000-000000000002', 'sede-chilca-2', d, TIME '09:00', TIME '17:00', true
FROM (VALUES (1), (3), (5)) AS v(d)
ON CONFLICT DO NOTHING;

-- Dr. Roberto Paz (Cirugía Bucal): Martes, Jueves, Sábado 10:00 - 19:00
INSERT INTO especialista_horarios (especialista_id, sede_id, dia_semana, hora_inicio, hora_fin, activo)
SELECT '10000000-0000-0000-0000-000000000003', 'sede-eltambo', d, TIME '10:00', TIME '19:00', true
FROM (VALUES (2), (4), (6)) AS v(d)
ON CONFLICT DO NOTHING;
