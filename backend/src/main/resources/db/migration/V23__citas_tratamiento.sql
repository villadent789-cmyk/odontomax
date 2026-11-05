-- =============================================================================
-- V23 · Cita enlazada a un plan de tratamiento
-- La columna citas.tratamiento_id existe desde V1 pero ningún código la usaba
-- (siempre NULL). Desde ahora una cita puede ser una sesión de un plan y, al
-- marcarla ATENDIDA, se suma una sesión realizada al plan.
--
-- La FK original de V1 no tenía acción al borrar: eliminar un plan con citas
-- enlazadas fallaría. Se reemplaza por ON DELETE SET NULL (la cita se conserva
-- sin enlace) y se agrega un índice para buscar las citas de un plan.
-- =============================================================================

ALTER TABLE citas DROP CONSTRAINT IF EXISTS citas_tratamiento_id_fkey;
ALTER TABLE citas DROP CONSTRAINT IF EXISTS fk_citas_tratamiento;

ALTER TABLE citas ADD CONSTRAINT fk_citas_tratamiento
    FOREIGN KEY (tratamiento_id) REFERENCES tratamientos (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_citas_tratamiento ON citas (tratamiento_id) WHERE tratamiento_id IS NOT NULL;
