-- V43: Soporte para cierre explícito de planes de tratamiento (TERMINADO, INTERRUMPIDO) y motivo de cierre.

ALTER TABLE tratamientos DROP CONSTRAINT IF EXISTS tratamientos_estado_check;
ALTER TABLE tratamientos ADD CONSTRAINT tratamientos_estado_check
    CHECK (estado IN ('PLANIFICADO', 'EN_PROCESO', 'COMPLETADO', 'TERMINADO', 'INTERRUMPIDO', 'CANCELADO'));

ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS motivo_cierre text;
ALTER TABLE tratamientos ADD COLUMN IF NOT EXISTS cerrado_en timestamptz;
